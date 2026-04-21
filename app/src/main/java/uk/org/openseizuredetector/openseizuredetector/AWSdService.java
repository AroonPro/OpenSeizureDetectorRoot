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
 * AWSdService - Unit Regtien Optimized.
 * Bridges sensors to UI and handles foreground transitions with forensic precision.
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
        
        // Protocol: Only trigger activity transition on alarm onset to minimize RenderThread load.
        if (sdData.alarmState >= 2 && !mWasInAlarm) {
            mWasInAlarm = true;
            bringActivityToFront();
        } else if (sdData.alarmState < 2) {
            mWasInAlarm = false;
        }
    }

    private void bringActivityToFront() {
        try {
            Intent intent = new Intent();
            intent.setClassName(getPackageName(), "uk.org.openseizuredetector.aw.StartUpActivityWear");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Foreground Transition Fail: " + e.getMessage());
        }
    }

    @Override
    public void acceptAlarm() {
        Log.i(TAG, "acceptAlarm: Commencing forensic reset flow.");
        super.acceptAlarm();
        mWasInAlarm = false;
    }

    @Override
    public void sendSMSAlarm() {
        Log.i(TAG, "sendSMSAlarm: Alert distribution triggered.");
        super.sendSMSAlarm();
    }

    @Override
    public void onSdDataFault(SdData sdData) {
        this.mSdData = sdData;
        statusLiveData.postValue("FAULT: Sensor Issue");
        dataLiveData.postValue(sdData);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
}
