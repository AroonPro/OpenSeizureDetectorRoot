package uk.org.openseizuredetector.openseizuredetector;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
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
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.telephony.SmsManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

/**
 * SdServer - Centrale server logica.
 * Beheert de data source lifecycle en alarm meldingen.
 */
public abstract class SdServer extends Service implements SdDataReceiver {
    protected static final String TAG = "SdServer";
    
    protected final int NOTIFICATION_ID = 1;
    protected final String mNotChId = "OSD_Service_Channel";

    public SdData mSdData = new SdData();
    public SdDataSource mSdDataSource;
    protected NotificationManager mNM;
    protected ToneGenerator mToneGenerator;
    protected Vibrator mVibrator;
    protected WakeLock mWakeLock;
    protected SdWebServer webServer;
    protected Handler mHandler = new Handler();

    protected boolean mSMSAlarm = false;
    protected String[] mSMSNumbers = new String[0];
    protected long mLastSmsTime = 0;
    protected SmsTimer mSmsTimer = null;
    protected AudioAlarmTimer mAudioTimer = null;

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
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (mSdDataSource == null) {
            mSdDataSource = createDataSource();
            if (mSdDataSource != null) {
                mSdDataSource.start();
                Log.i(TAG, "onStartCommand: Data Source started.");
            }
        }
        updateNotification(0);
        return START_STICKY;
    }

    private void setupChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(mNotChId, "OSD Status", NotificationManager.IMPORTANCE_LOW);
            mNM.createNotificationChannel(channel);
        }
    }

    @Override
    public void onSdDataReceived(SdData sdData) {
        this.mSdData = sdData;
        if (sdData.alarmState >= 2) {
            handleAlarmState(sdData);
        } else if (sdData.alarmState == 1) {
            warningBeep();
        }
        if (webServer != null) webServer.setSdData(mSdData);
    }

    @Override
    public void onSdDataFault(SdData sdData) {
        this.mSdData = sdData;
        Log.w(TAG, "onSdDataFault: " + sdData.alarmPhrase);
    }

    protected void handleAlarmState(SdData sdData) {
        sdData.alarmStanding = true;
        
        // Start vibration immediately
        vibrate(1000);

        // Delay audio alarm by 1.5 minutes (90,000 ms)
        if (mAudioTimer == null) {
            mAudioTimer = new AudioAlarmTimer(90000, 1000);
            mAudioTimer.start();
        }

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

    public class SmsTimer extends CountDownTimer {
        public SmsTimer(long millisInFuture, long countDownInterval) { super(millisInFuture, countDownInterval); }
        @Override public void onTick(long millisUntilFinished) {}
        @Override public void onFinish() { sendSMSAlarm(); }
    }

    public class AudioAlarmTimer extends CountDownTimer {
        public AudioAlarmTimer(long millisInFuture, long countDownInterval) { super(millisInFuture, countDownInterval); }
        @Override public void onTick(long millisUntilFinished) {}
        @Override public void onFinish() { alarmBeep(); }
    }

    public void sendSMSAlarm() {
        try {
            SmsManager sm = getSystemService(SmsManager.class);
            String msg = "OSD: Seizure Detected! Location: http://maps.google.com/?q=" + mSdData.latitude + "," + mSdData.longitude;
            for (String num : mSMSNumbers) { if (num != null && !num.isEmpty()) sm.sendTextMessage(num, null, msg, null, null); }
        } catch (Exception e) { Log.e(TAG, "Failed to send SMS: " + e.toString()); }
    }

    public void acceptAlarm() {
        mSdData.alarmStanding = false;
        if (mSmsTimer != null) { mSmsTimer.cancel(); mSmsTimer = null; }
        if (mAudioTimer != null) { mAudioTimer.cancel(); mAudioTimer = null; }
        if (mVibrator != null) mVibrator.cancel();
    }

    public void updateNotification(int state) {
        Notification notification = new NotificationCompat.Builder(this, mNotChId)
                .setContentTitle("OpenSeizureDetector")
                .setContentText("Monitoring Active")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            int type = getOsdForegroundServiceType();
            
            // Validate Location type
            if ((type & ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION) != 0) {
                boolean hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
                boolean hasCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
                if (!hasFine && !hasCoarse) {
                    Log.w(TAG, "Location permission missing - removing type from FGS.");
                    type &= ~ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION;
                }
            }
            
            // Validate Health type (API 34+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                if ((type & ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH) != 0) {
                    boolean hasSensors = ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED;
                    boolean hasHealthHr = ContextCompat.checkSelfPermission(this, "android.permission.health.READ_HEART_RATE") == PackageManager.PERMISSION_GRANTED;
                    if (!hasSensors && !hasHealthHr) {
                        Log.w(TAG, "Health permissions missing - removing type from FGS.");
                        type &= ~ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH;
                    }
                }
            }

            try {
                if (type != 0) {
                    startForeground(NOTIFICATION_ID, notification, type);
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    // CRITICAL: On API 34+, if we have types in manifest but none are granted,
                    // calling 2-arg startForeground() defaults to all manifest types and CRASHES.
                    // So we show the notification but don't enter foreground state if we have no valid types.
                    Log.e(TAG, "No valid FGS types granted. Not starting as Foreground Service to prevent crash.");
                    mNM.notify(NOTIFICATION_ID, notification);
                } else {
                    startForeground(NOTIFICATION_ID, notification);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to start foreground service: " + e.toString());
            }
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    protected void alarmBeep() { 
        try { 
            mToneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 2000); 
            vibrate(1000);
        } catch (Exception e) {} 
    }
    
    protected void warningBeep() { 
        try { 
            mToneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 100);
            vibrate(200);
        } catch (Exception e) {} 
    }

    private void vibrate(long duration) {
        if (mVibrator != null && mVibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mVibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                mVibrator.vibrate(duration);
            }
        }
    }

    protected abstract SdDataSource createDataSource();
    public abstract int getOsdForegroundServiceType();

    @Override
    public void onDestroy() {
        if (mSdDataSource != null) mSdDataSource.stop();
        if (mWakeLock != null && mWakeLock.isHeld()) mWakeLock.release();
        if (mVibrator != null) mVibrator.cancel();
        if (mAudioTimer != null) mAudioTimer.cancel();
        super.onDestroy();
    }
}
