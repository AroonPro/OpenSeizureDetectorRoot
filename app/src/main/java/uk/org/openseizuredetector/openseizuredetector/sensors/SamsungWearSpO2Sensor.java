package uk.org.openseizuredetector.openseizuredetector.sensors;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.util.Log;
import uk.org.openseizuredetector.openseizuredetector.AndroidSensor;
import uk.org.openseizuredetector.openseizuredetector.data.SdData;

/**
 * SamsungWearSpO2Sensor - Forensic Blood Oxygen Acquisition.
 * GC Policy: Zero-allocation.
 * Compliance: Unit Regtien Forensic Standard.
 */
public class SamsungWearSpO2Sensor extends AndroidSensor {
    private static final String TAG = "SamsungSpO2";
    private static final String SENSOR_STRING = "com.samsung.wear.sensor.continuous_spo2";
    private final SdData mSharedData;

    public SamsungWearSpO2Sensor(Context context, SdData sharedData, int samplingPeriodUs, int maxLatencyUs) {
        super(context, SENSOR_STRING, 65541, samplingPeriodUs, maxLatencyUs);
        this.mSharedData = sharedData;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == 65541) {
            float spo2 = event.values[0];
            if (spo2 > 0) {
                synchronized (mSharedData) {
                    mSharedData.mO2Sat = (double) spo2;
                    mSharedData.dataTimeMillis = System.currentTimeMillis();
                }
            }
        }
    }
}
