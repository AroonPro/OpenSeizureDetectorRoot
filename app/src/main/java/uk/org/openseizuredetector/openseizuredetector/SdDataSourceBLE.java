/*
  OpenSeizureDetector - SdDataSourceBLE.java
  Integral en_GB Version - BLE Stack with Kirk-Proof Error Handling
*/

package uk.org.openseizuredetector.openseizuredetector;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import co.beeline.android.bluetooth.currenttimeservice.CurrentTimeService;


/**
 * A data source that registers for BLE GATT notifications from a device and
 * waits to be notified of data being available.
 */
public class SdDataSourceBLE extends SdDataSource {
    private int MAX_RAW_DATA = 125;  // 5 seconds at 25 Hz.
    private String TAG = "SdDataSourceBLE";
    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    private int mConnectionState = STATE_DISCONNECTED;

    private int nRawData = 0;
    private double[] rawData = new double[MAX_RAW_DATA];
    private double[] rawData3d = new double[MAX_RAW_DATA * 3];
    private int mAccFmt = 0;
    private boolean waitForDescriptorWrite = false;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    public static String SERV_DEV_INFO = "0000180a-0000-1000-8000-00805f9b34fb";
    public static String SERV_HEART_RATE = "0000180d-0000-1000-8000-00805f9b34fb";
    public static String CHAR_HEART_RATE_MEASUREMENT = "00002a37-0000-1000-8000-00805f9b34fb";

    public static String SERV_OSD = "000085e9-0000-1000-8000-00805f9b34fb";
    public static String CHAR_OSD_ACC_DATA = "000085e9-0001-1000-8000-00805f9b34fb";
    public static String CHAR_OSD_BATT_DATA = "000085e9-0002-1000-8000-00805f9b34fb";
    public static String CHAR_OSD_WATCH_ID = "000085e9-0003-1000-8000-00805f9b34fb";
    public static String CHAR_OSD_WATCH_FW = "000085e9-0004-1000-8000-00805f9b34fb";
    public static String CHAR_OSD_ACC_FMT = "000085e9-0005-1000-8000-00805f9b34fb";
    // Valid values are 0: 8 bit vector magnitude scaled so 1g=44
    public final static int ACC_FMT_8BIT = 0;
    public final static int ACC_FMT_16BIT = 1;
    public final static int ACC_FMT_3D = 3;
    public static String CHAR_OSD_STATUS = "000085e9-0006-1000-8000-00805f9b34fb";

    public static String SERV_INFINITIME_MOTION = "00030000-78fc-48fe-8e23-433b3a1942d0";
    public static String CHAR_INFINITIME_ACC_DATA = "00030002-78fc-48fe-8e23-433b3a1942d0";
    public static String CHAR_INFINITIME_OSD_STATUS = "00030078-78fc-48fe-8e23-433b3a1942d0";

    public static String CHAR_BATT_DATA = "00002a19-0000-1000-8000-00805f9b34fb";
    public static String SERV_BATT = "0000180f-0000-1000-8000-00805f9b34fb";

    private BluetoothGatt mGatt;
    private BluetoothGattCharacteristic mOsdChar;
    private BluetoothGattCharacteristic mStatusChar;
    private String mBleDeviceAddr = "";
    private String mBleDeviceName = "";
    private boolean mWatchAppRunningCheck = false;
    private long mDataStatusTime = 0;


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

    public SdDataSourceBLE(Context context, Handler handler,
                           SdDataReceiver sdDataReceiver) {
        super(context, handler, sdDataReceiver);
        mName = "BLE";
    }


