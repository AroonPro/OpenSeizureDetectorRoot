package uk.org.openseizuredetector.openseizuredetector;

import java.util.ArrayList;
import java.util.List;

/**
 * SdAlgHr - Unit Regtien Optimized.
 * High-precision HR analysis with zero object churn in the hot path.
 * Eliminates primitive boxing and optimizes for low-memory environments.
 */
public class SdAlgHr {
    private static final String TAG = "SdAlgHr";
    private static final int BUFFER_SIZE = 60; // 1 minute at 1Hz

    // Hot path primitives - No boxing
    private final float[] mHrBuffer = new float[BUFFER_SIZE];
    private int mBufIndex = 0;
    private int mCurrentCount = 0;
    
    private double mCurrentAvg = 0;
    private double mCurrentHrv = 0;

    /**
     * Process new HR sample with zero allocation.
     */
    public void addSample(float hrVal) {
        if (hrVal <= 0) return;

        mHrBuffer[mBufIndex] = hrVal;
        mBufIndex = (mBufIndex + 1) % BUFFER_SIZE;
        if (mCurrentCount < BUFFER_SIZE) mCurrentCount++;

        calculateStats();
    }

    private void calculateStats() {
        if (mCurrentCount == 0) return;

        double sum = 0;
        double sqSum = 0;
        
        for (int i = 0; i < mCurrentCount; i++) {
            float val = mHrBuffer[i];
            sum += val;
            sqSum += (val * val);
        }

        mCurrentAvg = sum / mCurrentCount;
        
        // Forensische variantie berekening (Zero-churn)
        double variance = (sqSum / mCurrentCount) - (mCurrentAvg * mCurrentAvg);
        mCurrentHrv = (variance > 0) ? Math.sqrt(variance) : 0;
    }

    public double getAverageHr() { return mCurrentAvg; }
    public double getHrv() { return mCurrentHrv; }

    public void reset() {
        mBufIndex = 0;
        mCurrentCount = 0;
        mCurrentAvg = 0;
        mCurrentHrv = 0;
    }
}
