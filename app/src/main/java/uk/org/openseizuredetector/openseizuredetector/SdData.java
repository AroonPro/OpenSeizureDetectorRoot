package uk.org.openseizuredetector.openseizuredetector;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/**
 * SdData - en_GB
 * Standardized Data Container fully compatible with Graham's Web Protocol.
 * Forensic accuracy with zero redundant allocation.
 */
public class SdData implements Parcelable {
    private final static String TAG = "SdData";
    
    public boolean mWatchOnBody = false;
    public boolean mIsCharging = false;
    public boolean mIsSleeping = false;
    public float batteryTemp = 0.0f;
    public float ambientTemp = 20.0f;

    public long batteryPc = -1;
    public int phoneBatteryPc = -1;
    public boolean watchConnected = false;
    public boolean watchAppRunning = false;
    public boolean serverOK = false;
    public boolean haveData = false;
    public boolean haveSettings = false;
    public boolean webServerAlive = false;

    public int alarmState = 0;
    public String alarmPhrase = "";
    public String alarmCause = "";
    public boolean alarmStanding = false;
    public boolean mLocalAlarmSuppressed = false;
    public short mMutePeriod = 0;
    public long lastUpdateMs = 0;

    public double mHR = 0, mHr = 0, mHrAvg = 0, mO2Sat = 0, mHrv = 0;
    public double mO2SatThreshMin = 90.0;
    
    // GPS Data
    public double latitude = 0;
    public double longitude = 0;

    // en_GB: Analytical fields for Graham's Web UI
    public long maxVal = 0;
    public long maxFreq = 0;
    public long specPower = 0;
    public long roiPower = 0;
    public long roiRatio = 0;
    public int[] simpleSpec = new int[10];
    
    // Sampling metadata
    public long mSampleFreq = 25;
    public int mNsamp = 0;
    public int mNsampDefault = 250;

    // en_GB: Settings expected by Web UI (OsdSettings wrapper)
    public int alarmFreqMin = 3;
    public int alarmFreqMax = 8;
    public int alarmThresh = 15;
    public int alarmRatioThresh = 4;
    public int warnTime = 10;
    public int alarmTime = 10;

    // Activity status
    public boolean isExerciseActive = false;
    public String exerciseType = "NONE";

    public String watchSerNo = "", watchSdName = "", watchPartNo = "";
    public double[] rawData = new double[250];
    public double[] rawData3D = new double[750];

    public SdData() { lastUpdateMs = System.currentTimeMillis(); }

    public JSONObject toJson() {
        JSONObject root = new JSONObject();
        JSONObject data = new JSONObject();
        try {
            data.put("batteryPc", batteryPc);
            data.put("alarmState", alarmState);
            data.put("hr", mHR);
            data.put("o2Sat", mO2Sat);
            data.put("serverOK", serverOK);
            data.put("webServerAlive", webServerAlive);
            data.put("mWatchOnBody", mWatchOnBody);
            data.put("mIsCharging", mIsCharging);
            data.put("mIsSleeping", mIsSleeping);
            data.put("batteryTemp", batteryTemp);
            data.put("ambientTemp", ambientTemp);
            data.put("roiPower", roiPower);
            data.put("roiRatio", roiRatio);
            data.put("specPower", specPower);
            data.put("maxVal", maxVal);
            data.put("maxFreq", maxFreq);
            data.put("alarmPhrase", alarmPhrase);
            data.put("isExerciseActive", isExerciseActive);
            data.put("exerciseType", exerciseType);
            data.put("latitude", latitude);
            data.put("longitude", longitude);
            
            JSONArray specArr = new JSONArray();
            if (simpleSpec != null) {
                for (int val : simpleSpec) specArr.put(val);
            }
            data.put("simpleSpec", specArr);
            
            // en_GB: Essential wrapper for Graham's live graph
            root.put("OsdData", data);
        } catch (JSONException e) { Log.e(TAG, "toJson Error: " + e.getMessage()); }
        return root;
    }

