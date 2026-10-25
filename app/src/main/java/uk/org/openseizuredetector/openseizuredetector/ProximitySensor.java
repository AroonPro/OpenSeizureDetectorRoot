package uk.org.openseizuredetector.openseizuredetector;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;

public class ProximitySensor extends AndroidSensor {
    public ProximitySensor(Context context, int samplingPeriodUs, int maxLatencyUs) {
        super(context, PackageManager.FEATURE_SENSOR_PROXIMITY, Sensor.TYPE_PROXIMITY, samplingPeriodUs, maxLatencyUs);
    }
    @Override public void onSensorChanged(SensorEvent event) {}
}
