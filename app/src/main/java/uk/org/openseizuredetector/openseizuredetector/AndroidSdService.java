package uk.org.openseizuredetector.openseizuredetector;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import uk.org.openseizuredetector.openseizuredetector.datasource.SdDataSourcePhone;

/**
 * AndroidSdService - en_GB
 * Phone implementation inheriting robust SdServer logic with forensic optimizations.
 */
public class AndroidSdService extends SdServer {
    private static final String TAG = "AndroidSdService";

    @Override
    protected void onCreateInternal() {
        Log.i(TAG, "AndroidSdService initialised.");
        mSdDataSource = createDataSource();
        if (mSdDataSource != null) {
            mSdDataSource.start();
        }
        // Start the web server for remote monitoring
        webServer = new SdWebServer(getApplicationContext(), mSdData, this);
        try {
            webServer.start();
            Log.i(TAG, "Web server started on port 8080");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start web server: " + e.getMessage());
        }
    }

    @Override
    protected void onForensicUpdate(SdData data) {
        // Additional phone-specific processing if needed
    }

    @Override
    protected SdDataSource createDataSource() {
        return new SdDataSourcePhone(getApplicationContext(), mHandler, this);
    }

    @Override
    public int getOsdForegroundServiceType() {
        int type = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            type |= ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE;
            type |= ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                type |= ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION;
            }
        }
        return type;
    }

    @Override public void alarmBeep() {}
    @Override public void warningBeep() {}
    @Override public void sendSMSAlarm() {}
    @Override public void sendFalseAlarmSMS() {}

    @Override
    public void raiseManualAlarm() {
        mSdData.alarmState = 2;
        mSdData.alarmPhrase = "MANUAL ALARM";
        onSdDataReceived(mSdData);
    }

    @Override
    public boolean isSshActive() {
        // SSH functionality not currently implemented for phone service
        return false;
    }

    @Override
    public void forceRestartSshTunnel() {
        // SSH functionality not currently implemented for phone service
    }

    // Resource mapping for Phone (strings.xml in app module)
    @Override protected int getNotificationTitleResId() { return android.R.string.dialog_alert_title; }
    @Override protected int getNotificationChannelNameResId() { return android.R.string.ok; }
    @Override protected int getSmsAlertPrefixResId() { return android.R.string.unknownName; }
    @Override protected int getMidnightLogTagResId() { return android.R.string.untitled; }
    @Override protected int getConnectedResId() { return android.R.string.ok; }
    @Override protected int getDisconnectedResId() { return android.R.string.cancel; }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new SdBinder();
    }
}
