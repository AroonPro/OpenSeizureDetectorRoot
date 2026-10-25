package uk.org.openseizuredetector.openseizuredetector.webserver;

import uk.org.openseizuredetector.openseizuredetector.EncryptedSettingsManager;
import uk.org.openseizuredetector.openseizuredetector.SdServer;
import uk.org.openseizuredetector.openseizuredetector.SshCluster;
import uk.org.openseizuredetector.openseizuredetector.data.SdData;
import uk.org.openseizuredetector.openseizuredetector.utils.OsdUtil;
import uk.org.openseizuredetector.openseizuredetector.utils.PersistentFileLogger;
import uk.org.openseizuredetector.openseizuredetector.utils.SettingsUtil;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import android.content.res.AssetManager;

import fi.iki.elonen.NanoHTTPD;

/**
 * SdWebServer - Local HTTP server for OpenSeizureDetector.
 * Provides endpoints for data updates, configuration, and SSH/Gist management.
 * Optimized by recycling JSON objects to reduce GC pressure.
 */
public class SdWebServer extends NanoHTTPD {
    private static final String TAG = "SdWebServer";
    private static final String KEY_FILENAME = "id_rsa_osd";
    
    // URI Paths
    private static final String URI_ROOT = "/";
    private static final String URI_INDEX = "/index.html";
    private static final String URI_ASSETS_PREFIX = "/";
    private static final String URI_UPDATE_IP = "/update_ip";
    private static final String URI_UPDATE_PHONE_BATTERY = "/update_phone_battery";
    private static final String URI_PHONE_BAT = "/phonebat";
    private static final String URI_DATA = "/data";
    private static final String URI_SPECTRUM = "/spectrum";
    private static final String URI_SETTINGS = "/settings";
    private static final String URI_ACCEPT_ALARM = "/acceptalarm";
    private static final String URI_CONFIG = "/config";
    private static final String URI_SSH_SETUP = "/ssh_setup";
    private static final String URI_LOGS = "/logs";

    // JSON Keys for Data endpoint
    private static final String KEY_TIME = "Time";
    private static final String KEY_MAX_FREQ = "maxFreq";
    private static final String KEY_MAX_VAL = "maxVal";
    private static final String KEY_SPEC_POWER = "specPower";
    private static final String KEY_ROI_POWER = "roiPower";
    private static final String KEY_ALARM_STATE = "alarmState";
    private static final String KEY_ALARM_PHRASE = "alarmPhrase";
    private static final String KEY_PEB_CON = "pebCon";
    private static final String KEY_PEB_APP_RUN = "pebAppRun";

    // JSON Keys for Settings endpoint
    private static final String KEY_ALARM_FREQ_MIN = "alarmFreqMin";
    private static final String KEY_ALARM_FREQ_MAX = "alarmFreqMax";
    private static final String KEY_ALARM_THRESH = "alarmThresh";
    private static final String KEY_ALARM_RATIO_THRESH = "alarmRatioThresh";
    private static final String KEY_WARN_TIME = "warnTime";
    private static final String KEY_ALARM_TIME = "alarmTime";
    private static final String KEY_N_MIN = "nMin";
    private static final String KEY_N_MAX = "nMax";
    private static final String KEY_BATTERY_PC = "batteryPc";

    // JSON Keys & Values
    private static final String KEY_IP = "ip";
    private static final String KEY_STATUS = "status";
    private static final String KEY_OK = "ok";
    private static final String KEY_ERROR = "error";
    private static final String KEY_MSG = "msg";
    private static final String KEY_POST_DATA = "postData";
    private static final String KEY_DATA_OBJ = "dataObj";
    private static final String KEY_LEVEL = "level";
    private static final String KEY_CHARGING = "charging";
    private static final String KEY_SIMPLE_SPEC = "simpleSpec";
    private static final String KEY_WEBHOOK_URL = "webhookUrl";
    private static final String KEY_GIST_ID = "gist_ip_update_path";
    private static final String KEY_KEY_CONTENT = "keyContent";
    private static final String KEY_LOG_FILE_LIST = "logFileList";

    // MIME Types
    private static final String MIME_JSON = "application/json";
    private static final String MIME_PLAINTEXT = "text/plain";
    private static final String MIME_HTML = "text/html";
    private static final String MIME_CSS = "text/css";
    private static final String MIME_JS = "application/javascript";

    private final SdData mSdData;
    private final SdServer mSdServer;
    private final Context mContext;
    private final Handler mHandler;
    private final OsdUtil mUtil;
    private final EncryptedSettingsManager mSettingsManager;

    // Recycled JSON objects to reduce GC pressure
    private final JSONObject mRecycledJo = new JSONObject();
    private final JSONArray mRecycledJa = new JSONArray();

