package uk.org.openseizuredetector.openseizuredetector.alg;

import android.content.Context;
import uk.org.openseizuredetector.openseizuredetector.data.SdData;
import uk.org.openseizuredetector.openseizuredetector.data.AlarmState;
import uk.org.openseizuredetector.openseizuredetector.data.logging.Log;

/**
 * SdAlgHr - Heart Rate Analysis Algorithm.
 * GC-Optimized: Zero-allocation policy in the high-frequency path.
 * Compliance: Unit Regtien Forensic Standard / Graham Jones RC2.
 */
public class SdAlgHr extends SdAlgBase {
    private static final String TAG = "SdAlgHr";

    private double mHrThreshMin = 40.0;
    private double mHrThreshMax = 150.0;
    private boolean mHrAlarmActive = false;

    public SdAlgHr(Context context) {
        super(context);
        updatePrefs();
    }

    public void updatePrefs() {
        try {
            mHrAlarmActive = mSP.getBoolean("HRAlarmActive", false);
            mHrThreshMin = Double.parseDouble(mSP.getString("HRThreshMin", "40.0"));
            mHrThreshMax = Double.parseDouble(mSP.getString("HRThreshMax", "150.0"));
        } catch (Exception e) {
            Log.e(TAG, "HR Prefs Error");
        }
    }

    @Override
    public int processSdData(SdData sdData) {
        if (!mHrAlarmActive || sdData.mHR <= 0) {
            sdData.hrAlgState = AlarmState.OK;
            return AlarmState.OK;
        }

        // Forensic check for heart rate limits
        if (sdData.mHR < mHrThreshMin || sdData.mHR > mHrThreshMax) {
            sdData.hrAlgState = AlarmState.ALARM;
            sdData.mHRAlarmStanding = true;
            return AlarmState.ALARM;
        }

        sdData.hrAlgState = AlarmState.OK;
        sdData.mHRAlarmStanding = false;
        return AlarmState.OK;
    }

    @Override public String getAlarmCause() { return "HR"; }
}
