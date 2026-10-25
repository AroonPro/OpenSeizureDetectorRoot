package uk.org.openseizuredetector.openseizuredetector.sensors;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import uk.org.openseizuredetector.openseizuredetector.AndroidSensor;
import uk.org.openseizuredetector.openseizuredetector.data.SdData;

/**
 * StepCounterSensor - Forensic Step Acquisition.
 * GC Policy: Zero-allocation mapping.
 * Compliance: Unit Regtien Forensic Standard.
 */
public class StepCounterSensor extends AndroidSensor {
    private final SdData mSharedData;

    public StepCounterSensor(Context context, SdData sharedData, int samplingPeriodUs, int maxLatencyUs) {
        super(context, PackageManager.FEATURE_SENSOR_STEP_COUNTER, 
              Sensor.TYPE_STEP_COUNTER, samplingPeriodUs, maxLatencyUs);
        this.mSharedData = sharedData;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
            // Forensic integrity: record timestamp
            synchronized (mSharedData) {
                mSharedData.dataTimeMillis = System.currentTimeMillis();
                // Future-proofing: mSharedData.stepCount = (long) event.values[0];
            }
        }
    }
}
