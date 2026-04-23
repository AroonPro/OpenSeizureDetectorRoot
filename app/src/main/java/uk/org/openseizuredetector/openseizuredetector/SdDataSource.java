package uk.org.openseizuredetector.openseizuredetector;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import java.util.Timer;
import java.util.TimerTask;

import org.jtransforms.fft.DoubleFFT_1D;

/**
 * SdDataSource - Unit Regtien Optimized.
 * Core analysis engine using Graham's rolling buffer to prevent index-overflows.
 * Logic: Fixed-window FFT analysis (zero-allocation).
 */
public abstract class SdDataSource extends Service {
    protected final String TAG = this.getClass().getSimpleName();
    protected final Context mContext;
    protected final Handler mHandler;
    protected static SdDataReceiver mSdDataReceiver;
    protected final OsdUtil mUtil;
    public SdData mSdData;
    public String mName = "BaseSource";

    protected short mAlarmThresh = 15;
    protected short mAlarmRatioThresh = 4;
    protected int mAlarmCountThreshold = 10;
    protected int mAlarmCount = 0;
    protected int mHrAlarmCount = 0;
    protected int mO2AlarmCount = 0;
    
    protected int mHrAlarmThreshMax = 120;
    protected final int HR_ALARM_COUNT_MAX = 10; 
    protected final int O2_ALARM_COUNT_MAX = 5; 

    private static final int ACCEL_SCALE_FACTOR = 1000;
    protected boolean mIsRunning = false;
    private Timer mStatusTimer;

    // Architecture First: Rolling buffer for seismic data
    protected final CircBuf mAccelBuffer = new CircBuf(250, -1.0);

