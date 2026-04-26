package uk.org.openseizuredetector.openseizuredetector;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

public class SdWebServer extends NanoHTTPD {
    private final String TAG = "SdWebServer";
    private SdData mSdData;
    private final SdServer mSdServer;
    private final Context mContext;
    private final OsdUtil mUtil;

    public SdWebServer(Context context, SdData sdData, SdServer sdServer) {
        // en_GB: Listen on 8080, accessible via SSH tunnel (e.g. 28080 -> 8080)
        super(8080);
        this.mSdData = sdData;
        this.mContext = context;
        this.mSdServer = sdServer;
        this.mUtil = new OsdUtil(mContext, new Handler(Looper.getMainLooper()));
    }

    public void setSdData(SdData sdData) {
        this.mSdData = sdData;
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Method method = session.getMethod();
        Map<String, String> files = new HashMap<String, String>();

        if (Method.POST.equals(method)) {
            try {
                session.parseBody(files);
            } catch (IOException | ResponseException e) {
                return new Response(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Server Error");
            }
        }

        if (uri.equals("/")) uri = "/index.html";
        Log.v(TAG, "Request: " + method + " " + uri);

        try {
            switch (uri) {
                case "/data":
                    return handleData(method, session.getParms(), files);
                case "/settings":
                    return handleSettings(method, session.getParms(), files);
                case "/spectrum":
                    return handleSpectrum();
                case "/acceptalarm":
                    mSdServer.acceptAlarm();
                    return createJsonResponse("{\"msg\": \"Alarm Accepted\"}");
                case "/logs":
                    return serveLogList();
                default:
                    if (uri.startsWith("/logs/")) return serveLogFile(uri);
                    if (uri.equals("/logfiles.html")) return serveAssetFile("/logfiles.html");
                    return serveAssetFile(uri);
            }
        } catch (Exception e) {
            Log.e(TAG, "Routing Error", e);
            return createJsonResponse("{\"error\": \"Server Error\"}");
        }
    }

    private Response handleData(Method method, Map<String, String> params, Map<String, String> files) {
        if (Method.GET.equals(method)) {
            return createJsonResponse(mSdData.toJson().toString());
        }
        return new Response(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Bad Request");
    }

    private Response handleSettings(Method method, Map<String, String> params, Map<String, String> files) {
        if (Method.GET.equals(method)) {
            return createJsonResponse(mSdData.toSettingsJSON().toString());
        }
        return new Response(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Bad Request");
    }

    private Response handleSpectrum() {
        try {
            JSONObject json = new JSONObject();
            JSONArray arr = new JSONArray();
            if (mSdData.simpleSpec != null) {
                for (int d : mSdData.simpleSpec) arr.put(d);
            }
            json.put("simpleSpec", arr);
            return createJsonResponse(json.toString());
        } catch (Exception e) {
            return createJsonResponse("{\"error\": \"Spectrum Error\"}");
        }
    }

    private Response serveLogList() {
        try {
            JSONObject json = new JSONObject();
            File logDir = mUtil.getDataStorageDir();
            File[] fileList = logDir.listFiles();
            JSONArray arr = new JSONArray();
            if (fileList != null) {
                for (File f : fileList) {
                    if (f.isFile()) arr.put(f.getName());
                }
            }
            json.put("logFileList", arr);
            return createJsonResponse(json.toString());
        } catch (Exception e) {
            return new Response(Response.Status.INTERNAL_ERROR, MIME_HTML, "Log List Error");
        }
    }

    private Response serveLogFile(String uri) {
        try {
            String fname = uri.replace("/logs/", "");
            File file = new File(mUtil.getDataStorageDir(), fname);
            if (file.exists()) {
                return new Response(Response.Status.OK, "text/plain", new FileInputStream(file));
            }
        } catch (Exception e) {
            Log.e(TAG, "File Error", e);
        }
        return new Response(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Log File Not Found");
    }

    private Response serveAssetFile(String uri) {
        try {
            String path = "www" + (uri.startsWith("/") ? uri : "/" + uri);
            InputStream is = mContext.getAssets().open(path);
            return new Response(Response.Status.OK, getMimeType(uri), is);
        } catch (IOException e) {
            return new Response(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Asset Not Found: " + uri);
        }
    }

    private Response createJsonResponse(String json) {
        Response res = new Response(Response.Status.OK, "application/json", json);
        res.addHeader("Access-Control-Allow-Origin", "*");
        return res;
    }

    private String getMimeType(String uri) {
        if (uri.endsWith(".js")) return "application/javascript";
        if (uri.endsWith(".css")) return "text/css";
        if (uri.endsWith(".png")) return "image/png";
        if (uri.endsWith(".ico")) return "image/x-icon";
        return "text/html";
    }
}
