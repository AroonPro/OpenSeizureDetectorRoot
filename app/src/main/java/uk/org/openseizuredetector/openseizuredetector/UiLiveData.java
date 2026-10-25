package uk.org.openseizuredetector.openseizuredetector;

import androidx.lifecycle.LiveData;

/**
 * UiLiveData - Forensic Signaling Channel.
 * Purpose: Decouples the Backend Service from the UI RenderThread.
 * Pattern: Dirty-bit signaling via timestamp to avoid heavy object passing in LiveData.
 *
 * Compliance: Unit Regtien Forensic Standard / Code Review Checklist.
 */
public class UiLiveData extends LiveData<Long> {
    private static final String TAG = "UiLiveData";

    public UiLiveData() {
        // Initialise with start-time marker
        postValue(System.currentTimeMillis());
    }

    /**
     * signalChangedData - Triggers UI observers to refresh their view state.
     * GC-Safe: Only posts a primitive Long, zero heap churn for complex data.
     */
    public void signalChangedData() {
        postValue(System.currentTimeMillis());
    }
}
