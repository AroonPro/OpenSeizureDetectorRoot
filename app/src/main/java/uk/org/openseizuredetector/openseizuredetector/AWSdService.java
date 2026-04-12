package uk.org.openseizuredetector.openseizuredetector;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.MutableLiveData;

/**
 * AWSdService - en_GB
 * Standalone Wear OS Service.
 * Bridges data source events to LiveData for the UI.
 */
public class AWSdService extends SdServer {
    private final String TAG = "AWSdService";

    public static MutableLiveData<String> statusLiveData = new MutableLiveData<>("Service Created");
    public static MutableLiveData<SdData> dataLiveData = new MutableLiveData<>();

    public class Access extends Binder {
        public AWSdService getService() {
            return AWSdService.this;
        }
    }

    private final IBinder mBinder = new Access();

    public SdData getSdData() {
        return mSdData;
    }

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
            return ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH;
        }
        return 0;
    }

    @Override
    public void onSdDataReceived(SdData sdData) {
        super.onSdDataReceived(sdData);
        Log.d(TAG, "onSdDataReceived: HR=" + sdData.mHr + ", OnBody=" + sdData.mWatchOnBody);
        // en_GB: Push update to UI Layer
        dataLiveData.postValue(sdData);
    }

    @Override
    public void onSdDataFault(SdData sdData) {
        super.onSdDataFault(sdData);
        Log.e(TAG, "onSdDataFault: Sensor Issue reported");
        statusLiveData.postValue("FAULT: Sensor Issue");
        dataLiveData.postValue(sdData);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
}
