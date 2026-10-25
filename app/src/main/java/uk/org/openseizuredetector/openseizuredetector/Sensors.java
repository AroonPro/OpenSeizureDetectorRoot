package uk.org.openseizuredetector.openseizuredetector;

import android.content.Context;
import android.os.Handler;
import android.util.Log;
import uk.org.openseizuredetector.openseizuredetector.data.SdData;

/**
 * Sensors - Hardware Management Hub.
 * GC Policy: Zero-allocation sensor management.
 * Compliance: Unit Regtien Forensic Standard / Graham Jones RC2.
 */
public class Sensors {
    private static final String TAG = "Sensors";
    private final Context mContext;
    private final SdData mSdData;
    
    // GC-Optimized: Pre-allocated sensor instances
    private AccelerationSensor mAccel;
    private HeartRateSensor mHr;
    private SamsungWearSpO2Sensor mSpO2;
    private OffBodyDetectSensor mOffBody;

    public Sensors(Context context, SdData sharedData) {
        this.mContext = context.getApplicationContext();
        this.mSdData = sharedData;
        initHardware();
    }

    private void initHardware() {
        Log.i(TAG, "Initialising Forensic Sensor Stack...");
        
        // Setup sensors with fixed sampling rates (25Hz for Accel)
        mAccel = new AccelerationSensor(mContext, 40000, 0); // 25Hz
        mHr = new HeartRateSensor(mContext, 1000000, 0);   // 1Hz
        mSpO2 = new SamsungWearSpO2Sensor(mContext, 1000000, 0);
        mOffBody = new OffBodyDetectSensor(mContext, 5000000, 0);
    }

    public void startAll() {
        Log.i(TAG, "Starting hardware data acquisition.");
        if (mAccel != null) mAccel.startListening();
        if (mHr != null) mHr.startListening();
        if (mSpO2 != null) mSpO2.startListening();
        if (mOffBody != null) mOffBody.startListening();
    }

    public void stopAll() {
        Log.i(TAG, "Terminating hardware data acquisition.");
        if (mAccel != null) mAccel.stopListening();
        if (mHr != null) mHr.stopListening();
        if (mSpO2 != null) mSpO2.stopListening();
        if (mOffBody != null) mOffBody.stopListening();
    }

    /**
     * Use explicit close for forensic memory cleanup.
     */
    public void close() {
        stopAll();
        if (mAccel != null) mAccel.close();
        if (mHr != null) mHr.close();
        if (mSpO2 != null) mSpO2.close();
        if (mOffBody != null) mOffBody.close();
    }
}
