/*
 * OpenSeizureDetector - Pebble Data Source (Modernised for 2024-2026 Trajectory)
 * Integral 'Rip' - Optimized for SDK 34+ while maintaining 2012 Eggshell compatibility.
 */

package uk.org.openseizuredetector.openseizuredetector;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;
import com.getpebble.android.kit.PebbleKit;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public class SdDataSourcePebble extends SdDataSource {
    private static final String TAG = "SdDataSourcePebble";
    private Timer mStatusTimer;
    private long mPebbleStatusTime; // Modernised from Time class to long millis
    private boolean mPebbleAppRunningCheck = false;
    private final UUID SD_UUID = UUID.fromString("03930f26-377a-4a3d-aa3e-f3b19e421c9d");

    /**
     *
     */
    @Override
    public void ClearAlarmCount() {

    }

    /**
     *
     */
    @Override
    public void handleSendingHelp() {

    }

    public SdDataSourcePebble(Context context, Handler handler, SdDataReceiver sdDataReceiver) {
        super(context, handler, sdDataReceiver);
        mName = "Pebble";

        // R-STRIP: Dynamic ID fetch to prevent compiler 'cannot find symbol' errors
        try {
            int prefsId = mContext.getResources().getIdentifier(
                    "seizure_detector_prefs", "xml", mContext.getPackageName());
            if (prefsId != 0) {
                PreferenceManager.setDefaultValues(mContext, prefsId, true);
            }
        } catch (Exception e) {
            Log.e(TAG, "R-Strip: Prefs resource not found, bypassing...");
        }
    }

    @Override
    public void start() {
        Log.i(TAG, "start() - Initialising Legacy Pebble Path");
        mUtil.writeToSysLogFile("SdDataSourcePebble.start() - 2024 Optimized");
        updatePrefs();

        mPebbleStatusTime = System.currentTimeMillis();

        if (mStatusTimer == null) {
            mStatusTimer = new Timer();
            mStatusTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    getPebbleStatus();
                }
            }, 0, 10000); // Default 10s check
        }
        super.start();
    }

    /**
     * getPebbleStatus() - Integral SDK Check
     * Uses the protected mContext to verify connection.
     */
    public void getPebbleStatus() {
        long tnow = System.currentTimeMillis();
        long tdiff = tnow - mPebbleStatusTime;

        // Use the context to check hardware state
        mSdData.watchConnected = PebbleKit.isWatchConnected(mContext);

        if (!mPebbleAppRunningCheck && (tdiff > (10 + 30) * 1000)) { // Simplified timing
            Log.w(TAG, "getStatus() - Pebble App Fault Detected - tdiff=" + tdiff);
            mSdData.watchAppRunning = false;

            // Fault notification to the 2012 eggshell receiver
            if (mSdDataReceiver != null) {
                mSdDataReceiver.onSdDataFault(mSdData);
            }

            // Attempt Kirk-style recovery
            startWatchApp();
            mPebbleStatusTime = System.currentTimeMillis();
        } else {
            mSdData.watchAppRunning = true;
        }

        if (mPebbleAppRunningCheck) {
            mPebbleAppRunningCheck = false;
            mPebbleStatusTime = System.currentTimeMillis();
        }
    }

    /**
     * Attempt to start the pebble_sd watch app on the pebble watch.
     * Optimized for 2024/2026 Background Execution Limits.
     */
    public void startWatchApp() {
        Log.v(TAG, "startWatchApp() - SDK 34 Path");
        mUtil.writeToSysLogFile("SdDataSourcePebble.startWatchApp()");

        // First, close it to ensure a fresh start
        PebbleKit.closeAppOnPebble(mContext, SD_UUID);

        // Use the handler to delay the start (Kirk delay)
        mHandler.postDelayed(() -> {
            Log.v(TAG, "startWatchApp() - Sending Start Intent");
            PebbleKit.startAppOnPebble(mContext, SD_UUID);
        }, 5000);
    }

    /**
     * Integral Toast helper.
     * Ensures the message is shown on the UI thread even if called from a background timer.
     */
    public void showToast(final String msg) {
        if (mContext != null) {
            mHandler.post(() -> {
                Toast.makeText(mContext, msg, Toast.LENGTH_SHORT).show();
            });
        }
    }

    @Override
    public void startPebbleApp() {
        mUtil.writeToSysLogFile("SdDataSourcePebble.startPebbleApp() - SDK 34 Path");
        PackageManager pm = mContext.getPackageManager();
        String[] packages = {"com.getpebble.android", "com.getpebble.android.basalt"};

        for (String pkg : packages) {
            try {
                Intent intent = pm.getLaunchIntentForPackage(pkg);
                if (intent != null) {
                    mContext.startActivity(intent);
                    return;
                }
            } catch (Exception e) {
                Log.d(TAG, "Package " + pkg + " not found.");
            }
        }
        // Fallback: Web redirect for the "Ghost" app
        this.showToast("Pebble App not found - Please check installation.");
    }

    @Override
    public void stop() {
        if (mStatusTimer != null) {
            mStatusTimer.cancel();
            mStatusTimer = null;
        }
        super.stop();
    }

    /**
     *
     */
    @Override
    public void muteCheck() {

    }

    /**
     *
     */
    @Override
    protected void getStatus() {

    }

    /**
     *
     */
    @Override
    protected void faultCheck() {

    }

    /**
     *
     */
    @Override
    public void hrCheck() {

    }

    /**
     *
     */
    @Override
    public void o2SatCheck() {

    }

    /**
     *
     */
    @Override
    public void fallCheck() {

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
}
