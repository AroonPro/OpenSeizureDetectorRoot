package uk.org.openseizuredetector.openseizuredetector;

import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;

/**
 * AWSdService - Standalone Wear OS Service.
 * Bridges sensors to UI and handles foreground transitions.
 */
public class AWSdService extends SdServer {
    private final String TAG = "AWSdService";
    private boolean mWasInAlarm = false;

    public static final MutableLiveData<String> statusLiveData = new MutableLiveData<>("Service Started");
    public static final MutableLiveData<SdData> dataLiveData = new MutableLiveData<>(new SdData());

    public class Access extends Binder {
        public AWSdService getService() {
            return AWSdService.this;
        }
    }

    private final IBinder mBinder = new Access();

    @Override
    protected SdDataSource createDataSource() {
        return new SdDataSourceAw(this, mHandler, this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int res = super.onStartCommand(intent, flags, startId);
        statusLiveData.postValue("Monitoring Active");
        return res;
    }

    @Override
    public int getOsdForegroundServiceType() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH | ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION;
        }
        return 0;
    }

    @Override
    public void onSdDataReceived(SdData sdData) {
        super.onSdDataReceived(sdData);
        dataLiveData.postValue(sdData);
        
        // KRITIEK: Breng activity ENKEL naar voorgrond bij de transitie naar alarm.
        // Dit voorkomt flikkeren en RenderThread crashes.
        if (sdData.alarmState >= 2 && !mWasInAlarm) {
            mWasInAlarm = true;
            bringActivityToFront();
        } else if (sdData.alarmState < 2) {
            mWasInAlarm = false;
        }
    }

    private void bringActivityToFront() {
        Log.i(TAG, "Alarm Transition: Bringing StartUpActivityWear to front.");
        try {
            Intent intent = new Intent();
            intent.setClassName(this.getPackageName(), "uk.org.openseizuredetector.aw.StartUpActivityWear");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to bring Activity to front: " + e.getMessage());
        }
    }

    @Override
    public void onSdDataFault(SdData sdData) {
        super.onSdDataFault(sdData);
        statusLiveData.postValue("FAULT: Sensor Issue");
        dataLiveData.postValue(sdData);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
}
