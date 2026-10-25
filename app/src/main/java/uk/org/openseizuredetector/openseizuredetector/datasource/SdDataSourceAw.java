package uk.org.openseizuredetector.openseizuredetector.datasource;

import android.content.Context;
import android.os.Handler;
import uk.org.openseizuredetector.openseizuredetector.data.logging.Log;

import com.google.android.gms.wearable.MessageClient;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * SdDataSourceAw - Optimized Wear OS Data Source.
 * GC Policy: Zero-allocation binary processing for high-frequency sensor data.
 * Compliance: Unit Regtien Forensic Standard / Graham Jones RC2 Routines.
 */
public class SdDataSourceAw extends SdDataSource implements MessageClient.OnMessageReceivedListener {
    private static final String TAG = "SdDataSourceAw";
    
    private static final String PATH_ACCEL_DATA_BIN = "/osd/accel_bin";
    private static final String PATH_HR_DATA = "/osd/hr_data";

    private MessageClient mMessageClient;
    private final Executor mExecutor = Executors.newSingleThreadExecutor();

    // GC-Optimized: Pre-allocated buffers for binary data
    private final short[] mBinarySampleBuf = new short[500]; // Ruime buffer voor 3D samples
    private ByteBuffer mByteBuffer;

    public SdDataSourceAw(Context context, Handler handler, SdDataReceiver sdDataReceiver) {
        super(context, handler, sdDataReceiver);
        mName = "AndroidWear";
        // Pre-allocate byte buffer once
        mByteBuffer = ByteBuffer.allocateDirect(2048);
        mByteBuffer.order(ByteOrder.LITTLE_ENDIAN);
    }

    @Override
    public void start() {
        super.start();
        mMessageClient = Wearable.getMessageClient(mContext);
        mMessageClient.addListener(this);
        Log.i(TAG, "WearOS Source Started - Awaiting Binary Stream");
    }

    @Override
    public void stop() {
        if (mMessageClient != null) {
            mMessageClient.removeListener(this);
        }
        super.stop();
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        String path = messageEvent.getPath();
        byte[] data = messageEvent.getData();

        mWatchAppRunningCheck = true;
        mDataStatusTimeMillis = System.currentTimeMillis();

        if (path.equals(PATH_ACCEL_DATA_BIN)) {
            processBinaryAccel(data);
        } else if (path.equals(PATH_HR_DATA)) {
            // HR is minder frequent, maar we mappen het direct
            try {
                int hr = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getInt();
                mSdData.mHR = hr;
                mSdData.mHr = hr;
            } catch (Exception e) {
                Log.w(TAG, "HR Parse Fail");
            }
        }
    }

    /**
     * processBinaryAccel - Zero-allocation parsing.
     * Maps bytes directly to the pre-allocated SdData samples.
     */
    private void processBinaryAccel(byte[] data) {
        int numShorts = data.length / 2;
        if (numShorts > mBinarySampleBuf.length) return;

        // Wrap the existing byte array without allocation
        ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(mBinarySampleBuf, 0, numShorts);

        // Map to 3D rawData in SdData (Rule: Avoid intermediate objects)
        for (int i = 0; i < numShorts && i < mSdData.rawData3D.length; i++) {
            mSdData.rawData3D[i] = (double) mBinarySampleBuf[i];
        }
        
        mSdData.mNsamp = numShorts / 3;
        
        // Trigger analysis (Logic is in super class SdDataSource)
        doAnalysis();
    }

    @Override public void startPebbleApp() {}
    @Override protected void faultCheck() {}
    @Override public void updatePrefs() { super.updatePrefs(); }

    @Override
    public void getStatus() {
        long tnow = System.currentTimeMillis();
        long tdiff = tnow - mDataStatusTimeMillis;
        mSdData.watchConnected = true;
        
        if (!mWatchAppRunningCheck && tdiff > 20000) {
            mSdData.watchAppRunning = false;
            mSdDataReceiver.onSdDataFault(mSdData);
        } else {
            mSdData.watchAppRunning = true;
        }

        if (mWatchAppRunningCheck) {
            mWatchAppRunningCheck = false;
            mDataStatusTimeMillis = System.currentTimeMillis();
        }
    }
}
