package uk.org.openseizuredetector.openseizuredetector;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;

public class SamsungWearSpO2Sensor extends AndroidSensor {
    public SamsungWearSpO2Sensor(Context context, int samplingPeriodUs, int maxLatencyUs) {
        super(context, "com.samsung.wear.sensor.continuous_spo2", 65541, samplingPeriodUs, maxLatencyUs);
    }
    @Override public void onSensorChanged(SensorEvent event) {}
}
