package uk.org.openseizuredetector.openseizuredetector;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Base64;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;

/**
 * EncryptedSettingsManager - Beheert gevoelige instellingen (zoals SSH-config en Webhooks) 
 * door ze te versleutelen voordat ze in SharedPreferences worden opgeslagen.
 */
public class EncryptedSettingsManager {
    private static final String TAG = "EncryptedSettings";
    private static final String PREF_NAME = "encrypted_osd_settings";
    private static final String KEY_SSH_CONFIG = "ssh_config_encrypted";
    private static final String KEY_LIFECYCLE_WEBHOOK = "lifecycle_webhook_encrypted";
    private static final String KEY_MIDNIGHT_LOG_PATH = "midnight_log_path";
    private static final String KEY_AUTO_START = "auto_start_on_boot";
    private static final String KEY_ADB_PORT = "adb_wireless_port";
    private static final String KEY_BIRTH_YEAR = "wearer_birth_year";
    
    private final SharedPreferences mPrefs;

    public EncryptedSettingsManager(Context context) {
        mPrefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Slaat een SshCluster object versleuteld op.
     */
    public void saveSshConfig(SshCluster cluster) {
        try {
            JSONObject jo = cluster.toJson();
            String plainText = jo.toString();
            
            byte[] encryptedBytes = CryptoUtil.encrypt(plainText.getBytes(StandardCharsets.UTF_8));
            String base64Encrypted = Base64.encodeToString(encryptedBytes, Base64.DEFAULT);
            
            mPrefs.edit().putString(KEY_SSH_CONFIG, base64Encrypted).apply();
            Log.i(TAG, "SSH Config succesvol versleuteld opgeslagen.");
        } catch (Exception e) {
            Log.e(TAG, "Fout bij opslaan SSH config: " + e.getMessage());
        }
    }

    public SshCluster getSshConfig() {
        String base64Encrypted = mPrefs.getString(KEY_SSH_CONFIG, null);
        if (base64Encrypted == null) return null;

        try {
            byte[] encryptedBytes = Base64.decode(base64Encrypted, Base64.DEFAULT);
            byte[] decryptedBytes = CryptoUtil.decrypt(encryptedBytes);
            String plainText = new String(decryptedBytes, StandardCharsets.UTF_8);
            
            JSONObject jo = new JSONObject(plainText);
            return SshCluster.fromJson(jo);
        } catch (Exception e) {
            Log.e(TAG, "Fout bij ophalen/decoderen SSH config: " + e.getMessage());
            return null;
        }
    }

    public void saveLifecycleWebhookUrl(String url) {
        try {
            byte[] encryptedBytes = CryptoUtil.encrypt(url.getBytes(StandardCharsets.UTF_8));
            String base64Encrypted = Base64.encodeToString(encryptedBytes, Base64.DEFAULT);
            
            mPrefs.edit().putString(KEY_LIFECYCLE_WEBHOOK, base64Encrypted).apply();
        } catch (Exception e) { Log.e(TAG, "Fout bij opslaan Webhook URL: " + e.getMessage()); }
    }

    public String getLifecycleWebhookUrl() {
        String base64Encrypted = mPrefs.getString(KEY_LIFECYCLE_WEBHOOK, null);
        if (base64Encrypted == null) return "https://192.168.178.253:8123/api/webhook/--jd-wR5oXkvJ62StGUa799JP";

        try {
            byte[] encryptedBytes = Base64.decode(base64Encrypted, Base64.DEFAULT);
            byte[] decryptedBytes = CryptoUtil.decrypt(encryptedBytes);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) { return "https://192.168.178.253:8123/api/webhook/--jd-wR5oXkvJ62StGUa799JP"; }
    }

    public void saveMidnightLogPath(String path) { mPrefs.edit().putString(KEY_MIDNIGHT_LOG_PATH, path).apply(); }
    
    public String getMidnightLogPath() {
        String defaultPath = new File(Environment.getExternalStorageDirectory(), "OpenSeizureDetectorLogs/osd_midnight.log").getAbsolutePath();
        return mPrefs.getString(KEY_MIDNIGHT_LOG_PATH, defaultPath);
    }

    public void setAutoStart(boolean enabled) { mPrefs.edit().putBoolean(KEY_AUTO_START, enabled).apply(); }
    public boolean isAutoStartEnabled() { return mPrefs.getBoolean(KEY_AUTO_START, false); }

    public void setAdbPort(int port) { mPrefs.edit().putInt(KEY_ADB_PORT, port).apply(); }
    public int getAdbPort() { return mPrefs.getInt(KEY_ADB_PORT, 5555); }

    public void setBirthYear(int year) { mPrefs.edit().putInt(KEY_BIRTH_YEAR, year).apply(); }
    public int getBirthYear() { return mPrefs.getInt(KEY_BIRTH_YEAR, 1980); }

    public int getAge() {
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        return currentYear - getBirthYear();
    }

    public int getMaxHrFormula() {
        return 220 - getAge();
    }

    public void clearAll() {
        mPrefs.edit().clear().apply();
    }
}
