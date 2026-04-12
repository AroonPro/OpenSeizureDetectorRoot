package uk.org.openseizuredetector.openseizuredetector;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/**
 * SdData - en_GB Standardized Data Container.
 * Fixed Parcellation order for battery and SpO2 updates.
 */
public class SdData implements Parcelable {
    private final static String TAG = "SdData";
    
    public boolean mWatchOnBody = false;
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
    public short mMutePeriod = 0;
    public long lastUpdateMs = 0;

    public double mHR = 0, mHr = 0, mHrAvg = 0, mO2Sat = 0, mHrv = 0;
    public double mO2SatThreshMin = 90.0;

    public long specPower, roiPower, roiRatio;
    public int mNsamp = 0, mNsampDefault = 125;
    public long mSampleFreq = 25;
    public double dT = 0;
    public double[] rawData = new double[250];
    public double[] rawData3D = new double[750];
    public int[] simpleSpec = new int[10];

    public double latitude = 0.0, longitude = 0.0;
    public boolean isExerciseActive = false;
    public String exerciseType = "NONE";
    public boolean isSleepMode = false;
    public String watchSerNo = "", watchSdName = "", watchPartNo = "";
    public List<Double> heartRates = new ArrayList<>();

    public SdData() { lastUpdateMs = System.currentTimeMillis(); }

    public boolean updateFromJSON(JSONObject jo) {
        try {
            batteryPc = jo.optLong("batteryPc", batteryPc);
            alarmState = jo.optInt("alarmState", alarmState);
            mHR = jo.optDouble("hr", mHR);
            mHr = mHR;
            mO2Sat = jo.optDouble("o2Sat", mO2Sat);
            isExerciseActive = jo.optBoolean("isExerciseActive", isExerciseActive);
            isSleepMode = jo.optBoolean("isSleepMode", isSleepMode);
            serverOK = jo.optBoolean("serverOK", serverOK);
            watchConnected = jo.optBoolean("watchConnected", watchConnected);
            webServerAlive = jo.optBoolean("webServerAlive", webServerAlive);
            haveData = true;
            return true;
        } catch (Exception e) { return false; }
    }

    public void fromJSON(String jsonStr) {
        try { updateFromJSON(new JSONObject(jsonStr)); }
        catch (JSONException e) { Log.e(TAG, "fromJSON Error: " + e.getMessage()); }
    }

    public JSONObject toJson() {
        JSONObject jo = new JSONObject();
        try {
            jo.put("batteryPc", batteryPc);
            jo.put("alarmState", alarmState);
            jo.put("hr", mHR);
            jo.put("o2Sat", mO2Sat);
            jo.put("serverOK", serverOK);
            jo.put("isExerciseActive", isExerciseActive);
            jo.put("webServerAlive", webServerAlive);
        } catch (JSONException e) {}
        return jo;
    }

    public String toDataString(boolean includeRaw) { return toJson().toString(); }
    public String toSettingsJSON() { return toJson().toString(); }
    public String toDatapointJSON() { return toJson().toString(); }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(batteryPc);              // 1
        dest.writeDouble(mO2Sat);               // 2
        dest.writeDouble(mHR);                  // 3
        dest.writeInt(alarmState);              // 4 - FIXED: writeInt instead of writeLong
        dest.writeString(alarmPhrase);          // 5
        dest.writeInt(isExerciseActive ? 1 : 0);// 6
        dest.writeInt(isSleepMode ? 1 : 0);     // 7
        dest.writeString(exerciseType);         // 8
        dest.writeInt(phoneBatteryPc);          // 9
        dest.writeInt(webServerAlive ? 1 : 0);  // 10
        dest.writeInt(watchConnected ? 1 : 0);  // 11
        dest.writeInt(serverOK ? 1 : 0);        // 12
        dest.writeDouble(latitude);             // 13
        dest.writeDouble(longitude);            // 14
        dest.writeString(alarmCause);           // 15
        dest.writeInt(mWatchOnBody ? 1 : 0);    // 16
        dest.writeLong(mSampleFreq);            // 17
        dest.writeInt(mNsamp);                  // 18
    }

    protected SdData(Parcel in) {
        batteryPc = in.readLong();              // 1
        mO2Sat = in.readDouble();               // 2
        mHR = in.readDouble();                  // 3
        alarmState = in.readInt();              // 4
        alarmPhrase = in.readString();          // 5
        isExerciseActive = in.readInt() == 1;   // 6
        isSleepMode = in.readInt() == 1;        // 7
        exerciseType = in.readString();         // 8
        phoneBatteryPc = in.readInt();          // 9
        webServerAlive = in.readInt() == 1;     // 10
        watchConnected = in.readInt() == 1;     // 11
        serverOK = in.readInt() == 1;           // 12
        latitude = in.readDouble();             // 13
        longitude = in.readDouble();            // 14
        alarmCause = in.readString();           // 15
        mWatchOnBody = in.readInt() == 1;       // 16
        mSampleFreq = in.readLong();            // 17
        mNsamp = in.readInt();                  // 18
    }

    public static final Creator<SdData> CREATOR = new Creator<SdData>() {
        @Override public SdData createFromParcel(Parcel in) { return new SdData(in); }
        @Override public SdData[] newArray(int size) { return new SdData[size]; }
    };
}
