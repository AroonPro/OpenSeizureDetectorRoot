package uk.org.openseizuredetector.openseizuredetector;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Binder;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.telephony.SmsManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SdServer - en_GB
 * Root service logic with forensic state management and zero-churn resource handling.
 * Protocol: Managed WakeLock for sensor stability and tilt response.
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
    protected SdWebServer webServer;

    protected boolean mAlarmActive = false;
    protected boolean mAlarmMutedByUser = false;
    protected boolean mSshTunnelConfirmed = false;
    protected String[] mSMSNumbers = new String[0];

    protected SshClient mSshClient;
    protected EncryptedSettingsManager mSettingsManager;
    protected HealthServicesManager mHealthManager;

    protected final Handler mHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService mBackgroundExecutor = Executors.newSingleThreadExecutor();

    /**
     * en_GB: Acquires a partial wake lock to ensure the CPU remains active for forensic sensor polling.
     * This improves gesture response (tilt-to-wake) by preventing deep CPU sleep states.
     */
    private void acquireWakeLock() {
        if (mWakeLock != null && !mWakeLock.isHeld()) {
            mWakeLock.acquire(10 * 60 * 1000L); // 10 minute safety limit
            Log.i(TAG, "WakeLock acquired: Forensic stability active.");
        }
    }

    private final InetSocketAddress mSshAddr = new InetSocketAddress("127.0.0.1", 8080); // Linked to WebServer Port

    public class SdBinder extends Binder {
        public SdServer getService() { return SdServer.this; }
    }
    private final IBinder mBinder = new SdBinder();

    private final Runnable mTunnelWatchdogTask = new Runnable() {
        @Override
        public void run() {
            checkTunnelLiveness();
            mHandler.postDelayed(this, 10000); 
        }
    };

    @Override
    public IBinder onBind(Intent intent) { return mBinder; }

    @Override
    public void onCreate() {
        super.onCreate();
        Context appCtx = getApplicationContext();
        mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        mToneGenerator = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
        mVibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OSD:WakeLock");
        
        setupChannels();
        startOsdForeground();

        mSettingsManager = new EncryptedSettingsManager(appCtx);
        mHealthManager = new HealthServicesManager(appCtx, mSdData);
        mHealthManager.startMonitoring();
        
        startSshTunnel();
        startWebServer();
        mHandler.postDelayed(mTunnelWatchdogTask, 5000);
    }

    private void checkTunnelLiveness() {
        mBackgroundExecutor.execute(() -> {
            // Protocol Fix: Check actual SSH Session state via the client
            boolean active = (mSshClient != null && mSshClient.isConnected());

            final boolean result = active;
            mHandler.post(() -> {
                mSshTunnelConfirmed = result;
                if (mSdData != null) mSdData.serverOK = result; 
            });
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
            mHandler.postDelayed(this::startWebServer, 5000);
        }
    }

    private void startSshTunnel() {
        SshCluster cluster = mSettingsManager.getSshConfig();
        if (cluster != null && cluster.host != null && !cluster.host.isEmpty()) {
            if (mSshClient == null) mSshClient = new SshClient(getApplicationContext());
            // Standard session does NOT open R-tunnels.
            mSshClient.connectAndUploadConfig(cluster, false, (success, msg) -> checkTunnelLiveness());
        }
    }

    /**
     * Forced R-tunnel restart with PID lock consideration via Webhook.
     */
    public void forceRestartSshTunnel() {
        SshCluster cluster = mSettingsManager.getSshConfig();
        if (cluster != null && cluster.host != null && !cluster.host.isEmpty()) {
            if (mSshClient == null) mSshClient = new SshClient(getApplicationContext());
            mSshClient.connectAndUploadConfig(cluster, true, (success, msg) -> {
                checkTunnelLiveness();
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
            if (sdData.alarmState == 0) {
                mAlarmMutedByUser = false;
                sdData.alarmStanding = false;
            }
            mAlarmActive = false;
        }
    }

    public void acceptAlarm() {
        Log.i(TAG, "acceptAlarm: I'M OK triggered.");
        mAlarmMutedByUser = true; 
        mAlarmActive = false;
        mSdData.alarmStanding = false;
        mSdData.alarmState = 0;
        if (mSdDataSource != null) mSdDataSource.ClearAlarmCount();
        if (mVibrator != null) mVibrator.cancel();
    }

    public void sendSMSAlarm() {
        try {
            SmsManager sm = getSystemService(SmsManager.class);
            String msg = "OSD Alert! Location: http://maps.google.com/?q=" + mSdData.latitude + "," + mSdData.longitude;
            for (String num : mSMSNumbers) { 
                if (num != null && !num.isEmpty()) sm.sendTextMessage(num, null, msg, null, null); 
            }
        } catch (Exception e) { Log.e(TAG, "SMS Error: " + e.toString()); }
    }

    protected void handleAlarmState(SdData sdData) {
        sdData.alarmStanding = true;
        vibrate(1000);
    }

    private void vibrate(long d) {
        if (mVibrator == null || !mVibrator.hasVibrator()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mVibrator.vibrate(VibrationEffect.createOneShot(d, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            mVibrator.vibrate(d);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (mSdDataSource == null) {
            mSdDataSource = createDataSource();
            if (mSdDataSource != null) mSdDataSource.start();
        }
        acquireWakeLock();
        return START_STICKY;
    }

    protected abstract SdDataSource createDataSource();
    public abstract int getOsdForegroundServiceType();

    @Override public void onDestroy() {
        mHandler.removeCallbacks(mTunnelWatchdogTask);
        mBackgroundExecutor.shutdownNow();
        if (mSdDataSource != null) mSdDataSource.stop();
        if (mHealthManager != null) mHealthManager.stopMonitoring();
        if (webServer != null) webServer.stop();
        if (mWakeLock != null && mWakeLock.isHeld()) mWakeLock.release();
        super.onDestroy();
    }
}
