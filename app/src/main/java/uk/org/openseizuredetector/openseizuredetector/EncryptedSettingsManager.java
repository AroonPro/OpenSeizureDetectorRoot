package uk.org.openseizuredetector.openseizuredetector;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import androidx.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import uk.org.openseizuredetector.openseizuredetector.data.Profile;

/**
 * EncryptedSettingsManager - en_GB
 * Manages sensitive settings and dynamic Profiles by encrypting them before saving to SharedPreferences.
 * Provides a central hub for Graham algorithm parameters and Gist integration.
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
    private static final String KEY_PROFILES = "profiles_json";
    private static final String KEY_SELECTED_PROFILE_INDEX = "selected_profile_index";
    private static final String KEY_GITHUB_TOKEN = "github_token_encrypted";
    private static final String KEY_IP_GIST_ID = "ip_gist_id";
    
    private final SharedPreferences mPrefs;
    private final Context mContext;

    public EncryptedSettingsManager(Context context) {
        mContext = context.getApplicationContext();
        mPrefs = mContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Encrypts and saves the SSH cluster configuration.
     * @param cluster The SshCluster object to save.
     */
    public void saveSshConfig(SshCluster cluster) {
        try {
            JSONObject jo = cluster.toJson();
            byte[] encryptedBytes = CryptoUtil.encrypt(jo.toString().getBytes(StandardCharsets.UTF_8));
            mPrefs.edit().putString(KEY_SSH_CONFIG, Base64.encodeToString(encryptedBytes, Base64.DEFAULT)).apply();
        } catch (Exception e) { Log.e(TAG, "Save SSH fail"); }
    }

    /**
     * Decrypts and retrieves the SSH cluster configuration.
     * @return The SshCluster object or null if not found.
     */
    public SshCluster getSshConfig() {
        String base64 = mPrefs.getString(KEY_SSH_CONFIG, null);
        if (base64 == null) return null;
        try {
            byte[] decrypted = CryptoUtil.decrypt(Base64.decode(base64, Base64.DEFAULT));
            return SshCluster.fromJson(new JSONObject(new String(decrypted, StandardCharsets.UTF_8)));
        } catch (Exception e) { return null; }
    }

    /**
     * Encrypts and saves the GitHub access token.
     */
    public void saveGitHubToken(String token) {
        try {
            byte[] encryptedBytes = CryptoUtil.encrypt(token.getBytes(StandardCharsets.UTF_8));
            mPrefs.edit().putString(KEY_GITHUB_TOKEN, Base64.encodeToString(encryptedBytes, Base64.DEFAULT)).apply();
        } catch (Exception e) { }
    }

    /**
     * Decrypts and retrieves the GitHub access token.
     */
    public String getGitHubToken() {
        String base64 = mPrefs.getString(KEY_GITHUB_TOKEN, null);
        if (base64 == null) return null;
        try {
            byte[] decrypted = Base64.decode(base64, Base64.DEFAULT);
            byte[] decryptedBytes = CryptoUtil.decrypt(decrypted);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) { return null; }
    }

    /**
     * Saves the Gist ID used for external IP updates.
     */
    public void saveIpGistId(String gistId) { mPrefs.edit().putString(KEY_IP_GIST_ID, gistId).apply(); }
    
    /**
     * Retrieves the Gist ID for IP updates, defaulting to the OSD setup Gist.
     */
    public String getIpGistId() { return mPrefs.getString(KEY_IP_GIST_ID, "bdac0e3ddc2f94130e644d014f6ba632"); }

    /**
     * Saves the list of dynamic detection profiles.
     */
    public void saveProfiles(List<Profile> profiles) {
        try {
            JSONArray ja = new JSONArray();
            for (Profile p : profiles) ja.put(p.toJson());
            mPrefs.edit().putString(KEY_PROFILES, ja.toString()).apply();
        } catch (Exception e) { Log.e(TAG, "Save profiles fail"); }
    }

    /**
     * Retrieves all saved detection profiles.
     */
    public List<Profile> getProfiles() {
        List<Profile> list = new ArrayList<>();
        String json = mPrefs.getString(KEY_PROFILES, null);
        if (json == null) {
            list.add(new Profile("Standard TC", 500, 57));
            list.add(new Profile("Hyperarousal", 50, 10));
            return list;
        }
        try {
            JSONArray ja = new JSONArray(json);
            for (int i = 0; i < ja.length(); i++) list.add(Profile.fromJson(ja.getJSONObject(i)));
        } catch (Exception e) { }
        return list;
    }

    /**
     * Selects a profile by index and applies its parameters to the app.
     */
    public void setSelectedProfileIndex(int index) {
        mPrefs.edit().putInt(KEY_SELECTED_PROFILE_INDEX, index).apply();
        List<Profile> profiles = getProfiles();
        if (index >= 0 && index < profiles.size()) {
            applyProfile(profiles.get(index));
        }
    }

    public int getSelectedProfileIndex() {
        return mPrefs.getInt(KEY_SELECTED_PROFILE_INDEX, 0);
    }

    /**
     * Directly applies a Profile's parameters to SharedPreferences for immediate effect on algorithms.
     */
    private void applyProfile(Profile p) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);
        sp.edit()
            .putString("AlarmThresh", String.valueOf(p.alarmThresh))
            .putString("AlarmRatioThresh", String.valueOf(p.alarmRatioThresh))
            .putString("AlarmFreqMin", String.valueOf(p.alarmFreqMin))
            .putString("AlarmFreqMax", String.valueOf(p.alarmFreqMax))
            .putString("WarnTime", String.valueOf(p.warnTime))
            .putString("AlarmTime", String.valueOf(p.alarmTime))
            .putString("MlAccelStdThresholdPct", String.valueOf(p.mlAccelStdThresh))
            .putString("MlSeizureProbabilityThresholdPct", String.valueOf(p.mlProbThresh))
            .putBoolean("OsdAlarmActive", p.osdAlarmActive)
            .putBoolean("CnnAlarmActive", p.cnnAlarmActive)
            .putBoolean("FlapAlarmActive", p.flapAlarmActive)
            .putBoolean("HRAlarmActive", p.hrAlarmActive)
            .putBoolean("FallActive", p.fallActive)
            .apply();
        Log.i(TAG, "Applied Profile: " + p.name);
    }

    public void saveLifecycleWebhookUrl(String url) {
        try {
            byte[] encryptedBytes = CryptoUtil.encrypt(url.getBytes(StandardCharsets.UTF_8));
            mPrefs.edit().putString(KEY_LIFECYCLE_WEBHOOK, Base64.encodeToString(encryptedBytes, Base64.DEFAULT)).apply();
        } catch (Exception e) { }
    }

    public String getLifecycleWebhookUrl() {
        String base64 = mPrefs.getString(KEY_LIFECYCLE_WEBHOOK, null);
        if (base64 == null) return "";
        try {
            byte[] decrypted = CryptoUtil.decrypt(Base64.decode(base64, Base64.DEFAULT));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) { return ""; }
    }

    public void saveMidnightLogPath(String path) { mPrefs.edit().putString(KEY_MIDNIGHT_LOG_PATH, path).apply(); }

    /**
     * Retrieves the path for midnight logs, defaulting to its own dedicated directory.
     */
    public String getMidnightLogPath() {
        String savedPath = mPrefs.getString(KEY_MIDNIGHT_LOG_PATH, null);
        if (savedPath != null && !savedPath.isEmpty()) return savedPath;

        File midDir = mContext.getExternalFilesDir("logs/midnight");
        if (midDir != null) {
            if (!midDir.exists()) midDir.mkdirs();
            return new File(midDir, "osd_midnight.log").getAbsolutePath();
        }
        return new File(mContext.getFilesDir(), "osd_midnight.log").getAbsolutePath();
    }

    public void setAutoStart(boolean enabled) { mPrefs.edit().putBoolean(KEY_AUTO_START, enabled).apply(); }
    public boolean isAutoStartEnabled() { return mPrefs.getBoolean(KEY_AUTO_START, false); }
    public void setAdbPort(int port) { mPrefs.edit().putInt(KEY_ADB_PORT, port).apply(); }
    public int getAdbPort() { return mPrefs.getInt(KEY_ADB_PORT, 5555); }
    public void setBirthYear(int year) { mPrefs.edit().putInt(KEY_BIRTH_YEAR, year).apply(); }
    public int getBirthYear() { return mPrefs.getInt(KEY_BIRTH_YEAR, 1980); }
    public int getAge() { return Calendar.getInstance().get(Calendar.YEAR) - getBirthYear(); }
    
    /**
     * Bram's Forensic Heart Rate Formula: 220 - Age.
     */
    public int getMaxHrFormula() { return 220 - getAge(); }
}
