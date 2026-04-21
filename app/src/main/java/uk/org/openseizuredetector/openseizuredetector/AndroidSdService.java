package uk.org.openseizuredetector.openseizuredetector;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

/**
 * AndroidSdService - Unit Regtien Optimized.
 * Forensic binder management and structural alignment with the shared SdDataSource base.
 */
public class AndroidSdService extends SdDataSource {
    private static final String TAG = "AndroidSdService";
    public LogManager mLm;

    public class AndroidSdBinder extends SdDataSource.SdBinder {
        @Override
        public AndroidSdService getService() {
            return AndroidSdService.this;
        }
    }

    private final IBinder mBinder = new AndroidSdBinder();

    public AndroidSdService(Context context, Handler handler, SdDataReceiver sdDataReceiver) {
        super(context, handler, sdDataReceiver);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        this.mName = "AndroidSdService";

        // Protocol: Non-blocking log initialization using restored SdData structure
        mLm = new LogManager(getApplicationContext(), true, false, "OSD_Log", 10485760, 60000, true, false, 5000, mSdData);
        Log.i(TAG, "Service and LogManager Initialized");
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }
    @Override public IBinder onBind(Intent intent) { return mBinder; }

    @Override
    public void ClearAlarmCount() {
        super.ClearAlarmCount();
        triggerUiUpdate();
    }

    @Override public void handleSendingHelp() { Log.i(TAG, "Alert Triggered"); }

    public void acceptAlarm() {
        // Architecture Rule: Centralized alarm state management
        ClearAlarmCount();
        Log.i(TAG, "Alarm Accepted via Phone Service");
    }

    @Override public void muteCheck() {}
    @Override protected void getStatus() { triggerUiUpdate(); }
    @Override public void startPebbleApp() {}
    @Override public void o2SatCheck() {}
}
