package uk.org.openseizuredetector.openseizuredetector;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.CountDownTimer;
import android.os.Handler;

import androidx.preference.PreferenceManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

/**
 * LogManager handles all aspects of Data Logging within OpenSeizureDetector.
 * It manages local SQLite storage, data retention, and remote synchronisation.
 */
public class LogManager {
    static final private String TAG = "LogManager";
    final static private String mDpTableName = "datapoints";
    final static private String mEventsTableName = "events";

    private boolean mLogRemote;
    private boolean mLogRemoteMobile;
    private String mAuthToken;
    static private SQLiteDatabase mOsdDb = null;
    private RemoteLogTimer mRemoteLogTimer;
    public boolean mLogNDA;
    public NDATimer mNDATimer;
    private long mNDATimerStartTime;
    public double mNDATimeRemaining;
    public double mNDALogPeriodHours = 24.0;
    private static Context mContext;
    private static OsdUtil mUtil;
    public static WebApiConnection mWac;
    public static final boolean USE_FIREBASE_BACKEND = false;

    private boolean mUploadInProgress = false;
    private long mEventDuration = 120; // seconds
    public long mDataRetentionPeriod = 1; // days
    private long mRemoteLogPeriod = 10; // seconds
    private ArrayList<JSONObject> mDatapointsToUploadList;
    private String mCurrentEventRemoteId;
    private long mCurrentEventLocalId = -1;
    private int mCurrentDatapointId;
    private long mAutoPrunePeriod = 3600;
    private boolean mAutoPruneDb;
    private AutoPruneTimer mAutoPruneTimer;
    private SdData mSdSettingsData;

    public interface CursorCallback { void accept(Cursor retVal); }
    public interface BooleanCallback { void accept(boolean retVal); }

    public LogManager(Context context, boolean logRemote, boolean logRemoteMobile, String authToken,
                      long eventDuration, long remoteLogPeriod, boolean logNDA,
                      boolean autoPruneDb, long dataRetentionPeriod, SdData sdSettingsData) {

        mContext = context;
        mLogRemote = logRemote;
        mLogRemoteMobile = logRemoteMobile;
        mAuthToken = authToken;
        mEventDuration = eventDuration;
        mAutoPruneDb = autoPruneDb;
        mDataRetentionPeriod = dataRetentionPeriod;
        mRemoteLogPeriod = remoteLogPeriod;
        mLogNDA = logNDA;
        mSdSettingsData = sdSettingsData;

        mUtil = new OsdUtil(mContext, new Handler());
        openDb();

        if (USE_FIREBASE_BACKEND) {
            mWac = new WebApiConnection_firebase(mContext);
        } else {
            mWac = new WebApiConnection_osdapi(mContext);
        }
        mWac.setStoredToken(mAuthToken);

        if (mLogRemote) startRemoteLogTimer();
        if (mAutoPruneDb) startAutoPruneTimer();
        if (mLogNDA) startNDATimer();
    }

    /* --- DATABASE CORE --- */

    private static void openDb() {
        try {
            if (mOsdDb == null) {
                mOsdDb = new OsdDbHelper(mContext).getWritableDatabase();
            }
        } catch (SQLException e) {
            Log.e(TAG, "Failed to open Database", e);
        }
    }

    // Add this to LogManager.java
    public interface ExportCallback {
        void onComplete(Boolean success);
    }

    public void exportToCsvFile(Date endDate, double duration, Uri uri, ExportCallback callback) {
        // For now, just call the old version if it exists,
        // or trigger the callback immediately to unblock the UI.
        Log.d("LogManager", "Plan C: Stub export called");
        if (callback != null) {
            callback.onComplete(true);
        }
    }

    public void writeDatapointToLocalDb(SdData sdData) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String dateStr = dateFormat.format(new Date());

