package uk.org.openseizuredetector.openseizuredetector;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.util.Log;

import java.io.Closeable;

/**
 * AndroidSensor - Unified base class for OSD wearable sensors.
 * Optimized for GC: Explicit lifecycle management instead of finalize().
 * Compliance: Unit Regtien Forensic Standard / Code Review Checklist.
 */
public abstract class AndroidSensor implements SensorEventListener, Closeable {

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
        this.TAG = (className.isEmpty()) ? "OSD_Sensor" : className;
        
        // Attribution for API 30+ to respect forensic auditing
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            this.mContext = context.createAttributionContext("osd_sensors");
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
        }
    }

    public boolean getDoesSensorExist() {
        return mSensor != null;
    }

    public void startListening() {
        if (getDoesSensorExist() && !isSensorListening) {
            Log.d(TAG, "LSA Audit: Commencing sensor flow for " + mSensorFeature);
            mSensorManager.registerListener(this, mSensor, mSensorSamplingPeriodUs, mSensorMaxReportLatencyUs);
            isSensorListening = true;
        }
    }

    public void stopListening() {
        if (isSensorListening) {
            mSensorManager.unregisterListener(this);
            isSensorListening = false;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    /**
     * Use close() for explicit memory cleanup to prevent GC stalls.
     */
    @Override
    public void close() {
        stopListening();
        mSensorManager = null;
        mSensor = null;
        mContext = null;
    }
}
