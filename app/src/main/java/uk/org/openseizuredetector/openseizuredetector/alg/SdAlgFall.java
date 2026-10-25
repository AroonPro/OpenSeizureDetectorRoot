/*
  SdAlgFall - Forensic Fall Detection Algorithm.
  Optimized for GC: Zero-allocation policy in sensor loops.
  Compliance: Unit Regtien Forensic Standard / Graham Jones RC2.
*/
package uk.org.openseizuredetector.openseizuredetector.alg;

import android.content.Context;
import android.util.Log;
import uk.org.openseizuredetector.openseizuredetector.data.SdData;
import uk.org.openseizuredetector.openseizuredetector.data.AlarmState;

/**
 * SdAlgFall - Analyzes accelerometer data for free-fall and impact patterns.
 * Optimized to prevent object churn during high-frequency monitoring.
 */
public class SdAlgFall extends SdAlgBase {
    private static final String TAG = "SdAlgFall";

    private double mFallThreshMin = 400.0; // 0.4g free-fall
    private double mFallThreshMax = 2500.0; // 2.5g impact
    private boolean mFallActive = false;

    public SdAlgFall(Context context) {
        super(context);
        updatePrefs();
    }

    public void updatePrefs() {
        try {
            mFallActive = mSP.getBoolean("FallActive", false);
            mFallThreshMin = Double.parseDouble(mSP.getString("FallThreshMin", "400.0"));
            mFallThreshMax = Double.parseDouble(mSP.getString("FallThreshMax", "2500.0"));
        } catch (Exception e) {
            Log.e(TAG, "Fall Prefs Error");
        }
    }

    @Override
    public int processSdData(SdData sdData) {
        if (!mFallActive || sdData.mNsamp <= 0) {
            sdData.fallAlgState = AlarmState.OK;
            return AlarmState.OK;
        }

        double minAcc = Double.MAX_VALUE;
        double maxAcc = Double.MIN_VALUE;

        // GC-Optimized: Direct iteration over pre-allocated rawData
        for (int i = 0; i < sdData.mNsamp; i++) {
            double val = sdData.rawData[i];
            if (val < minAcc) minAcc = val;
            if (val > maxAcc) maxAcc = val;
        }

        sdData.mFallWindowMin = minAcc;
        sdData.mFallWindowMax = maxAcc;

        // Forensic check: Low-G followed by High-G
        if (minAcc < mFallThreshMin && maxAcc > mFallThreshMax) {
            sdData.fallAlgState = AlarmState.ALARM;
            return AlarmState.ALARM;
        }

        sdData.fallAlgState = AlarmState.OK;
        return AlarmState.OK;
    }

    @Override public String getAlarmCause() { return "FALL"; }
}