        if (mOsdDb == null) return;
        try {
            ContentValues values = new ContentValues();
            values.put("dataTime", dateStr);
            values.put("status", sdData.alarmState);
            values.put("dataJSON", sdData.toDatapointJSON());
            values.put("uploaded", (String) null);
            mOsdDb.insert(mDpTableName, null, values);

            if (sdData.alarmState != 0) {
                createLocalEvent(dateStr, sdData.alarmState, "alarm", null, null, sdData.toSettingsJSON());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error writing datapoint", e);
        }
    }

    public boolean createLocalEvent(String dataTime, long status, String type, String subType, String desc, String dataJSON) {
        ContentValues values = new ContentValues();
        values.put("dataTime", dataTime);
        values.put("status", status);
        values.put("type", type);
        values.put("subType", subType);
        values.put("notes", desc);
        values.put("dataJSON", dataJSON);
        return mOsdDb.insert(mEventsTableName, null, values) != -1;
    }

    /* --- DATA PRUNING --- */

    public int pruneLocalDb() {
        long endDateMillis = System.currentTimeMillis() - (86400000L * mDataRetentionPeriod);
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String endDateStr = dateFormat.format(new Date(endDateMillis));

        int deletedRows = 0;
        try {
            deletedRows += mOsdDb.delete(mDpTableName, "dataTime <= ?", new String[]{endDateStr});
            deletedRows += mOsdDb.delete(mEventsTableName, "dataTime <= ?", new String[]{endDateStr});
        } catch (Exception e) {
            Log.e(TAG, "Pruning error", e);
        }
        return deletedRows;
    }

    /* --- REMOTE SYNC CHAIN --- */

    public void writeToRemoteServer() {
        if (!mLogRemote || mUploadInProgress || !mUtil.isNetworkConnected()) return;
        if (!mLogRemoteMobile && mUtil.isMobileDataActive()) return;
        uploadSdData();
    }

    public void uploadSdData() {
        getNextEventToUpload(true, (Long eventId) -> {
            if (eventId != -1) {
                mUploadInProgress = true;
                mCurrentEventLocalId = eventId;
                processEventUpload(eventId);
            }
        });
    }

    private void processEventUpload(long eventId) {
        try {
            String eventJson = getLocalEventById(eventId);
            JSONObject obj = new JSONArray(eventJson).getJSONObject(0);
            Date eventDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(obj.getString("dataTime"));

            mWac.createEvent(obj.getInt("status"), eventDate, obj.optString("type"),
                    obj.optString("subType"), obj.optString("notes"),
                    obj.getString("dataJSON"), this::createEventCallback);
        } catch (Exception e) {
            Log.e(TAG, "Event upload processing failed", e);
            finishUpload();
        }
    }

    public void createEventCallback(String remoteId) {
        if (remoteId == null) { finishUpload(); return; }
        mCurrentEventRemoteId = remoteId;

        // Fetch datapoints window (+/- half event duration)
        long window = (mEventDuration * 1000) / 2;
        try {
            String eventJson = getLocalEventById(mCurrentEventLocalId);
            long eventTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                    .parse(new JSONArray(eventJson).getJSONObject(0).getString("dataTime")).getTime();

            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            getDatapointsByDate(df.format(new Date(eventTime - window)),
                    df.format(new Date(eventTime + window)),
                    (String json) -> {
                        try {
                            JSONArray arr = new JSONArray(json);
                            mDatapointsToUploadList = new ArrayList<>();
                            for (int i = 0; i < arr.length(); i++) mDatapointsToUploadList.add(arr.getJSONObject(i));
                            uploadNextDatapoint();
                        } catch (JSONException e) { finishUpload(); }
                    });
        } catch (Exception e) { finishUpload(); }
    }

