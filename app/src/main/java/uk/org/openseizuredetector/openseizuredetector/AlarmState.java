package uk.org.openseizuredetector.openseizuredetector;

/**
 * AlarmState - Central definitions for OSD status levels.
 * Transposed from Source 48cc2332 & 6a44565d.
 */
public class AlarmState {
    public static final int OK = 0;
    public static final int WARNING = 1;
    public static final int ALARM = 2;
    public static final int FALL = 3;
    public static final int FAULT = 4;
    public static final int MANUAL = 5;
    public static final int MUTE = 6;
    public static final int NETFAULT = 7;
}
