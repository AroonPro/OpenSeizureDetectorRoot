package uk.org.openseizuredetector.openseizuredetector;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import androidx.lifecycle.MutableLiveData;

import java.util.Timer;
import java.util.TimerTask;

import org.jtransforms.fft.DoubleFFT_1D;

/**
 * SdDataSource - Base Class
 * Harmonized to support both Modern (WearOS) and Legacy (Pebble/Garmin/Phone) sources.
 */
public abstract class SdDataSource extends Service {
    protected final String TAG = this.getClass().getSimpleName();
    protected Context mContext;
    protected Handler mHandler;
    protected static SdDataReceiver mSdDataReceiver;
    protected OsdUtil mUtil;
    public SdData mSdData;
    public String mName = "BaseSource";

    public MutableLiveData<SdData> serviceLiveData = new MutableLiveData<>();
    
    protected short mAlarmThresh = 15;
    protected short mAlarmRatioThresh = 4;
    protected int mAlarmCountThreshold = 10;
    protected int mAlarmCount = 0;
    
    protected int mHrAlarmThreshMax = 120;
    protected int mHrAlarmCount = 0;
    protected final int HR_ALARM_COUNT_MAX = 10; 

    protected int mO2AlarmCount = 0;
    protected final int O2_ALARM_COUNT_MAX = 5; 

    private final int ACCEL_SCALE_FACTOR = 1000;

    protected long mDataStatusTimeMillis;
    protected boolean mIsRunning = false;
    private Timer mStatusTimer;

    private DoubleFFT_1D mFft;
    private int mLastN = -1;

    public class SdBinder extends Binder {
        public SdDataSource getService() { return SdDataSource.this; }
    }

    public SdDataSource(Context context, Handler handler, SdDataReceiver sdDataReceiver) {
        this.mContext = context;
        this.mHandler = handler;
        mSdDataReceiver = sdDataReceiver;
        this.mUtil = new OsdUtil(context, mHandler);
        this.mSdData = pullSdData();
    }

    protected SdData pullSdData() {
        if (mSdDataReceiver instanceof SdServer) return ((SdServer) mSdDataReceiver).mSdData;
        return new SdData();
    }

    public void start() {
        updatePrefs();
        mIsRunning = true;
        mDataStatusTimeMillis = System.currentTimeMillis();
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
            if (mSdData != null) {
                mSdData.mO2SatThreshMin = (double) mUtil.getPrefs().getInt("o2_threshold", 90);
            }
        }
    }

    public void updateFromJSON(String jsonStr) { if (mSdData != null) mSdData.fromJSON(jsonStr); }

    protected void triggerUiUpdate() {
        if (mSdDataReceiver != null) {
            mSdDataReceiver.onSdDataReceived(mSdData);
        }
    }

    // Methods needed by subclasses to override
    public void muteCheck() {}
    protected void faultCheck() {}
    public void handleSendingHelp() {}
    public void fallCheck() {}
    public void o2SatCheck() {}

    protected void hrCheck() {
        if (mSdData == null || mSdData.mHr <= 0) {
            mHrAlarmCount = 0;
            return;
        }
        if (mSdData.mHr >= mHrAlarmThreshMax) {
            mHrAlarmCount++;
            if (mHrAlarmCount >= HR_ALARM_COUNT_MAX) {
                if (mSdData.mO2Sat > 0 && mSdData.mO2Sat < mSdData.mO2SatThreshMin) {
                    mSdData.alarmState = 2;
                    mSdData.alarmPhrase = "HR/O2 CRITICAL";
                } else if (mSdData.alarmState < 1) {
                    mSdData.alarmState = 1;
                    mSdData.alarmPhrase = "WARNING: High HR";
                }
            }
        } else if (mHrAlarmCount > 0) mHrAlarmCount--;
    }

    protected void checkAlarm() {
        if (mSdData == null) return;
        hrCheck();
        o2SatCheck();
        
        if (mSdData.roiPower >= mAlarmThresh && mSdData.roiRatio >= mAlarmRatioThresh) {
            mAlarmCount++;
            if (mAlarmCount >= mAlarmCountThreshold) {
                mSdData.alarmState = 2;
                mSdData.alarmPhrase = "SEIZURE DETECTED";
            } else {
                mSdData.alarmState = Math.max(mSdData.alarmState, 1);
                mSdData.alarmPhrase = "WARNING: Tremor";
            }
        } else if (mAlarmCount > 0) mAlarmCount--;

        if (mAlarmCount == 0 && mHrAlarmCount == 0 && mO2AlarmCount == 0) {
            if (mSdData.alarmState != 0) {
                mSdData.alarmState = 0;
                mSdData.alarmPhrase = "";
            }
        }
    }

    protected void doAnalysis() {
        if (mSdData == null || mSdData.rawData == null || mSdData.mNsamp <= 0) return;
        try {
            int n = mSdData.mNsamp;
            if (n != mLastN) { mFft = new DoubleFFT_1D(n); mLastN = n; }
            double[] fft = new double[n * 2];
            System.arraycopy(mSdData.rawData, 0, fft, 0, n);
            mFft.realForward(fft);
            double specPower = 0, roiPower = 0;
            double freqStep = (double)mSdData.mSampleFreq / (double)n;
            for (int i = 1; i < n / 2; i++) {
                double freq = i * freqStep;
                double power = (fft[2 * i] * fft[2 * i] + fft[2 * i + 1] * fft[2 * i + 1]);
                specPower += power;
                if (freq >= 3.0 && freq <= 8.0) roiPower += power;
            }
            specPower /= (n / 2.0);
            roiPower /= (n / 2.0);
            mSdData.specPower = (long) (specPower / ACCEL_SCALE_FACTOR);
            mSdData.roiPower = (long) (roiPower / ACCEL_SCALE_FACTOR);
            mSdData.roiRatio = (specPower > 0) ? (long) ((roiPower / specPower) * 100.0) : 0;
            mSdData.haveData = true;
            checkAlarm();
        } catch (Exception e) { Log.e(TAG, "FFT Error: " + e.getMessage()); }
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
