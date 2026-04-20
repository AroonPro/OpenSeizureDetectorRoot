package uk.org.openseizuredetector.openseizuredetector;

import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import android.os.IBinder;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * SdDataSourcePhone - Uses internal phone accelerometer for testing.
 * Updated: Conditional UI updates to save battery on Wear devices.
 */
public class SdDataSourcePhone extends SdDataSource implements SensorEventListener {
    private String TAG = "SdDataSourcePhone";

    private SensorManager mSensorManager;
    private int mMode = 0;   
    private long mStartTs = 0;
    private double mSampleTimeUs = -1;
    private int mCurrentMaxSampleCount = -1;

    private boolean sensorsActive = false;
    private List<Double> rawDataList = new ArrayList<>();
    private List<Double> rawDataList3D = new ArrayList<>();

    @Override public void ClearAlarmCount() { super.ClearAlarmCount(); }
    @Override public void handleSendingHelp() {}

    public SdDataSourcePhone(Context context, Handler handler, SdDataReceiver sdDataReceiver) {
        super(context, handler, sdDataReceiver);
        mName = "Phone";
        updatePrefs();
    }

    private void bindSensorListeners() {
        if (mSampleTimeUs <= 0) mSampleTimeUs = SensorManager.SENSOR_DELAY_GAME;
        mSensorManager = (SensorManager) mContext.getSystemService(SENSOR_SERVICE);
        if (mSensorManager != null) {
            Sensor mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            mSensorManager.registerListener(this, mSensor, (int) mSampleTimeUs, mHandler);
            sensorsActive = true;
        }
    }

    @Override
    public void start() {
        mCurrentMaxSampleCount = mSdData.mNsampDefault;
        bindSensorListeners();
        mIsRunning = true;
    }

    @Override public void startPebbleApp() {}

    @Override
    public void stop() {
        if (sensorsActive && mSensorManager != null) mSensorManager.unregisterListener(this);
        sensorsActive = false;
        mIsRunning = false;
    }

    @Override protected void getStatus() { triggerUiUpdate(); }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;

        double x = OsdUtil.convertMetresPerSecondSquaredToMilliG(event.values[0]);
        double y = OsdUtil.convertMetresPerSecondSquaredToMilliG(event.values[1]);
        double z = OsdUtil.convertMetresPerSecondSquaredToMilliG(event.values[2]);
        double magnitude = Math.sqrt(x * x + y * y + z * z);

        if (mMode == 0) handleCalibrationMode(event);
        else handleRunningMode(magnitude, x, y, z, event.timestamp);
    }

    private void handleCalibrationMode(SensorEvent event) {
        if (mStartTs == 0) { mStartTs = event.timestamp; mSdData.mNsamp = 0; }
        else mSdData.mNsamp++;

        if (mSdData.mNsamp >= 100) {
            double dT = 1.0e-9 * (event.timestamp - mStartTs);
            mSdData.mSampleFreq = (int) (mSdData.mNsamp / dT);
            mMode = 1; mSdData.mNsamp = 0; mStartTs = event.timestamp;
        }
    }

    private void handleRunningMode(double mag, double x, double y, double z, long ts) {
        rawDataList.add(mag);
        rawDataList3D.add(x);
        rawDataList3D.add(y);
        rawDataList3D.add(z);
        mSdData.mNsamp++;

        if (mSdData.mNsamp >= mCurrentMaxSampleCount) {
            for (int i = 0; i < mCurrentMaxSampleCount; i++) {
                if (i < rawDataList.size()) mSdData.rawData[i] = rawDataList.get(i);
            }
            doAnalysis();

            // BATTERIJ BESPARING: Enkel UI update als dit GEEN Wear app is, of bij ALARM
            boolean isWearApp = mContext.getPackageName().equals("uk.org.openseizuredetector.aw");
            if (!isWearApp || mSdData.alarmState >= 2) {
                triggerUiUpdate();
            }

            rawDataList.clear(); rawDataList3D.clear();
            mSdData.mNsamp = 0; mStartTs = ts;
        }
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
}
