package uk.org.openseizuredetector.openseizuredetector;

import android.content.Context;
import android.os.Handler;
import android.util.Log;
import androidx.preference.PreferenceManager;

/**
 * SdDataSourceGarmin - Unit Regtien Optimized.
 * Forensic JSON ingestion from Garmin devices with zero-overhead analysis.
 */
public class SdDataSourceGarmin extends SdDataSource {
    private static final String TAG = "SdDataSourceGarmin";

    public SdDataSourceGarmin(Context context, Handler handler, SdDataReceiver sdDataReceiver) {
        super(context, handler, sdDataReceiver);
        this.mName = "Garmin";
        initialisePrefs(context);
    }

    private void initialisePrefs(Context context) {
        int prefsId = context.getResources().getIdentifier("network_passive_datasource_prefs", "xml", context.getPackageName());
        if (prefsId != 0) {
            PreferenceManager.setDefaultValues(context, prefsId, true);
        }
    }

    @Override public void start() { super.start(); }
    @Override public void stop() { super.stop(); }

    @Override
    public void updateFromJSON(String jsonStr) {
        if (jsonStr == null || jsonStr.isEmpty()) return;
        // Directly invoke restored base method for JSON ingestion
        super.updateFromJSON(jsonStr);
        doAnalysis();
    }

    @Override public void startPebbleApp() {}
    @Override protected void getStatus() { triggerUiUpdate(); }
    @Override public void handleSendingHelp() { Log.i(TAG, "Garmin SOS Triggered"); }
    @Override public void o2SatCheck() { super.o2SatCheck(); }
}
