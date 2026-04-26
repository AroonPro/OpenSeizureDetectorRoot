package uk.org.openseizuredetector.openseizuredetector;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import androidx.preference.PreferenceManager;

import java.io.File;
import android.os.SystemClock;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import org.jtransforms.fft.DoubleFFT_1D;

/**
 * SdDataSource - en_GB
 * Forensic analysis engine - Unit Regtien Standard.
 * Protocol #osd_260426: Stability-driven Recovery (10-minute continuous baseline required).
 */
public abstract class SdDataSource extends Service {
    protected final String TAG = this.getClass().getSimpleName();
    protected final Context mContext;
    protected final Handler mHandler;
    protected static SdDataReceiver mSdDataReceiver;
    protected final OsdUtil mUtil;
    protected final EncryptedSettingsManager mSettingsManager;
    public SdData mSdData;
    public String mName = "BaseSource";

    protected short mAlarmThresh = 15;
    protected short mAlarmRatioThresh = 4;
    protected int mAlarmCountThreshold = 10;
    protected int mAlarmCount = 0;
    protected int mHrAlarmCount = 0;
    protected int mO2AlarmCount = 0;
    
    protected int mHrAlarmThreshMax = 120;
    
    // Stability logic constants
    private static final long STABILITY_REQUIRED_MS = 10 * 60 * 1000; 
    protected boolean mInRecoveryMode = false;
    protected long mStableStartTimestamp = 0;

    protected final int HR_ALARM_COUNT_MAX = 15; 
    protected final int O2_ALARM_COUNT_MAX = 5; 

    private static final int ACCEL_SCALE_FACTOR = 1000;
    protected boolean mIsRunning = false;
    private Timer mStatusTimer;

    protected final CircBuf mAccelBuffer = new CircBuf(250, -1.0);
    private DoubleFFT_1D mFft;
    private double[] mFftBuffer;
    private int mLastN = -1;

    public class SdBinder extends Binder {
        public SdDataSource getService() { return SdDataSource.this; }
    }

    public SdDataSource(Context context, Handler handler, SdDataReceiver sdDataReceiver) {
        this.mContext = context;
        this.mHandler = handler;
        mSdDataReceiver = sdDataReceiver;
        this.mUtil = new OsdUtil(context, mHandler);
        this.mSettingsManager = new EncryptedSettingsManager(context);
        this.mSdData = (sdDataReceiver instanceof SdServer) ? ((SdServer) sdDataReceiver).mSdData : new SdData();
    }

    public void start() {
        updatePrefs();
        mIsRunning = true;
        if (mStatusTimer == null) {
            mStatusTimer = new Timer();
            mStatusTimer.schedule(new TimerTask() {
                @Override public void run() { getStatus(); }
            }, 0, 5000);
        }
    }

    public void stop() {
        mIsRunning = false;
        if (mStatusTimer != null) { mStatusTimer.cancel(); mStatusTimer = null; }
    }

