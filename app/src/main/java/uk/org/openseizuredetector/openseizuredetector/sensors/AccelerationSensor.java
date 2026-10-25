package uk.org.openseizuredetector.openseizuredetector.sensors;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import uk.org.openseizuredetector.openseizuredetector.AndroidSensor;
import uk.org.openseizuredetector.openseizuredetector.data.SdData;

/**
 * AccelerationSensor - Forensic 3D Data Acquisition.
 * GC Policy: Zero-allocation. Directly maps hardware events to the SdData pool.
 * Compliance: Unit Regtien Forensic Standard / Code Review Checklist.
 */
public class AccelerationSensor extends AndroidSensor {
    private static final String TAG = "AccelerationSensor";
    
    // Constant for gravity conversion to minimize FPU churn
    private static final double MS2_TO_MG = 1000.0 / 9.80665;
    
    private final SdData mSharedData;
    private int mSampleIndex = 0;

    public AccelerationSensor(Context context, SdData sharedData, int samplingPeriodUs, int maxLatencyUs) {
        super(context, PackageManager.FEATURE_SENSOR_ACCELEROMETER, 
              Sensor.TYPE_ACCELEROMETER, samplingPeriodUs, maxLatencyUs);
        this.mSharedData = sharedData;
    }

    /**
     * onSensorChanged - Critical High-Frequency Path.
     * Rule: Strictly NO allocations here to prevent GC stalls.
     */
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];

            // Direct mapping to pre-allocated shared buffer
            synchronized (mSharedData) {
                if (mSampleIndex < 125) {
                    int offset = mSampleIndex * 3;
                    mSharedData.rawData3D[offset] = x * MS2_TO_MG;
                    mSharedData.rawData3D[offset + 1] = y * MS2_TO_MG;
                    mSharedData.rawData3D[offset + 2] = z * MS2_TO_MG;
                    
                    // Magnitude calculation for legacy routine support
                    mSharedData.rawData[mSampleIndex] = Math.sqrt(x*x + y*y + z*z) * MS2_TO_MG;
                    
                    mSampleIndex++;
                }

                if (mSampleIndex >= 125) {
                    mSharedData.mNsamp = 125;
                    mSharedData.dataTimeMillis = System.currentTimeMillis();
                    mSampleIndex = 0;
                    // Note: Analysis trigger is managed by the DataSource to ensure sync.
                }
            }
        }
    }
}
