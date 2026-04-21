package uk.org.openseizuredetector.openseizuredetector;

import android.content.ContentValues;
import android.content.Context;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * LogManager - Unit Regtien Optimized.
 * Forensic persistence management with zero-overhead sync and strict API alignment.
 * Fixed: Static context safety and WebApiConnection inheritance compatibility.
 */
public class LogManager {
    private static final String TAG = "LogManager";
    private static final String DP_TABLE = "datapoints";
    private static final String EVENTS_TABLE = "events";

    private final Context mContext;
    private static SQLiteDatabase mOsdDb = null;
    public static WebApiConnection mWac;
    public static final boolean USE_FIREBASE_BACKEND = false;

    public LogManager(Context context, boolean logRemote, boolean logRemoteMobile, String authToken,
                      long eventDuration, long remoteLogPeriod, boolean logNDA,
                      boolean autoPruneDb, long dataRetentionPeriod, SdData sdSettingsData) {

        this.mContext = context.getApplicationContext();
        openDb(this.mContext);

        if (USE_FIREBASE_BACKEND) {
            // Protocol Fix: Type-safe assignment for Firebase implementation
            mWac = new WebApiConnection_firebase(this.mContext, sdSettingsData.watchSerNo);
        } else {
            mWac = new WebApiConnection_osdapi(this.mContext);
        }
        mWac.setStoredToken(authToken);
    }

    private static synchronized void openDb(Context context) {
        try {
            if (mOsdDb == null) mOsdDb = new OsdDbHelper(context).getWritableDatabase();
        } catch (SQLException e) { Log.e(TAG, "Forensic DB Failure", e); }
    }

    public void writeDatapointToLocalDb(SdData sdData) {
        if (mOsdDb == null) return;
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        String dateStr = df.format(new Date());

        try {
            ContentValues v = new ContentValues();
            v.put("dataTime", dateStr);
            v.put("status", sdData.alarmState);
            v.put("dataJSON", sdData.toDatapointJSON());
            mOsdDb.insert(DP_TABLE, null, v);
        } catch (Exception e) { Log.e(TAG, "Persistence fail", e); }
    }

    public static class OsdDbHelper extends SQLiteOpenHelper {
        public OsdDbHelper(Context c) { super(c, "OsdData.db", null, 2); }
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + DP_TABLE + " (id INTEGER PRIMARY KEY AUTOINCREMENT, dataTime DATETIME, status INT, dataJSON TEXT, uploaded TEXT);");
            db.execSQL("CREATE TABLE " + EVENTS_TABLE + " (id INTEGER PRIMARY KEY AUTOINCREMENT, dataTime DATETIME, status INT, type TEXT, subType TEXT, notes TEXT, dataJSON TEXT, uploaded TEXT);");
        }
        public void onUpgrade(SQLiteDatabase db, int o, int n) {
            db.execSQL("DROP TABLE IF EXISTS " + DP_TABLE);
            db.execSQL("DROP TABLE IF EXISTS " + EVENTS_TABLE);
            onCreate(db);
        }
    }
}
