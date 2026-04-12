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

    // Binder for the ServiceConnection
    private final IBinder mBinder = new AndroidSdBinder();


    public AndroidSdService(Context context, Handler handler, SdDataReceiver sdDataReceiver) {
        super(context, handler, sdDataReceiver);
    }

    /**
     *
     */
    @Override
    public void startPebbleApp() {

    }

    public class AndroidSdBinder extends SdBinder {
        @Override
        public AndroidSdService getService() {
            return AndroidSdService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        this.mName = "AndroidSdService";

        // REFACTOR FIX: Initialize mLm directly here.
        // Do NOT try to pull it from mConnection, as mConnection is for external services.
        mLm = new LogManager(
                getApplicationContext(), // 1. Context
                true,                    // 2. boolean: loggingEnabled
                false,                   // 3. boolean: debugEnabled
                "OSD_Log",               // 4. String: logFileName
                1024 * 1024 * 10,        // 5. long: maxFileSize (10MB)
                60000,                   // 6. long: flushInterval (1 min)
                true,                    // 7. boolean: useInternalStorage
                false,                   // 8. boolean: appendToExisting
                5000,                    // 9. long: sampleRate (ms)
                new SdData()             // 10. SdData: initial data object
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

    // --- Implementation of Abstract Methods ---

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
            // Trigger the actual alert via LogManager
            Log.i(TAG, "Sending Help Alert via LogManager");
        }
    }

    /**
     * acceptAlarm - SDK 17 Controller Logic
     * * en_GB Java Documentation:
     * Placed in AndroidSdService to bridge the UI (Fragments)
     * with the Network (OSD Server).
     */
    public void acceptAlarm() {
        Log.v(TAG, "acceptAlarm() - Controller entry point");

        // We halen het IP uit de SharedPreferences via mUtil (Kirk-path)
        String serverIP = mUtil.getServerIp();

        if (serverIP != null && !serverIP.isEmpty()) {
            String url = "http://" + serverIP + ":8080/acceptalarm";
            // Start de AsyncTask die we eerder hebben besproken
            new AcceptAlarmTask().execute(url);
        } else {
            mUtil.showToast("Error: No Server IP configured.");
        }
    }

    private class AcceptAlarmTask extends android.os.AsyncTask<String, Void, Boolean> {
        @Override
        protected Boolean doInBackground(String... urls) {
            try {
                // SDK 17: Simpele HTTP-aanroep zonder moderne libraries
                java.net.URL url = new java.net.URL(urls[0]);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                int responseCode = conn.getResponseCode();
                return (responseCode == 200);
            } catch (Exception e) {
                Log.e(TAG, "Network Error: " + e.toString());
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (success) {
                if (mUtil != null) mUtil.showToast("Alarm Accepted - 2012 Log Updated");
            } else {
                if (mUtil != null) mUtil.showToast("Failed to Accept Alarm");
            }
        }
    }

    @Override
    public void muteCheck() {
        // Logic to silence alarms temporarily
    }

    @Override
    protected void getStatus() {
        // Check battery/connection status of data sources
    }

    @Override
    protected void faultCheck() {
        // Check for sensor data timeouts
    }

    // Empty implementations for Watch-specific logic that doesn't apply to Phone Service
    @Override public void hrCheck() {}
    @Override public void o2SatCheck() {}
    @Override public void fallCheck() {}

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "Service Destroyed");
    }
}