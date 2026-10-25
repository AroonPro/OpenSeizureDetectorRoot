package uk.org.openseizuredetector.openseizuredetector.alg;

import android.content.Context;
import uk.org.openseizuredetector.openseizuredetector.data.logging.Log;

import org.jtransforms.fft.DoubleFFT_1D;

import java.util.Arrays;

import uk.org.openseizuredetector.openseizuredetector.data.SdData;
import uk.org.openseizuredetector.openseizuredetector.data.AlarmState;

/**
 * SdAlgFlap - Detects flapping movements using narrow frequency band analysis.
 * GC-Optimized: Pre-allocated FFT buffers to prevent memory pressure in hot paths.
 * Compliance: Unit Regtien Forensic Standard / Code Review Checklist.
 */
public class SdAlgFlap extends SdAlgBase {
    private final static String TAG = "SdAlgFlap";
    private static final int ACCEL_SCALE_FACTOR = 1000;

    private short mFlapThresh;
    private short mFlapRatioThresh;
    private double mFlapFreqMin;
    private double mFlapFreqMax;
    private short mFreqCutoff = 12;
    private short mSampleFreq = 25;

    // GC-Optimized: Pre-allocated analytical components
    private DoubleFFT_1D mFftEngine;
    private double[] mFftBuffer;
    private int mCachedNSamp = -1;

    public SdAlgFlap(Context context) {
        super(context);
        Log.d(TAG, "SdAlgFlap Constructor - Initialising Forensic Analytic Engine");
        updatePrefs();
    }

    private void updatePrefs() {
        try {
            mFlapThresh = (short) readDoublePref("FlapAlarmThresh", "10");
            mFlapRatioThresh = (short) readDoublePref("FlapAlarmRatioThresh", "55");
            mFlapFreqMin = readDoublePref("FlapAlarmFreqMin", "2.5");
            mFlapFreqMax = readDoublePref("FlapAlarmFreqMax", "4.0");
        } catch (Exception ex) {
            Log.e(TAG, "updatePrefs() - Error parsing Flap prefs, using defaults");
            mFlapThresh = 10;
            mFlapRatioThresh = 55;
            mFlapFreqMin = 2.5;
            mFlapFreqMax = 4.0;
        }
    }

    private double getMagnitude(double[] fft, int i) {
        return (fft[2 * i] * fft[2 * i] + fft[2 * i + 1] * fft[2 * i + 1]);
    }

    private int freq2FftBin(double freqHz, double sampleFreq, int nSamp) {
        return (int)(nSamp * freqHz / sampleFreq);
    }

    @Override public String getAlarmCause() { return "Flap"; }

    @Override
    public int processSdData(SdData sdData) {
        if (sdData.mNsamp <= 0) return AlarmState.OK;

        try {
            // RECYCLING: Lazy-init of FFT engine to prevent churn
            if (sdData.mNsamp != mCachedNSamp) {
                mFftEngine = new DoubleFFT_1D(sdData.mNsamp);
                mFftBuffer = new double[sdData.mNsamp * 2];
                mCachedNSamp = sdData.mNsamp;
            }

            mSampleFreq = 25; // TODO: Get from sdData.mSampleFreq if available
            double freqRes = 1.0 * mSampleFreq / sdData.mNsamp;

            int nMin = freq2FftBin(mFlapFreqMin, mSampleFreq, sdData.mNsamp);
            int nMax = freq2FftBin(mFlapFreqMax, mSampleFreq, sdData.mNsamp);
            int nFreqCutoff = freq2FftBin(mFreqCutoff, mSampleFreq, sdData.mNsamp);

            // Forensically clean buffer before copy
            Arrays.fill(mFftBuffer, 0.0);
            System.arraycopy(sdData.rawData, 0, mFftBuffer, 0, sdData.mNsamp);
            
            mFftEngine.realForward(mFftBuffer);

            double specPower = 0;
            for (int i = 1; i < sdData.mNsamp / 2; i++) {
                if (i <= nFreqCutoff) {
                    specPower += getMagnitude(mFftBuffer, i);
                } else {
                    mFftBuffer[2 * i] = 0.;
                    mFftBuffer[2 * i + 1] = 0.;
                }
            }
            specPower = (specPower / sdData.mNsamp / 2) / ACCEL_SCALE_FACTOR;

            double roiPower = 0;
            for (int i = nMin; i < nMax; i++) {
                roiPower += getMagnitude(mFftBuffer, i);
            }
            roiPower = (roiPower / (nMax - nMin)) / ACCEL_SCALE_FACTOR;

            double roiRatio = (specPower > 0) ? (10 * roiPower / specPower) : 0;

            if (roiPower > mFlapThresh && roiRatio > mFlapRatioThresh) {
                Log.i(TAG, "processSdData() - *** Flap pattern detected ***");
                sdData.flapAlgState = AlarmState.ALARM;
                return AlarmState.ALARM;
            }

        } catch (Exception e) {
            Log.e(TAG, "Flap Analysis Error: " + e.getMessage());
        }

        sdData.flapAlgState = AlarmState.OK;
        return AlarmState.OK;
    }

    @Override
    public void close() {
        mFftBuffer = null;
        mFftEngine = null;
        super.close();
    }
}
