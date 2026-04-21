package uk.org.openseizuredetector.openseizuredetector;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/**
 * SdData - Standardized Data Container (Unit Regtien Protocol Optimized).
 * Forensic accuracy in parcellation; zero redundant allocation.
 */
public class SdData implements Parcelable {
    private final static String TAG = "SdData";
    
    public boolean mWatchOnBody = false;
    public boolean mIsCharging = false;
    public boolean mIsSleeping = false;
    public float batteryTemp = 0.0f;
    public float ambientTemp = 20.0f; // Default assumption

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

    public long specPower, roiPower, roiRatio;
    public int mNsamp = 0, mNsampDefault = 125;
    public long mSampleFreq = 25;
    public double latitude = 0.0, longitude = 0.0;
    public boolean isExerciseActive = false;
    public String exerciseType = "NONE";

    // Re-adding missing fields for Legacy/Web support
    public String watchSerNo = "", watchSdName = "", watchPartNo = "";
    public int[] simpleSpec = new int[10];
    public double[] rawData = new double[250];
    public double[] rawData3D = new double[750];

    public SdData() { lastUpdateMs = System.currentTimeMillis(); }

    public boolean updateFromJSON(JSONObject jo) {
        try {
            batteryPc = jo.optLong("batteryPc", batteryPc);
            alarmState = jo.optInt("alarmState", alarmState);
            mHR = jo.optDouble("hr", mHR);
            mHr = mHR;
            mO2Sat = jo.optDouble("o2Sat", mO2Sat);
            serverOK = jo.optBoolean("serverOK", serverOK);
            webServerAlive = jo.optBoolean("webServerAlive", webServerAlive);
            
            mWatchOnBody = jo.optBoolean("mWatchOnBody", mWatchOnBody);
            mIsCharging = jo.optBoolean("mIsCharging", mIsCharging);
            mIsSleeping = jo.optBoolean("mIsSleeping", mIsSleeping);
            batteryTemp = (float) jo.optDouble("batteryTemp", batteryTemp);
            ambientTemp = (float) jo.optDouble("ambientTemp", ambientTemp);
            mLocalAlarmSuppressed = jo.optBoolean("mLocalAlarmSuppressed", mLocalAlarmSuppressed);
            
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
            jo.put("webServerAlive", webServerAlive);
            jo.put("mWatchOnBody", mWatchOnBody);
            jo.put("mIsCharging", mIsCharging);
            jo.put("mIsSleeping", mIsSleeping);
            jo.put("batteryTemp", batteryTemp);
            jo.put("ambientTemp", ambientTemp);
            jo.put("mLocalAlarmSuppressed", mLocalAlarmSuppressed);
        } catch (JSONException e) {}
        return jo;
    }

    public String toSettingsJSON() { return toJson().toString(); }
    public String toDatapointJSON() { return toJson().toString(); }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(batteryPc);              // 1
        dest.writeDouble(mO2Sat);               // 2
        dest.writeDouble(mHR);                  // 3
        dest.writeInt(alarmState);              // 4
        dest.writeString(alarmPhrase != null ? alarmPhrase : ""); // 5
        dest.writeInt(isExerciseActive ? 1 : 0);// 6
        dest.writeString(exerciseType != null ? exerciseType : "NONE"); // 7
        dest.writeInt(phoneBatteryPc);          // 8
        dest.writeInt(webServerAlive ? 1 : 0);  // 9
        dest.writeInt(watchConnected ? 1 : 0);  // 10
        dest.writeInt(serverOK ? 1 : 0);        // 11
        dest.writeDouble(latitude);             // 12
        dest.writeDouble(longitude);            // 13
        dest.writeInt(mWatchOnBody ? 1 : 0);    // 14
        dest.writeLong(mSampleFreq);            // 15
        dest.writeInt(mNsamp);                  // 16
        dest.writeString(watchSerNo);           // 17
        dest.writeString(watchSdName);          // 18
        dest.writeString(watchPartNo);          // 19
        dest.writeInt(mIsCharging ? 1 : 0);     // 20
        dest.writeInt(mIsSleeping ? 1 : 0);     // 21
        dest.writeFloat(batteryTemp);           // 22
        dest.writeFloat(ambientTemp);           // 23
        dest.writeInt(mLocalAlarmSuppressed ? 1 : 0); // 24
    }

    protected SdData(Parcel in) {
        batteryPc = in.readLong();              // 1
        mO2Sat = in.readDouble();               // 2
        mHR = in.readDouble();                  // 3
        alarmState = in.readInt();              // 4
        alarmPhrase = in.readString();          // 5
        isExerciseActive = in.readInt() == 1;   // 6
        exerciseType = in.readString();         // 7
        phoneBatteryPc = in.readInt();          // 8
        webServerAlive = in.readInt() == 1;     // 9
        watchConnected = in.readInt() == 1;     // 10
        serverOK = in.readInt() == 1;           // 11
        latitude = in.readDouble();             // 12
        longitude = in.readDouble();            // 13
        mWatchOnBody = in.readInt() == 1;       // 14
        mSampleFreq = in.readLong();            // 15
        mNsamp = in.readInt();                  // 16
        watchSerNo = in.readString();           // 17
        watchSdName = in.readString();          // 18
        watchPartNo = in.readString();          // 19
        mIsCharging = in.readInt() == 1;        // 20
        mIsSleeping = in.readInt() == 1;        // 21
        batteryTemp = in.readFloat();           // 22
        ambientTemp = in.readFloat();           // 23
        mLocalAlarmSuppressed = in.readInt() == 1; // 24
    }

    public static final Creator<SdData> CREATOR = new Creator<SdData>() {
        @Override public SdData createFromParcel(Parcel in) { return new SdData(in); }
        @Override public SdData[] newArray(int size) { return new SdData[size]; }
    };
}
