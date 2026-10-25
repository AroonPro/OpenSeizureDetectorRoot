package uk.org.openseizuredetector.openseizuredetector;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;

public class SamsungSamsungHealthSpO2Sensor extends AndroidSensor {
    public SamsungSamsungHealthSpO2Sensor(Context context, int samplingPeriodUs, int maxLatencyUs) {
        super(context, "com.samsung.health.sensor.spo2", 65541, samplingPeriodUs, maxLatencyUs);
    }
    @Override public void onSensorChanged(SensorEvent event) {}
}
