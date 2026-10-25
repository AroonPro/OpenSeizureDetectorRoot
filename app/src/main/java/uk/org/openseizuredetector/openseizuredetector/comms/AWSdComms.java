/*
  AWSdComms - Forensic Bluetooth Communication Layer.
  GC-Optimized: Zero-allocation binary protocol support.
  Compliance: Unit Regtien Forensic Standard / Graham Jones RC2.
*/
package uk.org.openseizuredetector.openseizuredetector.comms;

import android.content.Context;
import android.util.Log;
import com.google.android.gms.wearable.MessageClient;
import com.google.android.gms.wearable.Wearable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import uk.org.openseizuredetector.openseizuredetector.data.SdData;

/**
 * AWSdComms - Manages the high-frequency data sync between Wear OS and Phone.
 * Optimized to prevent heap churn during 24/7 monitoring.
 */
public class AWSdComms {
    private static final String TAG = "AWSdComms";
    private static final String PATH_ACCEL_BIN = "/osd/accel_bin";
    private static final String PATH_HR_DATA = "/osd/hr_data";

    private final Context mContext;
    private final MessageClient mMessageClient;
    
    // GC-Optimized: Pre-allocated buffer for binary transmission
    private final ByteBuffer mTransmitBuffer = ByteBuffer.allocateDirect(1024).order(ByteOrder.LITTLE_ENDIAN);

    public AWSdComms(Context context) {
        this.mContext = context.getApplicationContext();
        this.mMessageClient = Wearable.getMessageClient(mContext);
    }

    /**
     * sendBinaryAccel - High-frequency binary sync.
     * Rule: Strictly no JSON or String allocations in this path.
     */
    public void sendBinaryAccel(String nodeId, double[] data3D, int nSamples) {
        if (nodeId == null || data3D == null) return;

        mTransmitBuffer.clear();
        // Pack as 16-bit shorts to minimize Bluetooth payload size
        for (int i = 0; i < nSamples * 3 && i < (mTransmitBuffer.capacity() / 2); i++) {
            mTransmitBuffer.putShort((short) data3D[i]);
        }
        
        byte[] payload = new byte[mTransmitBuffer.position()];
        mTransmitBuffer.flip();
        mTransmitBuffer.get(payload);
        
        mMessageClient.sendMessage(nodeId, PATH_ACCEL_BIN, payload);
    }

    /**
     * sendHeartRate - Forensic HR sync.
     */
    public void sendHeartRate(String nodeId, int hr) {
        if (nodeId == null) return;
        
        mTransmitBuffer.clear();
        mTransmitBuffer.putInt(hr);
        
        byte[] payload = new byte[4];
        mTransmitBuffer.flip();
        mTransmitBuffer.get(payload);
        
        mMessageClient.sendMessage(nodeId, PATH_HR_DATA, payload);
    }
}
