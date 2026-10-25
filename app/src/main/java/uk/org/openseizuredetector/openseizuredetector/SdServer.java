package uk.org.openseizuredetector.openseizuredetector;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Vibrator;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.preference.PreferenceManager;

import uk.org.openseizuredetector.openseizuredetector.data.SdData;
import uk.org.openseizuredetector.openseizuredetector.datasource.SdDataReceiver;
import uk.org.openseizuredetector.openseizuredetector.datasource.SdDataSource;
import uk.org.openseizuredetector.openseizuredetector.webserver.SdWebServer;
import uk.org.openseizuredetector.openseizuredetector.data.logging.LogManager;
import uk.org.openseizuredetector.openseizuredetector.alg.SeizureDetector;

/**
 * SdServer - Forensic Root Service.
 * GC-Optimized: Zero-allocation data synchronization via object recycling.
 * Complies with Code Review Checklist and Unit Regtien Standard.
 */
public abstract class SdServer extends Service implements SdDataReceiver {
    protected static final String TAG = "SdServer";
    protected static final int NOTIFICATION_ID = 101;
    protected static final String CHANNEL_ID = "OSD_Service_Channel";

    // CORE DATA - Recycled Instance
    public final SdData mSdData = new SdData();
    public SdDataSource mSdDataSource;
    
    protected LogManager mLm;
    protected SeizureDetector mSeizureDetector;
    protected NotificationManager mNM;
    protected Vibrator mVibrator;
    protected SdWebServer webServer;
    
    protected final Handler mHandler = new Handler(Looper.getMainLooper());
    protected boolean mIsWear = false;

    public static void setPlannedShutdown(Context context) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit().putBoolean("plannedShutdown", true).apply();
    }

    @Override
    public void onCreate() {
        mIsWear = getPackageName().contains(".aw");
        super.onCreate();
        mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        mVibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        
        // Forensische Log Initialisatie
        mLm = new LogManager(this);
        mSeizureDetector = new SeizureDetector(this);
        
        createNotificationChannel();
        startForegroundOSD();
        onCreateInternal();
    }

    protected abstract void onCreateInternal();
    protected abstract int getNotificationTitleResId();
    protected abstract int getNotificationChannelNameResId();
    protected abstract int getSmsAlertPrefixResId();
    protected abstract int getMidnightLogTagResId();
    protected abstract int getConnectedResId();
    protected abstract int getDisconnectedResId();
    public abstract int getOsdForegroundServiceType();

    // Abstract methods for datasource creation
    protected abstract SdDataSource createDataSource();

    // Abstract methods for alarm and SMS handling
    public abstract void alarmBeep();
    public abstract void warningBeep();
    public abstract void sendSMSAlarm();
    public abstract void sendFalseAlarmSMS();
    public abstract void raiseManualAlarm();

    // Abstract methods for SSH management
    public abstract boolean isSshActive();
    public abstract void forceRestartSshTunnel();

    /**
     * GC-Optimized Data Reception.
     * Rule: Never create a new SdData object here. Use copyFrom().
     */
    @Override
    public void onSdDataReceived(SdData incoming) {
        if (incoming != null) {
            // Recycling the persistent member instead of discarding it
            mSdData.copyFrom(incoming);
            
            // Pass to algorithms
            int newState = mSeizureDetector.processData(mSdData);
            mSdData.alarmState = newState;
            
            // Forensic Persistence
            if (mLm != null) mLm.logData(mSdData);
            
            // Trigger specific service overrides
            onForensicUpdate(mSdData);
        }
    }

    protected abstract void onForensicUpdate(SdData data);

    /**
     * Updates the external IP address in the data object.
     * Called by the web server when receiving IP updates.
     */
    public void updateExternalIp(String ip) {
        // Store IP in data object if needed
        Log.d(TAG, "External IP updated: " + ip);
    }

    protected void startForegroundOSD() {
        Intent notificationIntent = new Intent(); // Setup via subclass
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("OSD " + (mIsWear ? "Wear" : "Phone") + " Active")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, getOsdForegroundServiceType());
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "OSD Service", NotificationManager.IMPORTANCE_LOW);
            mNM.createNotificationChannel(channel);
        }
    }

    @Override public IBinder onBind(Intent intent) { return new SdBinder(); }
    public class SdBinder extends Binder { public SdServer getService() { return SdServer.this; } }

    @Override
    public void onDestroy() {
        if (mSdDataSource != null) mSdDataSource.stop();
        if (mLm != null) mLm.shutdown();
        if (webServer != null) {
            webServer.stop();
            Log.i(TAG, "Web server stopped");
        }
        super.onDestroy();
    }
}
