package uk.org.openseizuredetector.openseizuredetector.sensors;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.util.Log;

/**
 * OffBodyDetectSensor - Manages the hardware off-body detection sensor.
 * This sensor is optimized for Wear OS to detect if the device is being worn.
 * Logic: 1.0 = ON-BODY, 0.0 = OFF-BODY.
 */
public class OffBodyDetectSensor extends AndroidSensor {

    /**
     * Listener interface for off-body state changes.
     */
    public interface OffBodyListener {
        /**
         * Called when the on-body status changes.
         * @param onBody true if the watch is detected on the body.
         */
        void onOffBodyStateChanged(boolean onBody);
    }

    private static final String SENSOR_STRING = "android.hardware.sensor.low_latency_offbody_detect";
    private static final String LOG_REPORT_PREFIX = "Hardware Sensor Report: ";
    private static final String ON_BODY_STR = "ON-BODY";
    private static final String OFF_BODY_STR = "OFF-BODY";
    private static final float THRESHOLD_ON_BODY = 0.5f;

    private OffBodyListener mListener;

    /**
     * Initializes the off-body detector.
     * @param context Application context.
     * @param samplingPeriodUs Sampling period (0 for default).
     * @param maxLatencyUs Max latency (0 for default).
     */
    public OffBodyDetectSensor(Context context, int samplingPeriodUs, int maxLatencyUs) {
        super(context, SENSOR_STRING, 
              Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT, samplingPeriodUs, maxLatencyUs);
    }

    /**
     * Sets the listener for state changes.
     * @param listener The listener to notify.
     */
    public void setOffBodyListener(OffBodyListener listener) {
        this.mListener = listener;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT) {
            float val = event.values[0];
            // Hardware logic: values above 0.5 indicate device is being worn
            boolean onBody = (val > THRESHOLD_ON_BODY);
            
            Log.i(TAG, LOG_REPORT_PREFIX + (onBody ? ON_BODY_STR : OFF_BODY_STR) + " (Raw: " + val + ")");

            if (mListener != null) {
                mListener.onOffBodyStateChanged(onBody);
            }
        }
    }
}
