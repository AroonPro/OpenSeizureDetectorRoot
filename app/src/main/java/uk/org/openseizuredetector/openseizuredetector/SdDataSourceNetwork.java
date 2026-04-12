package uk.org.openseizuredetector.openseizuredetector;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Handler;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import android.os.IBinder;
import android.text.format.Time;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by graham on 22/11/15.
 */
public class SdDataSourceNetwork extends SdDataSource {
    private String TAG = "SdDataSourceNetwork";
    private Time mStatusTime;
    private Timer mDataUpdateTimer;
    private int mDataUpdatePeriod = 2000;
    private int mConnectTimeoutPeriod = 5000;
    private int mReadTimeoutPeriod = 5000;
    private String mServerIP = "unknown";

    private int ALARM_STATE_NETFAULT = 7;


    /**
     *
     */
    @Override
    public void ClearAlarmCount() {

    }

    /**
     *
     */
    public SdDataSourceNetwork(Context context, Handler handler, SdDataReceiver sdDataReceiver) {
        super(context, handler, sdDataReceiver);
        mName = "Network";
    }

    @Override
    public void start() {
        // Update preferences.
        Log.v(TAG, "start(): calling updatePrefs()");
        mUtil.writeToSysLogFile("SdDataSourceNetwork().start()");
        updatePrefs();

        // Start timer to retrieve seizure detector data regularly.
        mStatusTime = new Time(Time.getCurrentTimezone());
        mStatusTime.setToNow();
        if (mDataUpdateTimer == null) {
            Log.v(TAG, "start(): starting data update timer");
            mDataUpdateTimer = new Timer();
            mDataUpdateTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    downloadSdData();
                }
            }, 0, mDataUpdatePeriod);
        } else {
            Log.v(TAG, "start(): data update timer already running.");
        }


    }

    /**
     *
     */
    @Override
    public void startPebbleApp() {

    }

    @Override
    public void stop() {
        mUtil.writeToSysLogFile("SdDataSourceNetwork().stop()");
        // Stop the data update timer
        if (mDataUpdateTimer != null) {
            Log.v(TAG, "stop(): cancelling status timer");
            mDataUpdateTimer.cancel();
            mDataUpdateTimer.purge();
            mDataUpdateTimer = null;
        }

    }


    /**
     * updatePrefs() - update basic settings from the SharedPreferences
     * - defined in res/xml/prefs.xml
     */
    public void updatePrefs() {
        Log.v(TAG, "updatePrefs()");
        mUtil.writeToSysLogFile("SdDataSourceNetwork().updatePrefs()");
        SharedPreferences SP = PreferenceManager
                .getDefaultSharedPreferences(mContext);
        mServerIP = SP.getString("ServerIP", "192.168.1.175");
        Log.v(TAG, "updatePrefs() - mServerIP = " + mServerIP);
        try {
            String dataUpdatePeriodStr = SP.getString("DataUpdatePeriod", "2000");
            mDataUpdatePeriod = Integer.parseInt(dataUpdatePeriodStr);
            Log.v(TAG, "updatePrefs() - mDataUpdatePeriod = " + mDataUpdatePeriod);
            String connectTimeoutPeriodStr = SP.getString("ConnectTimeoutPeriod", "5000");
            mConnectTimeoutPeriod = Integer.parseInt(connectTimeoutPeriodStr);
            Log.v(TAG, "updatePrefs() - mConnectTimeoutPeriod = " + mConnectTimeoutPeriod);
            String readTimeoutPeriodStr = SP.getString("ReadTimeoutPeriod", "5000");
            mReadTimeoutPeriod = Integer.parseInt(readTimeoutPeriodStr);
            Log.v(TAG, "updatePrefs() - mReadTimeoutPeriod = " + mReadTimeoutPeriod);
        } catch (Exception ex) {
            Log.v(TAG, "updatePrefs() - Problem parsing preferences!");
            mUtil.writeToSysLogFile("SdDataSourceNetwork().updatePrefs() - " + ex.toString() + " " + Arrays.toString(Thread.currentThread().getStackTrace()));
            mUtil.showToast("Problem Parsing Preferences - Something won't work");
        }
    }

    /**
     *
     */
    @Override
    protected void getStatus() {

    }

    /**
     * Retrive the current Seizure Detector Data from the server.
     * Uses the DownloadSdDataTask class to download the data in the
     * background.  The data is processed in DownloadSdDataTask.onPostExecute().
     */
    public void downloadSdData() {
        Log.v(TAG, "downloadSdData()");
        new DownloadSdDataTask().execute("http://" + mServerIP + ":8080/data");
    }

    /**
     * Return the communication channel to the service.  May return null if
     * clients can not bind to the service.  The returned
     * {@link IBinder} is usually for a complex interface
     * that has been <a href="{@docRoot}guide/components/aidl.html">described using
     * aidl</a>.
     *
     * <p><em>Note that unlike other application components, calls on to the
     * IBinder interface returned here may not happen on the main thread
     * of the process</em>.  More information about the main thread can be found in
     * <a href="{@docRoot}guide/topics/fundamentals/processes-and-threads.html">Processes and
     * Threads</a>.</p>
     *
     * @param intent The Intent that was used to bind to this service,
     *               as given to {@link Context#bindService
     *               Context.bindService}.  Note that any extras that were included with
     *               the Intent at that point will <em>not</em> be seen here.
     * @return Return an IBinder through which clients can call on to the
     * service.
     */
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private class DownloadSdDataTask extends AsyncTask<String, Void, SdData> {
        private SdData sdData;

        @Override
        protected SdData doInBackground(String... urls) {
            // params comes from the execute() call: params[0] is the url.
            sdData = new SdData();
            try {
                String result = downloadUrl(urls[0]);
                if (result.startsWith("Unable to retrieve web page")) {
                    Log.v(TAG, "doInBackground() - Unable to retrieve data");
                    sdData.serverOK = false;
                    sdData.watchConnected = false;
                    sdData.watchAppRunning = false;
                    sdData.alarmState = ALARM_STATE_NETFAULT;
                    sdData.alarmPhrase = "Warning - No Connection to Server";
                    Log.v(TAG, "doInBackground(): No Connection to Server - sdData = " + sdData.toString());
                } else {
                    Log.v(TAG, "doInBackground - result = " + result);
                    sdData.fromJSON(result);
                    // Populate mSdData using the received data.
                    sdData.serverOK = true;
                    if (sdData.batteryPc > 0) {
                        sdData.haveSettings = true;
                    }
                    mStatusTime.setToNow();
                    Log.v(TAG, "doInBackground(): sdData = " + sdData.toString());
                }
                return (sdData);

            } catch (IOException e) {
                sdData.serverOK = false;
                sdData.watchConnected = false;
                sdData.watchAppRunning = false;
                sdData.alarmState = ALARM_STATE_NETFAULT;
                sdData.alarmPhrase = "Warning - No Connection to Server";
                Log.v(TAG, "doInBackground(): IOException - " + e.toString());
                return sdData;
            }
        }

        // onPostExecute displays the results of the AsyncTask.
        @Override
        protected void onPostExecute(SdData sdData) {
            Log.v(TAG, "onPostExecute() - sdData = " + sdData.toString());
            mSdDataReceiver.onSdDataReceived(sdData);
        }
    }




    // Given a URL, establishes an HttpUrlConnection and retrieves
    // the web page content as a InputStream, which it returns as
    // a string.
    private String downloadUrl(String myurl) throws IOException {
        InputStream is = null;
        // Only retrieve the first 2048 characters of the retrieved
        // web page content.
        int len = 2048;

        try {
            URL url = new URL(myurl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setReadTimeout(mReadTimeoutPeriod /* milliseconds */);
            conn.setConnectTimeout(mConnectTimeoutPeriod /* milliseconds */);
            conn.setRequestMethod("GET");
            conn.setDoInput(true);
            // Starts the query
            conn.connect();
            int response = conn.getResponseCode();
            Log.d(TAG, "downloadUrl(): The response is: " + response);
            is = conn.getInputStream();

            // Convert the InputStream into a string
            String contentAsString = readInputStream(is, len);
            return contentAsString;

            // Makes sure that the InputStream is closed after the app is
            // finished using it.
        } finally {
            if (is != null) {
                is.close();
            }
        }
    }

    // Reads an InputStream and converts it to a String.
    public String readInputStream(InputStream stream, int len) throws IOException, UnsupportedEncodingException {
        Reader reader = null;
        reader = new InputStreamReader(stream, "UTF-8");
        char[] buffer = new char[len];
        reader.read(buffer);
        return new String(buffer);
    }


}
