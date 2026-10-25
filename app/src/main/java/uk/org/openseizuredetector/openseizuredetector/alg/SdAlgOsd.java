package uk.org.openseizuredetector.openseizuredetector.alg;

import android.content.Context;
import uk.org.openseizuredetector.openseizuredetector.data.logging.Log;

import org.jtransforms.fft.DoubleFFT_1D;

import java.util.Arrays;

import uk.org.openseizuredetector.openseizuredetector.data.SdData;

/**
 * OpenSeizureDetector (OSD) algorithm - frequency-based seizure detection.
 * Optimized for GC: Reuses FFT instances and buffers to eliminate heap churn in hot paths.
 * Compliance: Unit Regtien Forensic Standard / Code Review Checklist.
 */
public class SdAlgOsd extends SdAlgBase {
    private final static String TAG = "SdAlgOsd";
    private static final int ACCEL_SCALE_FACTOR = 1000;
    private static final int SIMPLE_SPEC_FMAX = 10;

    private short mAlarmThresh;
    private short mAlarmRatioThresh;
    private short mAlarmFreqMin;
    private short mAlarmFreqMax;
    private short mFreqCutoff = 12;
    private short mSampleFreq = 25;

    // GC-Optimized: Pre-allocated members
    private DoubleFFT_1D mFftEngine;
    private double[] mFftBuffer;
    private int mCachedNSamp = -1;

    public SdAlgOsd(Context context) {
        super(context);
        Log.d(TAG, "SdAlgOsd Constructor - Initializing Forensic Engine");
        updatePrefs();
    }

    private void updatePrefs() {
        try {
            mAlarmThresh = Short.parseShort(mSP.getString("AlarmThresh", "500"));
            mAlarmRatioThresh = Short.parseShort(mSP.getString("AlarmRatioThresh", "57"));
            mAlarmFreqMin = Short.parseShort(mSP.getString("AlarmFreqMin", "3"));
            mAlarmFreqMax = Short.parseShort(mSP.getString("AlarmFreqMax", "8"));
        } catch (Exception ex) {
            mAlarmThresh = 500; mAlarmRatioThresh = 57; mAlarmFreqMin = 3; mAlarmFreqMax = 8;
        }
    }

    private double getMagnitude(double[] fft, int i) {
        // Correct magnitude calculation for realForward FFT
        return (fft[2 * i] * fft[2 * i] + fft[2 * i + 1] * fft[2 * i + 1]);
    }

    private int freq2FftBin(double freqHz, double sampleFreq, int nSamp) {
        return (int)(nSamp * freqHz / sampleFreq);
    }

    @Override public String getAlarmCause() { return "OsdAlg"; }

    @Override
    public int processSdData(SdData sdData) {
        if (sdData.mNsamp <= 0) return 0;

        try {
            // RECYCLING: Only re-allocate FFT engine if sample size changed
            if (sdData.mNsamp != mCachedNSamp) {
                Log.i(TAG, "Re-allocating FFT engine for nSamp: " + sdData.mNsamp);
                mFftEngine = new DoubleFFT_1D(sdData.mNsamp);
                mFftBuffer = new double[sdData.mNsamp * 2];
                mCachedNSamp = sdData.mNsamp;
            }

            mSampleFreq = 25;
            double freqRes = 1.0 * mSampleFreq / sdData.mNsamp;

            int nMin = freq2FftBin(mAlarmFreqMin, mSampleFreq, sdData.mNsamp);
            int nMax = freq2FftBin(mAlarmFreqMax, mSampleFreq, sdData.mNsamp);
            int nFreqCutoff = freq2FftBin(mFreqCutoff, mSampleFreq, sdData.mNsamp);

            // Clean buffer and copy data without 'new'
            Arrays.fill(mFftBuffer, 0.0);
            System.arraycopy(sdData.rawData, 0, mFftBuffer, 0, sdData.mNsamp);
            
            mFftEngine.realForward(mFftBuffer);

            double specPower = 0;
            double maxMag = 0;
            int maxBin = 0;

            for (int i = 1; i < sdData.mNsamp / 2; i++) {
                double mag = getMagnitude(mFftBuffer, i);
                if (i <= nFreqCutoff) {
                    specPower += mag;
                    if (mag > maxMag) { maxMag = mag; maxBin = i; }
                } else {
                    mFftBuffer[2 * i] = 0.; mFftBuffer[2 * i + 1] = 0.;
                }
            }
            specPower = specPower / sdData.mNsamp / 2;

            double roiPower = 0;
            for (int i = nMin; i < nMax; i++) {
                roiPower += getMagnitude(mFftBuffer, i);
            }
            roiPower = roiPower / (nMax - nMin);
            
            // Simplified spectrum (1Hz bins) - No allocations in loop
            for (int ifreq = 0; ifreq < SIMPLE_SPEC_FMAX; ifreq++) {
                int binMin = (int) (1 + ifreq / freqRes);
                int binMax = (int) (1 + (ifreq + 1) / freqRes);
                double binPower = 0;
                if (binMax > binMin) {
                    for (int i = binMin; i < binMax; i++) binPower += getMagnitude(mFftBuffer, i);
                    binPower /= (binMax - binMin);
                }
                sdData.simpleSpec[ifreq] = (int) (binPower / ACCEL_SCALE_FACTOR);
            }

            sdData.specPower = (long) (specPower / ACCEL_SCALE_FACTOR);
            sdData.roiPower = (long) (roiPower / ACCEL_SCALE_FACTOR);
            sdData.maxVal = (long) (maxMag / ACCEL_SCALE_FACTOR);
            sdData.maxFreq = (long) (maxBin * freqRes);
            
            // Metadata sync
            sdData.alarmThresh = mAlarmThresh;
            sdData.alarmRatioThresh = mAlarmRatioThresh;

            if (sdData.specPower == 0) sdData.specPower = 1;

            boolean inAlarm = (sdData.roiPower > mAlarmThresh) && 
                              ((10 * sdData.roiPower / sdData.specPower) > mAlarmRatioThresh);

            return inAlarm ? 2 : 0;
        } catch (Exception e) {
            Log.e(TAG, "Forensic Analysis Fail: " + e.getMessage());
            return 0;
        }
    }

    @Override
    public void close() {
        mFftBuffer = null;
        mFftEngine = null;
        super.close();
    }
}
