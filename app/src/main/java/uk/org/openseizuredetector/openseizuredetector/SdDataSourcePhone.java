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
 * Refactored for en_GB standards and MPAndroidChart 3.x compatibility.
 */
public class SdDataSourcePhone extends SdDataSource implements SensorEventListener {
    private String TAG = "SdDataSourcePhone";

    private SensorManager mSensorManager;
    private int mMode = 0;   // 0=check data rate, 1=running
    private long mStartTs = 0;
    private double mSampleTimeUs = -1;
    private int mCurrentMaxSampleCount = -1;
    private double mConversionSampleFactor = 1.0;

    private boolean sensorsActive = false;
    private List<Double> rawDataList = new ArrayList<>();
    private List<Double> rawDataList3D = new ArrayList<>();

    /**
     *
     */
    @Override
    public void ClearAlarmCount() {

    }

    /**
     *
     */
    @Override
    public void handleSendingHelp() {

    }

    public SdDataSourcePhone(Context context, Handler handler, SdDataReceiver sdDataReceiver) {
        super(context, handler, sdDataReceiver);
        mName = "Phone";

        // en_GB Fix: Use mContext from super instead of missing useSdServerBinding()
        int xmlId1 = mContext.getResources().getIdentifier("network_passive_datasource_prefs", "xml", mContext.getPackageName());
        int xmlId2 = mContext.getResources().getIdentifier("seizure_detector_prefs", "xml", mContext.getPackageName());

        if (xmlId1 != 0) PreferenceManager.setDefaultValues(mContext, xmlId1, true);
        if (xmlId2 != 0) PreferenceManager.setDefaultValues(mContext, xmlId2, true);

        updatePrefs();
        mSdData = pullSdData();
    }

    private void bindSensorListeners() {
        if (mSampleTimeUs <= 0) {
            mSampleTimeUs = SensorManager.SENSOR_DELAY_GAME; // ~20ms / 50Hz
        }
        mSensorManager = (SensorManager) mContext.getSystemService(SENSOR_SERVICE);
        if (mSensorManager != null) {
            Sensor mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            mSensorManager.registerListener(this, mSensor, (int) mSampleTimeUs, mHandler);
            sensorsActive = true;
            Log.d(TAG, "bindSensorListeners: Active at " + mSampleTimeUs + "us");
        }
    }

    private void unBindSensorListeners() {
        if (sensorsActive && mSensorManager != null) {
            mSensorManager.unregisterListener(this);
        }
        sensorsActive = false;
    }

    @Override
    public void start() {
        Log.i(TAG, "start()");
        mCurrentMaxSampleCount = Constants.SD_SERVICE_CONSTANTS.defaultSampleCount;
        bindSensorListeners();
        mIsRunning = true;
    }

    /**
     *
     */
    @Override
    public void startPebbleApp() {

    }

    @Override
    public void stop() {
        Log.i(TAG, "stop()");
        unBindSensorListeners();
        mIsRunning = false;
    }

    /**
     *
     */
    @Override
    public void muteCheck() {

    }

    /**
     *
     */
    @Override
    protected void getStatus() {

    }

    @Override
    protected void checkAlarm() {
        // Implementation for phone-based alarm checking
    }

    /**
     *
     */
    @Override
    public void faultCheck() {

    }

    /**
     *
     */
    @Override
    public void hrCheck() {

    }

    /**
     *
     */
    @Override
    public void o2SatCheck() {

    }

    /**
     *
     */
    @Override
    public void fallCheck() {

    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;

        // Convert m/s^2 to milli-G
        double x = OsdUtil.convertMetresPerSecondSquaredToMilliG(event.values[0]);
        double y = OsdUtil.convertMetresPerSecondSquaredToMilliG(event.values[1]);
        double z = OsdUtil.convertMetresPerSecondSquaredToMilliG(event.values[2]);
        double magnitude = Math.sqrt(x * x + y * y + z * z);

        if (mMode == 0) {
            handleCalibrationMode(event);
        } else {
            handleRunningMode(magnitude, x, y, z, event.timestamp);
        }
    }

    private void handleCalibrationMode(SensorEvent event) {
        if (mStartTs == 0) {
            mStartTs = event.timestamp;
            mSdData.mNsamp = 0;
        } else {
            mSdData.mNsamp++;
        }

        if (mSdData.mNsamp >= 100) { // Check frequency over 100 samples
            double dT = 1.0e-9 * (event.timestamp - mStartTs);
            mSdData.mSampleFreq = (int) (mSdData.mNsamp / dT);
            mMode = 1;
            mSdData.mNsamp = 0;
            mStartTs = event.timestamp;
            Log.i(TAG, "Calibration complete. Freq: " + mSdData.mSampleFreq + "Hz");
        }
    }

    private void handleRunningMode(double mag, double x, double y, double z, long ts) {
        rawDataList.add(mag);
        rawDataList3D.add(x);
        rawDataList3D.add(y);
        rawDataList3D.add(z);
        mSdData.mNsamp++;

        // If we have enough samples for an analysis window
        if (mSdData.mNsamp >= mCurrentMaxSampleCount) {
            for (int i = 0; i < mCurrentMaxSampleCount; i++) {
                if (i < rawDataList.size()) {
                    mSdData.rawData[i] = rawDataList.get(i);
                    // 3D data is stored in triplets
                    if (i * 3 + 2 < rawDataList3D.size()) {
                        mSdData.rawData3D[i * 3] = rawDataList3D.get(i * 3);
                        mSdData.rawData3D[i * 3 + 1] = rawDataList3D.get(i * 3 + 1);
                        mSdData.rawData3D[i * 3 + 2] = rawDataList3D.get(i * 3 + 2);
                    }
                }
            }

            // Standard OSD resets for phone data
            mSdData.mHR = -1;
            mSdData.mO2Sat = -1;

            doAnalysis(); // Perform FFT and check for seizures

            // Reset buffers
            rawDataList.clear();
            rawDataList3D.clear();
            mSdData.mNsamp = 0;
            mStartTs = ts;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    /**
     * Return the communication channel to the service.  May return null if
     * clients can not bind to the service.  The returned
     * {@link IBinder} is usually for a complex interface
     * that has been <a href="{@docRoot}guide/components/aidl.html">described using
     * aidl</a>.
     *
     * <p><em>Note that unlike other application components, calls on to the
     * IBinder interface returned here may not happen on the main thread
     * of the process</em>.  More information about the main thread can be found in
     * <a href="{@docRoot}guide/topics/fundamentals/processes-and-threads.html">Processes and
     * Threads</a>.</p>
     *
     * @param intent The Intent that was used to bind to this service,
     *               as given to {@link Context#bindService
     *               Context.bindService}.  Note that any extras that were included with
     *               the Intent at that point will <em>not</em> be seen here.
     * @return Return an IBinder through which clients can call on to the
     * service.
     */
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
