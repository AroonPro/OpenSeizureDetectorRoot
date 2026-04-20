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
 * SdDataSourceAw - Optimized for Wear OS.
 * Structural Change: No screen updates on accelerometer movement unless in alarm state.
 */
public class SdDataSourceAw extends SdDataSource implements 
        SensorEventListener, 
        MessageClient.OnMessageReceivedListener, 
        CapabilityClient.OnCapabilityChangedListener {
        
    private final String TAG = "SdDataSourceAw";
    private final List<AndroidSensor> mActiveSensors = new ArrayList<>();
    private final Random mRandom = new Random();

    private final Handler mClockHandler = new Handler(Looper.getMainLooper());
    private final Runnable mClockRunnable = new Runnable() {
        @Override
        public void run() {
            if (isEmulator()) simulateData();
            // Periodic update for clock/battery (low frequency)
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
        mName = "WearOS_DataSource";
        initialiseHardware(context);
    }

    private boolean isEmulator() { return Build.FINGERPRINT.startsWith("generic") || Build.MODEL.contains("Emulator"); }

    private void updateBatteryStatus(Intent intent) {
        if (intent == null) return;
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        if (level != -1 && scale != -1) {
            mSdData.batteryPc = (long) ((level / (float) scale) * 100);
        }
    }

    @Override
    protected void triggerUiUpdate() {
        if (mSdDataReceiver != null) {
            mSdDataReceiver.onSdDataReceived(mSdData);
        }
    }

    private void simulateData() {
        if (mSdData.mHR <= 0) mSdData.mHR = 72.0;
        mSdData.mHR += (mRandom.nextInt(3) - 1);
        mSdData.haveData = true;
    }

    private void initialiseHardware(Context context) {
        mActiveSensors.clear();
        AccelerationSensor accel = new AccelerationSensor(context, 40000, 0) {
            @Override public void onSensorChanged(SensorEvent event) { accelerationEvent(event); }
        };
        if (accel.getDoesSensorExist()) mActiveSensors.add(accel);

        HeartRateSensor hr = new HeartRateSensor(context, 1000000, 0) {
            @Override public void onSensorChanged(SensorEvent event) { heartRateEvent(event); }
        };
        if (hr.getDoesSensorExist()) mActiveSensors.add(hr);
    }

    @Override
    public void start() {
        if (mIsRunning) return;
        super.start();
        mClockHandler.post(mClockRunnable);
        Intent batteryIntent = mContext.registerReceiver(mBatteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        updateBatteryStatus(batteryIntent);
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
        double magnitude = Math.sqrt(event.values[0]*event.values[0] + event.values[1]*event.values[1] + event.values[2]*event.values[2]);
        mSdData.rawData[mSdData.mNsamp % mSdData.rawData.length] = magnitude * 100;
        mSdData.mNsamp++;

        // Detectie loopt altijd door
        if (mSdData.mNsamp >= mSdData.mNsampDefault && mSdData.mNsamp % 25 == 0) {
            doAnalysis(); 
        }

        // STRUCTURAL FIX: No UI update on movement unless in alarm state.
        if (mSdData.alarmState >= 2 && (mSdData.mNsamp % 25 == 0)) {
            triggerUiUpdate();
        }
    }

    public void heartRateEvent(SensorEvent event) {
        float hr = event.values[0];
        if (hr > 0) {
            mSdData.mHR = hr;
            mSdData.mHr = hr;
            mSdData.haveData = true;
            hrCheck();
            triggerUiUpdate(); // HR updates allowed as they are low frequency
        }
    }

    @Override public void onMessageReceived(@NonNull MessageEvent messageEvent) {}
    @Override public void onCapabilityChanged(@NonNull CapabilityInfo capabilityInfo) {}
    @Override public void onSensorChanged(SensorEvent event) {}
    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
}
