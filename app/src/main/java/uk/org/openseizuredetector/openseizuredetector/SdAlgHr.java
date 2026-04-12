package uk.org.openseizuredetector.openseizuredetector;

import android.app.Notification;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.util.Log;

import android.preference.PreferenceManager;

import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.utils.ColorTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class SdAlgHr {
    private final static String TAG = "SdAlgHr";
    private Context mContext;

    // Algorithm Settings
    protected boolean mSimpleHrAlarmActive;
    private double mSimpleHrAlarmThreshMin;
    private double mSimpleHrAlarmThreshMax;

    protected boolean mAdaptiveHrAlarmActive;
    private int mAdaptiveHrAlarmWindowDp;
    private int mAHistoricHrAlarmWindowDp;
    private double mAdaptiveHrAlarmThresh;

    protected boolean mAverageHrAlarmActive;
    private int mAverageHrAlarmWindowDp;
    private double mAverageHrAlarmThreshMin;
    private double mAverageHrAlarmThreshMax;

    // Buffers
    private List<Entry> mHistoricHrBuff;
    private CircBuf mAdaptiveHrBuff;
    private CircBuf mAverageHrBuff;
    private CircBuf mHRHist;

    // Chart Data
    private LineDataSet lineDataSet;
    private LineDataSet lineDataSetAverage;


    public SdAlgHr(Context context) {
        Log.i(TAG, "SdAlgHr Constructor");
        mContext = context;
        updatePrefs();

        mHistoricHrBuff = new ArrayList<>(mAHistoricHrAlarmWindowDp);
        mAdaptiveHrBuff = new CircBuf(mAdaptiveHrAlarmWindowDp, -1.0);
        mAverageHrBuff = new CircBuf(mAverageHrAlarmWindowDp, -1.0);

        // 3 hour period at 5s intervals
        int threeHourDp = (int) (3 * 3600 / 5);
        mHRHist = new CircBuf(threeHourDp, -1);

        // Initialize DataSets with MPAndroidChart 3.x styling
        lineDataSet = new LineDataSet(new ArrayList<>(), "Heart rate history");
        lineDataSet.setColors(ColorTemplate.JOYFUL_COLORS);
        lineDataSet.setValueTextColor(Color.LTGRAY); // Fixed: avoided R.color to prevent package errors
        lineDataSet.setValueTextSize(18f);

        lineDataSetAverage = new LineDataSet(new ArrayList<>(), "Average HR history");
        lineDataSetAverage.setColors(ColorTemplate.PASTEL_COLORS);
        lineDataSetAverage.setValueTextColor(Color.LTGRAY);
        lineDataSetAverage.setValueTextSize(18f);
    }

    private void updatePrefs() {
        SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(mContext);

        mSimpleHrAlarmActive = SP.getBoolean("HRAlarmActive", false);
        mSimpleHrAlarmThreshMin = Double.parseDouble(SP.getString("HRThreshMin", "20"));
        mSimpleHrAlarmThreshMax = Double.parseDouble(SP.getString("HRThreshMax", "150"));

        // Use OsdUtil for time conversion
        mAHistoricHrAlarmWindowDp = (int) Math.round(OsdUtil.convertTimeUnit(9, TimeUnit.HOURS, TimeUnit.SECONDS) / 5.0);

        mAdaptiveHrAlarmActive = SP.getBoolean("HRAdaptiveAlarmActive", false);
        double adaptiveSecs = Double.parseDouble(SP.getString("HRAdaptiveAlarmWindowSecs", "30"));
        mAdaptiveHrAlarmWindowDp = (int) Math.round(adaptiveSecs / 5.0);
        mAdaptiveHrAlarmThresh = Double.parseDouble(SP.getString("HRAdaptiveAlarmThresh", "20"));

        mAverageHrAlarmActive = SP.getBoolean("HRAverageAlarmActive", false);
        double averageSecs = Double.parseDouble(SP.getString("HRAverageAlarmWindowSecs", "120"));
        mAverageHrAlarmWindowDp = (int) Math.round(averageSecs / 5.0);
        mAverageHrAlarmThreshMin = Double.parseDouble(SP.getString("HRAverageAlarmThreshMin", "40"));
        mAverageHrAlarmThreshMax = Double.parseDouble(SP.getString("HRAverageAlarmThreshMax", "120"));
    }

    public double getSimpleHrAverage() {
        return OsdUtil.getAverageValueFromListOfEntry(lineDataSet);
    }

    public void addLineDataSetAverage(float newValue) {
        // v3.x Fix: use getEntryCount() instead of getYVals().size()
        int index = lineDataSetAverage.getEntryCount();
        lineDataSetAverage.addEntry(new Entry(index, newValue));
    }

    public LineData getLineData(boolean isAverage) {
        // v3.x Fix: Constructor only takes ILineDataSet, not String labels
        return new LineData(isAverage ? lineDataSetAverage : lineDataSet);
    }

    public ArrayList<Boolean> checkHr(double hrVal) {
        Log.v(TAG, "checkHr(" + hrVal + ")");
        mAdaptiveHrBuff.add(hrVal);
        mAverageHrBuff.add(hrVal);
        mHRHist.add(hrVal);

        // Update the Chart History
        int index = lineDataSet.getEntryCount();
        Entry newEntry = new Entry(index, (float) hrVal);

        lineDataSet.addEntry(newEntry);
        mHistoricHrBuff.add(newEntry);

        ArrayList<Boolean> retVal = new ArrayList<>();
        retVal.add(checkSimpleHr(hrVal));
        retVal.add(checkAdaptiveHr(hrVal));
        retVal.add(checkAverageHr(hrVal));

        return retVal;
    }

    private boolean checkSimpleHr(double hrVal) {
        if (!mSimpleHrAlarmActive) return false;
        return (hrVal > mSimpleHrAlarmThreshMax || hrVal < mSimpleHrAlarmThreshMin);
    }

    private boolean checkAdaptiveHr(double hrVal) {
        if (!mAdaptiveHrAlarmActive) return false;
        double avHr = mAdaptiveHrBuff.getAverageVal();
        return (hrVal < (avHr - mAdaptiveHrAlarmThresh) || hrVal > (avHr + mAdaptiveHrAlarmThresh));
    }

    private boolean checkAverageHr(double hrVal) {
        if (!mAverageHrAlarmActive) return false;
        double avHr = mAverageHrBuff.getAverageVal();
        return (avHr < mAverageHrAlarmThreshMin || avHr > mAverageHrAlarmThreshMax);
    }




    // Getters
    public CircBuf getAverageHrBuff() { return mAverageHrBuff; }
    public CircBuf getAdaptiveHrBuff() { return mAdaptiveHrBuff; }
    public CircBuf getHrHistBuff() { return mHRHist; }
}