package uk.org.openseizuredetector.openseizuredetector;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

/**
 * AndroidSensor - en_GB
 * Unified base class for OSD wearable sensors.
 */
abstract class AndroidSensor implements SensorEventListener {

    protected final String TAG;
    protected Context mContext;
    protected String mSensorFeature;
    protected int mSensorType;
    protected int mSensorSamplingPeriodUs;
    protected int mSensorMaxReportLatencyUs;

    private SensorManager mSensorManager;
    private Sensor mSensor;
    private boolean isSensorListening = false;

    public AndroidSensor(Context context, String sensorFeature, int sensorType,
                         int sensorSamplingPeriodUs, int sensorMaxReportLatencyUs) {
        String className = this.getClass().getSimpleName();
        this.TAG = (className.isEmpty()) ? "OSD_Sensor_Anon" : className;
        this.mContext = context;
        this.mSensorFeature = sensorFeature;
        this.mSensorType = sensorType;
        this.mSensorSamplingPeriodUs = sensorSamplingPeriodUs;
        this.mSensorMaxReportLatencyUs = sensorMaxReportLatencyUs;

        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if (mSensorManager != null) {
            mSensor = mSensorManager.getDefaultSensor(mSensorType);
        }
    }

    public boolean getDoesSensorExist() {
        return mSensor != null;
    }

    public void startListening() {
        if (getDoesSensorExist() && !isSensorListening) {
            Log.d(TAG, "LSA Audit: Starting sensor listening for " + mSensorFeature + " (Type: " + mSensorType + ")");
            boolean result = mSensorManager.registerListener(this, mSensor, mSensorSamplingPeriodUs, mSensorMaxReportLatencyUs);
            Log.d(TAG, "registerListener result: " + result);
            isSensorListening = true;
        }
    }

    public void stopListening() {
        if (isSensorListening) {
            Log.d(TAG, "LSA Audit: Stopping sensor listening for " + mSensorFeature);
            mSensorManager.unregisterListener(this);
            isSensorListening = false;
        }
    }

    public boolean isSensorListening() {
        return isSensorListening;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        if (getDoesSensorExist() && sensor.getType() == mSensorType) {
            Log.v(TAG, "Accuracy changed: " + accuracy);
        }
    }

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
