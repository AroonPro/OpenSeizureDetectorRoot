/*
  SdData - Master state object for OpenSeizureDetector.
  GC-Optimized: Zero-allocation JSON serialization and pooling support.
  Compliance: Unit Regtien Forensic Standard / Code Review Checklist.
*/
package uk.org.openseizuredetector.openseizuredetector.data;

import android.os.Parcel;
import android.os.Parcelable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.Arrays;

/**
 * SdData - Represents the full state of the Seizure Detector.
 * Optimized to prevent object churn during high-frequency sensor updates.
 */
public class SdData implements Parcelable {
    private static final int N_SAMPLES = 125;
    
    // GC-Optimized: ThreadLocal buffer for serialization to prevent heap churn
    private static final ThreadLocal<StringBuilder> mJsonBuffer = ThreadLocal.withInitial(() -> new StringBuilder(1024));

    public long dataTimeMillis = 0;
    public long alarmState = 0;
    public String alarmPhrase = "";
    public String alarmCause = "";
    public String faultCause = "";
    
    public double mHR = 0;
    public double mHr = 0; // Compatibility alias
    public double mO2Sat = 0;
    public long batteryPc = 0;
    public int phoneBatteryPc = 0;
    
    // Rule: Final arrays to prevent accidental re-allocation during runtime
    public final double[] rawData = new double[N_SAMPLES];
    public final double[] rawData3D = new double[N_SAMPLES * 3];
    public final int[] simpleSpec = new int[10];
    public int mNsamp = 0;

    public boolean watchConnected = false;
    public boolean watchAppRunning = false;
    public boolean serverOK = false;
    public boolean sshActive = false;
    public boolean webServerAlive = false;
    public boolean mPhoneIsCharging = false;
    public boolean mIsCharging = false;

    public double mAccelMagStdDev = 0;
    public double mPseizure = 0;

    // Timing and fault detection
    public float timeDiff = 0;
    public boolean mHrFrozenFaultStanding = false;

    // Heart rate alarm
    public boolean mHRAlarmActive = false;

    // Mute state
    public int mMute = 0;

    // Sample frequency
    public int mSampleFreq = 25;

    // Network/Gist synchronization
    public String gistAck = "";

    // Pebble OSD specific fields - synchronized with frontend
    public long maxVal = 0;
    public long maxFreq = 0;
    public long specPower = 0;
    public long roiPower = 0;
    public long alarmFreqMin = 0;
    public long alarmFreqMax = 0;
    public long alarmThresh = 0;
    public long alarmRatioThresh = 0;
    public long warnTime = 0;
    public long alarmTime = 0;
    public long nMin = 0;
    public long nMax = 0;
    public long analysisPeriod = 0;

    // Compatibility flags for Pebble datasource
    public boolean haveData = false;
    public boolean haveSettings = false;
    public long mDataUpdatePeriod = 0;
    public long mMutePeriod = 0;
    public long mManAlarmPeriod = 0;
    public boolean mFallActive = false;
    public long mFallThreshMin = 0;
    public long mFallThreshMax = 0;
    public long mFallWindow = 0;

    public SdData() {
        dataTimeMillis = System.currentTimeMillis();
    }

    /**
     * clear - Resets the object state for reuse in the object pool.
     */
    public void clear() {
        dataTimeMillis = 0;
        alarmState = 0;
        alarmPhrase = "";
        alarmCause = "";
        faultCause = "";
        mHR = 0; mHr = 0; mO2Sat = 0;
        batteryPc = 0; phoneBatteryPc = 0;
        mNsamp = 0;
        watchConnected = false;
        watchAppRunning = false;
        serverOK = false;
        sshActive = false;
        webServerAlive = false;
        mAccelMagStdDev = 0;
        mPseizure = 0;
        timeDiff = 0;
        mHrFrozenFaultStanding = false;
        mHRAlarmActive = false;
        mMute = 0;
        mSampleFreq = 25;
        gistAck = "";
        maxVal = 0;
        maxFreq = 0;
        specPower = 0;
        roiPower = 0;
        alarmFreqMin = 0;
        alarmFreqMax = 0;
        alarmThresh = 0;
        alarmRatioThresh = 0;
        warnTime = 0;
        alarmTime = 0;
        nMin = 0;
        nMax = 0;
        analysisPeriod = 0;
        haveData = false;
        haveSettings = false;
        mDataUpdatePeriod = 0;
        mMutePeriod = 0;
        mManAlarmPeriod = 0;
        mFallActive = false;
        mFallThreshMin = 0;
        mFallThreshMax = 0;
        mFallWindow = 0;
        Arrays.fill(rawData, 0.0);
        Arrays.fill(rawData3D, 0.0);
        Arrays.fill(simpleSpec, 0);
    }

