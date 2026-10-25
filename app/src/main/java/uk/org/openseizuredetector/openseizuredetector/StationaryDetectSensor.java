package uk.org.openseizuredetector.openseizuredetector;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;

public class StationaryDetectSensor extends AndroidSensor {
    public StationaryDetectSensor(Context context, int samplingPeriodUs, int maxLatencyUs) {
        super(context, "android.hardware.sensor.stationary_detect", Sensor.TYPE_STATIONARY_DETECT, samplingPeriodUs, maxLatencyUs);
    }
    @Override public void onSensorChanged(SensorEvent event) {}
}
