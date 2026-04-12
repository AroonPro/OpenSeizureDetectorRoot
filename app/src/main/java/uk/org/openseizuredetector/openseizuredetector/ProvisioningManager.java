package uk.org.openseizuredetector.openseizuredetector;

import android.content.Context;
import android.util.Log;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import org.json.JSONObject;

/**
 * ProvisioningManager - Handles the request to create a secure Cloudflare interface 
 * for the user based on their User ID on openseizuredetector.uk.org via the SSH reverse tunnel.
 */
public class ProvisioningManager {
    private static final String TAG = "ProvisioningMgr";
    
    /**
     * Sends a provisioning request to the HomeAssistant/Server webhook.
     * @param context App context
     * @param userId Graham's OSD User ID
     * @param remotePort The unique port assigned for the reverse tunnel
     */
    public static void requestInterface(Context context, String userId, int remotePort) {
        // We still detect IPv6 for logging/admin purposes, but it's not used for DNS.
        String ipv6 = IPv6Manager.getPublicIPv6();
        
        try {
            JSONObject json = new JSONObject();
            json.put("user_id", userId);
            json.put("remote_port", remotePort);
            json.put("watch_ipv6", ipv6);
            
            // Webhook endpoint on HomeAssistant (reached via the SSH tunnel L8123)
            String url = "http://localhost:8123/api/webhook/osd_provisioning";
            
            Log.i(TAG, "Sending provisioning request for User ID: " + userId + " on port: " + remotePort);
            
            JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, json,
                response -> Log.i(TAG, "Provisioning request successful: " + response.toString()),
                error -> Log.e(TAG, "Provisioning request failed: " + error.toString())
            );
            
            Volley.newRequestQueue(context).add(request);
        } catch (Exception e) {
            Log.e(TAG, "Provisioning Error: " + e.getMessage());
        }
    }
}