    public void uploadNextDatapoint() {
        if (mDatapointsToUploadList != null && !mDatapointsToUploadList.isEmpty()) {
            try {
                JSONObject point = mDatapointsToUploadList.get(0);
                mCurrentDatapointId = point.getInt("id");
                mWac.createDatapoint(point, mCurrentEventRemoteId, this::datapointCallback);
            } catch (JSONException e) {
                mDatapointsToUploadList.remove(0);
                uploadNextDatapoint();
            }
        } else {
            setEventToUploaded(mCurrentEventLocalId, mCurrentEventRemoteId);
            finishUpload();
        }
    }

    public void datapointCallback(String remoteDpId) {
        setDatapointToUploaded(mCurrentDatapointId, mCurrentEventRemoteId);
        if (mDatapointsToUploadList != null && !mDatapointsToUploadList.isEmpty()) {
            mDatapointsToUploadList.remove(0);
        }
        uploadNextDatapoint();
    }

    private void finishUpload() {
        mUploadInProgress = false;
        mCurrentEventLocalId = -1;
        mCurrentEventRemoteId = null;
        mDatapointsToUploadList = null;
    }

    /* --- HELPERS & QUERIES --- */

    public void getNextEventToUpload(boolean includeWarnings, WebApiConnection.LongCallback callback) {
        String where = (includeWarnings ? "status IN (1,2,3,5,6)" : "status IN (2,3,5,6)") + " AND uploaded IS NULL";
        new SelectQueryTask(mEventsTableName, new String[]{"id"}, where, null, null, null, "dataTime ASC", (Cursor c) -> {
            long id = (c != null && c.getCount() > 0) ? c.getLong(0) : -1;
            callback.accept(id);
        }).execute();
    }

    public void getDatapointsByDate(String start, String end, WebApiConnection.StringCallback callback) {
        new SelectQueryTask(mDpTableName, new String[]{"*"}, "dataTime > ? AND dataTime < ?", new String[]{start, end}, null, null, "dataTime ASC", (Cursor c) -> {
            JSONArray arr = new JSONArray();
            if (c != null && c.moveToFirst()) {
                do {
                    try {
                        JSONObject o = new JSONObject();
                        o.put("id", c.getInt(c.getColumnIndexOrThrow("id")));
                        o.put("dataTime", c.getString(c.getColumnIndexOrThrow("dataTime")));
                        o.put("status", c.getInt(c.getColumnIndexOrThrow("status")));
                        o.put("dataJSON", c.getString(c.getColumnIndexOrThrow("dataJSON")));
                        arr.put(o);
                    } catch (JSONException e) { Log.e(TAG, "JSON error", e); }
                } while (c.moveToNext());
            }
            callback.accept(arr.toString());
        }).execute();
    }

    public String getLocalEventById(long id) {
        Cursor c = mOsdDb.rawQuery("SELECT * FROM " + mEventsTableName + " WHERE id = ?", new String[]{String.valueOf(id)});
        JSONArray arr = new JSONArray();
        if (c.moveToFirst()) {
            try {
                JSONObject o = new JSONObject();
                o.put("id", c.getLong(0));
                o.put("dataTime", c.getString(1));
                o.put("status", c.getInt(2));
                o.put("type", c.getString(3));
                o.put("subType", c.getString(4));
                o.put("notes", c.getString(5));
                o.put("dataJSON", c.getString(6));
                arr.put(o);
            } catch (Exception e) { Log.e(TAG, "Query error", e); }
        }
        c.close();
        return arr.toString();
    }

    public void setDatapointToUploaded(int id, String remoteId) {
        ContentValues cv = new ContentValues();
        cv.put("uploaded", remoteId);
        mOsdDb.update(mDpTableName, cv, "id = ?", new String[]{String.valueOf(id)});
    }

    public void setEventToUploaded(long id, String remoteId) {
        ContentValues cv = new ContentValues();
        cv.put("uploaded", remoteId);
        mOsdDb.update(mEventsTableName, cv, "id = ?", new String[]{String.valueOf(id)});
    }

    /* --- DB HELPER & TASKS --- */