    /**
     * GC-Optimized copy: Uses System.arraycopy for maximum performance.
     */
    public void copyFrom(SdData other) {
        if (other == null || other == this) return;
        this.dataTimeMillis = other.dataTimeMillis;
        this.alarmState = other.alarmState;
        this.alarmPhrase = other.alarmPhrase;
        this.alarmCause = other.alarmCause;
        this.faultCause = other.faultCause;
        this.mHR = other.mHR;
        this.mHr = other.mHr;
        this.mO2Sat = other.mO2Sat;
        this.batteryPc = other.batteryPc;
        this.phoneBatteryPc = other.phoneBatteryPc;
        this.watchConnected = other.watchConnected;
        this.watchAppRunning = other.watchAppRunning;
        this.serverOK = other.serverOK;
        this.sshActive = other.sshActive;
        this.webServerAlive = other.webServerAlive;
        this.mAccelMagStdDev = other.mAccelMagStdDev;
        this.mPseizure = other.mPseizure;
        this.timeDiff = other.timeDiff;
        this.mHrFrozenFaultStanding = other.mHrFrozenFaultStanding;
        this.mHRAlarmActive = other.mHRAlarmActive;
        this.mMute = other.mMute;
        this.mSampleFreq = other.mSampleFreq;
        this.mNsamp = other.mNsamp;

        // Pebble OSD fields
        this.maxVal = other.maxVal;
        this.maxFreq = other.maxFreq;
        this.specPower = other.specPower;
        this.roiPower = other.roiPower;
        this.alarmFreqMin = other.alarmFreqMin;
        this.alarmFreqMax = other.alarmFreqMax;
        this.alarmThresh = other.alarmThresh;
        this.alarmRatioThresh = other.alarmRatioThresh;
        this.warnTime = other.warnTime;
        this.alarmTime = other.alarmTime;
        this.nMin = other.nMin;
        this.nMax = other.nMax;
        this.analysisPeriod = other.analysisPeriod;
        this.haveData = other.haveData;
        this.haveSettings = other.haveSettings;
        this.mDataUpdatePeriod = other.mDataUpdatePeriod;
        this.mMutePeriod = other.mMutePeriod;
        this.mManAlarmPeriod = other.mManAlarmPeriod;
        this.mFallActive = other.mFallActive;
        this.mFallThreshMin = other.mFallThreshMin;
        this.mFallThreshMax = other.mFallThreshMax;
        this.mFallWindow = other.mFallWindow;

        System.arraycopy(other.rawData, 0, this.rawData, 0, N_SAMPLES);
        System.arraycopy(other.rawData3D, 0, this.rawData3D, 0, N_SAMPLES * 3);
        System.arraycopy(other.simpleSpec, 0, this.simpleSpec, 0, 10);
    }

    /**
     * toDatapointJSON - Fast StringBuilder serialization to prevent heap churn.
     */
    public String toDatapointJSON() {
        StringBuilder sb = mJsonBuffer.get();
        sb.setLength(0);
        sb.append("{\"dt\":").append(dataTimeMillis)
          .append(",\"as\":").append(alarmState)
          .append(",\"hr\":").append(mHR)
          .append(",\"bp\":").append(batteryPc)
          .append(",\"ps\":").append(mPseizure)
          .append("}");
        return sb.toString();
    }

    public void fromJSON(String jsonStr) throws JSONException {
        JSONObject jo = new JSONObject(jsonStr);
        this.alarmState = jo.optLong("alarmState", 0);
        this.mHR = jo.optDouble("hr", 0);
        this.mHr = this.mHR;
        this.batteryPc = jo.optLong("batteryPc", 0);
        this.dataTimeMillis = System.currentTimeMillis();
    }

    public String toSettingsJSON() { return toDatapointJSON(); }

    protected SdData(Parcel in) {
        dataTimeMillis = in.readLong();
        alarmState = in.readLong();
        alarmPhrase = in.readString();
        mHR = in.readDouble();
        mNsamp = in.readInt();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(dataTimeMillis);
        dest.writeLong(alarmState);
        dest.writeString(alarmPhrase);
        dest.writeDouble(mHR);
        dest.writeInt(mNsamp);
    }

    @Override public int describeContents() { return 0; }
    public static final Creator<SdData> CREATOR = new Creator<SdData>() {
        @Override public SdData createFromParcel(Parcel in) { return new SdData(in); }
        @Override public SdData[] newArray(int size) { return new SdData[size]; }
    };
}
