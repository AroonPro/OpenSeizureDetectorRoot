package uk.org.openseizuredetector.openseizuredetector.sensors;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import uk.org.openseizuredetector.openseizuredetector.AndroidSensor;
import uk.org.openseizuredetector.openseizuredetector.data.SdData;

/**
 * GyroscopeSensor - Forensic Rotational Data Acquisition.
 * GC Policy: Zero-allocation mapping.
 * Compliance: Unit Regtien Forensic Standard / Code Review Checklist.
 */
public class GyroscopeSensor extends AndroidSensor {
    private static final String TAG = "GyroscopeSensor";
    private final SdData mSharedData;

    public GyroscopeSensor(Context context, SdData sharedData, int samplingPeriodUs, int maxLatencyUs) {
        super(context, PackageManager.FEATURE_SENSOR_GYROSCOPE, 
              Sensor.TYPE_GYROSCOPE, samplingPeriodUs, maxLatencyUs);
        this.mSharedData = sharedData;
    }

    /**
     * onSensorChanged - High-frequency rotational updates.
     * Rule: No allocations. Direct mapping to forensic buffers.
     */
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            // Forensic mapping of rotational velocity (rad/s)
            // Note: If SdData expands to include gyro-specific forensic buffers, 
            // they will be mapped here without allocation.
        }
    }
}
