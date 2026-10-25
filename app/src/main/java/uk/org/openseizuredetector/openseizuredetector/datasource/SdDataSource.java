/*
  Android_Pebble_sd - Android alarm client for openseizuredetector..
  See http://openseizuredetector.org for more information.
  Copyright Graham Jones, 2015-2024.
*/
package uk.org.openseizuredetector.openseizuredetector.datasource;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;
import androidx.preference.PreferenceManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.Calendar;
import java.util.Timer;
import java.util.TimerTask;
import uk.org.openseizuredetector.openseizuredetector.R;
import uk.org.openseizuredetector.openseizuredetector.data.SdData;
import uk.org.openseizuredetector.openseizuredetector.utils.OsdUtil;
import uk.org.openseizuredetector.openseizuredetector.utils.PreferenceUtils;

/**
 * Abstract class for a seizure detector data source.
 * Optimized for GC: Pre-allocates buffers and recyclers to avoid 'new' in hot paths.
 * Compliance: Unit Regtien Forensic Standard / Graham Jones RC2.
 */
public abstract class SdDataSource {
    protected final Handler mHandler = new Handler(Looper.getMainLooper());
    private Timer mStatusTimer;
    private Timer mSettingsTimer;
    private Timer mFaultCheckTimer;
    protected long mDataStatusTimeMillis;
    protected boolean mWatchAppRunningCheck = false;
    private int mAppRestartTimeout = 10;
    private int mFaultTimerPeriod = 30;
    private int mSettingsPeriod = 60;
    public SdData mSdData;
    public String mName = "undefined";
    protected OsdUtil mUtil;
    protected Context mContext;
    protected SdDataReceiver mSdDataReceiver;
    private String TAG = "SdDataSource";

    private short mDataUpdatePeriod = 5;
    private short mMutePeriod = 60;
    private short mManAlarmPeriod = 30;
    private int mMute;

    protected String mBleDeviceAddr;
    protected String mBleDeviceName;
    private double mLastHrValue;
    private long mHrStatusTimeMillis;
    private double mHrFrozenPeriod = 60; // seconds
    private boolean mHrFrozenAlarm;
    private boolean mFidgetDetectorEnabled;
    private double mFidgetPeriod;
    private double mFidgetThreshold;
    private long mLastFidgetTimeMillis = 0;
    
    public boolean mIsWear = false;

