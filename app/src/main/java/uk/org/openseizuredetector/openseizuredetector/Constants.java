package uk.org.openseizuredetector.openseizuredetector;

import android.net.Uri;

public class Constants {
    public interface GLOBAL_CONSTANTS {
        public final int ALARMS_OFF = 6;
        public final int ALARMS_ON = 0;
        // OSD Architecture Fix: mAppPackageName is now handled dynamically via OsdUtil.getPackageName()
        // to support multi-module deployments (Phone/Wear/Regtien).
    }

    public interface ACTION {
        public static String STARTFOREGROUND_ACTION = "uk.org.openseizuredetector.startforeground";
        public static String STOPFOREGROUND_ACTION = "uk.org.openseizuredetector.stopforeground";
        public static String BATTERYUPDATE_ACTION = "uk.org.openseizuredetector.onBatteryUpdate";
        public static String CONNECTIONUPDATE_ACTION = "uk.org.openseizuredetector.onConnectionUpdate";
    }

    public interface TAGS {
        public static String AWSDService = "AWSdService";
    }

    public interface SD_SERVICE_CONSTANTS {
        public static short defaultSampleRate = 25;
        public static short defaultSampleTime = 10; // 10 seconds for stable FFT
        public static short defaultSampleCount = (short)(defaultSampleRate * defaultSampleTime);
    }
}
