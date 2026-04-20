package uk.org.openseizuredetector.openseizuredetector;

import android.content.Context;
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

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutionException;

/**
 * HealthServicesManager - Monitors user activity (Sport Mode).
 * Fixed: Robust checks for emulator/device compatibility to prevent startup crashes.
 */
public class HealthServicesManager {
    private static final String TAG = "HealthServicesMgr";
    private ExerciseClient mExerciseClient;
    private final SdData mSdData;

    public HealthServicesManager(Context context, SdData sdData) {
        mSdData = sdData;
        try {
            mExerciseClient = HealthServices.getClient(context).getExerciseClient();
        } catch (Exception e) {
            Log.e(TAG, "Health Services not available on this device: " + e.getMessage());
            mExerciseClient = null;
        }
    }

    public void startMonitoring() {
        if (mExerciseClient == null) return;
        
        try {
            Log.i(TAG, "Starting user activity monitoring...");
            mExerciseClient.setUpdateCallback(mCallback);
            checkCurrentExercise();
        } catch (Exception e) {
            Log.e(TAG, "Failed to start monitoring: " + e.getMessage());
        }
    }

    public void stopMonitoring() {
        if (mExerciseClient != null) {
            try {
                mExerciseClient.clearUpdateCallbackAsync(mCallback);
            } catch (Exception e) {
                Log.e(TAG, "Error stopping monitoring: " + e.getMessage());
            }
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
                    mSdData.exerciseType = isActive ? info.getExerciseType().getName() : "NONE";
                }
            } catch (Exception e) {
                Log.e(TAG, "Error checking current exercise: " + e.getMessage());
            }
        }, Runnable::run);
    }

    private final ExerciseUpdateCallback mCallback = new ExerciseUpdateCallback() {
        @Override
        public void onExerciseUpdateReceived(@NonNull ExerciseUpdate update) {
            checkCurrentExercise();
        }

        @Override public void onLapSummaryReceived(@NonNull ExerciseLapSummary lapSummary) {}
        @Override public void onAvailabilityChanged(@NonNull DataType<?, ?> dataType, @NonNull Availability availability) {}
        @Override public void onRegistrationFailed(@NonNull Throwable throwable) {
            Log.e(TAG, "Health Services registration failed: " + throwable.getMessage());
        }
        @Override public void onRegistered() {
            Log.i(TAG, "Health Services callback registered.");
        }
    };
}
