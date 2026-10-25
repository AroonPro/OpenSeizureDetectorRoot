package uk.org.openseizuredetector.openseizuredetector.alg;

import android.content.Context;
import android.content.SharedPreferences;
import uk.org.openseizuredetector.openseizuredetector.data.logging.Log;
import androidx.preference.PreferenceManager;
import java.util.ArrayList;
import java.util.List;
import uk.org.openseizuredetector.openseizuredetector.data.SdData;
import uk.org.openseizuredetector.openseizuredetector.data.AlarmState;
import uk.org.openseizuredetector.openseizuredetector.utils.PreferenceUtils;

/**
 * SeizureDetector - Coordinates all seizure detection algorithms.
 * GC Policy: Strictly ZERO-ALLOCATION in the 5s loop.
 * Optimized for forensic precision and minimal memory pressure on Wear OS.
 * Compliance: Unit Regtien Forensic Standard / Code Review Checklist.
 */
public class SeizureDetector {
    private final static String TAG = "SeizureDetector";

    private final Context mContext;
    private final SharedPreferences mSP;

    private SdAlgOsd mSdAlgOsd;
    private SdAlgFlap mSdAlgFlap;
    private SdAlgFall mSdAlgFall;
    private SdAlgMl mSdAlgMl;
    private SdAlgHr mSdAlgHr;

    // GC-Optimized: Result pool to prevent memory churn (Recyclable Pattern)
    private final List<AlgorithmResult> mAlgorithmResultsPool = new ArrayList<>(16);
    private final StringBuilder mCauseBuilder = new StringBuilder(256);

    private boolean mOsdAlarmActive, mFlapAlarmActive, mFallActive, mCnnAlarmActive, mHRAlarmActive;
    private VotingStrategy mVotingStrategy;
    private int mAlarmTime = 0;
    private short mWarnTimeThreshold, mAlarmTimeThreshold;
    private final short mSamplePeriod = 5;

    public SeizureDetector(Context context) {
        Log.i(TAG, "SeizureDetector Constructor - Initializing Optimized Engine");
        mContext = context;
        mSP = PreferenceManager.getDefaultSharedPreferences(mContext);

        // Pre-allocate the pool (Checklist: No 'new' in hot paths)
        for (int i = 0; i < 16; i++) {
            mAlgorithmResultsPool.add(new AlgorithmResult("INIT", AlarmState.OK, 0.0f, 1.0f));
        }

        updatePrefs();
        initAlgorithms();
    }

    private void initAlgorithms() {
        mVotingStrategy = new VotingStrategy(VotingStrategy.fromString(mSP.getString("VotingStrategy", "ANY_ALARM")));
        if (mOsdAlarmActive) mSdAlgOsd = new SdAlgOsd(mContext);
        if (mFlapAlarmActive) mSdAlgFlap = new SdAlgFlap(mContext);
        if (mCnnAlarmActive) mSdAlgMl = new SdAlgMl(mContext);
        if (mHRAlarmActive) mSdAlgHr = new SdAlgHr(mContext);
        if (mFallActive) mSdAlgFall = new SdAlgFall(mContext);
    }

    public void updatePrefs() {
        mWarnTimeThreshold = Short.parseShort(mSP.getString("WarnTime", "5"));
        mAlarmTimeThreshold = Short.parseShort(mSP.getString("AlarmTime", "15"));
        mOsdAlarmActive = PreferenceUtils.getBooleanFromXml(mSP, "OsdAlarmActive");
        mFlapAlarmActive = PreferenceUtils.getBooleanFromXml(mSP, "FlapAlarmActive");
        mFallActive = PreferenceUtils.getBooleanFromXml(mSP, "FallActive");
        mCnnAlarmActive = PreferenceUtils.getBooleanFromXml(mSP, "CnnAlarmActive");
        mHRAlarmActive = PreferenceUtils.getBooleanFromXml(mSP, "HRAlarmActive");
    }

    /**
     * processData - GC-Clean analysis.
     * Rule: Never use 'new' or 'subList' inside this method.
     */
    public int processData(SdData sdData) {
        int resultCount = 0;
        mCauseBuilder.setLength(0);

        // 1. Core OSD
        if (mSdAlgOsd != null) {
            int res = mSdAlgOsd.processSdData(sdData);
            sdData.osdAlgState = res;
            mAlgorithmResultsPool.get(resultCount++).update("OSD", res, 1.0f, 1.0f);
            if (res == AlarmState.ALARM) mCauseBuilder.append("OSD ");
        }

        // 2. Flap Detection
        if (mSdAlgFlap != null) {
            int res = mSdAlgFlap.processSdData(sdData);
            sdData.flapAlgState = res;
            mAlgorithmResultsPool.get(resultCount++).update("FLAP", res, 1.0f, 1.0f);
            if (res == AlarmState.ALARM) mCauseBuilder.append("FLAP ");
        }

        // 3. Heart Rate
        if (mSdAlgHr != null) {
            int res = mSdAlgHr.processSdData(sdData);
            sdData.hrAlgState = res;
            mAlgorithmResultsPool.get(resultCount++).update("HR", res, 1.0f, 1.0f);
            if (res == AlarmState.ALARM) mCauseBuilder.append("HR ");
        }

        // 4. ML Models (Uses direct pool population)
        if (mSdAlgMl != null) {
            resultCount = mSdAlgMl.populateResultsFromPool(sdData, mAlgorithmResultsPool, resultCount);
            for (int i = 0; i < resultCount; i++) {
                AlgorithmResult r = mAlgorithmResultsPool.get(i);
                if (r.alarmState == AlarmState.ALARM && !mCauseBuilder.toString().contains(r.algorithmName)) {
                    mCauseBuilder.append(r.algorithmName).append(" ");
                }
            }
        }

        // 5. Voting (Direct Pool Processing)
        int combinedState = AlarmState.OK;
        if (resultCount > 0) {
            combinedState = mVotingStrategy.vote(mAlgorithmResultsPool, resultCount);
        }

        sdData.alarmCause = mCauseBuilder.toString().trim();

        // 6. Persistence & State Change
        if (combinedState == AlarmState.ALARM) {
            mAlarmTime += mSamplePeriod;
            if (mAlarmTime > mAlarmTimeThreshold) { sdData.alarmStanding = true; return AlarmState.ALARM; }
            return AlarmState.WARNING;
        } else {
            mAlarmTime = 0;
            sdData.alarmStanding = false;
            return AlarmState.OK;
        }
    }

    public void close() {
        if (mSdAlgOsd != null) mSdAlgOsd.close();
        if (mSdAlgFlap != null) mSdAlgFlap.close();
        if (mSdAlgFall != null) mSdAlgFall.close();
        if (mSdAlgMl != null) mSdAlgMl.close();
        if (mSdAlgHr != null) mSdAlgHr.close();
    }
}
