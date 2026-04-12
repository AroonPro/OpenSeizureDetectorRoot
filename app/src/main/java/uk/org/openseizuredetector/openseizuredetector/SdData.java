/*
  OpenSeizureDetector - SdData.java
  Integral en_GB Version - The Central Data Container
*/

package uk.org.openseizuredetector.openseizuredetector;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.format.Time;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class SdData implements Parcelable {
    public boolean mWatchOnBody = false;
    private final static String TAG = "SdData";
    private final static int N_RAW_DATA = 250;

    public boolean mOsdAlarmActive = true;
    public boolean mCnnAlarmActive = false;

    public String phoneAppVersion = "";
    public boolean haveSettings = false;
    public boolean haveData = false;
    public short mDataUpdatePeriod;
    public short mMutePeriod;
    public short mManAlarmPeriod;
    public boolean mFallActive;
    public short mFallThreshMin;
    public short mFallThreshMax;
    public short mFallWindow;
    public long mSdMode;
    public long mSampleFreq = 25;
    public long analysisPeriod = 5;
    public long alarmFreqMin = 3;
    public long alarmFreqMax = 8;
    public long alarmThresh = 100;
    public long alarmRatioThresh = 15;
    public long alarmTime = 10;
    
    public long batteryPc = -1;
    public int phoneBatteryPc = -1;

    public boolean mHRAlarmActive = false;
    public double mHRThreshMin = 40.0;
    public double mHRThreshMax = 150.0;
    public boolean mO2SatAlarmActive = false;
    public double mO2SatThreshMin = 80.0;
    public boolean mHrAlarmActive = false;
    public boolean mHrAlarmStanding = false;

    public String phoneName = "";

    public double[] rawData;
    public double[] rawData3D;
    public int[] simpleSpec;
    public int mNsamp = 0;
    public int mNsampDefault = 125;

    public Time dataTime = null;
    public float timeDiff = 0f;
    public long alarmState;
    public String alarmPhrase = "";
    public String alarmCause = "";
    public boolean alarmStanding = false;
    public boolean watchConnected = false;
    public boolean watchAppRunning = false;
    public boolean serverOK = false;

    public double mHR = 0;
    public double mHr = 0;
    public double mHrAvg = 0;
    public double mO2Sat = 0;
    public long specPower, roiPower, roiRatio;
    public long lastUpdateMs = 0;

    public String watchSdName = ""; 
    public String watchPartNo = ""; 
    public String watchFwVersion = "";
    public String watchSdVersion = "";
    public String watchSerNo = "";
    public double dT = 0;
    public String mDataType = "";

    public double latitude = 0.0;
    public double longitude = 0.0;

    public CircBuf watchBattBuff = new CircBuf(24 * 3600 / 5, -1);
    public CircBuf mHistoricHrBuf = new CircBuf(100, 0);
    public List<Double> heartRates = new ArrayList<>();

    public SdData() {
        simpleSpec = new int[10];
        rawData = new double[N_RAW_DATA];
        rawData3D = new double[N_RAW_DATA * 3];
        dataTime = new Time(Time.getCurrentTimezone());
        dataTime.setToNow();
        lastUpdateMs = System.currentTimeMillis();
    }

    public boolean fromJSON(String jsonStr) {
        try {
            JSONObject jo = new JSONObject(jsonStr);
            return updateFromJSON(jo);
        } catch (Exception e) {
            Log.e(TAG, "fromJSON Error: " + e.getMessage());
            return false;
        }
    }

    public boolean updateFromJSON(JSONObject jo) {
        try {
            dataTime.setToNow();
            this.lastUpdateMs = System.currentTimeMillis();
            specPower = jo.optLong("specPower", specPower);
            roiPower = jo.optLong("roiPower", roiPower);
            roiRatio = jo.optLong("roiRatio", roiRatio);
            batteryPc = jo.optInt("batteryPc", (int)batteryPc);
            alarmState = jo.optInt("alarmState", (int)alarmState);
            alarmPhrase = jo.optString("alarmPhrase", alarmPhrase);
            mHR = jo.optDouble("hr", mHR);
            mHr = mHR;
            mO2Sat = jo.optDouble("o2Sat", mO2Sat);
            latitude = jo.optDouble("lat", latitude);
            longitude = jo.optDouble("lon", longitude);
            watchSerNo = jo.optString("watchSerNo", watchSerNo);
            haveData = true;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public JSONObject toJson() {
        JSONObject jo = new JSONObject();
        try {
            jo.put("batteryPc", batteryPc);
            jo.put("alarmState", alarmState);
            jo.put("hr", mHR);
            jo.put("o2Sat", mO2Sat);
            jo.put("serverOK", serverOK);
            jo.put("lat", latitude);
            jo.put("lon", longitude);
            jo.put("watchSerNo", watchSerNo);
        } catch (JSONException e) {}
        return jo;
    }

    public String toDatapointJSON() {
        return toJson().toString();
    }

    public String toSettingsJSON() {
        return toJson().toString();
    }

    public String toDataString(boolean includeRaw) {
        return toSettingsJSON();
    }

    public String toHeartRatesArrayString() {
        JSONArray ja = new JSONArray();
        for (Double hr : heartRates) ja.put(hr);
        return ja.toString();
    }

    @Override
    public int describeContents() { return 0; }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(alarmState);
        dest.writeDouble(mHR);
        dest.writeLong(batteryPc);
        dest.writeDouble(latitude);
        dest.writeDouble(longitude);
        dest.writeString(watchSerNo);
    }

    protected SdData(Parcel in) {
        alarmState = in.readLong();
        mHR = in.readDouble();
        batteryPc = in.readLong();
        latitude = in.readDouble();
        longitude = in.readDouble();
        watchSerNo = in.readString();
    }

    public static final Parcelable.Creator<SdData> CREATOR = new Parcelable.Creator<SdData>() {
        @Override
        public SdData createFromParcel(Parcel in) { return new SdData(in); }
        @Override
        public SdData[] newArray(int size) { return new SdData[size]; }
    };
}
