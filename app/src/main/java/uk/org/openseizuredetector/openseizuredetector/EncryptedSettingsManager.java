package uk.org.openseizuredetector.openseizuredetector;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

/**
 * EncryptedSettingsManager - Beheert gevoelige instellingen (zoals SSH-config en Webhooks) 
 * door ze te versleutelen voordat ze in SharedPreferences worden opgeslagen.
 */
public class EncryptedSettingsManager {
    private static final String TAG = "EncryptedSettings";
    private static final String PREF_NAME = "encrypted_osd_settings";
    private static final String KEY_SSH_CONFIG = "ssh_config_encrypted";
    private static final String KEY_LIFECYCLE_WEBHOOK = "lifecycle_webhook_encrypted";
    
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

    /**
     * Haalt de versleutelde SSH config op.
     */
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

    /**
     * Slaat de Webhook URL voor Port Lifecycle Clean actie versleuteld op.
     */
    public void saveLifecycleWebhookUrl(String url) {
        try {
            byte[] encryptedBytes = CryptoUtil.encrypt(url.getBytes(StandardCharsets.UTF_8));
            String base64Encrypted = Base64.encodeToString(encryptedBytes, Base64.DEFAULT);
            
            mPrefs.edit().putString(KEY_LIFECYCLE_WEBHOOK, base64Encrypted).apply();
            Log.i(TAG, "Lifecycle Webhook URL succesvol versleuteld opgeslagen.");
        } catch (Exception e) {
            Log.e(TAG, "Fout bij opslaan Webhook URL: " + e.getMessage());
        }
    }

    /**
     * Haalt de versleutelde Lifecycle Webhook URL op.
     */
    public String getLifecycleWebhookUrl() {
        String base64Encrypted = mPrefs.getString(KEY_LIFECYCLE_WEBHOOK, null);
        if (base64Encrypted == null) return "https://192.168.178.253:8123/api/webhook/--jd-wR5oXkvJ62StGUa799JP"; // Default op verzoek

        try {
            byte[] encryptedBytes = Base64.decode(base64Encrypted, Base64.DEFAULT);
            byte[] decryptedBytes = CryptoUtil.decrypt(encryptedBytes);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.e(TAG, "Fout bij ophalen/decoderen Webhook URL: " + e.getMessage());
            return "https://192.168.178.253:8123/api/webhook/--jd-wR5oXkvJ62StGUa799JP";
        }
    }

    /**
     * Verwijdert alle gevoelige instellingen.
     */
    public void clearAll() {
        mPrefs.edit().clear().apply();
    }
}
