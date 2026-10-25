package uk.org.openseizuredetector.openseizuredetector.data;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Profile - Represents a set of seizure detection parameters for all active algorithms.
 * Allows switching between Standard TC, Hyperarousal, and custom modes dynamically.
 */
public class Profile {
    public String name = "";
    public String description = "";
    
    // OSD Deterministic
    public boolean osdAlarmActive = true;
    public long alarmThresh = 500;
    public long alarmRatioThresh = 57;
    public long alarmFreqMin = 3;
    public long alarmFreqMax = 8;
    
    // Timings
    public long warnTime = 5;
    public long alarmTime = 15;
    
    // Machine Learning / CNN
    public boolean cnnAlarmActive = false;
    public long mlAccelStdThresh = 5;
    public long mlProbThresh = 50;
    
    // Other Alarms
    public boolean flapAlarmActive = false;
    public boolean hrAlarmActive = false;
    public boolean fallActive = false;

    public Profile() {}

    public Profile(String name, long alarmThresh, long alarmRatioThresh) {
        this.name = name;
        this.alarmThresh = alarmThresh;
        this.alarmRatioThresh = alarmRatioThresh;
    }

    /**
     * Serializes the profile to a JSON object for storage or Gist transfer.
     */
    public JSONObject toJson() throws JSONException {
        JSONObject jo = new JSONObject();
        jo.put("name", name);
        jo.put("description", description);
        jo.put("osdAlarmActive", osdAlarmActive);
        jo.put("alarmThresh", alarmThresh);
        jo.put("alarmRatioThresh", alarmRatioThresh);
        jo.put("alarmFreqMin", alarmFreqMin);
        jo.put("alarmFreqMax", alarmFreqMax);
        jo.put("warnTime", warnTime);
        jo.put("alarmTime", alarmTime);
        jo.put("cnnAlarmActive", cnnAlarmActive);
        jo.put("mlAccelStdThresh", mlAccelStdThresh);
        jo.put("mlProbThresh", mlProbThresh);
        jo.put("flapAlarmActive", flapAlarmActive);
        jo.put("hrAlarmActive", hrAlarmActive);
        jo.put("fallActive", fallActive);
        return jo;
    }

    /**
     * Creates a Profile object from a JSON representation.
     */
    public static Profile fromJson(JSONObject jo) {
        Profile p = new Profile();
        p.name = jo.optString("name", "");
        p.description = jo.optString("description", "");
        p.osdAlarmActive = jo.optBoolean("osdAlarmActive", true);
        p.alarmThresh = jo.optLong("alarmThresh", 500);
        p.alarmRatioThresh = jo.optLong("alarmRatioThresh", 57);
        p.alarmFreqMin = jo.optLong("alarmFreqMin", 3);
        p.alarmFreqMax = jo.optLong("alarmFreqMax", 8);
        p.warnTime = jo.optLong("warnTime", 5);
        p.alarmTime = jo.optLong("alarmTime", 15);
        p.cnnAlarmActive = jo.optBoolean("cnnAlarmActive", false);
        p.mlAccelStdThresh = jo.optLong("mlAccelStdThresh", 5);
        p.mlProbThresh = jo.optLong("mlProbThresh", 50);
        p.flapAlarmActive = jo.optBoolean("flapAlarmActive", false);
        p.hrAlarmActive = jo.optBoolean("hrAlarmActive", false);
        p.fallActive = jo.optBoolean("fallActive", false);
        return p;
    }
}
