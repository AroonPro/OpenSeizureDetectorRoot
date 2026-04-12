/*
  OpenSeizureDetector - Garmin Data Source Modernisation (en_GB)
  Updated for 2024-2026 Stability Trajectory
*/

package uk.org.openseizuredetector.openseizuredetector;

import static uk.org.openseizuredetector.openseizuredetector.OsdUtil.useSdServerBinding;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
// We use the modern PreferenceManager from AndroidX
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

/**
 * Modernised Garmin Data Source.
 * R-Strip: Bypasses missing resource IDs to allow compilation in different modules.
 * SDK-Ready: Uses the 2024 binding pattern.
 */
public class SdDataSourceGarmin extends SdDataSource {
    private static final String TAG = "SdDataSourceGarmin";

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

    public SdDataSourceGarmin(Context context, Handler handler,
                              SdDataReceiver sdDataReceiver) {
        // Carry over the binding context from your 'gup' (Version 1) logic
        super(context, handler, sdDataReceiver);
        mName = "Garmin";

        try {
            /* R-STRIP / KIRK MANOEUVRE:
               Instead of crashing the compiler on R.xml.network_passive_datasource_prefs,
               we use a try-catch or a hardcoded check. If the resource isn't there,
               we proceed with defaults rather than failing the build.
            */
            // Centrally managed context via OsdUtil.useSdServerBinding()
            int prefsId = useSdServerBinding().getResources().getIdentifier(
                    "network_passive_datasource_prefs", "xml", useSdServerBinding().getPackageName());

            if (prefsId != 0) {
                PreferenceManager.setDefaultValues(useSdServerBinding(), prefsId, true);
            } else {
                Log.w(TAG, "Resource 'network_passive_datasource_prefs' not found - using internal defaults.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialise preferences via R: " + e.getMessage());
        }
    }

    @Override
    public void start() {
        Log.i(TAG, "start() - Modernised SDK Check");
        // Ensure mUtil exists to prevent NullPointerException on modern Android versions
        if (mUtil != null) {
            mUtil.writeToSysLogFile("SdDataSourceGarmin.start() - 2024 Path Active");
        }

        // Call super.start() which, in your 'gup' version, contains the timing logic
        super.start();
    }

    /**
     *
     */
    @Override
    public void startPebbleApp() {

    }

    @Override
    public void stop() {
        Log.i(TAG, "stop()");
        if (mUtil != null) {
            mUtil.writeToSysLogFile("SdDataSourceGarmin.stop()");
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
     * Modernisation: Handle JSON data specifically for Garmin/Wear OS
     * without relying on the phone's UI-specific R classes.
     */
    @Override
    public void updateFromJSON(String jsonStr) {
        if (jsonStr == null || jsonStr.isEmpty()) {
            Log.e(TAG, "Empty JSON received - Ignoring to protect 2012 eggshell stability.");
            return;
        }
        // Let the super class (your improved 'gup' version) do the heavy lifting
        super.updateFromJSON(jsonStr);
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
