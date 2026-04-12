package uk.org.openseizuredetector.openseizuredetector;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.wearable.CapabilityClient;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.MessageClient;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * SdDataSourceAw - en_GB
 * Smart Hybrid Data Source for Wear OS.
 * Fixed: Microseconds timing for HR sensors and explicit console logging.
 */
public class SdDataSourceAw extends SdDataSource implements 
        SensorEventListener, 
        MessageClient.OnMessageReceivedListener, 
        CapabilityClient.OnCapabilityChangedListener {
        
    private final String TAG = "OSD_DEBUG_AW";
    private final List<AndroidSensor> mActiveSensors = new ArrayList<>();
    private final List<Double> rawDataList = new ArrayList<>();
    
    private int mMode = 0; 
    private long mStartTs = 0;
    private int mCurrentMaxSampleCount = 125;
    private double mConversionSampleFactor = 1.0;
    private double mSampleTimeUs = 40000; 
    
    private String mMobileNodeUri = null;
    private boolean mIsCharging = false;

    // UI Tick Handler
    private final Handler mClockHandler = new Handler(Looper.getMainLooper());
    private final Runnable mClockRunnable = new Runnable() {
        @Override
        public void run() {
            triggerUiUpdate();
            mClockHandler.postDelayed(this, 1000);
        }
    };

    // Battery Monitoring
    private final BroadcastReceiver mBatteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateBatteryStatus(intent);
        }
    };

    public SdDataSourceAw(Context context, Handler handler, SdDataReceiver sdDataReceiver) {
        super(context, handler, sdDataReceiver);
        Log.i(TAG, "Constructor: Initializing Smart Hybrid");
        mName = "WearOS_SmartHybrid";
        calculateStaticTimings();
        initialiseHardware(context);
    }

    private void updateBatteryStatus(Intent intent) {
        if (intent == null) return;
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        if (level != -1 && scale != -1) {
            mSdData.batteryPc = (long) ((level / (float) scale) * 100);
            Log.d(TAG, "Battery Update: " + mSdData.batteryPc + "%");
            triggerUiUpdate();
        }
    }

    private void triggerUiUpdate() {
        if (mSdDataReceiver != null) {
            mSdDataReceiver.onSdDataReceived(mSdData);
        }
    }

    private void calculateStaticTimings() {
        if (mSdData == null) return;
        mCurrentMaxSampleCount = mSdData.mNsampDefault;
        if (mSdData.dT <= 0) mSdData.dT = Constants.SD_SERVICE_CONSTANTS.defaultSampleTime;
        mSdData.mSampleFreq = (long) ((double) mCurrentMaxSampleCount / mSdData.dT);
        if (mSdData.mSampleFreq < 1) mSdData.mSampleFreq = Constants.SD_SERVICE_CONSTANTS.defaultSampleRate;
        mSampleTimeUs = (1.0 / (double) mSdData.mSampleFreq) * 1000000.0;
        double targetSampleTimeUs = (1.0 / (double) Constants.SD_SERVICE_CONSTANTS.defaultSampleRate) * 1000000.0;
        mConversionSampleFactor = mSampleTimeUs / targetSampleTimeUs;
    }

    private void initialiseHardware(Context context) {
        mActiveSensors.clear();
        // Convert milliseconds to microseconds for SensorManager
        int hrIntervalUs = (int) (Constants.GLOBAL_CONSTANTS.getMaxHeartRefreshRate * 1000);

        // 1. Accelerometer
        AccelerationSensor accel = new AccelerationSensor(context, (int)mSampleTimeUs, 0) {
            @Override public void onSensorChanged(SensorEvent event) { accelerationEvent(event); }
        };
        if (accel.getDoesSensorExist()) mActiveSensors.add(accel);

        // 2. Off-Body Detection
        OffBodyDetectSensor offBody = new OffBodyDetectSensor(context, (int)mSampleTimeUs, 0) {
            @Override public void onSensorChanged(SensorEvent event) {
                if (event.values.length > 0) {
                    mSdData.mWatchOnBody = (event.values[0] != 0d);
                    triggerUiUpdate();
                }
            }
        };
        if (offBody.getDoesSensorExist()) mActiveSensors.add(offBody);

        // 3. Heart Rate Hybrid (Galaxy prioritised, Emulator compatible)
        SamsungSamsungHealthHeartRateSensor samHr = new SamsungSamsungHealthHeartRateSensor(context, hrIntervalUs, 0) {
            @Override public void onSensorChanged(SensorEvent event) { heartRateEvent(event); }
        };
        
        if (samHr.getDoesSensorExist()) {
            mActiveSensors.add(samHr);
            Log.i(TAG, "Hardware: Samsung HR sensor found.");
        } else {
            HeartRateSensor stdHr = new HeartRateSensor(context, hrIntervalUs, 0) {
                @Override public void onSensorChanged(SensorEvent event) { heartRateEvent(event); }
            };
            if (stdHr.getDoesSensorExist()) {
                mActiveSensors.add(stdHr);
                Log.i(TAG, "Hardware: Standard HR sensor found (Emulator).");
            }
        }

        // 4. SpO2 Hybrid
        SamsungSamsungHealthSpO2Sensor samSpO2 = new SamsungSamsungHealthSpO2Sensor(context, hrIntervalUs, 0) {
            @Override public void onSensorChanged(SensorEvent event) { spO2SensorChanged(event); }
        };
        if (samSpO2.getDoesSensorExist()) mActiveSensors.add(samSpO2);
    }

    @Override
    public void start() {
        super.start();
        Log.i(TAG, "start: Engine ignition");
        
        // Manual Initial Battery Sync
        Intent sticky = mContext.registerReceiver(mBatteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (sticky != null) updateBatteryStatus(sticky); else {
            BatteryManager bm = (BatteryManager) mContext.getSystemService(Context.BATTERY_SERVICE);
            mSdData.batteryPc = (long) bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        }

        mClockHandler.post(mClockRunnable);
        bindSensorListeners();
        
        Wearable.getCapabilityClient(mContext).addListener(this, Uri.parse("wear://"), CapabilityClient.FILTER_REACHABLE);
        Wearable.getMessageClient(mContext).addListener(this);
        mIsRunning = true;
    }

    private void bindSensorListeners() {
        if (mIsCharging) return;
        for (AndroidSensor sensor : mActiveSensors) {
            sensor.startListening();
        }
    }

    private void unBindSensorListeners() {
        for (AndroidSensor sensor : mActiveSensors) {
            sensor.stopListening();
        }
    }

    @Override
    public void stop() {
        Log.i(TAG, "stop: Engine shutdown");
        mClockHandler.removeCallbacks(mClockRunnable);
        try { mContext.unregisterReceiver(mBatteryReceiver); } catch (Exception e) {}
        unBindSensorListeners();
        Wearable.getCapabilityClient(mContext).removeListener(this);
        Wearable.getMessageClient(mContext).removeListener(this);
        super.stop();
        mIsRunning = false;
    }

    public void onPowerStateChanged(boolean isCharging) {
        this.mIsCharging = isCharging;
        if (isCharging) unBindSensorListeners(); else bindSensorListeners();
    }

    public void accelerationEvent(SensorEvent event) {
        double x = OsdUtil.convertMetresPerSecondSquaredToMilliG(event.values[0]);
        double y = OsdUtil.convertMetresPerSecondSquaredToMilliG(event.values[1]);
        double z = OsdUtil.convertMetresPerSecondSquaredToMilliG(event.values[2]);
        double magnitude = Math.pow(x * x + y * y + z * z, 1.0/3.0);

        if (mMode == 0) { 
            if (mStartTs == 0) { mStartTs = event.timestamp; mSdData.mNsamp = 0; }
            else mSdData.mNsamp++;
            if (mSdData.mNsamp >= mSdData.mNsampDefault) {
                mSdData.dT = (double)(event.timestamp - mStartTs) / 1000000000.0;
                mMode = 1; mSdData.mNsamp = 0; mStartTs = event.timestamp;
                calculateStaticTimings();
            }
        } else {
            if (mSdData.mNsamp >= mCurrentMaxSampleCount) {
                for (int i = 0; i < Constants.SD_SERVICE_CONSTANTS.defaultSampleCount; i++) {
                    int readPos = (int) (i / mConversionSampleFactor);
                    if (readPos < rawDataList.size()) mSdData.rawData[i] = rawDataList.get(readPos);
                }
                mSdData.mNsamp = Constants.SD_SERVICE_CONSTANTS.defaultSampleCount;
                doAnalysis();
                sendMessage(Constants.GLOBAL_CONSTANTS.MESSAGE_ITEM_OSD_DATA, mSdData.toDataString(true));
                rawDataList.clear();
                mSdData.mNsamp = 0; mStartTs = event.timestamp;
            } else {
                rawDataList.add(magnitude);
                mSdData.mNsamp++;
            }
        }
    }

    public void heartRateEvent(SensorEvent event) {
        int newValue = Math.round(event.values[0]);
        Log.d(TAG, "HR sensor update: " + newValue + " bpm");
        if (newValue > 0 && mSdData.mHr != newValue) {
            mSdData.mHr = newValue;
            mSdData.mHR = newValue;
            mSdData.heartRates.add((double) newValue);
            mSdData.haveData = true;
            triggerUiUpdate(); 
            
            if (mSdData.heartRates.size() % 5 == 0) {
                sendMessage(Constants.GLOBAL_CONSTANTS.MESSAGE_ITEM_OSD_DATA, mSdData.toHeartRatesArrayString());
            }
        }
    }

    public void spO2SensorChanged(SensorEvent event) {
        if (event.values.length > 0) {
            mSdData.mO2Sat = event.values[0];
            Log.v(TAG, "Sensor Event: SpO2 = " + mSdData.mO2Sat);
            triggerUiUpdate();
            sendMessage(Constants.GLOBAL_CONSTANTS.MESSAGE_ITEM_OSD_DATA, mSdData.toDataString(true));
        }
    }

    @Override
    public void onMessageReceived(@NonNull MessageEvent messageEvent) {
        final String path = messageEvent.getPath();
        if (path.equals(Constants.ACTION.PUSH_SETTINGS_ACTION)) {
            mSdData.fromJSON(new String(messageEvent.getData(), StandardCharsets.UTF_8));
            calculateStaticTimings(); unBindSensorListeners(); bindSensorListeners();
        } else if (path.equals(Constants.ACTION.STOP_WEAR_SD_ACTION)) {
            stop();
        }
    }

    @Override
    public void onCapabilityChanged(@NonNull CapabilityInfo capabilityInfo) {
        Set<Node> nodes = capabilityInfo.getNodes();
        if (!nodes.isEmpty()) {
            mMobileNodeUri = nodes.iterator().next().getId();
            mSdData.watchConnected = true; mSdData.serverOK = true;
        } else {
            mMobileNodeUri = null;
            mSdData.watchConnected = false;
        }
        triggerUiUpdate();
    }

    private void sendMessage(final String path, final String text) {
        if (mMobileNodeUri != null) Wearable.getMessageClient(mContext).sendMessage(mMobileNodeUri, path, text.getBytes(StandardCharsets.UTF_8));
    }

    @Override public void onSensorChanged(SensorEvent event) {}
    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    @Override public void ClearAlarmCount() { if (mSdData != null) mSdData.alarmState = 0; triggerUiUpdate(); }
    @Override public void startPebbleApp() {}
    @Override protected void getStatus() { mSdData.watchConnected = true; triggerUiUpdate(); }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
}
