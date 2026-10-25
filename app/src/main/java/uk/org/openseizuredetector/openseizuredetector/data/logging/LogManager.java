package uk.org.openseizuredetector.openseizuredetector.data.logging;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import uk.org.openseizuredetector.openseizuredetector.data.SdData;
import uk.org.openseizuredetector.openseizuredetector.utils.OsdUtil;

/**
 * LogManager - Core Backend for forensic data logging.
 * GC-Optimized: Uses ThreadLocal recycling to eliminate object churn during 24/7 monitoring.
 * Separated from UI: Communicates via callbacks and background executors.
 */
public class LogManager {
    private static final String TAG = "LogManager";
    
    // GC-Optimized: ThreadLocal buffers to prevent heap allocation in log loops
    private static final ThreadLocal<StringBuilder> mJsonBuilder = ThreadLocal.withInitial(() -> new StringBuilder(2048));
    private static final ThreadLocal<SimpleDateFormat> mDateFormat = ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US));
    private static final ThreadLocal<Date> mRecycledDate = ThreadLocal.withInitial(Date::new);

    private final Context mContext;
    private final OsdUtil mUtil;
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    private SQLiteDatabase mDb;

    public LogManager(Context context) {
        this.mContext = context.getApplicationContext();
        this.mUtil = new OsdUtil(mContext, new Handler(Looper.getMainLooper()));
        initDatabase();
    }

    private void initDatabase() {
        mExecutor.execute(() -> {
            try {
                // Database initialisatie op achtergrond thread om UI-jank te voorkomen
                mDb = mContext.openOrCreateDatabase("osd_forensics.db", Context.MODE_PRIVATE, null);
                mDb.execSQL("CREATE TABLE IF NOT EXISTS logs (id INTEGER PRIMARY KEY AUTOINCREMENT, timestamp TEXT, data TEXT)");
                Log.i(TAG, "Forensic Database Initialized.");
            } catch (Exception e) {
                Log.e(TAG, "DB Init Fail: " + e.getMessage());
            }
        });
    }

    /**
     * GC-Friendly logging of sensor data.
     */
    public void logData(final SdData sdData) {
        if (sdData == null) return;

        mExecutor.execute(() -> {
            // RECYCLING: Haal de buffers op voor deze thread
            StringBuilder sb = mJsonBuilder.get();
            SimpleDateFormat df = mDateFormat.get();
            Date date = mRecycledDate.get();
            
            sb.setLength(0);
            date.setTime(System.currentTimeMillis());
            String timestamp = df.format(date);

            // Snelle JSON opbouw zonder 'new JSONObject' overhead
            sb.append("{\"hr\":").append(sdData.mHR)
              .append(",\"alarm\":").append(sdData.alarmState)
              .append(",\"batt\":").append(sdData.batteryPc)
              .append("}");

            if (mDb != null && mDb.isOpen()) {
                mDb.execSQL("INSERT INTO logs (timestamp, data) VALUES (?, ?)", new Object[]{timestamp, sb.toString()});
            }
        });
    }

    public void shutdown() {
        mExecutor.shutdown();
        if (mDb != null) mDb.close();
    }
}
