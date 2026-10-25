package uk.org.openseizuredetector.openseizuredetector;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;

public class LightSensor extends AndroidSensor {
    public LightSensor(Context context, int samplingPeriodUs, int maxLatencyUs) {
        super(context, PackageManager.FEATURE_SENSOR_LIGHT, Sensor.TYPE_LIGHT, samplingPeriodUs, maxLatencyUs);
    }
    @Override public void onSensorChanged(SensorEvent event) {}
}