    public void updatePrefs() {
        if (mUtil != null) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
            boolean isFitness = prefs.getBoolean("fitness_profile", false);
            boolean isWalking = prefs.getBoolean("walking_profile", false);
            boolean isSleep = prefs.getBoolean("sleep_mode", false);
            
            // Baseline Defaults (The original high-sensitivity values)
            mAlarmThresh = 15;
            mAlarmRatioThresh = 4;
            mHrAlarmThreshMax = 120;

            if (isSleep) {
                mAlarmThresh = 12; mAlarmRatioThresh = 3; mHrAlarmThreshMax = 100;
            } else if (isFitness) {
                mAlarmThresh = 150; mAlarmRatioThresh = 20;
                mHrAlarmThreshMax = mSettingsManager.getMaxHrFormula() - 10; 
            } else if (isWalking) {
                mAlarmThresh = 80; mAlarmRatioThresh = 12; mHrAlarmThreshMax = 145; 
            }

            // Apply Recovery Dampening if not stable yet
            if (mInRecoveryMode) {
                mAlarmThresh *= 1.5; 
                mAlarmRatioThresh *= 1.25; 
            }

            if (mSdData != null) mSdData.mO2SatThreshMin = (double) prefs.getInt("o2_threshold", 85);
        }
    }

    public void triggerUiUpdate() {
        if (mSdDataReceiver != null) mSdDataReceiver.onSdDataReceived(mSdData);
    }

    protected void hrCheck() {
        if (mSdData == null || mSdData.mHr <= 0) { mHrAlarmCount = 0; return; }

        // In recovery mode, we use a higher tolerance (mHrAlarmThreshMax + 20) or just suppress if within wandel-range
        int effectiveMax = mInRecoveryMode ? Math.max(mHrAlarmThreshMax, 150) : mHrAlarmThreshMax;

        if (mSdData.mHr >= effectiveMax) {
            if (++mHrAlarmCount >= HR_ALARM_COUNT_MAX) {
                mSdData.alarmState = 1;
                mSdData.alarmPhrase = "HIGH HR";
            }
        } else if (mHrAlarmCount > 0) mHrAlarmCount--;
    }

    protected void doAnalysis() {
        if (!mSdData.mWatchOnBody && !mSdData.mIsCharging) return;
        int n = mSdData.mNsampDefault;
        if (mSdData == null || mAccelBuffer.getNumVals() < n) return;
        try {
            updatePrefs(); 
            if (n != mLastN) { mFft = new DoubleFFT_1D(n); mFftBuffer = new double[n * 2]; mLastN = n; }
            mAccelBuffer.copyTo(mFftBuffer);
            for(int i=n; i < mFftBuffer.length; i++) mFftBuffer[i] = 0;
            mFft.realForward(mFftBuffer);
            double specPower = 0, roiPower = 0;
            final double freqRes = (double)mSdData.mSampleFreq / (double)n;
            
            for (int i = 1; i < n / 2; i++) {
                double power = (mFftBuffer[2 * i] * mFftBuffer[2 * i] + mFftBuffer[2 * i + 1] * mFftBuffer[2 * i + 1]);
                specPower += power;
                double freq = i * freqRes;
                if (freq >= 3.0 && freq <= 8.0) roiPower += power;
            }
            double normN = n / 2.0;
            mSdData.specPower = (long) ((specPower / normN) / ACCEL_SCALE_FACTOR);
            mSdData.roiPower = (long) ((roiPower / normN) / ACCEL_SCALE_FACTOR);
            mSdData.roiRatio = (specPower > 0) ? (long) ((roiPower / specPower) * 100.0) : 0;
            mSdData.haveData = true;

            monitorStability(); // Check if we can exit recovery mode
            checkAlarm();
        } catch (Exception e) { Log.e(TAG, "FFT Fault: " + e.getMessage()); }
    }

    private void monitorStability() {
        if (!mInRecoveryMode) return;

        // Check if current metrics are below the ORIGINAL (non-dampened) thresholds
        // We use a temporary non-dampened check here
        boolean isCurrentStateStable = (mSdData.roiPower < (mAlarmThresh / 1.5)) && 
                                     (mSdData.mHr < mHrAlarmThreshMax);

        if (isCurrentStateStable) {
            if (mStableStartTimestamp == 0) {
                mStableStartTimestamp = SystemClock.elapsedRealtime();
            } else if (SystemClock.elapsedRealtime() - mStableStartTimestamp >= STABILITY_REQUIRED_MS) {
                mInRecoveryMode = false;
                mStableStartTimestamp = 0;
                Log.i(TAG, "OSD: 10min Stability reached. Normal sensitivity restored.");
            }
        } else {
            // Not stable! Reset the 10min counter
            if (mStableStartTimestamp != 0) {
                Log.d(TAG, "OSD: Stability broken. Resetting 10min timer.");
                mStableStartTimestamp = 0;
            }
        }
    }

    protected void checkAlarm() {
        if (mSdData == null) return;
        if (!mSdData.mWatchOnBody && !mSdData.mIsCharging) { mSdData.alarmState = 0; return; }
        hrCheck();
        if (mSdData.roiPower >= mAlarmThresh && mSdData.roiRatio >= mAlarmRatioThresh) {
            if (++mAlarmCount >= mAlarmCountThreshold) {
                mSdData.alarmState = 2; mSdData.alarmPhrase = "SEIZURE DETECTED";
            } else {
                mSdData.alarmState = Math.max((int)mSdData.alarmState, 1); mSdData.alarmPhrase = "TREMOR DETECTED";
            }
        } else if (mAlarmCount > 0) mAlarmCount--;
        
        if (mAlarmCount == 0 && mSdData.alarmState != 0 && mHrAlarmCount == 0) {
            mSdData.alarmState = 0; mSdData.alarmPhrase = "STATUS: OK";
        }
    }

    public void ClearAlarmCount() {
        mAlarmCount = 0; mHrAlarmCount = 0; mO2AlarmCount = 0;
        mInRecoveryMode = true; // Enter stability-monitored recovery
        mStableStartTimestamp = 0;
        if (mSdData != null) { mSdData.alarmState = 0; mSdData.alarmPhrase = "STATUS: OK"; }
        triggerUiUpdate();
    }
    
    protected abstract void getStatus();
    public abstract void startPebbleApp();
    @Override public IBinder onBind(Intent intent) { return null; }
}
