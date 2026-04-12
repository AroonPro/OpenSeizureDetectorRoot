package uk.org.openseizuredetector.openseizuredetector;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import com.welie.blessed.BluetoothBytesParser;
import com.welie.blessed.BluetoothCentralManager;
import com.welie.blessed.BluetoothCentralManagerCallback;
import com.welie.blessed.BluetoothPeripheral;
import com.welie.blessed.BluetoothPeripheralCallback;
import com.welie.blessed.ConnectionPriority;
import com.welie.blessed.GattStatus;
import com.welie.blessed.HciStatus;
import com.welie.blessed.PhyOptions;
import com.welie.blessed.PhyType;
import com.welie.blessed.WriteType;

import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import co.beeline.android.bluetooth.currenttimeservice.CurrentTimeService;
import uk.org.openseizuredetector.openseizuredetector.*;


/**
 * A data source that registers for BLE GATT notifications from a device and
 * waits to be notified of data being available.
 * SdDataSourceBLE2 uses the BLESSED library for the BLE access rather than native Android
 * BLE methods to try to improve start-up/shutdown reliability.
 */
public class SdDataSourceBLE2 extends SdDataSource {
    private int MAX_RAW_DATA = 125;  // 5 seconds at 25 Hz.
    private String TAG = "SdDataSourceBLE2";
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
    public static String CHAR_DEV_MANUF = "00002a29-0000-1000-8000-00805f9b34fb";
    public static String CHAR_DEV_MODEL_NO = "00002a24-0000-1000-8000-00805f9b34fb";
    public static String CHAR_DEV_SER_NO = "00002a25-0000-1000-8000-00805f9b34fb";
    public static String CHAR_DEV_FW_VER = "00002a26-0000-1000-8000-00805f9b34fb";
    public static String CHAR_DEV_HW_VER = "00002a27-0000-1000-8000-00805f9b34fb";
    public static String CHAR_DEV_FW_NAME = "00002a28-0000-1000-8000-00805f9b34fb";
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

    // public static String CHAR_MANUF_NAME = "00002a29-0000-1000-8000-00805f9b34fb";
    // public static String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";
    private BluetoothGatt mGatt;
    private BluetoothGattCharacteristic mOsdChar;
    private BluetoothGattCharacteristic mStatusChar;
    BluetoothGattCharacteristic mHRChar;
    BluetoothGattCharacteristic mBattChar;
    private BluetoothCentralManager mBluetoothCentralManager;
    private boolean mShutdown = false;

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

    public SdDataSourceBLE2(Context context, Handler handler,
                            SdDataReceiver sdDataReceiver) {
        super(context, handler, sdDataReceiver);
        mName = "BLE2";
        }

    /* * en_GB Java Documentation:
     * These members store the Bluetooth Low Energy (BLE) identity.
     * mBleDeviceAddr: The MAC address (e.g., AA:BB:CC:DD:EE:FF).
     * mBleDeviceName: The human-readable name (e.g., 'Garmin Venu').
     * mWatchAppRunningCheck: Heartbeat monitor for the remote app.
     * * LSA Audit Significance:
     * These identify the exact hardware source of the seizure logs.
     */
    String mBleDeviceAddr = "";
    String mBleDeviceName = "";
    boolean mWatchAppRunningCheck = false;
    long mDataStatusTime = 0L; // SDK 17: Use long for system millis

