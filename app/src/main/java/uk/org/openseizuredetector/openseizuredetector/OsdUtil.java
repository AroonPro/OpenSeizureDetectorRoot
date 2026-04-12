package uk.org.openseizuredetector.openseizuredetector;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Handler;
import androidx.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * OsdUtil - Core Utility Engine
 */
public class OsdUtil {
    private static final String TAG = "OsdUtil";
    private static Context mContext;
    private Handler mHandler;
    private static SQLiteDatabase mSysLogDb = null;

    public OsdUtil(Context context, Handler handler) {
        mContext = context;
        this.mHandler = handler;
        openDb();
    }

    public static SdServer useSdServerBinding() {
        if (SdDataSource.mSdDataReceiver instanceof SdServer) {
            return (SdServer) SdDataSource.mSdDataReceiver;
        }
        return null;
    }

    public SharedPreferences getPrefs() {
        return PreferenceManager.getDefaultSharedPreferences(mContext);
    }

    public void runOnUiThread(Runnable runnable) {
        if (mHandler != null) mHandler.post(runnable);
    }

    public void showToast(final String msg) {
        runOnUiThread(() -> Toast.makeText(mContext, msg, Toast.LENGTH_LONG).show());
    }

    public int getResId(String resName, String resType) {
        if (mContext == null) return 0;
        return mContext.getResources().getIdentifier(resName, resType, mContext.getPackageName());
    }

    public boolean isServerRunning() {
        if (mContext == null) return false;
        ActivityManager manager = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) return false;
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            String name = service.service.getClassName();
            if (name.contains("SdServer") || name.contains("AWSdService")) return true;
        }
        return false;
    }

    public void startServer() {
        if (mContext == null) return;
        Class<?> serviceClass = SdServer.class;
        if (mContext.getPackageName().contains(".aw")) {
            try { serviceClass = Class.forName("uk.org.openseizuredetector.openseizuredetector.AWSdService"); } catch (Exception e) {}
        }
        Intent intent = new Intent(mContext, serviceClass);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) mContext.startForegroundService(intent);
        else mContext.startService(intent);
    }

    public void stopServer() {
        if (mContext == null) return;
        mContext.stopService(new Intent(mContext, SdServer.class));
        try { mContext.stopService(new Intent(mContext, Class.forName("uk.org.openseizuredetector.openseizuredetector.AWSdService"))); } catch (Exception e) {}
    }

    public boolean isNetworkConnected() {
        if (mContext == null) return false;
        ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = cm.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }

    public boolean isMobileDataActive() {
        if (mContext == null) return false;
        ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = cm.getActiveNetworkInfo();
        return info != null && info.getType() == ConnectivityManager.TYPE_MOBILE;
    }

    public void writeToSysLogFile(String msgStr) { Log.i("OSD_LOG", msgStr); }

    public static long convertTimeUnit(long duration, TimeUnit from, TimeUnit to) {
        return to.convert(duration, from);
    }

    public String getServerIp() {
        if (mContext == null) return "192.168.1.175";
        return PreferenceManager.getDefaultSharedPreferences(mContext).getString("ServerIP", "192.168.1.175");
    }

    public void setServerIp(String ip) {
        if (mContext != null) {
            getPrefs().edit().putString("ServerIP", ip).apply();
        }
    }

    public String alarmStatusToString(int status) {
        switch (status) {
            case 0: return "OK";
            case 1: return "WARNING";
            case 2: return "ALARM";
            default: return "UNKNOWN";
        }
    }

    public File getDataStorageDir() { return (mContext != null) ? mContext.getExternalFilesDir(null) : null; }

    public File[] getDataFilesList() {
        File dir = getDataStorageDir();
        return (dir != null) ? dir.listFiles() : new File[0];
    }

    public String getAppVersionName() {
        try { return mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), 0).versionName; }
        catch (Exception e) { return "unknown"; }
    }

    public static double convertMetresPerSecondSquaredToMilliG(float value) {
        return (double) value * 1000.0 / 9.80665;
    }

    public static double getAverageValueFromListOfEntry(com.github.mikephil.charting.data.LineDataSet dataSet) {
        if (dataSet == null || dataSet.getEntryCount() == 0) return 0.0;
        float sum = 0;
        for (int i = 0; i < dataSet.getEntryCount(); i++) sum += dataSet.getEntryForIndex(i).getY();
        return (double) (sum / dataSet.getEntryCount());
    }

    private static void openDb() {
        try { if (mSysLogDb == null && mContext != null) mSysLogDb = new OsdSysLogHelper(mContext).getWritableDatabase(); } catch (Exception e) {}
    }

    private static class OsdSysLogHelper extends SQLiteOpenHelper {
        OsdSysLogHelper(Context context) { super(context, "OsdSysLog.db", null, 1); }
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS SysLog (id INTEGER PRIMARY KEY, dataTime DATETIME, logLevel TEXT, dataJSON TEXT, uploaded INT);");
        }
        public void onUpgrade(SQLiteDatabase db, int old, int n) {}
    }
}
