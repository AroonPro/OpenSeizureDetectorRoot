package uk.org.openseizuredetector.openseizuredetector.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;
import uk.org.openseizuredetector.openseizuredetector.data.logging.Log;

public class BootBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "BootBroadcastReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "onReceive(): Boot completed, checking auto-start");
        SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(context);
        boolean autoStart = SP.getBoolean("AutoStart", false);
        if (autoStart) {
            Log.i(TAG, "onReceive(): AutoStart is enabled - launching Main Activity");
            try {
                Class<?> activityClass = getMainActivityClass(context);
                if (activityClass != null) {
                    Intent startUpIntent = new Intent(context, activityClass);
                    startUpIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(startUpIntent);
                    Log.i(TAG, "onReceive(): Activity launched successfully");
                } else {
                    Log.e(TAG, "onReceive(): failed to find activity class");
                }
            } catch (Exception e) {
                Log.e(TAG, "onReceive(): failed to launch Activity - " + e.getMessage());
            }
        }
    }

    private Class<?> getMainActivityClass(Context context) {
        // Controleer of we op Wear zitten (package bevat .aw)
        boolean isWear = context.getPackageName().contains(".aw");
        
        try {
            if (isWear) {
                // Pad naar de Wear Startup Activity
                return Class.forName("uk.org.openseizuredetector.aw.StartUpActivityWear");
            } else {
                // Pad naar de Phone/App Main Activity
                return Class.forName("uk.org.openseizuredetector.openseizuredetector.activity.main.MainActivity2");
            }
        } catch (ClassNotFoundException e) {
            Log.e(TAG, "MainActivity class not found: " + e.getMessage());
            return null;
        }
    }
}
