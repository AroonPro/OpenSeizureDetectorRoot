package uk.org.openseizuredetector.openseizuredetector;

import android.util.Log;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

/**
 * IPv6Manager - Finds the public IPv6 address of the device for administration.
 */
public class IPv6Manager {
    private static final String TAG = "IPv6Manager";

    public static String getPublicIPv6() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress() && addr instanceof Inet6Address) {
                        String sAddr = addr.getHostAddress();
                        // Filter out link-local addresses (fe80::)
                        if (!sAddr.toLowerCase().startsWith("fe80")) {
                            // Strip scope ID if present (e.g. %wlan0)
                            int delim = sAddr.indexOf('%');
                            return delim < 0 ? sAddr.toUpperCase() : sAddr.substring(0, delim).toUpperCase();
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting IPv6: " + e.getMessage());
        }
        return "UNKNOWN";
    }
}
