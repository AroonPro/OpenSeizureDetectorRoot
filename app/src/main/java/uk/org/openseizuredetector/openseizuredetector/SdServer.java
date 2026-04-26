package uk.org.openseizuredetector.openseizuredetector;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.telephony.SmsManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SdServer - en_GB
 * Root service logic with Heartbeat-driven persistence and forensic state management.
 * Protocol: Self-healing SSH tunnels and zero-allocation logging.
 */
public abstract class SdServer extends Service implements SdDataReceiver {
    protected static final String TAG = "SdServer";
    protected static final String CHANNEL_ID = "OSD_Service_Channel";
    protected static final int NOTIFICATION_ID = 101;
    
    public SdData mSdData = new SdData();
    public SdDataSource mSdDataSource;
    
    protected NotificationManager mNM;
    protected ToneGenerator mToneGenerator;
    protected Vibrator mVibrator;
    protected PowerManager.WakeLock mWakeLock;
    protected WifiManager.WifiLock mWifiLock;
    protected SdWebServer webServer;

    protected boolean mAlarmActive = false;
    protected boolean mAlarmMutedByUser = false;
    protected boolean mSshTunnelConfirmed = false;
    protected String[] mSMSNumbers = new String[0];

    protected SshClient mSshClient;
    protected EncryptedSettingsManager mSettingsManager;
    protected HealthServicesManager mHealthManager;

    protected final Handler mHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService mBackgroundExecutor = Executors.newFixedThreadPool(2);

    private final Calendar mCalendar = Calendar.getInstance();
    private final Date mRecycledDate = new Date();
    private final SimpleDateFormat mLogTimestampFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
    private final SimpleDateFormat mFileNameDateFormat = new SimpleDateFormat("yyMMdd", Locale.US);
    private final StringBuilder mRecycledLogBuilder = new StringBuilder();

    private int mLastLoggedDay = -1;