    // Reusable FFT buffers to eliminate GC pressure
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
            mAlarmThresh = (short) mUtil.getPrefs().getInt("alarm_threshold", 15);
            mAlarmRatioThresh = (short) mUtil.getPrefs().getInt("alarm_ratio_threshold", 4);
            mHrAlarmThreshMax = mUtil.getPrefs().getInt("hr_alarm_threshold", 120);
            if (mSdData != null) mSdData.mO2SatThreshMin = (double) mUtil.getPrefs().getInt("o2_threshold", 90);
        }
    }

    public void updateFromJSON(String jsonStr) {
        if (mSdData != null) {
            mSdData.fromJSON(jsonStr);
        }
    }

    protected void triggerUiUpdate() {
        if (mSdDataReceiver != null) mSdDataReceiver.onSdDataReceived(mSdData);
    }

    public void muteCheck() {}
    protected void faultCheck() {}
    public void handleSendingHelp() {}
    public void fallCheck() {}

    public void o2SatCheck() {
        // OFF-BODY CHECK: Geen SpO2 analyse als watch niet om de pols zit
        if (!mSdData.mWatchOnBody && !mSdData.mIsCharging) {
            mO2AlarmCount = 0;
            return;
        }

        if (mSdData == null || mSdData.mO2Sat <= 0) { mO2AlarmCount = 0; return; }
        if (mSdData.mO2Sat < mSdData.mO2SatThreshMin) {
            if (++mO2AlarmCount >= O2_ALARM_COUNT_MAX) {
                mSdData.alarmState = 2;
                mSdData.alarmPhrase = "O2 CRITICAL";
            }
        } else if (mO2AlarmCount > 0) mO2AlarmCount--;
    }

    protected void hrCheck() {
        if (mSdData == null || mSdData.mHr <= 0) { mHrAlarmCount = 0; return; }
        if (mSdData.mHr >= mHrAlarmThreshMax) {
            if (++mHrAlarmCount >= HR_ALARM_COUNT_MAX) {
                mSdData.alarmState = 1;
                mSdData.alarmPhrase = "WARNING: High HR";
            }
        } else if (mHrAlarmCount > 0) mHrAlarmCount--;
    }

    protected void checkAlarm() {
        if (mSdData == null) return;
        
        // OFF-BODY CHECK: Geen alarm als watch niet om de pols zit, niet oplaadt en niet in slaap-profiel zit
        if (!mSdData.mWatchOnBody && !mSdData.mIsCharging && !mSdData.mIsSleeping) {
             mSdData.mLocalAlarmSuppressed = true; // Markeer dat alarm lokaal onderdrukt is
             if (mSdData.alarmState != 0) {
                 mSdData.alarmState = 0;
                 mSdData.alarmPhrase = "OFF-BODY: Muted";
             }
             
             // NACHTELIJK OFF-BODY (Noodgeval): Alarm voor ontvanger als batterij temp hoog is
             if (mSdData.batteryTemp > mSdData.ambientTemp + 5.0f) {
                 mSdData.alarmState = 2;
                 mSdData.alarmPhrase = "NIGHT CRITICAL: Heat Detect";
                 mSdData.mLocalAlarmSuppressed = false; // Forceer alarm naar ontvanger
             }
             return;
        } else {
             mSdData.mLocalAlarmSuppressed = false;
        }

        hrCheck();
        o2SatCheck();
        
        if (mSdData.roiPower >= mAlarmThresh && mSdData.roiRatio >= mAlarmRatioThresh) {
            if (++mAlarmCount >= mAlarmCountThreshold) {
                mSdData.alarmState = 2;
                mSdData.alarmPhrase = "SEIZURE DETECTED";
            } else {
                mSdData.alarmState = Math.max(mSdData.alarmState, 1);
                mSdData.alarmPhrase = "WARNING: Tremor";
            }
        } else if (mAlarmCount > 0) mAlarmCount--;

        if (mAlarmCount == 0 && mHrAlarmCount == 0 && mO2AlarmCount == 0 && mSdData.alarmState != 0) {
            mSdData.alarmState = 0;
            mSdData.alarmPhrase = "";
        }
    }

    protected void doAnalysis() {
        // OFF-BODY CHECK: Geen FFT analyse als watch niet om de pols zit
        if (!mSdData.mWatchOnBody && !mSdData.mIsCharging) return;

        // Architecture Fix: Analyze fixed window from rolling buffer
        int n = mSdData.mNsampDefault;
        if (mSdData == null || mAccelBuffer.getNumVals() < n) return;
        
        try {
            if (n != mLastN) {
                mFft = new DoubleFFT_1D(n);
                mFftBuffer = new double[n * 2];
                mLastN = n;
            }
            
            // Forensic Copy: Get last N samples from rolling buffer without allocation
            mAccelBuffer.copyTo(mFftBuffer);
            for(int i=n; i < mFftBuffer.length; i++) mFftBuffer[i] = 0; // Pad

            mFft.realForward(mFftBuffer);
            
            double specPower = 0, roiPower = 0;
            final double freqStep = (double)mSdData.mSampleFreq / (double)n;
            
            // Clear old spectrum
            if (mSdData.simpleSpec == null) mSdData.simpleSpec = new int[10];

            for (int i = 1; i < n / 2; i++) {
                double power = (mFftBuffer[2 * i] * mFftBuffer[2 * i] + mFftBuffer[2 * i + 1] * mFftBuffer[2 * i + 1]);
                specPower += power;
                double freq = i * freqStep;
                if (freq >= 3.0 && freq <= 8.0) roiPower += power;

                // Unit Regtien: Fill simpleSpec bins (first 10 relevant frequency bins)
                if (i <= 10) {
                    mSdData.simpleSpec[i-1] = (int) (power / ACCEL_SCALE_FACTOR);
                }
            }
            
            double normN = n / 2.0;
            mSdData.specPower = (long) ((specPower / normN) / ACCEL_SCALE_FACTOR);
            mSdData.roiPower = (long) ((roiPower / normN) / ACCEL_SCALE_FACTOR);
            mSdData.roiRatio = (specPower > 0) ? (long) ((roiPower / specPower) * 100.0) : 0;
            mSdData.haveData = true;

            checkAlarm();
        } catch (Exception e) { Log.e(TAG, "FFT Fault: " + e.getMessage()); }
    }

    public void ClearAlarmCount() {
        mAlarmCount = 0; mHrAlarmCount = 0; mO2AlarmCount = 0;
        if (mSdData != null) { mSdData.alarmState = 0; mSdData.alarmPhrase = ""; }
        triggerUiUpdate();
    }

    protected abstract void getStatus();
    public abstract void startPebbleApp();
    @Override public IBinder onBind(Intent intent) { return null; }
}
