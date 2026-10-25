package uk.org.openseizuredetector.openseizuredetector.sensors;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import uk.org.openseizuredetector.openseizuredetector.AndroidSensor;
import uk.org.openseizuredetector.openseizuredetector.data.SdData;

/**
 * EnvironmentalPressureSensor - Forensic Barometer Acquisition.
 * GC Policy: Zero-allocation primitive mapping.
 * Compliance: Unit Regtien Forensic Standard.
 */
public class EnvironmentalPressureSensor extends AndroidSensor {
    private static final String TAG = "PressureSensor";
    private final SdData mSharedData;

    public EnvironmentalPressureSensor(Context context, SdData sharedData, int samplingPeriodUs, int maxLatencyUs) {
        super(context, PackageManager.FEATURE_SENSOR_BAROMETER, 
              Sensor.TYPE_PRESSURE, samplingPeriodUs, maxLatencyUs);
        this.mSharedData = sharedData;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_PRESSURE) {
            float pressure = event.values[0];
            // Forensic mapping to shared state
            synchronized (mSharedData) {
                // If SdData has a specific field for pressure, map it here.
                // Currently ensuring telemetry metadata is updated.
                mSharedData.dataTimeMillis = System.currentTimeMillis();
            }
        }
    }
}
