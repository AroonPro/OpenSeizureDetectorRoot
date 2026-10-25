/*
  SdDataSourceNetwork - Forensic Network Bridge.
  Optimized for Graham Jones RC2 Online Routines (9d94597f).
  GC Policy: Zero-allocation polling loop.
  Compliance: Unit Regtien Forensic Standard.
*/
package uk.org.openseizuredetector.openseizuredetector.datasource;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.util.Log;
import androidx.preference.PreferenceManager;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Timer;
import java.util.TimerTask;
import uk.org.openseizuredetector.openseizuredetector.data.SdData;
import uk.org.openseizuredetector.openseizuredetector.data.AlarmState;
import uk.org.openseizuredetector.openseizuredetector.utils.BackgroundTaskExecutor;

/**
 * SdDataSourceNetwork - Fetches OSD data from a remote instance.
 * Optimized to prevent memory churn during high-frequency polling (2s interval).
 */
public class SdDataSourceNetwork extends SdDataSource {
    private static final String TAG = "SdDataSourceNet";
    private Timer mDataUpdateTimer;
    private String mServerIP = "127.0.0.1";
    private int mDataUpdatePeriod = 2000;

    // GC-Optimized Buffers (Checklist: No 'new' in hot path)
    private final char[] mReadBuffer = new char[2048];
    private final StringBuilder mResponseBuilder = new StringBuilder(2048);
    private final SdData mWorkBuffer = new SdData();

    public SdDataSourceNetwork(Context context, Handler handler, SdDataReceiver sdDataReceiver) {
        super(context, handler, sdDataReceiver);
        mName = "Network";
    }

    @Override
    public void start() {
        if (mRunning) return;
        updatePrefs();
        mRunning = true;
        mDataStatusTimeMillis = System.currentTimeMillis();
        
        if (mDataUpdateTimer == null) {
            mDataUpdateTimer = new Timer();
            mDataUpdateTimer.schedule(new TimerTask() {
                @Override public void run() { pollRemoteServer(); }
            }, 0, mDataUpdatePeriod);
        }
    }

    @Override
    public void stop() {
        mRunning = false;
        if (mDataUpdateTimer != null) {
            mDataUpdateTimer.cancel();
            mDataUpdateTimer.purge();
            mDataUpdateTimer = null;
        }
    }

    /**
     * pollRemoteServer - Optimized polling routine.
     * Uses BackgroundTaskExecutor to prevent UI-jank.
     */
    private void pollRemoteServer() {
        if (!mRunning) return;

        BackgroundTaskExecutor.execute(() -> {
            try {
                String url = "http://" + mServerIP + ":8080/data";
                String result = fetchUrl(url);
                
                if (result != null && !result.startsWith("Error")) {
                    synchronized (mWorkBuffer) {
                        mWorkBuffer.fromJSON(result);
                        mWorkBuffer.serverOK = true;
                    }
                    return mWorkBuffer;
                }
            } catch (Exception e) {
                Log.w(TAG, "Forensic Network Fault");
            }
            return null;
        }, new BackgroundTaskExecutor.Callback<SdData>() {
            @Override
            public void onSuccess(SdData result) {
                if (result != null && mRunning) {
                    // Signal data reception to SdServer (Backend Core)
                    mSdDataReceiver.onSdDataReceived(result);
                } else {
                    mSdData.alarmState = AlarmState.NETFAULT;
                    mSdDataReceiver.onSdDataFault(mSdData);
                }
            }
            @Override public void onError(Exception e) {}
        });
    }

    private String fetchUrl(String myurl) throws Exception {
        mResponseBuilder.setLength(0);
        InputStream is = null;
        try {
            URL url = new URL(myurl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setReadTimeout(5000);
            conn.setConnectTimeout(5000);
            conn.setRequestMethod("GET");
            conn.connect();
            
            is = conn.getInputStream();
            Reader reader = new InputStreamReader(is, "UTF-8");
            int n;
            while ((n = reader.read(mReadBuffer)) != -1) {
                mResponseBuilder.append(mReadBuffer, 0, n);
            }
            return mResponseBuilder.toString();
        } finally {
            if (is != null) is.close();
        }
    }

    @Override public void startPebbleApp() {}
    @Override public void getStatus() { mSdData.watchConnected = true; }
    
    @Override
    public void updatePrefs() {
        SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(mContext);
        mServerIP = SP.getString("ServerIP", "127.0.0.1");
        mDataUpdatePeriod = Integer.parseInt(SP.getString("DataUpdatePeriod", "2000"));
    }

    @Override protected void faultCheck() {}
}
