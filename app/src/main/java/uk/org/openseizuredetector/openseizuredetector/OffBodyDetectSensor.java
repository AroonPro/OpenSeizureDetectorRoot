package uk.org.openseizuredetector.openseizuredetector;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.util.Log;

/**
 * OffBodyDetectSensor - Optimized for Wear OS.
 * Logic: 1.0 = ON-BODY, 0.0 = OFF-BODY.
 */
public class OffBodyDetectSensor extends AndroidSensor {
    public interface OffBodyListener {
        void onOffBodyStateChanged(boolean onBody);
    }

    private static final String SENSOR_STRING = "android.hardware.sensor.low_latency_offbody_detect";
    private static final String LOG_REPORT_PREFIX = "Hardware Sensor Report: ";
    private static final String ON_BODY_STR = "ON-BODY";
    private static final String OFF_BODY_STR = "OFF-BODY";

    private OffBodyListener mListener;

    public OffBodyDetectSensor(Context context, int samplingPeriodUs, int maxLatencyUs) {
        super(context, SENSOR_STRING, 
              Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT, samplingPeriodUs, maxLatencyUs);
    }

    public void setOffBodyListener(OffBodyListener listener) {
        this.mListener = listener;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT) {
            float val = event.values[0];
            // Hardware fix: val > 0.5 is ON-BODY
            boolean onBody = (val > 0.5f);
            
            Log.i(TAG, LOG_REPORT_PREFIX + (onBody ? ON_BODY_STR : OFF_BODY_STR) + " (Raw: " + val + ")");

            if (mListener != null) {
                mListener.onOffBodyStateChanged(onBody);
            }
        }
    }
}