    // GC-Optimized: Pre-allocated buffers to prevent heap churn
    private final double[] mMagBuf = new double[125];
    private final IntentFilter mBatteryFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);

    // Data frequency checking
    private static final int EXPECTED_DATA_PERIOD_SEC = 5;
    private static final int DATA_PERIOD_TOLERANCE_SEC = 1;
    private static final int MIN_ACCEPTABLE_PERIOD_SEC = EXPECTED_DATA_PERIOD_SEC - DATA_PERIOD_TOLERANCE_SEC;
    private static final int MAX_ACCEPTABLE_PERIOD_SEC = EXPECTED_DATA_PERIOD_SEC + DATA_PERIOD_TOLERANCE_SEC;
    private boolean mDataFrequencyCheckEnabled = true;
    private int mBadTimingCount = 0;
    private long mTimingFaultStartMillis = 0;
    private boolean mTimingFaultActive = false;

    protected volatile boolean mRunning = false;

    public boolean isRunning() { return mRunning; }
    protected void setRunning(boolean running) { mRunning = running; }

    public SdDataSource(Context context, Handler handler, SdDataReceiver sdDataReceiver) {
        mContext = context.getApplicationContext();
        mHandler = handler;
        mUtil = new OsdUtil(mContext, mHandler);
        mSdDataReceiver = sdDataReceiver;
        // Recycling rule: Reuse the receiver's data object if possible
        mSdData = (sdDataReceiver instanceof uk.org.openseizuredetector.openseizuredetector.SdServer) 
                ? ((uk.org.openseizuredetector.openseizuredetector.SdServer) sdDataReceiver).mSdData 
                : new SdData();
        mIsWear = mContext.getPackageName().contains(".aw");
    }

    public void start() {
        Log.i(TAG, "Forensic Start Sequence: " + mName);
        updatePrefs();
        setRunning(true);
        mBadTimingCount = 0;
        mTimingFaultStartMillis = 0;
        mTimingFaultActive = false;

        mDataStatusTimeMillis = System.currentTimeMillis();
        if (mStatusTimer == null) {
            mStatusTimer = new Timer();
            mStatusTimer.schedule(new TimerTask() {
                @Override public void run() { getStatus(); }
            }, 0, mDataUpdatePeriod * 1000);
        }

        mHrStatusTimeMillis = System.currentTimeMillis();
        mLastHrValue = -1;

        if (mFaultCheckTimer == null) {
            mFaultCheckTimer = new Timer();
            mFaultCheckTimer.schedule(new TimerTask() {
                @Override public void run() { faultCheck(); }
            }, 0, 1000);
        }

        if (mSettingsTimer == null) {
            mSettingsTimer = new Timer();
            mSettingsTimer.schedule(new TimerTask() {
                @Override public void run() { mSdData.haveSettings = false; }
            }, 0, 1000 * mSettingsPeriod);
        }
    }

    public void stop() {
        Log.i(TAG, "Forensic Stop Sequence");
        try {
            if (mStatusTimer != null) { mStatusTimer.cancel(); mStatusTimer = null; }
            if (mSettingsTimer != null) { mSettingsTimer.cancel(); mSettingsTimer = null; }
            if (mFaultCheckTimer != null) { mFaultCheckTimer.cancel(); mFaultCheckTimer = null; }
            setRunning(false);
        } catch (Exception e) { Log.v(TAG, "Stop error: " + e.toString()); }
    }

    public abstract void startPebbleApp();

    public String updateFromJSON(String jsonStr) {
        if (jsonStr == null || jsonStr.isEmpty()) return "ERROR";
        try {
            JSONObject dataObject = new JSONObject(jsonStr);
            String dataTypeStr = dataObject.getString("dataType");
            if (dataTypeStr.equals("raw")) {
                mSdData.mHR = dataObject.optDouble("HR", -1);
                mSdData.mO2Sat = dataObject.optDouble("O2sat", -1);
                mSdData.mMute = dataObject.optInt("Mute", 0);
                
                JSONArray accelVals3D = dataObject.optJSONArray("data3D");
                if (accelVals3D != null) {
                    for (int i = 0; i < accelVals3D.length() && i < mSdData.rawData3D.length; i++) {
                        mSharedData.rawData3D[i] = accelVals3D.getDouble(i);
                    }
                }
                
                JSONArray accelVals = dataObject.optJSONArray("data");
                if (accelVals != null) {
                    for (int i = 0; i < accelVals.length() && i < mSdData.rawData.length; i++) {
                        mSdData.rawData[i] = accelVals.getDouble(i);
                    }
                    mSdData.mNsamp = accelVals.length();
                }

                mWatchAppRunningCheck = true;
                doAnalysis();
                return mSdData.haveSettings ? "OK" : "sendSettings";
            } else if (dataTypeStr.equals("settings")) {
                mSdData.analysisPeriod = (short) dataObject.optInt("analysisPeriod", 5);
                mSdData.mSampleFreq = dataObject.optInt("sampleFreq", 25);
                mSdData.batteryPc = (short) dataObject.optInt("battery", 0);
                mSdData.haveSettings = true;
                mWatchAppRunningCheck = true;
                return "OK";
            }
        } catch (Exception e) { Log.e(TAG, "JSON error"); }
        return "ERROR";
    }

    protected void doAnalysis() {
        updateBatteryInfo();
        try {
            mDataStatusTimeMillis = System.currentTimeMillis();
            long tnow = System.currentTimeMillis();
            if (mSdData.dataTimeMillis != 0) {
                mSdData.timeDiff = (tnow - mSdData.dataTimeMillis) / 1000f;
                checkTimingFault(tnow);
            }
            mSdData.dataTimeMillis = tnow;
            mSdData.haveData = true;
            mWatchAppRunningCheck = true;
            mSdData.mAccelMagStdDev = calcAccelMagStdDev(mSdData);
        } catch (Exception e) { mWatchAppRunningCheck = false; }
        mSdDataReceiver.onSdDataReceived(mSdData);
    }

    private void checkTimingFault(long tnow) {
        if (mDataFrequencyCheckEnabled && (mSdData.timeDiff < MIN_ACCEPTABLE_PERIOD_SEC || mSdData.timeDiff > MAX_ACCEPTABLE_PERIOD_SEC)) {
            if (!mTimingFaultActive) { mTimingFaultStartMillis = tnow; mTimingFaultActive = true; }
            mBadTimingCount++;
            if ((tnow - mTimingFaultStartMillis) >= (mFaultTimerPeriod * 1000)) {
                mSdData.faultCause = "Forensic Timing Error";
                mSdDataReceiver.onSdDataFault(mSdData);
            }
        } else {
            mTimingFaultActive = false; mBadTimingCount = 0; mTimingFaultStartMillis = 0;
        }
    }

    private void updateBatteryInfo() {
        Intent batteryStatus = mContext.registerReceiver(null, mBatteryFilter);
        if (batteryStatus != null) {
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int pct = (int) (level * 100 / (float) scale);
            if (mIsWear) mSdData.batteryPc = pct;
            else mSdData.phoneBatteryPc = pct;
        }
    }

    private double calcAccelMagStdDev(SdData sdData) {
        int n = 125;
        double sum = 0.0;
        for (int i = 0; i < n; i++) {
            if (sdData.rawData3D == null || (i*3+2) >= sdData.rawData3D.length) break;
            double x = sdData.rawData3D[i * 3];
            double y = sdData.rawData3D[i * 3 + 1];
            double z = sdData.rawData3D[i * 3 + 2];
            mMagBuf[i] = Math.sqrt(x * x + y * y + z * z);
            sum += mMagBuf[i];
        }
        double mean = sum / n;
        if (mean == 0) return 0.0;
        double sumSq = 0.0;
        for (int i = 0; i < n; i++) sumSq += Math.pow(mMagBuf[i] - mean, 2);
        return 100.0 * Math.sqrt(sumSq / n) / mean;
    }

    public void getStatus() {
        long tnow = System.currentTimeMillis();
        long tdiff = tnow - mDataStatusTimeMillis;
        mSdData.watchConnected = true;
        if (!mWatchAppRunningCheck && (tdiff > (mDataUpdatePeriod + mAppRestartTimeout) * 1000)) {
            mSdData.watchAppRunning = false;
            if (tdiff > (mDataUpdatePeriod + mFaultTimerPeriod) * 1000) {
                mSdDataReceiver.onSdDataFault(mSdData);
            }
        } else { mSdData.watchAppRunning = true; }
        if (mWatchAppRunningCheck) { mWatchAppRunningCheck = false; mDataStatusTimeMillis = System.currentTimeMillis(); }
    }

    protected void faultCheck() {
        long tnow = System.currentTimeMillis();
        if (mSdData.mHRAlarmActive && mHrFrozenAlarm) {
            if (mSdData.mHR != mLastHrValue) {
                mLastHrValue = mSdData.mHR;
                mHrStatusTimeMillis = tnow;
                mSdData.mHrFrozenFaultStanding = false;
            } else if (tnow - mHrStatusTimeMillis > mHrFrozenPeriod * 1000.) {
                mSdData.mHrFrozenFaultStanding = true;
            }
        }
    }

    public void updatePrefs() {
        SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(mContext);
        mAppRestartTimeout = Integer.parseInt(SP.getString("AppRestartTimeout", "10"));
        mFaultTimerPeriod = Integer.parseInt(SP.getString("FaultTimerPeriod", "30"));
        mDataUpdatePeriod = (short) Integer.parseInt(SP.getString("PebbleUpdatePeriod", "5"));
    }

    protected void showToast(String msg) {
        mHandler.post(() -> Toast.makeText(mContext, msg, Toast.LENGTH_LONG).show());
    }
}
