package uk.org.openseizuredetector.openseizuredetector;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.util.Log;

/**
 * Sensors.java - en_GB
 * Complete Registry of Physical and Virtual OSD sensors.
 */

class AccelerationSensor extends AndroidSensor {
    public AccelerationSensor(Context context, int samplingPeriodUs, int maxLatencyUs) {
        super(context, PackageManager.FEATURE_SENSOR_ACCELEROMETER, Sensor.TYPE_ACCELEROMETER, samplingPeriodUs, maxLatencyUs);
    }
    @Override public void onSensorChanged(SensorEvent event) {}
}

class GyroscopeSensor extends AndroidSensor {
    public GyroscopeSensor(Context context, int samplingPeriodUs, int maxLatencyUs) {
        super(context, PackageManager.FEATURE_SENSOR_GYROSCOPE, Sensor.TYPE_GYROSCOPE, samplingPeriodUs, maxLatencyUs);
    }
    @Override public void onSensorChanged(SensorEvent event) {}
}

class HeartRateSensor extends AndroidSensor {
    public HeartRateSensor(Context context, int samplingPeriodUs, int maxLatencyUs) {
        super(context, PackageManager.FEATURE_SENSOR_HEART_RATE, Sensor.TYPE_HEART_RATE, samplingPeriodUs, maxLatencyUs);
    }
    @Override public void onSensorChanged(SensorEvent event) {}
}

class SamsungSamsungHealthHeartRateSensor extends AndroidSensor {
    public SamsungSamsungHealthHeartRateSensor(Context context, int samplingPeriodUs, int maxLatencyUs) {
        super(context, "com.samsung.health.sensor.heart_rate", Sensor.TYPE_HEART_RATE, samplingPeriodUs, maxLatencyUs);
    }
    @Override public void onSensorChanged(SensorEvent event) {}
}

class SamsungSamsungHealthSpO2Sensor extends AndroidSensor {
    public SamsungSamsungHealthSpO2Sensor(Context context, int samplingPeriodUs, int maxLatencyUs) {
        super(context, "com.samsung.health.sensor.spo2", 65541, samplingPeriodUs, maxLatencyUs);
    }
    @Override public void onSensorChanged(SensorEvent event) {}
}

class SamsungWearSpO2Sensor extends AndroidSensor {
    public SamsungWearSpO2Sensor(Context context, int samplingPeriodUs, int maxLatencyUs) {
        super(context, "com.samsung.wear.sensor.continuous_spo2", 65541, samplingPeriodUs, maxLatencyUs);
    }
    @Override public void onSensorChanged(SensorEvent event) {}
}

class OffBodyDetectSensor extends AndroidSensor {
    public OffBodyDetectSensor(Context context, int samplingPeriodUs, int maxLatencyUs) {
        super(context, "android.hardware.sensor.low_latency_offbody_detect", Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT, samplingPeriodUs, maxLatencyUs);
    }
    @Override public void onSensorChanged(SensorEvent event) {}
}

class StationaryDetectSensor extends AndroidSensor {
    public StationaryDetectSensor(Context context, int samplingPeriodUs, int maxLatencyUs) {
        super(context, "android.hardware.sensor.stationary_detect", Sensor.TYPE_STATIONARY_DETECT, samplingPeriodUs, maxLatencyUs);
    }
    @Override public void onSensorChanged(SensorEvent event) {}
}

class EnvironmentalPressureSensor extends AndroidSensor {
    public EnvironmentalPressureSensor(Context context, int samplingPeriodUs, int maxLatencyUs) {
        super(context, PackageManager.FEATURE_SENSOR_BAROMETER, Sensor.TYPE_PRESSURE, samplingPeriodUs, maxLatencyUs);
    }
    @Override public void onSensorChanged(SensorEvent event) {}
}

class LightSensor extends AndroidSensor {
    public LightSensor(Context context, int samplingPeriodUs, int maxLatencyUs) {
        super(context, PackageManager.FEATURE_SENSOR_LIGHT, Sensor.TYPE_LIGHT, samplingPeriodUs, maxLatencyUs);
    }
    @Override public void onSensorChanged(SensorEvent event) {}
}

class ProximitySensor extends AndroidSensor {
    public ProximitySensor(Context context, int samplingPeriodUs, int maxLatencyUs) {
        super(context, PackageManager.FEATURE_SENSOR_PROXIMITY, Sensor.TYPE_PROXIMITY, samplingPeriodUs, maxLatencyUs);
    }
    @Override public void onSensorChanged(SensorEvent event) {}
}

class StepCounterSensor extends AndroidSensor {
    public StepCounterSensor(Context context, int samplingPeriodUs, int maxLatencyUs) {
        super(context, PackageManager.FEATURE_SENSOR_STEP_COUNTER, Sensor.TYPE_STEP_COUNTER, samplingPeriodUs, maxLatencyUs);
    }
    @Override public void onSensorChanged(SensorEvent event) {}
}

class AmbientTemperatureSensor extends AndroidSensor {
    public AmbientTemperatureSensor(Context context, int samplingPeriodUs, int maxLatencyUs) {
        super(context, PackageManager.FEATURE_SENSOR_AMBIENT_TEMPERATURE, Sensor.TYPE_AMBIENT_TEMPERATURE, samplingPeriodUs, maxLatencyUs);
    }
    @Override public void onSensorChanged(SensorEvent event) {}
}
