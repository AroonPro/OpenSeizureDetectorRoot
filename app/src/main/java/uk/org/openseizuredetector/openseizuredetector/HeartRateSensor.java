package uk.org.openseizuredetector.openseizuredetector;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;

public class HeartRateSensor extends AndroidSensor {
    public HeartRateSensor(Context context, int samplingPeriodUs, int maxLatencyUs) {
        super(context, PackageManager.FEATURE_SENSOR_HEART_RATE, Sensor.TYPE_HEART_RATE, samplingPeriodUs, maxLatencyUs);
    }
    @Override public void onSensorChanged(SensorEvent event) {}
}
