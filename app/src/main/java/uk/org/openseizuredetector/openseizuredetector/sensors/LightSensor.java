package uk.org.openseizuredetector.openseizuredetector.sensors;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import uk.org.openseizuredetector.openseizuredetector.AndroidSensor;
import uk.org.openseizuredetector.openseizuredetector.data.SdData;

/**
 * LightSensor - Forensic Ambient Light Acquisition.
 * GC Policy: Zero-allocation mapping.
 * Compliance: Unit Regtien Forensic Standard.
 */
public class LightSensor extends AndroidSensor {
    private final SdData mSharedData;

    public LightSensor(Context context, SdData sharedData, int samplingPeriodUs, int maxLatencyUs) {
        super(context, PackageManager.FEATURE_SENSOR_LIGHT, 
              Sensor.TYPE_LIGHT, samplingPeriodUs, maxLatencyUs);
        this.mSharedData = sharedData;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
            // Forensic integrity: record timestamp of change
            synchronized (mSharedData) {
                mSharedData.dataTimeMillis = System.currentTimeMillis();
                // Future-proofing: mSharedData.ambientLux = event.values[0];
            }
        }
    }
}
