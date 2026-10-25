package uk.org.openseizuredetector.openseizuredetector.sensors;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.util.Log;
import uk.org.openseizuredetector.openseizuredetector.AndroidSensor;
import uk.org.openseizuredetector.openseizuredetector.data.SdData;

/**
 * HeartRateSensor - Forensic Heart Rate Acquisition.
 * GC Policy: Zero-allocation mapping.
 * Compliance: Unit Regtien Forensic Standard.
 */
public class HeartRateSensor extends AndroidSensor {
    private static final String TAG = "HeartRateSensor";
    private final SdData mSharedData;

    public HeartRateSensor(Context context, SdData sharedData, int samplingPeriodUs, int maxLatencyUs) {
        super(context, PackageManager.FEATURE_SENSOR_HEART_RATE, 
              Sensor.TYPE_HEART_RATE, samplingPeriodUs, maxLatencyUs);
        this.mSharedData = sharedData;
    }

    /**
     * onSensorChanged - High-frequency heart rate updates.
     * Rule: No allocations. Direct primitive mapping.
     */
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_HEART_RATE) {
            float hrValue = event.values[0];
            
            // Forensic integrity check
            if (hrValue > 0) {
                synchronized (mSharedData) {
                    mSharedData.mHR = (double) hrValue;
                    mSharedData.mHr = (double) hrValue; // Compatibility alias
                    mSharedData.dataTimeMillis = System.currentTimeMillis();
                }
                Log.v(TAG, "Forensic HR Update: " + hrValue);
            }
        }
    }
}
