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
 * Protocol #osd_260426: Enhanced accelerometer responsiveness.
 */
public class SdDataSourceAw extends SdDataSource implements 
        SensorEventListener, 
        MessageClient.OnMessageReceivedListener, 
        CapabilityClient.OnCapabilityChangedListener {
        
    private final String TAG = "SdDataSourceAw";
    private final List<AndroidSensor> mActiveSensors = new ArrayList<>();
    private final Random mRandom = new Random();
    
    private final IntentFilter mBatteryFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
    private final String mPkgName;

    private final Handler mClockHandler = new Handler(Looper.getMainLooper());
    private final Runnable mClockRunnable = new Runnable() {
        @Override
        public void run() {
            if (isEmulator()) simulateData();
            
            SdServer server = OsdUtil.useSdServerBinding();
            if (mSdData != null) {
                mSdData.webServerAlive = (server != null && server.webServer != null);
            }

            triggerUiUpdate(); 
            mClockHandler.postDelayed(this, 5000); // Back to 5s for better status visibility
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
            mSdData.batteryTemp = temp / 10.0f;
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
        // #osd_260426: Restore accelerometer sampling rate for higher sensitivity
        mActiveSensors.add(new AccelerationSensor(context, 40000, 1) { // 25Hz, high priority
            @Override public void onSensorChanged(SensorEvent event) { accelerationEvent(event); }
        });
        mActiveSensors.add(new HeartRateSensor(context, 1000000, 0) { // 1s HR poll
            @Override public void onSensorChanged(SensorEvent event) { heartRateEvent(event); }
        });
        
        mActiveSensors.add(new AmbientTemperatureSensor(context, 10000000, 0) {
            @Override public void onSensorChanged(SensorEvent event) {
                if (event.values.length > 0) mSdData.ambientTemp = event.values[0];
            }
        });
        
        mActiveSensors.add(new OffBodyDetectSensor(context, 1000000, 0) { // 1s check
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
        // Enforce update prefs for fitness profile changes
        updatePrefs();

        float vx = event.values[0];
        float vy = event.values[1];
        float vz = event.values[2];
        double magnitude = Math.sqrt(vx*vx + vy*vy + vz*vz);
        
        mAccelBuffer.add(magnitude * 100);
        mSdData.mNsamp++;

        // Analyze every 25 samples (~1 second at 25Hz) for lower latency detection
        if (mSdData.mNsamp >= mSdData.mNsampDefault && mSdData.mNsamp % 25 == 0) {
            doAnalysis(); 
            // Aways trigger UI update if anything but OK state
            if (mSdData.alarmState > 0) {
                triggerUiUpdate();
            }
        }
    }

    public void heartRateEvent(SensorEvent event) {
        float hr = event.values[0];
        if (hr > 0) {
            mSdData.mHR = hr;
            mSdData.mHr = hr;
            mSdData.haveData = true;
            mSdData.mWatchOnBody = true;
            hrCheck();
            triggerUiUpdate();
        }
    }

    @Override public void onMessageReceived(@NonNull MessageEvent messageEvent) {}
    @Override public void onCapabilityChanged(@NonNull CapabilityInfo capabilityInfo) {}
    @Override public void onSensorChanged(SensorEvent event) {
        // Delegate based on sensor type if needed, but hardware init uses anonymous classes
    }
    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
}
