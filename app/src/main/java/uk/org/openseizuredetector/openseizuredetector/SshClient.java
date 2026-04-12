package uk.org.openseizuredetector.openseizuredetector;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
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
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Properties;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * SshClient - en_GB
 * Pure Service-Context SSH Manager. 
 * Handles manual tunnel configuration and background stability.
 */
public class SshClient {
    private static final String TAG = "SshClient";
    private final Context mContext;
    private final RequestQueue mQueue;
    private final EncryptedSettingsManager mSettingsManager;
    private Session mSession;

    public SshClient(Context context) {
        this.mContext = context;
        this.mSettingsManager = new EncryptedSettingsManager(context);
        this.mQueue = createInsecureVolleyQueue(context);
    }

    private Handler getServerHandler() {
        SdServer server = OsdUtil.useSdServerBinding();
        return (server != null) ? server.mHandler : null;
    }

    private RequestQueue createInsecureVolleyQueue(Context context) {
        try {
            @SuppressLint("CustomX509TrustManager") 
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
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
            return Volley.newRequestQueue(context, new HurlStack(null, sc.getSocketFactory()));
        } catch (Exception e) {
            return Volley.newRequestQueue(context);
        }
    }

    public interface SshCallback {
        void onComplete(boolean success, String message);
    }

    private void callWebhook(String type, String userId, int port, boolean useTunnel, int retryCount, SshCallback callback) {
        Handler handler = getServerHandler();
        try {
            JSONObject json = new JSONObject();
            json.put("type", type);
            json.put("user_id", userId);
            json.put("port", port);

            String url = mSettingsManager.getLifecycleWebhookUrl();
            if (useTunnel) url = url.replace("192.168.178.253", "127.0.0.1");

            JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, json,
                    response -> {
                        Log.i(TAG, "Webhook " + type + " SUCCESS");
                        if (callback != null) callback.onComplete(true, "OK");
                    },
                    error -> {
                        if (retryCount > 0 && handler != null) {
                            handler.postDelayed(() -> callWebhook(type, userId, port, useTunnel, retryCount - 1, callback), 4000);
                        } else {
                            if (callback != null) callback.onComplete(false, error.toString());
                        }
                    }
            );
            request.setRetryPolicy(new DefaultRetryPolicy(10000, 0, 1.0f));
            mQueue.add(request);
        } catch (Exception e) {
            Log.e(TAG, "Webhook JSON Error: " + e.getMessage());
        }
    }

    public void connectAndUploadConfig(final SshCluster cluster, final SshCallback callback) {
        final Handler handler = getServerHandler();
        new Thread(() -> {
            try {
                Log.i(TAG, "SSH: Connecting to " + cluster.host + " as " + cluster.user);
                JSch jsch = new JSch();
                
                byte[] keyBytes = readRawFileBytes(SshKeyConfig.KEY_PATH);
                if (keyBytes != null && keyBytes.length > 0) {
                    jsch.addIdentity("osd_key", keyBytes, null, null);
                    Log.i(TAG, "Loaded identity from file.");
                } else if (cluster.ppk != null && !cluster.ppk.isEmpty()) {
                    jsch.addIdentity("osd_key", cluster.ppk.getBytes(StandardCharsets.UTF_8), null, null);
                    Log.i(TAG, "Loaded identity from database.");
                }

                mSession = jsch.getSession(cluster.user, cluster.host, cluster.port);
                Properties config = new Properties();
                config.put("StrictHostKeyChecking", "no");
                // Mandatory for modern OpenSSH/Debian servers
                config.put("PubkeyAcceptedAlgorithms", "rsa-sha2-256,rsa-sha2-512,ssh-rsa,ssh-ed25519");
                mSession.setConfig(config);
                mSession.setServerAliveInterval(30000);
                mSession.connect(15000);

                Log.i(TAG, "SSH: Session Established. Opening tunnels...");

                // Always ensure L8123 for webhooks
                try { mSession.setPortForwardingL(8123, "127.0.0.1", 8123); } catch (Exception e) {}

                // Process manual tunnels
                if (cluster.tunnels != null) {
                    for (String tunnelStr : cluster.tunnels) {
                        try {
                            if (tunnelStr.startsWith("L")) {
                                String[] p = tunnelStr.substring(1).split(":");
                                if (p.length == 3 && !p[0].equals("8123")) {
                                    mSession.setPortForwardingL(Integer.parseInt(p[0]), p[1], Integer.parseInt(p[2]));
                                }
                            } else if (tunnelStr.startsWith("R")) {
                                String[] p = tunnelStr.substring(1).split(":");
                                if (p.length == 3) {
                                    mSession.setPortForwardingR(Integer.parseInt(p[0]), p[1], Integer.parseInt(p[2]));
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Manual Tunnel Error: " + tunnelStr);
                        }
                    }
                }

                if (handler != null) {
                    handler.post(() -> {
                        if (callback != null) callback.onComplete(true, "Connected");
                        // Trigger webhooks non-blocking
                        callWebhook("CONNECT", cluster.user, 8080, true, 2, null);
                    });
                }

            } catch (Exception e) {
                Log.e(TAG, "SSH Fatal: " + e.getMessage());
                if (callback != null && handler != null) {
                    handler.post(() -> callback.onComplete(false, e.getMessage()));
                }
            }
        }).start();
    }

    public void disconnect() {
        if (mSession != null && mSession.isConnected()) {
            mSession.disconnect();
            Log.i(TAG, "SSH Disconnected.");
        }
    }

    private byte[] readRawFileBytes(String path) {
        if (path == null || path.isEmpty()) return null;
        File file = new File(path);
        if (!file.exists()) return null;
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            return data;
        } catch (IOException e) { return null; }
    }
}
