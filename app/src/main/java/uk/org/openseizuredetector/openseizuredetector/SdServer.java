package uk.org.openseizuredetector.openseizuredetector;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Binder;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.telephony.SmsManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public abstract class SdServer extends Service implements SdDataReceiver {
    protected static final String TAG = "SdServer";
    protected static final String CHANNEL_ID = "OSD_Service_Channel";
    protected static final int NOTIFICATION_ID = 101;
    
    public SdData mSdData = new SdData();
    public SdDataSource mSdDataSource;
    protected NotificationManager mNM;
    protected ToneGenerator mToneGenerator;
    protected Vibrator mVibrator;
    protected WakeLock mWakeLock;
    protected Handler mHandler = new Handler(Looper.getMainLooper());
    protected SdWebServer webServer;

    protected boolean mAlarmActive = false;
    protected boolean mSMSAlarm = false;
    protected String[] mSMSNumbers = new String[0];
    protected long mLastSmsTime = 0;
    protected SmsTimer mSmsTimer = null;
    protected AudioAlarmTimer mAudioTimer = null;
    protected boolean mAlarmMutedByUser = false;
    protected boolean mSshTunnelConfirmed = false;

    protected SshClient mSshClient;
    protected EncryptedSettingsManager mSettingsManager;
    protected HealthServicesManager mHealthManager;

    public class SdBinder extends Binder {
        public SdServer getService() { return SdServer.this; }
    }
    private final IBinder mBinder = new SdBinder();

    @Override
    public IBinder onBind(Intent intent) { return mBinder; }

    @Override
    public void onCreate() {
        super.onCreate();
        mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        mToneGenerator = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
        mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OSD:WakeLock");
        
        setupChannels();
        startOsdForeground();

        mSettingsManager = new EncryptedSettingsManager(this);
        mHealthManager = new HealthServicesManager(this, mSdData);
        mHealthManager.startMonitoring();
        
        startSshTunnel();
        startWebServer();
        startTunnelWatchdog();
    }

    private void startTunnelWatchdog() {
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                checkTunnelLiveness();
                mHandler.postDelayed(this, 10000); 
            }
        }, 5000);
    }

    private void checkTunnelLiveness() {
        new Thread(() -> {
            boolean active = false;
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1", 8123), 2000);
                active = true;
            } catch (IOException e) {
                active = false;
            }
            final boolean result = active;
            mHandler.post(() -> {
                if (mSshTunnelConfirmed != result) {
                    mSshTunnelConfirmed = result;
                    Log.i(TAG, "SSH Tunnel status changed: " + (result ? "CONNECTED" : "DISCONNECTED"));
                }
                if (mSdData != null) {
                    mSdData.serverOK = result; 
                }
            });
        }).start();
    }

    public boolean isSshActive() {
        return mSshTunnelConfirmed;
    }

    private void setupChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "OSD Monitoring Status", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Toont of de aanvalsdetectie actief is op de achtergrond.");
            mNM.createNotificationChannel(channel);
        }
    }

    private void startOsdForeground() {
        Intent notificationIntent = new Intent();
        try {
            notificationIntent.setClassName(this.getPackageName(), "uk.org.openseizuredetector.aw.StartUpActivityWear");
        } catch (Exception e) {}

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("OSD Actief")
                .setContentText("Aanvalsdetectie wordt uitgevoerd...")
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock) 
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, getOsdForegroundServiceType());
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void startWebServer() {
        try {
            if (webServer != null) {
                webServer.stop();
                webServer = null;
            }
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
            mSshClient = new SshClient(this);
            mSshClient.connectAndUploadConfig(cluster, (success, message) -> {
                Log.i(TAG, "SSH Tunnel establishment event: " + success);
                checkTunnelLiveness(); 
            });
        }
    }

    @Override
    public void onSdDataReceived(final SdData sdData) {
        this.mSdData = sdData;
        if (webServer != null) webServer.setSdData(mSdData);

        mHandler.post(() -> {
            if (sdData.alarmState >= 2) {
                if (sdData.mMutePeriod == Constants.GLOBAL_CONSTANTS.ALARM_MUTE_INFINATE_TIME) return; 

                if (!mAlarmActive && !mAlarmMutedByUser) {
                    if (sdData.isExerciseActive) {
                        double anaerobicThreshold = 220 - 30; 
                        if (sdData.mHR < anaerobicThreshold && sdData.alarmPhrase.contains("Movement")) return; 
                    }
                    mAlarmActive = true;
                    handleAlarmState(sdData);
                }
            } else {
                if (sdData.alarmState == 0) {
                    mAlarmMutedByUser = false; 
                    sdData.alarmStanding = false;
                }
                if (mAlarmActive) {
                    mAlarmActive = false;
                    if (mAudioTimer != null) { mAudioTimer.cancel(); mAudioTimer = null; }
                    if (mSmsTimer != null) { mSmsTimer.cancel(); mSmsTimer = null; }
                }
            }
        });
    }

    @Override
    public void onSdDataFault(SdData sdData) { this.mSdData = sdData; }

    protected void handleAlarmState(SdData sdData) {
        sdData.alarmStanding = true;
        vibrate(1000);
        if (mAudioTimer != null) mAudioTimer.cancel();
        mAudioTimer = new AudioAlarmTimer(90000, 2000); 
        mAudioTimer.start();

        long now = System.currentTimeMillis();
        if (mSMSAlarm && (now - mLastSmsTime > 60000)) {
            startSmsTimer();
            mLastSmsTime = now;
        }
    }

    protected void startSmsTimer() {
        if (mSmsTimer != null) mSmsTimer.cancel();
        mSmsTimer = new SmsTimer(10000, 1000);
        mSmsTimer.start();
    }

    public class AudioAlarmTimer extends CountDownTimer {
        public AudioAlarmTimer(long m, long i) { super(m, i); }
        @Override public void onTick(long l) { vibrate(500); }
        @Override public void onFinish() { if (mAlarmActive) alarmBeep(); }
    }

    public void acceptAlarm() {
        Log.i(TAG, "acceptAlarm: I'M OK triggered.");
        mAlarmMutedByUser = true; 
        mAlarmActive = false;
        mSdData.alarmStanding = false;
        mSdData.alarmState = 0;

        if (mSdDataSource != null) {
            mSdDataSource.ClearAlarmCount();
        }
        
        if (mSmsTimer != null) { mSmsTimer.cancel(); mSmsTimer = null; }
        if (mAudioTimer != null) { mAudioTimer.cancel(); mAudioTimer = null; }
        if (mVibrator != null) mVibrator.cancel();
        if (mToneGenerator != null) mToneGenerator.stopTone();
    }

    public void sendSMSAlarm() {
        try {
            SmsManager sm = getSystemService(SmsManager.class);
            String msg = "OSD Seizure Alert! Location: http://maps.google.com/?q=" + mSdData.latitude + "," + mSdData.longitude;
            for (String num : mSMSNumbers) { if (num != null && !num.isEmpty()) sm.sendTextMessage(num, null, msg, null, null); }
        } catch (Exception e) { Log.e(TAG, "SMS Error: " + e.toString()); }
    }

    public class SmsTimer extends CountDownTimer {
        public SmsTimer(long m, long i) { super(m, i); }
        @Override public void onTick(long l) {}
        @Override public void onFinish() { sendSMSAlarm(); }
    }

    protected void alarmBeep() { try { mToneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 5000); } catch (Exception e) {} }
    protected void warningBeep() { try { mToneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 100); } catch (Exception e) {} }

    private void vibrate(long d) {
        if (mVibrator != null && mVibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) mVibrator.vibrate(VibrationEffect.createOneShot(d, VibrationEffect.DEFAULT_AMPLITUDE));
            else mVibrator.vibrate(d);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (mSdDataSource == null) {
            mSdDataSource = createDataSource();
            if (mSdDataSource != null) mSdDataSource.start();
        }
        return START_STICKY;
    }

    protected abstract SdDataSource createDataSource();
    public abstract int getOsdForegroundServiceType();

    @Override public void onDestroy() {
        if (mSdDataSource != null) mSdDataSource.stop();
        if (mHealthManager != null) mHealthManager.stopMonitoring();
        if (mAudioTimer != null) mAudioTimer.cancel();
        if (webServer != null) webServer.stop();
        super.onDestroy();
    }
}
