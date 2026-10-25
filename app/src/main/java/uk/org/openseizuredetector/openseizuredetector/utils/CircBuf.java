package uk.org.openseizuredetector.openseizuredetector.utils;

import uk.org.openseizuredetector.openseizuredetector.data.logging.Log;

/**
 * CircBuf - Optimized Circular Buffer.
 * GC Policy: Zero-allocation data retrieval via target buffer copying.
 */
public class CircBuf {
    private final static String TAG = "CircBuf";

    private final double[] mBuff;
    private final double mErrVal;
    private final int mBuffLen;
    private int mHead;
    private int mTail;
    private boolean mIsFull;

    public CircBuf(int n, double errVal) {
        mBuff = new double[n];
        mBuffLen = n;
        mErrVal = errVal;
        mHead = 0;
        mTail = 0;
        mIsFull = false;
    }

    public void add(double val) {
        if (mIsFull)
            mHead = (mHead + 1) % mBuffLen;

        mBuff[mTail] = val;
        mTail = (mTail + 1) % mBuffLen;
        if (mTail == mHead)
            mIsFull = true;
    }

    public int getNumVals() {
        if (mIsFull) return mBuffLen;
        return (mTail >= mHead) ? (mTail - mHead) : (mTail + mBuffLen - mHead);
    }

    /**
     * GC-Optimized: Copies values into a target array to avoid allocation.
     * @param target The pre-allocated array to fill.
     * @return Number of values copied.
     */
    public int copyTo(double[] target) {
        int numElements = getNumVals();
        int limit = Math.min(numElements, target.length);
        for (int i = 0; i < limit; i++) {
            target[i] = mBuff[(mHead + i) % mBuffLen];
        }
        return limit;
    }

    public double[] getVals() {
        double[] retArr = new double[getNumVals()];
        copyTo(retArr);
        return retArr;
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

    public int getBufferLength() { return mBuffLen; }
    public double getErrorValue() { return mErrVal; }
}
