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
 * SdDataSource - Core Data Handling
 * Implements Graham's Tremor Detection Algorithm (FFT Analysis).
 * Optimized for HyperArousal (C-PTSD/HB/HIS) monitoring.
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
    
    // Algorithm Thresholds (can be updated via updatePrefs)
    protected short mAlarmThresh = 15;
    protected short mAlarmRatioThresh = 4;
    protected int mAlarmCountThreshold = 10; // Number of consecutive analysis blocks
    protected int mAlarmCount = 0;
    
    // HR Monitoring for HyperArousal
    protected int mHrAlarmThreshMax = 120;
    protected int mHrAlarmCount = 0;
    protected final int HR_ALARM_COUNT_MAX = 5; // 5 consecutive high readings

    private final int ACCEL_SCALE_FACTOR = 1000;

    protected long mDataStatusTimeMillis;
    protected boolean mIsRunning = false;
    private Timer mStatusTimer;

    // FFT Optimization
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

    public void initialiseAlgorithms() {
        Log.i(TAG, "initialiseAlgorithms()");
    }

    protected SdData pullSdData() {
        if (mSdDataReceiver instanceof SdServer) {
            return ((SdServer) mSdDataReceiver).mSdData;
        }
        return new SdData();
    }

    public void start() {
        updatePrefs();
        mIsRunning = true;
        mDataStatusTimeMillis = System.currentTimeMillis();
        
        if (mStatusTimer == null) {
            mStatusTimer = new Timer();
            mStatusTimer.schedule(new TimerTask() {
                @Override
                public void run() { getStatus(); }
            }, 0, 5000);
        }
    }

    public void stop() {
        mIsRunning = false;
        if (mStatusTimer != null) {
            mStatusTimer.cancel();
            mStatusTimer = null;
        }
    }

    public void updatePrefs() {
        Log.v(TAG, "updatePrefs() - Base");
        // Pull latest thresholds from OsdUtil if available
        if (mUtil != null) {
            mAlarmThresh = (short) mUtil.getPrefs().getInt("alarm_threshold", 15);
            mAlarmRatioThresh = (short) mUtil.getPrefs().getInt("alarm_ratio_threshold", 4);
            mHrAlarmThreshMax = mUtil.getPrefs().getInt("hr_alarm_threshold", 120);
        }
    }

    public void updateFromJSON(String jsonStr) {
        if (mSdData != null) {
            mSdData.fromJSON(jsonStr);
        }
    }

    public void handleSendingHelp() {
        Log.i(TAG, "handleSendingHelp() - Base");
    }

    public void muteCheck() {
        Log.v(TAG, "muteCheck() - Base");
    }

    public void hrCheck() {
        if (mSdData == null || mSdData.mHr <= 0) return;

        if (mSdData.mHr >= mHrAlarmThreshMax) {
            mHrAlarmCount++;
            if (mHrAlarmCount >= HR_ALARM_COUNT_MAX) {
                mSdData.alarmState = 2;
                mSdData.alarmPhrase = "HIGH HEART RATE: " + (int)mSdData.mHr;
                mSdData.alarmCause = "HR";
            } else {
                // Warning only
                if (mSdData.alarmState < 1) {
                    mSdData.alarmState = 1;
                    mSdData.alarmPhrase = "WARNING: High HR (" + (int)mSdData.mHr + ")";
                }
            }
        } else {
            if (mHrAlarmCount > 0) mHrAlarmCount--;
        }
    }

    public void o2SatCheck() {
        Log.v(TAG, "o2SatCheck() - Base");
    }

    public void fallCheck() {
        Log.v(TAG, "fallCheck() - Base");
    }

    protected void faultCheck() {
        Log.v(TAG, "faultCheck() - Base");
    }

    /**
     * checkAlarm - Evaluates if analysis results exceed thresholds.
     */
    protected void checkAlarm() {
        if (mSdData == null) return;

        // Perform HR check alongside movement
        hrCheck();

        // Graham's Logic: ROI Power must be > Threshold AND Ratio must be > Ratio Threshold
        if (mSdData.roiPower >= mAlarmThresh && mSdData.roiRatio >= mAlarmRatioThresh) {
            mAlarmCount++;
            Log.w(TAG, "Potential Seizure! AlarmCount: " + mAlarmCount);
            
            if (mAlarmCount >= mAlarmCountThreshold) {
                mSdData.alarmState = 2; // ALARM
                mSdData.alarmPhrase = "SEIZURE DETECTED";
            } else {
                mSdData.alarmState = 1; // WARNING
                mSdData.alarmPhrase = "WARNING: Tremor";
            }
        } else {
            // Reset alarm count if conditions are not met
            if (mAlarmCount > 0) {
                mAlarmCount--;
            } else {
                // Only clear alarm state if HR is also okay
                if (mHrAlarmCount == 0) {
                    mSdData.alarmState = 0;
                    mSdData.alarmPhrase = "";
                }
            }
        }
    }

    /**
     * doAnalysis - Graham's Core Detection Logic
     * Performs FFT and calculates ROI Power (3-8Hz) and ROI Ratio.
     */
    protected void doAnalysis() {
        if (mSdData == null || mSdData.rawData == null || mSdData.mNsamp <= 0) return;

        try {
            int n = mSdData.mNsamp;
            
            // Optimization: Reuse FFT object if sample size hasn't changed
            if (n != mLastN) {
                mFft = new DoubleFFT_1D(n);
                mLastN = n;
            }
            
            double[] fft = new double[n * 2];
            System.arraycopy(mSdData.rawData, 0, fft, 0, n);
            mFft.realForward(fft);

            double specPower = 0;
            double roiPower = 0;
            
            double freqStep = (double)mSdData.mSampleFreq / (double)n;
            double lowFreq = 3.0;
            double highFreq = 8.0;

            for (int i = 1; i < n / 2; i++) {
                double freq = i * freqStep;
                double power = (fft[2 * i] * fft[2 * i] + fft[2 * i + 1] * fft[2 * i + 1]);
                
                specPower += power;
                if (freq >= lowFreq && freq <= highFreq) {
                    roiPower += power;
                }
            }

            specPower = specPower / (n / 2.0);
            roiPower = roiPower / (n / 2.0);

            mSdData.specPower = (long) (specPower / ACCEL_SCALE_FACTOR);
            mSdData.roiPower = (long) (roiPower / ACCEL_SCALE_FACTOR);
            
            if (specPower > 0) {
                mSdData.roiRatio = (long) ((roiPower / specPower) * 100.0);
            } else {
                mSdData.roiRatio = 0;
            }

            mSdData.haveData = true;
            checkAlarm();
            
        } catch (Exception e) {
            Log.e(TAG, "FFT Error: " + e.getMessage());
        }
    }

    protected abstract void getStatus();
    public abstract void ClearAlarmCount();
    public abstract void startPebbleApp();

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
