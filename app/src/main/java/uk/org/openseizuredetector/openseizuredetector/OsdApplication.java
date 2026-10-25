package uk.org.openseizuredetector.openseizuredetector;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.rohitss.uceh.UCEHandler;
import uk.org.openseizuredetector.openseizuredetector.utils.OsdUtil;

/**
 * OsdApplication - Root context for OpenSeizureDetector.
 * Complies with Code Review Checklist: Centralized singleton for forensic utils.
 */
public class OsdApplication extends Application {
    private static final String TAG = "OsdApplication";
    private static OsdUtil sUtils;
    private static OsdApplication sInstance;

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        
        Log.i(TAG, "Forensic Startup Initialised.");
        
        // Root Utility Engine
        sUtils = new OsdUtil(this, new Handler(Looper.getMainLooper()));
        
        // Forensic Theme Management
        OsdUtil.applyTheme(this);

        // Global Error Handling (Unit Regtien Standard)
        new UCEHandler.Builder(this)
                .addCommaSeparatedEmailAddresses("crashreports@openseizuredetector.org.uk")
                .build();
    }

    public static Context getContext() {
        return sInstance.getApplicationContext();
    }

    public static OsdUtil getUtils() {
        return sUtils;
    }
}
