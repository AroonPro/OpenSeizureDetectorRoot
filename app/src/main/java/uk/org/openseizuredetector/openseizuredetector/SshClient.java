package uk.org.openseizuredetector.openseizuredetector;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.HurlStack;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Properties;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * SshClient - Unit Regtien Optimized.
 * Forensic KEX alignment for modern OpenSSH (Raspberry Pi OS) compatibility.
 */
public class SshClient {
    private static final String TAG = "SshClient";
    private static final String KEY_FILENAME = "id_rsa_osd";
    
    // Protocol Constants: Prioritise ECDSA for Android/JSch stability
    private static final String KEX_ALGS = "ecdh-sha2-nistp256,ecdh-sha2-nistp384,ecdh-sha2-nistp521,diffie-hellman-group-exchange-sha256";
    private static final String HOST_KEY_ALGS = "ecdsa-sha2-nistp256,ecdsa-sha2-nistp384,ecdsa-sha2-nistp521,rsa-sha2-512,rsa-sha2-256";
    private static final String PUBKEY_ALGS = "ecdsa-sha2-nistp256,ecdsa-sha2-nistp384,ecdsa-sha2-nistp521,rsa-sha2-512,rsa-sha2-256";

    private final Context mContext;
    private final RequestQueue mQueue;
    private final EncryptedSettingsManager mSettingsManager;
    private Session mSession;

    public SshClient(Context context) {
        this.mContext = context.getApplicationContext();
        this.mSettingsManager = new EncryptedSettingsManager(mContext);
        this.mQueue = createInsecureVolleyQueue(mContext);
    }

    @SuppressLint("CustomX509TrustManager")
    private RequestQueue createInsecureVolleyQueue(Context context) {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
            };
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            return Volley.newRequestQueue(context, new HurlStack(null, sc.getSocketFactory()));
        } catch (Exception e) {
            return Volley.newRequestQueue(context);
        }
    }

    public void connectAndUploadConfig(final SshCluster cluster, final SshCallback callback) {
        new Thread(() -> {
            try {
                Log.i(TAG, "SSH: Dialing " + cluster.host + " as user: " + cluster.user);
                JSch jsch = new JSch();
                
                File internalKey = new File(mContext.getFilesDir(), KEY_FILENAME);
                if (internalKey.exists()) {
                    jsch.addIdentity(internalKey.getAbsolutePath());
                } else if (cluster.ppk != null && !cluster.ppk.isEmpty()) {
                    jsch.addIdentity("osd_key", cluster.ppk.getBytes(StandardCharsets.UTF_8), null, null);
                }

                mSession = jsch.getSession(cluster.user, cluster.host, cluster.port);
                Properties config = new Properties();
                config.put("StrictHostKeyChecking", "no");
                
                // ARCHITECTURE FIX: Force ECDSA to avoid "verify: false" RSA-SHA2 issues in JSch 0.1.55
                config.put("kex", KEX_ALGS);
                config.put("server_host_key", HOST_KEY_ALGS);
                config.put("PubkeyAcceptedAlgorithms", PUBKEY_ALGS);
                config.put("PreferredAuthentications", "publickey,password,keyboard-interactive");
                
                // JSch internal: ensures it doesn't fall back to problematic ssh-rsa
                config.put("CheckKex", "no");
                
                mSession.setConfig(config);
                mSession.setServerAliveInterval(30000);
                mSession.connect(15000);

                Log.i(TAG, "SSH: Handshake and Authentication Success.");

                mSession.setPortForwardingL(8123, "127.0.0.1", 8123);
                if (cluster.tunnels != null) {
                    for (String t : cluster.tunnels) processTunnel(t);
                }

                if (callback != null) callback.onComplete(true, "CONNECTED");
                executeWebhook("CONNECT", cluster.user);

            } catch (Exception e) {
                Log.e(TAG, "SSH Negotiation/Auth Failure: " + e.getMessage());
                if (callback != null) callback.onComplete(false, e.getMessage());
            }
        }).start();
    }

    private void processTunnel(String tunnelStr) {
        try {
            if (tunnelStr.startsWith("L")) {
                String[] p = tunnelStr.substring(1).split(":");
                if (p.length == 3) mSession.setPortForwardingL(Integer.parseInt(p[0]), p[1], Integer.parseInt(p[2]));
            } else if (tunnelStr.startsWith("R")) {
                String[] p = tunnelStr.substring(1).split(":");
                if (p.length == 3) mSession.setPortForwardingR(Integer.parseInt(p[0]), p[1], Integer.parseInt(p[2]));
            }
        } catch (Exception ignored) {}
    }

    private void executeWebhook(String type, String userId) {
        try {
            JSONObject json = new JSONObject();
            json.put("type", type);
            json.put("user_id", userId);
            String url = mSettingsManager.getLifecycleWebhookUrl().replace("192.168.178.253", "127.0.0.1");
            JsonObjectRequest req = new JsonObjectRequest(Request.Method.POST, url, json, null, null);
            req.setRetryPolicy(new DefaultRetryPolicy(10000, 0, 1.0f));
            mQueue.add(req);
        } catch (Exception ignored) {}
    }

    public void disconnect() {
        if (mSession != null && mSession.isConnected()) {
            mSession.disconnect();
            mSession = null;
        }
    }

    public interface SshCallback { void onComplete(boolean success, String message); }
}
