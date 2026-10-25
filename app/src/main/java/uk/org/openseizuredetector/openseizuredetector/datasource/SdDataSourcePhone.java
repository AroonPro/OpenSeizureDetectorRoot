package uk.org.openseizuredetector.openseizuredetector.datasource;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import uk.org.openseizuredetector.openseizuredetector.data.logging.Log;

/**
 * SdDataSourcePhone - Optimized Internal Sensor Source.
 * GC Policy: Zero-allocation policy in high-frequency sensor callbacks (25Hz+).
 * Compliance: Unit Regtien Forensic Standard / Code Review Checklist.
 */
public class SdDataSourcePhone extends SdDataSource implements SensorEventListener {
    private static final String TAG = "SdDataSourcePhone";
    
    private SensorManager mSensorManager;
    private Sensor mAccelSensor;
    
    // GC-Optimized: Target period to maintain consistent 25Hz without drift
    private static final long TARGET_PERIOD_NS = 40_000_000L; // 40ms = 25Hz
    private long mLastSampleTs = 0;
    private int mSampleCount = 0;
    
    // Constant for gravity conversion to minimize FPU churn
    private static final double MS2_TO_MG = 1000.0 / 9.80665;

    public SdDataSourcePhone(Context context, Handler handler, SdDataReceiver sdDataReceiver) {
        super(context, handler, sdDataReceiver);
        // Identify source correctly for forensic logs
        mName = mIsWear ? "Watch-Internal" : "Phone-Internal";
        mSdData.mSampleFreq = 25;
    }

    @Override
    public void start() {
        Log.i(TAG, "Commencing Internal Sensor Flow: " + mName);
        mSensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
        if (mSensorManager != null) {
            mAccelSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (mAccelSensor != null) {
                // Game delay provides ~50Hz, which we downsample to 25Hz forensically
                mSensorManager.registerListener(this, mAccelSensor, SensorManager.SENSOR_DELAY_GAME);
                mSdData.haveSettings = true;
            }
        }
        super.start();
    }

    @Override
    public void stop() {
        if (mSensorManager != null) {
            mSensorManager.unregisterListener(this);
        }
        super.stop();
    }

    /**
     * onSensorChanged - Critical Path.
     * Rule: Strictly NO allocations here. 
     */
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            long ts = event.timestamp;
            if (ts - mLastSampleTs >= TARGET_PERIOD_NS) {
                mLastSampleTs = ts;
                
                // Downsample & Map to pre-allocated SdData buffers
                if (mSampleCount < 125) {
                    float x = event.values[0];
                    float y = event.values[1];
                    float z = event.values[2];
                    
                    // 3D Mapping (Unit Regtien Forensic Layout)
                    mSdData.rawData3D[3 * mSampleCount] = x * MS2_TO_MG;
                    mSdData.rawData3D[3 * mSampleCount + 1] = y * MS2_TO_MG;
                    mSdData.rawData3D[3 * mSampleCount + 2] = z * MS2_TO_MG;
                    
                    // Magnitude calculation (Recycled logic)
                    mSdData.rawData[mSampleCount] = Math.sqrt(x*x + y*y + z*z) * MS2_TO_MG;
                    
                    mSampleCount++;
                }
                
                // Buffer complete: Trigger Analysis
                if (mSampleCount >= 125) {
                    mSdData.mNsamp = 125;
                    doAnalysis();
                    mSampleCount = 0;
                }
            }
        }
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    @Override public void startPebbleApp() {}
    @Override protected void faultCheck() {}
    @Override public void updatePrefs() { super.updatePrefs(); }
}
