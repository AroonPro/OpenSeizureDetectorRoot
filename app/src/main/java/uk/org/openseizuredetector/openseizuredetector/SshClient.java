package uk.org.openseizuredetector.openseizuredetector;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.HurlStack;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.jcraft.jsch.ChannelExec;
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
 * SshClient - Robust SSH tunneling for Wear OS.
 * Enhanced with port cleanup for both Local (L) and Remote (R) tunnels.
 */
public class SshClient {
    private static final String TAG = "SshClient";
    private static final String KEY_FILENAME = "id_rsa_osd";
    
    private static final String KEX_ALGS = "ecdh-sha2-nistp256,ecdh-sha2-nistp384,ecdh-sha2-nistp521,diffie-hellman-group-exchange-sha256,diffie-hellman-group14-sha256";
    private static final String HOST_KEY_ALGS = "ecdsa-sha2-nistp256,ecdsa-sha2-nistp384,ecdsa-sha2-nistp521,rsa-sha2-512,rsa-sha2-256,ssh-rsa";
    private static final String PUBKEY_ALGS = "ecdsa-sha2-nistp256,ecdsa-sha2-nistp384,ecdsa-sha2-nistp521,rsa-sha2-512,rsa-sha2-256,ssh-rsa";

    private final Context mContext;
    private final RequestQueue mQueue;
    private final EncryptedSettingsManager mSettingsManager;
    private Session mSession;
    private String mLastUser;

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
        } catch (Exception e) { return Volley.newRequestQueue(context); }
    }

    public void connectAndUploadConfig(final SshCluster cluster, final boolean forceRestart, final SshCallback callback) {
        // Close existing session before starting a new one to free local ports
        disconnect();
        
        this.mLastUser = cluster.user;
        new Thread(() -> {
            try {
                if (forceRestart) executeWebhook("RESTART", cluster.user);
                
                JSch jsch = new JSch();
                File key = new File(mContext.getFilesDir(), KEY_FILENAME);
                if (key.exists()) jsch.addIdentity(key.getAbsolutePath());
                else if (cluster.ppk != null && !cluster.ppk.isEmpty()) jsch.addIdentity("osd_key", cluster.ppk.getBytes(StandardCharsets.UTF_8), null, null);

                Log.i(TAG, "Connecting to " + cluster.host + ":" + cluster.port + " as " + cluster.user);
                mSession = jsch.getSession(cluster.user, cluster.host, cluster.port);
                Properties config = new Properties();
                config.put("StrictHostKeyChecking", "no");
                config.put("kex", KEX_ALGS);
                config.put("server_host_key", HOST_KEY_ALGS);
                config.put("PubkeyAcceptedAlgorithms", PUBKEY_ALGS);
                mSession.setConfig(config);
                mSession.setServerAliveInterval(30000); // 30s keepalive
                mSession.connect(30000);

                // Stage 1: Remote Cleanup for R tunnels (Server side)
                if (cluster.tunnels != null) {
                    StringBuilder sb = new StringBuilder();
                    for (String tunnel : cluster.tunnels) {
                        for (String t : tunnel.split(";")) {
                            String trimmedT = t.trim();
                            if (trimmedT.startsWith("R")) {
                                String[] p = trimmedT.substring(1).split(":");
                                String port = (p.length == 3) ? p[0] : (p.length == 4 ? p[1] : null);
                                if (port != null) sb.append("fuser -k -n tcp ").append(port).append(" 2>/dev/null; ");
                            }
                        }
                    }
                    if (sb.length() > 0) {
                        Log.d(TAG, "Cleaning up remote ports: " + sb);
                        executeRemoteCommand(sb.toString() + "true");
                        Thread.sleep(1000);
                    }
                }

                // Stage 2: Establish Tunnels with Local/Remote cleanup
                if (cluster.tunnels != null) {
                    for (String tunnel : cluster.tunnels) {
                        for (String t : tunnel.split(";")) {
                            processTunnel(t.trim());
                        }
                    }
                }

                Log.i(TAG, "SSH Connected and Tunnels established.");
                if (callback != null) callback.onComplete(true, "CONNECTED");
                executeWebhook("CONNECT", cluster.user);
            } catch (Exception e) {
                Log.e(TAG, "SSH Error: " + e.getMessage());
                if (callback != null) callback.onComplete(false, e.getMessage());
            }
        }).start();
    }

    private void processTunnel(String t) throws Exception {
        if (t.isEmpty()) return;
        Log.d(TAG, "Processing tunnel: " + t);
        if (t.startsWith("L")) {
            String[] p = t.substring(1).split(":");
            int localPort;
            String localBindAddr = null;
            String targetHost;
            int targetPort;

            if (p.length == 3) {
                localPort = Integer.parseInt(p[0]);
                targetHost = p[1];
                targetPort = Integer.parseInt(p[2]);
            } else if (p.length == 4) {
                localBindAddr = p[0];
                localPort = Integer.parseInt(p[1]);
                targetHost = p[2];
                targetPort = Integer.parseInt(p[3]);
            } else {
                return;
            }

            // Cleanup local forwarding in JSch state
            try { mSession.delPortForwardingL(localPort); } catch (Exception ignored) {}

            if (localBindAddr == null) {
                mSession.setPortForwardingL(localPort, targetHost, targetPort);
            } else {
                mSession.setPortForwardingL(localBindAddr, localPort, targetHost, targetPort);
            }
        } else if (t.startsWith("R")) {
            String[] p = t.substring(1).split(":");
            int remotePort;
            String remoteBindAddr = null;
            String targetHost;
            int targetPort;
            
            if (p.length == 3) {
                remotePort = Integer.parseInt(p[0]);
                targetHost = p[1];
                targetPort = Integer.parseInt(p[2]);
            } else if (p.length == 4) {
                remoteBindAddr = p[0];
                remotePort = Integer.parseInt(p[1]);
                targetHost = p[2];
                targetPort = Integer.parseInt(p[3]);
            } else {
                return;
            }
            
            // Explicitly remove old forwarding to prevent "resv" error
            try { mSession.delPortForwardingR(remotePort); } catch (Exception ignored) {}
            
            if (remoteBindAddr == null) {
                mSession.setPortForwardingR(remotePort, targetHost, targetPort);
            } else {
                mSession.setPortForwardingR(remoteBindAddr, remotePort, targetHost, targetPort);
            }
        }
    }

    private void executeRemoteCommand(String cmd) {
        try {
            ChannelExec channel = (ChannelExec) mSession.openChannel("exec");
            channel.setCommand(cmd);
            channel.connect(5000);
            Thread.sleep(500);
            channel.disconnect();
        } catch (Exception e) { Log.e(TAG, "Command failed: " + e.getMessage()); }
    }

    private void executeWebhook(String type, String userId) {
        try {
            JSONObject json = new JSONObject();
            json.put("type", type);
            json.put("user_id", userId);
            String url = mSettingsManager.getLifecycleWebhookUrl();
            if (url == null || url.isEmpty()) return;
            
            JsonObjectRequest req = new JsonObjectRequest(Request.Method.POST, url, json, null, null);
            mQueue.add(req);
        } catch (Exception ignored) {}
    }
    
    public void disconnect() {
        if (mSession != null && mSession.isConnected()) {
            executeWebhook("DISCONNECT", mLastUser);
            mSession.disconnect();
            mSession = null;
        }
    }

    public boolean isConnected() { return mSession != null && mSession.isConnected(); }
    public interface SshCallback { void onComplete(boolean success, String message); }
}
