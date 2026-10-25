package uk.org.openseizuredetector.openseizuredetector.sensors;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import uk.org.openseizuredetector.openseizuredetector.AndroidSensor;
import uk.org.openseizuredetector.openseizuredetector.data.SdData;

/**
 * ProximitySensor - Forensic Proximity Acquisition.
 * GC Policy: Zero-allocation mapping.
 * Compliance: Unit Regtien Forensic Standard.
 */
public class ProximitySensor extends AndroidSensor {
    private final SdData mSharedData;

    public ProximitySensor(Context context, SdData sharedData, int samplingPeriodUs, int maxLatencyUs) {
        super(context, PackageManager.FEATURE_SENSOR_PROXIMITY, 
              Sensor.TYPE_PROXIMITY, samplingPeriodUs, maxLatencyUs);
        this.mSharedData = sharedData;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_PROXIMITY) {
            // Forensic integrity: record timestamp
            synchronized (mSharedData) {
                mSharedData.dataTimeMillis = System.currentTimeMillis();
                // Note: Proximity value is often binary (0 or 5cm). 
                // Can be used to detect if watch is covered.
            }
        }
    }
}