    public JSONObject toSettingsJSON() {
        JSONObject root = new JSONObject();
        JSONObject settings = new JSONObject();
        try {
            settings.put("alarmFreqMin", alarmFreqMin);
            settings.put("alarmFreqMax", alarmFreqMax);
            settings.put("alarmThresh", alarmThresh);
            settings.put("alarmRatioThresh", alarmRatioThresh);
            settings.put("warnTime", warnTime);
            settings.put("alarmTime", alarmTime);
            settings.put("batteryPc", batteryPc);
            
            // en_GB: Essential wrapper for Graham's settings UI
            root.put("OsdSettings", settings);
        } catch (JSONException e) { Log.e(TAG, "toSettingsJSON Error: " + e.getMessage()); }
        return root;
    }

    public String toDatapointJSON() {
        return toJson().toString();
    }

    public void updateFromJSON(JSONObject jo) {
        try {
            JSONObject data = jo.has("OsdData") ? jo.getJSONObject("OsdData") : jo;
            batteryPc = data.optLong("batteryPc", batteryPc);
            alarmState = data.optInt("alarmState", alarmState);
            mHR = data.optDouble("hr", mHR);
            mO2Sat = data.optDouble("o2Sat", mO2Sat);
            serverOK = data.optBoolean("serverOK", serverOK);
            webServerAlive = data.optBoolean("webServerAlive", webServerAlive);
            mWatchOnBody = data.optBoolean("mWatchOnBody", mWatchOnBody);
            mIsCharging = data.optBoolean("mIsCharging", mIsCharging);
            batteryTemp = (float) data.optDouble("batteryTemp", batteryTemp);
            ambientTemp = (float) data.optDouble("ambientTemp", ambientTemp);
            isExerciseActive = data.optBoolean("isExerciseActive", isExerciseActive);
            exerciseType = data.optString("exerciseType", exerciseType);
            latitude = data.optDouble("latitude", latitude);
            longitude = data.optDouble("longitude", longitude);
            haveData = true;
        } catch (Exception e) { Log.e(TAG, "updateFromJSON Error: " + e.getMessage()); }
    }

    public void fromJSON(String jsonStr) {
        try { updateFromJSON(new JSONObject(jsonStr)); }
        catch (JSONException e) { Log.e(TAG, "fromJSON Error: " + e.getMessage()); }
    }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(batteryPc);
        dest.writeDouble(mO2Sat);
        dest.writeDouble(mHR);
        dest.writeInt(alarmState);
        dest.writeString(alarmPhrase != null ? alarmPhrase : "");
        dest.writeInt(mWatchOnBody ? 1 : 0);
        dest.writeLong(mSampleFreq);
        dest.writeInt(mNsamp);
        dest.writeFloat(batteryTemp);
        dest.writeFloat(ambientTemp);
        dest.writeInt(isExerciseActive ? 1 : 0);
        dest.writeString(exerciseType != null ? exerciseType : "NONE");
        dest.writeDouble(latitude);
        dest.writeDouble(longitude);
    }

    protected SdData(Parcel in) {
        batteryPc = in.readLong();
        mO2Sat = in.readDouble();
        mHR = in.readDouble();
        alarmState = in.readInt();
        alarmPhrase = in.readString();
        mWatchOnBody = in.readInt() == 1;
        mSampleFreq = in.readLong();
        mNsamp = in.readInt();
        batteryTemp = in.readFloat();
        ambientTemp = in.readFloat();
        isExerciseActive = in.readInt() == 1;
        exerciseType = in.readString();
        latitude = in.readDouble();
        longitude = in.readDouble();
    }

    public static final Creator<SdData> CREATOR = new Creator<SdData>() {
        @Override public SdData createFromParcel(Parcel in) { return new SdData(in); }
        @Override public SdData[] newArray(int size) { return new SdData[size]; }
    };
}