    /**
     * Start the datasource updating - initialises from sharedpreferences first to
     * make sure any changes to preferences are taken into account.
     */
    public void start() {
        super.start();
        Log.i(TAG, "start() - mBleDeviceAddr="+mBleDeviceAddr);
        mUtil.writeToSysLogFile("SdDataSourceBLE.start() - mBleDeviceAddr=" + mBleDeviceAddr);

        if (mBleDeviceAddr == "" || mBleDeviceAddr == null) {
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
        //mSdData.watchSdName = mBleDeviceName;
        mSdData.watchSerNo = mBleDeviceAddr;

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

        // Create BluetoothCentral and receive callbacks on the main thread
        mBluetoothCentralManager = new BluetoothCentralManager(mContext,
                mBluetoothCentralManagerCallback,
                new Handler(Looper.getMainLooper())
        );
        // Look for the specified device
        Log.i(TAG,"bleConnect() - scanning for device: "+mBleDeviceAddr);
        mShutdown = false;
        mBluetoothCentralManager.scanForPeripheralsWithAddresses(new String[]{mBleDeviceAddr});
    }


    private final BluetoothCentralManagerCallback mBluetoothCentralManagerCallback = new BluetoothCentralManagerCallback() {
        @Override
        public void onDiscoveredPeripheral(BluetoothPeripheral peripheral, ScanResult scanResult) {
            Log.i(TAG,"BluetoothCentralManagerCallback.onDiscoveredPeripheral()");
            mBluetoothCentralManager.stopScan();
            mBluetoothCentralManager.autoConnectPeripheral(peripheral, peripheralCallback);
        }
        @Override
        public void onConnectedPeripheral(BluetoothPeripheral peripheral) {
            Log.i(TAG,"BluetoothCentralManagerCallback.onConnectedPeripheral()");
            mUtil.showToast("Watch Connected");
            super.onConnectedPeripheral(peripheral);
        }
        @Override
        public void onConnectionFailed(BluetoothPeripheral peripheral, HciStatus status) {
            Log.i(TAG,"BluetoothCentralManagerCallback.onConnectionFailed() - attempting to reconnect");
            mUtil.showToast("Failed to Connect to Watch - Retrying");
            mBluetoothCentralManager.autoConnectPeripheral(peripheral, peripheralCallback);
            super.onConnectionFailed(peripheral, status);
        }
        @Override
        public void onDisconnectedPeripheral(BluetoothPeripheral peripheral, HciStatus status) {
            if (mShutdown) {
                Log.i(TAG,"BluetoothCentralManagerCallback.onDisonnectedPeripheral - mShutdown is set, so not reconnecting");
            } else {
                Log.i(TAG,"BluetoothCentralManagerCallback.onDisonnectedPeripheral");
                mUtil.showToast("WATCH CONNECTION LOST");
                Log.i(TAG, "BluetoothCentralManagerCallback.onDisonnectedPeripheral - attempting to re-connect...");
                bleDisconnect();
                mShutdown=false;
                mBluetoothCentralManager.autoConnectPeripheral(peripheral, peripheralCallback);
            }
            super.onDisconnectedPeripheral(peripheral, status);
        }


    };

    private @NotNull BluetoothPeripheral mBlePeripheral;
    // Callback for peripherals
    private final BluetoothPeripheralCallback peripheralCallback = new BluetoothPeripheralCallback() {

        @Override // BluetoothPeripheralCallback
        public void onServicesDiscovered(@NotNull BluetoothPeripheral peripheral) {
            Log.i(TAG,"onServicesDiscovered()");
            mBlePeripheral = peripheral;
            // Request a higher MTU, iOS always asks for 185 - This is likely to have no effect, as Pinetime uses 23 bytes.
            Log.i(TAG,"onServicesDiscovered() - requesting higher MTU");
            peripheral.requestMtu(185);
            // Request a new connection priority
            Log.i(TAG,"onServicesDiscovered() - requesting high priority connection");
            peripheral.requestConnectionPriority(ConnectionPriority.HIGH);
            Log.i(TAG,"onServicesDiscovered() - requesting Long Range Bluetooth 5 connection");
            //peripheral.setPreferredPhy(PhyType.LE_2M, PhyType.LE_2M, PhyOptions.S2);
            // Request long range Bluetooth 5 connection if available.
            peripheral.setPreferredPhy(PhyType.LE_CODED, PhyType.LE_CODED, PhyOptions.S8);
            peripheral.readPhy();

            peripheral.readRemoteRssi();

            boolean foundOsdService = false;
            for (BluetoothGattService service : peripheral.getServices()) {
                String servUuidStr = service.getUuid().toString();
                Log.d(TAG, "found service: " + servUuidStr);
                if (servUuidStr.equals(SERV_OSD)) {
                    Log.v(TAG, "OpenSeizureDetector Service Discovered");
                    foundOsdService = true;
                } else if (servUuidStr.equals(SERV_INFINITIME_MOTION)) {
                    Log.v(TAG, "InfiniTime Motion Service Discovered");
                    foundOsdService = true;
                } else if (servUuidStr.equals(SERV_HEART_RATE)) {
                    Log.v(TAG, "Heart Rate Measurement Service Service Discovered");
                } else if (servUuidStr.equals(SERV_BATT)) {
                    Log.v(TAG, "Battery Data Service Service Discovered");
                } else if (servUuidStr.equals(SERV_DEV_INFO)) {
                    Log.v(TAG, "Device Information Service Service Discovered");
                }


            // Loop through the available characteristics...
                for (BluetoothGattCharacteristic gattCharacteristic : service.getCharacteristics()) {
                    String charUuidStr = gattCharacteristic.getUuid().toString();
                    Log.d(TAG, "  found characteristic: " + charUuidStr);
                }
            }
        }
    };

    private void bleDisconnect() {
        if (mBluetoothCentralManager != null) {
            mBluetoothCentralManager.stopScan();
        }
    }

    @Override
    public void stop() {
        mShutdown = true;
        bleDisconnect();
        super.stop();
    }

    @Override
    public void hrCheck() {}
    @Override
    public void o2SatCheck() {}
    @Override
    public void fallCheck() {}
    @Override
    public void muteCheck() {}
    @Override
    protected void getStatus() {}
    @Override
    protected void faultCheck() {}

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
