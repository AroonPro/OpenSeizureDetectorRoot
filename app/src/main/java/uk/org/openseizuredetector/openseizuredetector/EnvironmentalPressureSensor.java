package uk.org.openseizuredetector.openseizuredetector;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;

public class EnvironmentalPressureSensor extends AndroidSensor {
    public EnvironmentalPressureSensor(Context context, int samplingPeriodUs, int maxLatencyUs) {
        super(context, PackageManager.FEATURE_SENSOR_BAROMETER, Sensor.TYPE_PRESSURE, samplingPeriodUs, maxLatencyUs);
    }
    @Override public void onSensorChanged(SensorEvent event) {}
}