    private final BroadcastReceiver mDisplayReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                Log.i(TAG, "Screen ON: Triggering link validation.");
                performHeartbeatCheck();
            }
        }
    };

    private final ConnectivityManager.NetworkCallback mNetworkCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(@NonNull Network network) {
            super.onAvailable(network);
            Log.i(TAG, "Network Recovery: Dialing SSH...");
            mHandler.post(() -> startSshTunnel());
        }
        @Override public void onLost(@NonNull Network network) { mSshTunnelConfirmed = false; }
    };

    private void acquireLocks() {
        if (mWakeLock != null && !mWakeLock.isHeld()) mWakeLock.acquire(10 * 60 * 1000L);
        if (mWifiLock != null && !mWifiLock.isHeld()) mWifiLock.acquire();
    }

    public class SdBinder extends Binder { public SdServer getService() { return SdServer.this; } }
    private final IBinder mBinder = new SdBinder();

    private final Runnable mHeartbeatTask = new Runnable() {
        @Override
        public void run() {
            performHeartbeatCheck();
            checkMidnightLog();
            mHandler.postDelayed(this, 30000); 
        }
    };

    @Override public IBinder onBind(Intent intent) { return mBinder; }

    @Override
    public void onCreate() {
        super.onCreate();
        mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        mToneGenerator = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
        mVibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OSD:CPUWakeLock");
        
        WifiManager wm = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        if (wm != null) mWifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "OSD:WifiLock");
        
        setupChannels();
        startOsdForeground();

        mSettingsManager = new EncryptedSettingsManager(getApplicationContext());
        mHealthManager = new HealthServicesManager(getApplicationContext(), mSdData);
        mHealthManager.startMonitoring();
        
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            cm.registerDefaultNetworkCallback(mNetworkCallback);
        }

        registerReceiver(mDisplayReceiver, new IntentFilter(Intent.ACTION_SCREEN_ON));
        
        startSshTunnel();
        startWebServer();
        mHandler.postDelayed(mHeartbeatTask, 5000);
        
        mLastLoggedDay = Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
    }

    private void checkMidnightLog() {
        mCalendar.setTimeInMillis(System.currentTimeMillis());
        int currentDay = mCalendar.get(Calendar.DAY_OF_YEAR);
        if (currentDay != mLastLoggedDay) {
            mLastLoggedDay = currentDay;
            performMidnightLog();
        }
    }

    private void performMidnightLog() {
        final String localPath = mSettingsManager.getMidnightLogPath();
        mBackgroundExecutor.execute(() -> {
            synchronized (mRecycledLogDateSync) {
                try {
                    mRecycledDate.setTime(System.currentTimeMillis());
                    String timeStr = mLogTimestampFormat.format(mRecycledDate);
                    String datePart = mFileNameDateFormat.format(mRecycledDate);
                    
                    mRecycledLogBuilder.setLength(0);
                    mRecycledLogBuilder.append(timeStr).append(" - OSD Midnight Log - Status: ")
                                      .append(mSshTunnelConfirmed ? "Connected" : "Disconnected").append("\n");
                    final String statusLine = mRecycledLogBuilder.toString();

                    final File logFile = new File(localPath);
                    if (logFile.getParentFile() != null && !logFile.getParentFile().exists()) logFile.getParentFile().mkdirs();
                    try (FileWriter writer = new FileWriter(logFile, true)) { writer.write(statusLine); }

                    if (mSshClient != null && mSshClient.isConnected()) {
                        String remoteDir = "~/OpenSeizureDetectorLogs";
                        String remoteFile = remoteDir + "/OSD_LOG_wear_" + datePart + ".log";
                        String latestLink = remoteDir + "/OSD_LOG_wear_latest.log";
                        
                        String remoteCmd = "mkdir -p " + remoteDir + " && echo \"" + statusLine.trim() + "\" >> " + remoteFile + " && ln -sf " + remoteFile + " " + latestLink;
                        
                        mSshClient.executeRemoteCommand(remoteCmd, (success, msg) -> {
                            if (success) logFile.delete(); // Only delete locally if remote write confirmed
                        });
                    }
                } catch (Exception e) { Log.e(TAG, "Midnight log error", e); }
            }
        });
    }
    private final Object mRecycledLogDateSync = new Object();

    private void performHeartbeatCheck() {
        if (mSshClient == null) { startSshTunnel(); return; }
        mSshClient.sendHeartbeat((success, msg) -> {
            mSshTunnelConfirmed = success;
            if (mSdData != null) mSdData.serverOK = success;
            if (!success) {
                Log.w(TAG, "Heartbeat lost. Forcing clean reconnect.");
                mSshClient.disconnect(); // Clear dead session
                mHandler.post(this::startSshTunnel);
            }
        });
    }

    public boolean isSshActive() { return mSshTunnelConfirmed; }

    private void setupChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "OSD Status", NotificationManager.IMPORTANCE_LOW);
            mNM.createNotificationChannel(channel);
        }
    }

    private void startOsdForeground() {
        Intent intent = new Intent();
        intent.setClassName(getPackageName(), "uk.org.openseizuredetector.aw.StartUpActivityWear");
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("OSD Active")
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock) 
                .setContentIntent(pi)
                .setOngoing(true)
                .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                .build();
        startForeground(NOTIFICATION_ID, notification);
    }

    private void startWebServer() {
        try {
            if (webServer != null) webServer.stop();
            webServer = new SdWebServer(this, mSdData, this);
            webServer.start();
            mSdData.webServerAlive = true;
        } catch (IOException e) {
            mSdData.webServerAlive = false;
            mHandler.postDelayed(this::startWebServer, 10000);
        }
    }

    private void startSshTunnel() {
        SshCluster cluster = mSettingsManager.getSshConfig();
        if (cluster != null && cluster.host != null && !cluster.host.isEmpty()) {
            int adbPort = mSettingsManager.getAdbPort();
            if (cluster.tunnels == null) cluster.tunnels = new ArrayList<>();
            String adbTunnel = "L" + adbPort + ":127.0.0.1:" + adbPort;
            if (!cluster.tunnels.contains(adbTunnel)) cluster.tunnels.add(adbTunnel);

            if (mSshClient == null) mSshClient = new SshClient(getApplicationContext());
            mSshClient.connectAndUploadConfig(cluster, false, (success, msg) -> {
                mSshTunnelConfirmed = success;
                if (mSdData != null) mSdData.serverOK = success;
            });
        }
    }

    public void forceRestartSshTunnel() {
        SshCluster cluster = mSettingsManager.getSshConfig();
        if (cluster != null && cluster.host != null && !cluster.host.isEmpty()) {
            int adbPort = mSettingsManager.getAdbPort();
            if (cluster.tunnels == null) cluster.tunnels = new ArrayList<>();
            cluster.tunnels.add("L" + adbPort + ":127.0.0.1:" + adbPort);
            if (mSshClient == null) mSshClient = new SshClient(getApplicationContext());
            mSshClient.disconnect();
            mSshClient.connectAndUploadConfig(cluster, true, (success, msg) -> {
                mSshTunnelConfirmed = success;
                if (success) Log.i(TAG, "Forced SSH Restart Successful.");
            });
        }
    }

    @Override
    public void onSdDataReceived(final SdData sdData) {
        this.mSdData = sdData;
        if (webServer != null) webServer.setSdData(mSdData);
        if (sdData.alarmState >= 2) {
            if (!mAlarmActive && !mAlarmMutedByUser) {
                mAlarmActive = true;
                handleAlarmState(sdData);
            }
        } else {
            if (sdData.alarmState == 0) { mAlarmMutedByUser = false; sdData.alarmStanding = false; }
            mAlarmActive = false;
        }
    }

    public void acceptAlarm() {
        mAlarmMutedByUser = true; mAlarmActive = false; mSdData.alarmStanding = false; mSdData.alarmState = 0;
        if (mSdDataSource != null) mSdDataSource.ClearAlarmCount();
        if (mVibrator != null) mVibrator.cancel();
    }

    public void ClearAlarmCount() { if (mSdDataSource != null) mSdDataSource.ClearAlarmCount(); }

    public void sendSMSAlarm() {
        try {
            SmsManager sm = getSystemService(SmsManager.class);
            String msg = "OSD Alert! Location: http://maps.google.com/?q=" + mSdData.latitude + "," + mSdData.longitude;
            for (String num : mSMSNumbers) { if (num != null && !num.isEmpty()) sm.sendTextMessage(num, null, msg, null, null); }
        } catch (Exception e) { Log.e(TAG, "SMS Error: " + e.toString()); }
    }

    protected void handleAlarmState(SdData sdData) { sdData.alarmStanding = true; vibrate(1000); }

    private void vibrate(long d) {
        if (mVibrator == null || !mVibrator.hasVibrator()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) mVibrator.vibrate(VibrationEffect.createOneShot(d, VibrationEffect.DEFAULT_AMPLITUDE));
        else mVibrator.vibrate(d);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (mSdDataSource == null) { mSdDataSource = createDataSource(); if (mSdDataSource != null) mSdDataSource.start(); }
        acquireLocks();
        return START_STICKY;
    }

    protected abstract SdDataSource createDataSource();
    public abstract int getOsdForegroundServiceType();

    @Override public void onDestroy() {
        try { unregisterReceiver(mDisplayReceiver); } catch (Exception ignored) {}
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) cm.unregisterNetworkCallback(mNetworkCallback);
        mHandler.removeCallbacks(mHeartbeatTask);
        mBackgroundExecutor.shutdownNow();
        if (mSdDataSource != null) mSdDataSource.stop();
        if (mHealthManager != null) mHealthManager.stopMonitoring();
        if (webServer != null) webServer.stop();
        if (mWakeLock != null && mWakeLock.isHeld()) mWakeLock.release();
        if (mWifiLock != null && mWifiLock.isHeld()) mWifiLock.release();
        if (mSshClient != null) mSshClient.shutdown();
        super.onDestroy();
    }
}
