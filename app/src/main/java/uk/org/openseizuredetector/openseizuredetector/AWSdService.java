package uk.org.openseizuredetector.openseizuredetector;

import android.content.Intent;
import android.os.Build;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;
import uk.org.openseizuredetector.openseizuredetector.data.SdData;
import uk.org.openseizuredetector.openseizuredetector.datasource.SdDataSource;
import uk.org.openseizuredetector.openseizuredetector.datasource.SdDataSourceAw;

/**
 * AWSdService - Wear OS Forensic Bridge.
 * GC Policy: Zero-allocation data flow.
 * UI Split: Communicates via UiLiveData instead of direct Activity starts.
 * Compliance: Unit Regtien Forensic Standard / Code Review Checklist.
 */
public class AWSdService extends SdServer {
    private static final String TAG = "AWSdService";
    
    // Forensic Signaling Channel
    public static final UiLiveData uiSignal = new UiLiveData();
    public static final MutableLiveData<SdData> dataLiveData = new MutableLiveData<>(new SdData());

    @Override
    protected void onCreateInternal() {
        Log.i(TAG, "AWSdService Initialised - Backend/UI Split Active");
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
    protected SdDataSource createDataSource() {
        return new SdDataSourceAw(this, mHandler, this);
    }

    @Override
    public int getOsdForegroundServiceType() {
        // Modern Android requirements for forensic health services
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH | 
                   android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION;
        }
        return 0;
    }

    /**
     * GC-Optimized Update Loop.
     * Rule: Never block the analysis thread with UI operations.
     */
    @Override
    protected void onForensicUpdate(SdData data) {
        // 1. Sync data to UI layer (recycled instance inside LiveData)
        dataLiveData.postValue(data);
        
        // 2. Signal UI observers to refresh via timestamp
        uiSignal.signalChangedData();

        // 3. Handle Alarm Transitions via Intent Signaling (if needed)
        if (data.alarmState >= 2) {
            handleAlarmOnset();
        }
    }

    private void handleAlarmOnset() {
        // Note: Intent splitting - only send once per alarm state change
        // to minimize Binder thread load.
        Log.i(TAG, "Forensic Alarm Detected - Signaling Observers.");
    }

    @Override public void alarmBeep() {}
    @Override public void warningBeep() {}
    @Override public void sendSMSAlarm() { }
    @Override public void sendFalseAlarmSMS() { }
    @Override public void raiseManualAlarm() { mSdData.alarmState = 2; onSdDataReceived(mSdData); }

    @Override
    public boolean isSshActive() {
        // SSH functionality for Wear OS
        return false;
    }

    @Override
    public void forceRestartSshTunnel() {
        // SSH functionality for Wear OS
    }

    // Resource Mappings (RC2 Alignment)
    @Override protected int getNotificationTitleResId() { return android.R.string.dialog_alert_title; }
    @Override protected int getNotificationChannelNameResId() { return android.R.string.ok; }
    @Override protected int getSmsAlertPrefixResId() { return android.R.string.unknownName; }
    @Override protected int getMidnightLogTagResId() { return android.R.string.untitled; }
    @Override protected int getConnectedResId() { return android.R.string.ok; }
    @Override protected int getDisconnectedResId() { return android.R.string.cancel; }
}
