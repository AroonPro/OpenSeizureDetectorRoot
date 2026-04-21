package uk.org.openseizuredetector.openseizuredetector;

import android.util.Log;

/**
 * CircBuf - Graham's Rolling Buffer (Unit Regtien Optimized).
 * Eliminates array allocation in getVals() and optimizes for zero-churn math.
 */
public class CircBuf {
    private static final String TAG = "CircBuf";

    private final double[] mBuff;
    private final double mErrVal;
    private final int mBuffLen;
    private int mHead;
    private int mTail;
    private boolean mIsFull;

    public CircBuf(int n, double errVal) {
        this.mBuff = new double[n];
        this.mBuffLen = n;
        this.mErrVal = errVal;
        this.mHead = 0;
        this.mTail = 0;
        this.mIsFull = false;
    }

    public void add(double val) {
        if (mIsFull) mHead = (mHead + 1) % mBuffLen;
        mBuff[mTail] = val;
        mTail = (mTail + 1) % mBuffLen;
        if (mTail == mHead) mIsFull = true;
    }

    public int getNumVals() {
        return mIsFull ? mBuffLen : mTail;
    }

    /**
     * Unit Regtien Fix: Copy data to an existing target array to prevent object churn.
     */
    public void copyTo(double[] target) {
        int numElements = getNumVals();
        int count = Math.min(numElements, target.length);
        for (int i = 0; i < count; i++) {
            target[i] = mBuff[(mHead + i) % mBuffLen];
        }
    }

    public double getAverageVal() {
        double hrSum = 0.;
        int hrCount = 0;
        int numElements = getNumVals();
        
        for (int i = 0; i < numElements; i++) {
            double val = mBuff[(mHead + i) % mBuffLen];
            if (val != mErrVal) {
                hrSum += val;
                hrCount++;
            }
        }
        return (hrCount > 0) ? (hrSum / hrCount) : -1.0;
    }
}