    public SdWebServer(Context context, SdData sdData, SdServer sdServer) {
        super(8080);
        mSdData = sdData;
        mContext = context.getApplicationContext();
        mSdServer = sdServer;
        mHandler = new Handler(Looper.getMainLooper());
        mUtil = new OsdUtil(mContext, mHandler);
        mSettingsManager = new EncryptedSettingsManager(mContext);
        Log.i(TAG, "WebServer initialized on port 8080");
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Method method = session.getMethod();
        Map<String, String> parameters = session.getParms();
        Map<String, String> files = new HashMap<>();

        if (method == Method.POST) {
            try { session.parseBody(files); } catch (Exception e) { Log.e(TAG, "Parse error: " + e.getMessage()); }
        }

        String answer = "{}";
        if (uri.equals(URI_ROOT)) uri = URI_INDEX;

        // Serve static assets from the assets/www directory
        if (uri.startsWith(URI_ASSETS_PREFIX) && !uri.startsWith("/data") &&
            !uri.startsWith("/settings") && !uri.startsWith("/spectrum") &&
            !uri.startsWith("/update_") && !uri.startsWith("/phonebat") &&
            !uri.startsWith("/acceptalarm") && !uri.startsWith("/config") &&
            !uri.startsWith("/ssh_setup") && !uri.startsWith("/logs")) {
            try {
                String assetPath = "www" + uri;
                AssetManager am = mContext.getAssets();
                InputStream is = am.open(assetPath);
                byte[] buffer = new byte[is.available()];
                is.read(buffer);
                is.close();

                String mimeType = MIME_PLAINTEXT;
                if (uri.endsWith(".html")) mimeType = MIME_HTML;
                else if (uri.endsWith(".css")) mimeType = MIME_CSS;
                else if (uri.endsWith(".js")) mimeType = MIME_JS;
                else if (uri.endsWith(".png")) mimeType = "image/png";
                else if (uri.endsWith(".jpg") || uri.endsWith(".jpeg")) mimeType = "image/jpeg";

                return new Response(Response.Status.OK, mimeType, new String(buffer, StandardCharsets.UTF_8));
            } catch (Exception e) {
                Log.e(TAG, "Error serving asset: " + uri + " - " + e.getMessage());
                return new Response(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found");
            }
        }

        switch (uri) {
            case URI_UPDATE_IP:
                if (method == Method.POST || method == Method.GET) {
                    String ip = parameters.get(KEY_IP);
                    if (ip == null && files.containsKey(KEY_POST_DATA)) {
                        try {
                            JSONObject json = new JSONObject(files.get(KEY_POST_DATA));
                            ip = json.optString(KEY_IP);
                        } catch (Exception e) { }
                    }
                    if (ip != null) {
                        mSdServer.updateExternalIp(ip);
                        answer = String.format("{\"%s\":\"%s\", \"gistAck\":\"%s\"}", KEY_STATUS, KEY_OK, mSdData.gistAck);
                    }
                }
                break;

            case URI_UPDATE_PHONE_BATTERY:
            case URI_PHONE_BAT:
                if (method == Method.POST || method == Method.GET) {
                    try {
                        JSONObject json = null;
                        if (files.containsKey(KEY_POST_DATA)) {
                            json = new JSONObject(files.get(KEY_POST_DATA));
                        } else if (parameters.containsKey(KEY_POST_DATA)) {
                            json = new JSONObject(parameters.get(KEY_POST_DATA));
                        }

                        int level = -1;
                        if (json != null && json.has(KEY_LEVEL)) {
                            level = json.optInt(KEY_LEVEL, -1);
                        } else if (parameters.containsKey(KEY_LEVEL)) {
                            level = Integer.parseInt(parameters.get(KEY_LEVEL));
                        }

                        if (level != -1) {
                            mSdData.phoneBatteryPc = level;
                            if (json != null && json.has(KEY_CHARGING)) {
                                mSdData.mPhoneIsCharging = json.optBoolean(KEY_CHARGING);
                            } else if (parameters.containsKey(KEY_CHARGING)) {
                                mSdData.mPhoneIsCharging = Boolean.parseBoolean(parameters.get(KEY_CHARGING));
                            }
                            
                            mSdServer.onSdDataReceived(mSdData);
                            answer = String.format("{\"%s\":\"%s\"}", KEY_STATUS, KEY_OK);
                        } else {
                            answer = String.format("{\"%s\":\"%s\", \"%s\":\"Missing level\"}", KEY_STATUS, KEY_ERROR, KEY_MSG);
                        }
                    } catch (Exception e) { 
                        answer = String.format("{\"%s\":\"%s\", \"%s\":\"%s\"}", KEY_STATUS, KEY_ERROR, KEY_MSG, e.getMessage());
                    }
                }
                break;

            case URI_SPECTRUM:
                try {
                    synchronized (mRecycledJo) {
                        mRecycledJo.remove(KEY_SIMPLE_SPEC);
                        synchronized (mRecycledJa) {
                            while(mRecycledJa.length() > 0) mRecycledJa.remove(0);
                            for (int i = 0; i < Math.min(10, mSdData.simpleSpec.length); i++) mRecycledJa.put(mSdData.simpleSpec[i]);
                            mRecycledJo.put(KEY_SIMPLE_SPEC, mRecycledJa);
                        }
                        answer = mRecycledJo.toString();
                    }
                } catch (Exception e) { answer = "{\"simpleSpec\":[]}"; }
                break;

            case URI_LOGS:
                try {
                    synchronized (mRecycledJo) {
                        mRecycledJo.remove(KEY_LOG_FILE_LIST);
                        synchronized (mRecycledJa) {
                            while(mRecycledJa.length() > 0) mRecycledJa.remove(0);
                            File logDir = new File(mContext.getExternalFilesDir(null), "logs");
                            if (logDir.exists()) {
                                File[] logFiles = logDir.listFiles();
                                if (logFiles != null) {
                                    for (File f : logFiles) mRecycledJa.put(f.getName());
                                }
                            }
                            mRecycledJo.put(KEY_LOG_FILE_LIST, mRecycledJa);
                        }
                        answer = mRecycledJo.toString();
                    }
                } catch (Exception e) { answer = String.format("{\"%s\":\"%s\"}", KEY_LOG_FILE_LIST, "[]"); }
                break;

            case URI_DATA:
                try {
                    synchronized (mRecycledJo) {
                        mRecycledJo.remove(KEY_TIME);
                        mRecycledJo.remove(KEY_MAX_FREQ);
                        mRecycledJo.remove(KEY_MAX_VAL);
                        mRecycledJo.remove(KEY_SPEC_POWER);
                        mRecycledJo.remove(KEY_ROI_POWER);
                        mRecycledJo.remove(KEY_ALARM_STATE);
                        mRecycledJo.remove(KEY_ALARM_PHRASE);
                        mRecycledJo.remove(KEY_PEB_CON);
                        mRecycledJo.remove(KEY_PEB_APP_RUN);

                        mRecycledJo.put(KEY_TIME, mSdData.dataTimeMillis);
                        mRecycledJo.put(KEY_MAX_FREQ, mSdData.maxFreq);
                        mRecycledJo.put(KEY_MAX_VAL, mSdData.maxVal);
                        mRecycledJo.put(KEY_SPEC_POWER, mSdData.specPower);
                        mRecycledJo.put(KEY_ROI_POWER, mSdData.roiPower);
                        mRecycledJo.put(KEY_ALARM_STATE, mSdData.alarmState);
                        mRecycledJo.put(KEY_ALARM_PHRASE, mSdData.alarmPhrase);
                        mRecycledJo.put(KEY_PEB_CON, mSdData.watchConnected);
                        mRecycledJo.put(KEY_PEB_APP_RUN, mSdData.watchAppRunning);
                        answer = mRecycledJo.toString();
                    }
                } catch (Exception e) { answer = "{}"; }
                break;

            case URI_SETTINGS:
                try {
                    synchronized (mRecycledJo) {
                        mRecycledJo.remove(KEY_ALARM_FREQ_MIN);
                        mRecycledJo.remove(KEY_ALARM_FREQ_MAX);
                        mRecycledJo.remove(KEY_ALARM_THRESH);
                        mRecycledJo.remove(KEY_ALARM_RATIO_THRESH);
                        mRecycledJo.remove(KEY_WARN_TIME);
                        mRecycledJo.remove(KEY_ALARM_TIME);
                        mRecycledJo.remove(KEY_N_MIN);
                        mRecycledJo.remove(KEY_N_MAX);
                        mRecycledJo.remove(KEY_BATTERY_PC);

                        mRecycledJo.put(KEY_ALARM_FREQ_MIN, mSdData.alarmFreqMin);
                        mRecycledJo.put(KEY_ALARM_FREQ_MAX, mSdData.alarmFreqMax);
                        mRecycledJo.put(KEY_ALARM_THRESH, mSdData.alarmThresh);
                        mRecycledJo.put(KEY_ALARM_RATIO_THRESH, mSdData.alarmRatioThresh);
                        mRecycledJo.put(KEY_WARN_TIME, mSdData.warnTime);
                        mRecycledJo.put(KEY_ALARM_TIME, mSdData.alarmTime);
                        mRecycledJo.put(KEY_N_MIN, mSdData.nMin);
                        mRecycledJo.put(KEY_N_MAX, mSdData.nMax);
                        mRecycledJo.put(KEY_BATTERY_PC, mSdData.batteryPc);
                        answer = mRecycledJo.toString();
                    }
                } catch (Exception e) { answer = "{}"; }
                break;

            case URI_ACCEPT_ALARM:
                if (mSdServer != null) {
                    mSdData.alarmState = 0;
                    mSdData.alarmPhrase = "Alarm Accepted";
                    answer = String.format("{\"%s\":\"%s\"}", KEY_STATUS, KEY_OK);
                }
                break;

            default:
                return new Response(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found");
        }

        return new Response(Response.Status.OK, MIME_JSON, answer);
    }
}
