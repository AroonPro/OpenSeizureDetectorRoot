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
import android.os.Build;
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
import com.google.android.gms.wearable.Wearable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * SdDataSourceAw - Unit Regtien Optimized.
 * Zero-allocation in accelerometer events; conditional UI updates.
 */
public class SdDataSourceAw extends SdDataSource implements 
        SensorEventListener, 
        MessageClient.OnMessageReceivedListener, 
        CapabilityClient.OnCapabilityChangedListener {
        
    private final String TAG = "SdDataSourceAw";
    private final List<AndroidSensor> mActiveSensors = new ArrayList<>();
    private final Random mRandom = new Random();
    
    // Static objects to prevent allocation in hot paths
    private final IntentFilter mBatteryFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
    private final String mPkgName;

    private final Handler mClockHandler = new Handler(Looper.getMainLooper());
    private final Runnable mClockRunnable = new Runnable() {
        @Override
        public void run() {
            if (isEmulator()) simulateData();
            triggerUiUpdate(); 
            mClockHandler.postDelayed(this, 5000); 
        }
    };

    private final BroadcastReceiver mBatteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) { updateBatteryStatus(intent); }
    };

    public SdDataSourceAw(Context context, Handler handler, SdDataReceiver sdDataReceiver) {
        super(context, handler, sdDataReceiver);
        this.mPkgName = context.getPackageName();
        mName = "WearOS_DataSource";
        initialiseHardware(context);
    }

    private boolean isEmulator() { return Build.FINGERPRINT.startsWith("generic") || Build.MODEL.contains("Emulator"); }

    private void updateBatteryStatus(Intent intent) {
        if (intent == null) return;
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        int temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
        
        if (level != -1 && scale != -1) {
            mSdData.batteryPc = (long) ((level / (float) scale) * 100);
        }
        
        if (temp != -1) {
            mSdData.batteryTemp = temp / 10.0f; // Battery temperature is in tenths of a degree Celsius
        }
        
        mSdData.mIsCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                             status == BatteryManager.BATTERY_STATUS_FULL;
    }

    private void simulateData() {
        if (mSdData.mHR <= 0) mSdData.mHR = 72.0;
        mSdData.mHR += (mRandom.nextInt(3) - 1);
        mSdData.haveData = true;
    }

    private void initialiseHardware(Context context) {
        mActiveSensors.clear();
        // Use fixed intervals to avoid redundant math in loop
        mActiveSensors.add(new AccelerationSensor(context, 40000, 0) {
            @Override public void onSensorChanged(SensorEvent event) { accelerationEvent(event); }
        });
        mActiveSensors.add(new HeartRateSensor(context, 1000000, 0) {
            @Override public void onSensorChanged(SensorEvent event) { heartRateEvent(event); }
        });
        
        // Architecture Addition: Ambient Temperature for forensic heat-mapping
        mActiveSensors.add(new AmbientTemperatureSensor(context, 10000000, 0) {
            @Override public void onSensorChanged(SensorEvent event) {
                if (event.values.length > 0) mSdData.ambientTemp = event.values[0];
            }
        });
        
        // Low Latency Off-Body Detect
        mActiveSensors.add(new OffBodyDetectSensor(context, 1000000, 0) {
            @Override public void onSensorChanged(SensorEvent event) {
                mSdData.mWatchOnBody = (event.values[0] != 0);
            }
        });
    }

    @Override
    public void start() {
        if (mIsRunning) return;
        super.start();
        mClockHandler.post(mClockRunnable);
        mContext.registerReceiver(mBatteryReceiver, mBatteryFilter);
        for (AndroidSensor sensor : mActiveSensors) sensor.startListening();
        Wearable.getCapabilityClient(mContext).addListener(this, Uri.parse("wear://"), CapabilityClient.FILTER_REACHABLE);
        Wearable.getMessageClient(mContext).addListener(this);
    }

    @Override
    public void stop() {
        mIsRunning = false;
        mClockHandler.removeCallbacks(mClockRunnable);
        try { mContext.unregisterReceiver(mBatteryReceiver); } catch (Exception e) {}
        for (AndroidSensor sensor : mActiveSensors) sensor.stopListening();
        Wearable.getCapabilityClient(mContext).removeListener(this);
        Wearable.getMessageClient(mContext).removeListener(this);
        super.stop();
    }

    @Override protected void getStatus() { triggerUiUpdate(); }
    @Override public void startPebbleApp() {}

    public void accelerationEvent(SensorEvent event) {
        // Zero-allocation magnitude calculation
        float vx = event.values[0];
        float vy = event.values[1];
        float vz = event.values[2];
        double magnitude = Math.sqrt(vx*vx + vy*vy + vz*vz);
        
        mSdData.rawData[mSdData.mNsamp % mSdData.rawData.length] = magnitude * 100;
        mSdData.mNsamp++;

        if (mSdData.mNsamp >= mSdData.mNsampDefault && mSdData.mNsamp % 25 == 0) {
            doAnalysis(); 
            // Architecture Rule: Conditional update only.
            // On Wear platform, we suppress UI updates for movement unless Alarm is active.
            if (mPkgName.contains(".aw")) {
                if (mSdData.alarmState >= 2) triggerUiUpdate();
            } else {
                triggerUiUpdate(); // Phone platform (Viewer) gets full visualization
            }
        }
    }

    public void heartRateEvent(SensorEvent event) {
        float hr = event.values[0];
        if (hr > 0) {
            mSdData.mHR = hr;
            mSdData.mHr = hr;
            mSdData.haveData = true;
            mSdData.mWatchOnBody = true; // Heart rate detected means watch is on body
            hrCheck();
            triggerUiUpdate(); // HR events are low frequency, updates allowed.
        } else {
            // If heart rate is 0, we don't immediately set off-body as sensors can be noisy
            // We rely on the dedicated Off-Body sensor where available.
        }
    }

    @Override public void onMessageReceived(@NonNull MessageEvent messageEvent) {}
    @Override public void onCapabilityChanged(@NonNull CapabilityInfo capabilityInfo) {}
    @Override public void onSensorChanged(SensorEvent event) {}
    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
}
