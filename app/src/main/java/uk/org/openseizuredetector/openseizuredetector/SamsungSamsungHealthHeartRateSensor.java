package uk.org.openseizuredetector.openseizuredetector;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;

public class SamsungSamsungHealthHeartRateSensor extends AndroidSensor {
    public SamsungSamsungHealthHeartRateSensor(Context context, int samplingPeriodUs, int maxLatencyUs) {
        super(context, "com.samsung.health.sensor.heart_rate", Sensor.TYPE_HEART_RATE, samplingPeriodUs, maxLatencyUs);
    }
    @Override public void onSensorChanged(SensorEvent event) {}
}