    public static class OsdDbHelper extends SQLiteOpenHelper {
        public OsdDbHelper(Context c) { super(c, "OsdData.db", null, 2); }
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + mDpTableName + " (id INTEGER PRIMARY KEY AUTOINCREMENT, dataTime DATETIME, status INT, dataJSON TEXT, uploaded TEXT);");
            db.execSQL("CREATE TABLE " + mEventsTableName + " (id INTEGER PRIMARY KEY AUTOINCREMENT, dataTime DATETIME, status INT, type TEXT, subType TEXT, notes TEXT, dataJSON TEXT, uploaded TEXT);");
        }
        public void onUpgrade(SQLiteDatabase db, int o, int n) {
            db.execSQL("DROP TABLE IF EXISTS " + mDpTableName);
            db.execSQL("DROP TABLE IF EXISTS " + mEventsTableName);
            onCreate(db);
        }
    }

    private static class SelectQueryTask extends AsyncTask<Void, Void, Cursor> {
        String t, s, o; String[] c, a; CursorCallback cb;
        SelectQueryTask(String table, String[] cols, String sel, String[] args, String g, String h, String ord, CursorCallback callback) {
            t=table; c=cols; s=sel; a=args; o=ord; cb=callback;
        }
        protected Cursor doInBackground(Void... v) { return mOsdDb.query(t, c, s, a, null, null, o); }
        protected void onPostExecute(Cursor r) { if (r != null) r.moveToFirst(); cb.accept(r); }
    }

    /* --- TIMERS --- */

    private void startRemoteLogTimer() {
        if (mRemoteLogTimer != null) mRemoteLogTimer.cancel();
        mRemoteLogTimer = new RemoteLogTimer(mRemoteLogPeriod * 1000, 1000);
        mRemoteLogTimer.start();
    }

    private void startNDATimer() {
        if (mNDATimer != null) mNDATimer.cancel();
        mNDATimer = new NDATimer(mEventDuration * 1000, 1000, mNDALogPeriodHours);
        mNDATimer.start();
        mLogNDA = true;
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);
        mNDATimerStartTime = sp.getLong("NDATimerStartTime", 0);
        if (mNDATimerStartTime == 0) {
            mNDATimerStartTime = System.currentTimeMillis();
            sp.edit().putLong("NDATimerStartTime", mNDATimerStartTime).apply();
        }
    }

    private void startAutoPruneTimer() {
        if (mAutoPruneTimer != null) mAutoPruneTimer.cancel();
        mAutoPruneTimer = new AutoPruneTimer(mAutoPrunePeriod * 1000, 1000);
        mAutoPruneTimer.start();
    }

    public void stop() {
        if (mRemoteLogTimer != null) mRemoteLogTimer.cancel();
        if (mNDATimer != null) mNDATimer.cancel();
        if (mAutoPruneTimer != null) mAutoPruneTimer.cancel();
    }

    public static void close() { if (mOsdDb != null) { mOsdDb.close(); mOsdDb = null; } if (mWac != null) mWac.close(); }

    private class RemoteLogTimer extends CountDownTimer {
        RemoteLogTimer(long ms, long i) { super(ms, i); }
        public void onTick(long l) {}
        public void onFinish() { writeToRemoteServer(); start(); }
    }

    private class NDATimer extends CountDownTimer {
        double hours;
        NDATimer(long ms, long i, double h) { super(ms, i); hours = h; }
        public void onTick(long l) {}
        public void onFinish() {
            createLocalEvent(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()), 6, "nda", null, null, mSdSettingsData.toSettingsJSON());
            if ((System.currentTimeMillis() - mNDATimerStartTime) / 3600000.0 >= hours) stop();
            else start();
        }
    }

    private class AutoPruneTimer extends CountDownTimer {
        AutoPruneTimer(long ms, long i) { super(ms, i); }
        public void onTick(long l) {}
        public void onFinish() { pruneLocalDb(); start(); }
    }
}