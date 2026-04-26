package uk.org.openseizuredetector.openseizuredetector;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.health.services.client.ExerciseClient;
import androidx.health.services.client.HealthServices;
import androidx.health.services.client.data.DataType;
import androidx.health.services.client.data.ExerciseUpdate;
import androidx.health.services.client.ExerciseUpdateCallback;
import androidx.health.services.client.data.Availability;
import androidx.health.services.client.data.ExerciseInfo;
import androidx.health.services.client.data.ExerciseLapSummary;
import androidx.health.services.client.data.ExerciseTrackedStatus;
import androidx.health.services.client.data.ExerciseType;

import androidx.preference.PreferenceManager;

import com.google.common.util.concurrent.ListenableFuture;

/**
 * HealthServicesManager - en_GB
 * Bridges System Health Data (Google Fit / Samsung Health) to OSD logic.
 * Protocol #osd_260426: Automatic Profile Mapping from active system exercises.
 * Fixed: SharedPreferences alignment for real-time algorithm synchronization.
 */
public class HealthServicesManager {
    private static final String TAG = "HealthServicesMgr";
    private ExerciseClient mExerciseClient;
    private final SdData mSdData;
    private final Context mContext;

    public HealthServicesManager(Context context, SdData sdData) {
        this.mContext = context;
        this.mSdData = sdData;
        try {
            mExerciseClient = HealthServices.getClient(context).getExerciseClient();
        } catch (Exception e) {
            Log.e(TAG, "Health Services unavailable: " + e.getMessage());
        }
    }

    public void startMonitoring() {
        if (mExerciseClient == null) return;
        try {
            Log.i(TAG, "OSD: Monitoring system exercise state...");
            mExerciseClient.setUpdateCallback(mCallback);
            checkCurrentExercise();
        } catch (Exception e) { Log.e(TAG, "Start monitor fail: " + e.getMessage()); }
    }

    public void stopMonitoring() {
        if (mExerciseClient != null) {
            try { mExerciseClient.clearUpdateCallbackAsync(mCallback); }
            catch (Exception ignored) {}
        }
    }

    private void checkCurrentExercise() {
        if (mExerciseClient == null) return;

        ListenableFuture<ExerciseInfo> future = mExerciseClient.getCurrentExerciseInfoAsync();
        future.addListener(() -> {
            try {
                ExerciseInfo info = future.get();
                if (info != null) {
                    boolean isActive = info.getExerciseTrackedStatus() == ExerciseTrackedStatus.OWNED_EXERCISE_IN_PROGRESS 
                                    || info.getExerciseTrackedStatus() == ExerciseTrackedStatus.OTHER_APP_IN_PROGRESS;
                    mSdData.isExerciseActive = isActive;
                    
                    if (isActive) {
                        ExerciseType type = info.getExerciseType();
                        mSdData.exerciseType = type.getName();
                        autoSwitchOsdProfile(type);
                    } else {
                        mSdData.exerciseType = "NONE";
                    }
                }
            } catch (Exception e) { Log.e(TAG, "Exercise check fail: " + e.getMessage()); }
        }, Runnable::run);
    }

    private void autoSwitchOsdProfile(ExerciseType type) {
        // Architecture Fix: Use DefaultSharedPreferences to ensure Algorithm sync
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        SharedPreferences.Editor editor = prefs.edit();
        String name = type.getName().toLowerCase();
        
        Log.i(TAG, "OSD Auto-Sync: System exercise detected: " + name);

        // Map system activity to OSD physiological thresholds
        if (name.contains("walking") || name.contains("hiking") || name.contains("tracking")) {
            editor.putBoolean("walking_profile", true)
                  .putBoolean("fitness_profile", false)
                  .putBoolean("sleep_mode", false).apply();
        } else if (name.contains("running") || name.contains("rowing") || name.contains("fitness") || name.contains("workout")) {
            editor.putBoolean("fitness_profile", true)
                  .putBoolean("walking_profile", false)
                  .putBoolean("sleep_mode", false).apply();
        }
    }

    private final ExerciseUpdateCallback mCallback = new ExerciseUpdateCallback() {
        @Override
        public void onExerciseUpdateReceived(@NonNull ExerciseUpdate update) { checkCurrentExercise(); }
        @Override public void onLapSummaryReceived(@NonNull ExerciseLapSummary lapSummary) {}
        @Override public void onAvailabilityChanged(@NonNull DataType<?, ?> dataType, @NonNull Availability availability) {}
        @Override public void onRegistrationFailed(@NonNull Throwable throwable) {}
        @Override public void onRegistered() {}
    };
}
