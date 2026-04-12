package uk.org.openseizuredetector.openseizuredetector;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import androidx.lifecycle.MutableLiveData;

/**
 * AndroidSdService - The main background service for the Phone App.
 * Handles the LogManager and provides the Binder for UI Activities.
 */
public class AndroidSdService extends SdDataSource {
    private static final String TAG = "AndroidSdService";

    // LogManager instance owned by this service
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
    public void startPebbleApp() {}

    @Override
    public void onCreate() {
        super.onCreate();
        this.mName = "AndroidSdService";

        mLm = new LogManager(
                getApplicationContext(), 
                true,                    
                false,                   
                "OSD_Log",               
                1024 * 1024 * 10,        
                60000,                   
                true,                    
                false,                   
                5000,                    
                new SdData()             
        );

        if (serviceLiveData == null) {
            serviceLiveData = new MutableLiveData<>();
        }

        Log.i(TAG, "AndroidSdService Created and LogManager Initialized");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void ClearAlarmCount() {
        if (mSdData != null) {
            mSdData.alarmState = 0;
            serviceLiveData.postValue(mSdData);
        }
    }

    @Override
    public void handleSendingHelp() {
        if (mLm != null) {
            Log.i(TAG, "Sending Help Alert via LogManager");
        }
    }

    public void acceptAlarm() {
        String serverIP = mUtil.getServerIp();
        if (serverIP != null && !serverIP.isEmpty()) {
            String url = "http://" + serverIP + ":8080/acceptalarm";
            new AcceptAlarmTask().execute(url);
        }
    }

    private class AcceptAlarmTask extends android.os.AsyncTask<String, Void, Boolean> {
        @Override
        protected Boolean doInBackground(String... urls) {
            try {
                java.net.URL url = new java.net.URL(urls[0]);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                return (conn.getResponseCode() == 200);
            } catch (Exception e) {
                return false;
            }
        }
    }

    @Override public void muteCheck() {}
    @Override protected void getStatus() {}
    @Override protected void faultCheck() {}
    @Override public void hrCheck() {}
    @Override public void o2SatCheck() {}
    @Override public void fallCheck() {}

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "Service Destroyed");
    }
}
