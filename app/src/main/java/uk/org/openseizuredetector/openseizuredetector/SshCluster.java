package uk.org.openseizuredetector.openseizuredetector;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/**
 * SshCluster - Configuration model for SSH connections and tunnels.
 * Includes support for public key auth, ADB debugging, and Gist-based passkeys.
 */
public class SshCluster {
    public String host = "";
    public int port = 22;
    public String user = "";
    public List<String> tunnels = new ArrayList<>();
    public String ppk = "";
    public boolean adbDebugEnabled = false;
    public int adbPort = 5555;
    public String passkey = ""; // Added for Gist-based auth/verification

    public SshCluster() {}

    /**
     * Serializes the SSH configuration to a JSON object.
     */
    public JSONObject toJson() throws JSONException {
        JSONObject jo = new JSONObject();
        jo.put("host", host);
        jo.put("port", port);
        jo.put("user", user);
        jo.put("ppk", ppk);
        jo.put("adbDebugEnabled", adbDebugEnabled);
        jo.put("adbPort", adbPort);
        jo.put("passkey", passkey);

        JSONArray ja = new JSONArray();
        for (String t : tunnels) {
            ja.put(t);
        }
        jo.put("tunnels", ja);
        return jo;
    }

    /**
     * Creates an SshCluster object from its JSON representation.
     */
    public static SshCluster fromJson(JSONObject jo) throws JSONException {
        SshCluster cluster = new SshCluster();
        cluster.host = jo.optString("host", "");
        cluster.port = jo.optInt("port", 22);
        cluster.user = jo.optString("user", "");
        cluster.ppk = jo.optString("ppk", "");
        cluster.adbDebugEnabled = jo.optBoolean("adbDebugEnabled", false);
        cluster.adbPort = jo.optInt("adbPort", 5555);
        cluster.passkey = jo.optString("passkey", "");

        JSONArray ja = jo.optJSONArray("tunnels");
        if (ja != null) {
            for (int i = 0; i < ja.length(); i++) {
                cluster.tunnels.add(ja.getString(i));
            }
        }
        return cluster;
    }
}
