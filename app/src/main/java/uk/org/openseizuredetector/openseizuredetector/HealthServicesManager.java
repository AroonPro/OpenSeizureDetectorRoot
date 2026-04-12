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
 * HealthServicesManager - Monitors user activity (Sport Mode) using Wear OS Health Services.
 */
public class HealthServicesManager {
    private static final String TAG = "HealthServicesMgr";
    private final ExerciseClient mExerciseClient;
    private final SdData mSdData;

    public HealthServicesManager(Context context, SdData sdData) {
        mExerciseClient = HealthServices.getClient(context).getExerciseClient();
        mSdData = sdData;
    }

    public void startMonitoring() {
        Log.i(TAG, "Monitoring user activity...");
        mExerciseClient.setUpdateCallback(mCallback);
        checkCurrentExercise();
    }

    public void triggerManualSpO2Measurement() {
        Log.d(TAG, "Manual SpO2 measurement request received (Samsung SDK/Hardware prioritized)");
        // Actual measurement is triggered via the Samsung sensor in SdDataSourceAw
    }

    public void stopMonitoring() {
        mExerciseClient.clearUpdateCallbackAsync(mCallback);
    }

    private void checkCurrentExercise() {
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
        @Override public void onRegistrationFailed(@NonNull Throwable throwable) {}
        @Override public void onRegistered() {}
    };
}
