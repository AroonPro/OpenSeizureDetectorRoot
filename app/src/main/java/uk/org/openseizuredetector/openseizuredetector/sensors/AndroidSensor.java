package uk.org.openseizuredetector.openseizuredetector.sensors;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.util.Log;

/**
 * AndroidSensor - Unified base class for OpenSeizureDetector wearable sensors.
 * This class handles common sensor lifecycle tasks such as registration, 
 * unregistration, and privacy attribution for Android 11+.
 */
public abstract class AndroidSensor implements SensorEventListener {

    protected final String TAG;
    protected final Context mContext;
    protected final String mSensorFeature;
    protected final int mSensorType;
    protected final int mSensorSamplingPeriodUs;
    protected final int mSensorMaxReportLatencyUs;

    private final SensorManager mSensorManager;
    private final Sensor mSensor;
    private boolean isSensorListening = false;

    // Log messages moved to constants
    private static final String LOG_START = "LSA Audit: Starting sensor listening for ";
    private static final String ATTR_TAG = "osd_sensors";

    /**
     * Initializes a new AndroidSensor instance.
     *
     * @param context The application context.
     * @param sensorFeature The Android feature string for the sensor.
     * @param sensorType The sensor type constant from {@link Sensor}.
     * @param sensorSamplingPeriodUs The sampling period in microseconds.
     * @param sensorMaxReportLatencyUs The maximum report latency in microseconds.
     */
    public AndroidSensor(Context context, String sensorFeature, int sensorType,
                         int sensorSamplingPeriodUs, int sensorMaxReportLatencyUs) {
        String className = this.getClass().getSimpleName();
        this.TAG = (className.isEmpty()) ? "OSD_Sensor_Anon" : className;
        
        // Apply Privacy Attribution for API 30+ to ensure proper auditing
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            this.mContext = context.createAttributionContext(ATTR_TAG);
        } else {
            this.mContext = context;
        }

        this.mSensorFeature = sensorFeature;
        this.mSensorType = sensorType;
        this.mSensorSamplingPeriodUs = sensorSamplingPeriodUs;
        this.mSensorMaxReportLatencyUs = sensorMaxReportLatencyUs;

        mSensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
        if (mSensorManager != null) {
            mSensor = mSensorManager.getDefaultSensor(mSensorType);
        } else {
            mSensor = null;
        }
    }

    /**
     * Checks if the hardware sensor exists on this device.
     * @return true if the sensor is available.
     */
    public boolean getDoesSensorExist() {
        return mSensor != null;
    }

    /**
     * Starts listening for sensor events.
     */
    public void startListening() {
        if (getDoesSensorExist() && !isSensorListening) {
            Log.d(TAG, LOG_START + mSensorFeature);
            mSensorManager.registerListener(this, mSensor, mSensorSamplingPeriodUs, mSensorMaxReportLatencyUs);
            isSensorListening = true;
        }
    }

    /**
     * Stops listening for sensor events.
     */
    public void stopListening() {
        if (isSensorListening) {
            mSensorManager.unregisterListener(this);
            isSensorListening = false;
        }
    }

    /**
     * @return true if the sensor is currently active and listening.
     */
    public boolean isSensorListening() {
        return isSensorListening;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    /**
     * Called when sensor values have changed. 
     * Implement this in subclasses to process specific sensor data.
     */
    @Override
    public abstract void onSensorChanged(SensorEvent event);

    @Override
    protected void finalize() throws Throwable {
        try {
            if (isSensorListening) {
                stopListening();
            }
        } finally {
            super.finalize();
        }
    }
}