    /**
     * Start the datasource updating - initialises from sharedpreferences first to
     * make sure any changes to preferences are taken into account.
     */
    public void start() {
        super.start();
        Log.i(TAG, "start() - mBleDeviceAddr="+mBleDeviceAddr);
        mUtil.writeToSysLogFile("SdDataSourceBLE.start() - mBleDeviceAddr=" + mBleDeviceAddr);

        if (mBleDeviceAddr == null || mBleDeviceAddr.isEmpty()) {
            // FIXED: Using ACTION_OPEN_UI to trigger the correct UI sequence
            // for scanning and permissions without circular dependencies.
            Intent intent = new Intent("uk.org.openseizuredetector.ACTION_OPEN_UI");
            intent.setPackage(mContext.getPackageName());
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(intent);
        }

        // Note, these values are set in BleScanActivity and written to shared preferences, which
        // ae read in SdDataSource.java
        // FIXME:  Read the shared preferences in this class so SdDataSource does not need to know
        // FIXME:   about BLE details.
        Log.i(TAG, "mBLEDevice is " + mBleDeviceName + ", Addr=" + mBleDeviceAddr);
        mSdData.watchSdName = mBleDeviceName;
        mSdData.watchPartNo = mBleDeviceAddr;

        boolean success = CurrentTimeService.startServer(mContext);

        bleConnect();

    }

    /**
     *
     */
    @Override
    public void startPebbleApp() {

    }

    private void bleConnect() {
        mSdData.watchConnected = false;
        mSdData.watchAppRunning = false;
        mBluetoothGatt = null;
        mConnectionState = STATE_DISCONNECTED;
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "bleConnect(): Unable to initialize BluetoothManager.");
                return;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "bleConnect(): Unable to obtain a BluetoothAdapter.");
            return;
        }

        if (mBluetoothAdapter == null || mBleDeviceAddr == null) {
            Log.w(TAG, "bleConnect(): BluetoothAdapter not initialized or unspecified address.");
            return;
        }

        BluetoothDevice device;
        try {
            device = mBluetoothAdapter.getRemoteDevice(mBleDeviceAddr);
        } catch (Exception e) {
            Log.w(TAG, "bleConnect(): Error connecting to device address " + mBleDeviceAddr + ".");
            device = null;
        }
        if (device == null) {
            Log.w(TAG, "bleConnect(): Device not found.  Unable to connect.");
            return;
        } else {
            // We want to directly connect to the device, so we are setting the autoConnect
            // parameter to false.
            if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            mBluetoothGatt = device.connectGatt(mContext, true, mGattCallback);
            Log.d(TAG, "bleConnect(): Trying to create a new connection.");
            mBluetoothDeviceAddress = mBleDeviceAddr;
            mConnectionState = STATE_CONNECTING;
        }
    }

    private void bleDisconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        // Un-register for BLE Notifications.
        if (mOsdChar != null) {
            setCharacteristicNotification(mOsdChar, false);
        }

        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        mBluetoothGatt.disconnect();
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
        mSdData.watchAppRunning = false;
        mSdData.watchConnected = false;
        mConnectionState = STATE_DISCONNECTED;

    }

    /**
     * Stop the datasource from updating
     */
    public void stop() {
        Log.i(TAG, "stop()");
        mUtil.writeToSysLogFile("SDDataSourceBLE.stop()");

        bleDisconnect();
        CurrentTimeService.stopServer();
        super.stop();
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

    /**
     *
     */
    @Override
    protected void faultCheck() {

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


    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                mConnectionState = STATE_CONNECTED;
                mSdData.watchConnected = true;
                Log.i(TAG, "onConnectionStateChange(): Connected to GATT server.");
                // Attempts to discover services after successful connection.
                Log.i(TAG, "onConnectionStateChange(): Attempting to start service discovery:");
                if (mBluetoothGatt != null)
                    if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        // TODO: Consider calling
                        //    ActivityCompat#requestPermissions
                        // here to request the missing permissions, and then overriding
                        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                        //                                          int[] grantResults)
                        // to handle the case where the user grants the permission. See the documentation
                        // for ActivityCompat#requestPermissions for more details.
                        return;
                    }mBluetoothGatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                mConnectionState = STATE_DISCONNECTED;
                mSdData.watchConnected = false;
                Log.i(TAG, "onConnectionStateChange(): Disconnected from GATT server");
            }
        }
    };
    
    private void setCharacteristicNotification(BluetoothGattCharacteristic characteristic, boolean enabled) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);
    }

    /**
     * @param intent 
     * @return
     */
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
