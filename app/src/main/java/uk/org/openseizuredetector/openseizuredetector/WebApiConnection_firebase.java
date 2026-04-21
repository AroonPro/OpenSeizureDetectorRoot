package uk.org.openseizuredetector.openseizuredetector;

import android.content.Context;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import org.json.JSONObject;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * WebApiConnection_firebase - Unit Regtien Optimized.
 * Forensic Firestore implementation extending WebApiConnection for architectural consistency.
 */
public class WebApiConnection_firebase extends WebApiConnection {
    private static final String TAG = "WebApiFirebase";
    private final FirebaseFirestore mDb;
    private final FirebaseAuth mAuth;
    private final String mDeviceSerial;

    public WebApiConnection_firebase(Context context, String deviceSerial) {
        super(context);
        this.mAuth = FirebaseAuth.getInstance();
        this.mDb = FirebaseFirestore.getInstance();
        this.mDeviceSerial = (deviceSerial != null) ? deviceSerial : "unknown_device";
    }

    @Override
    public boolean isLoggedIn() {
        return mAuth.getCurrentUser() != null;
    }

    public void pushSdData(SdData sdData) {
        if (!isLoggedIn()) return;
        final String uid = mAuth.getUid();
        if (uid == null) return;

        final Map<String, Object> data = new HashMap<>(8);
        data.put("hr", sdData.mHR);
        data.put("o2", sdData.mO2Sat);
        data.put("alarm", sdData.alarmState);
        data.put("ts", System.currentTimeMillis());

        mDb.collection("users").document(uid)
           .collection("devices").document(mDeviceSerial)
           .set(data, SetOptions.merge())
           .addOnFailureListener(e -> Log.e(TAG, "Firebase Sync fail: " + e.getMessage()));
    }

    // Mandatory overrides to satisfy WebApiConnection hierarchy
    @Override public boolean createEvent(int osdAlarmState, Date eventDate, String type, String subType, String eventDesc, String dataJSON, StringCallback callback) { return false; }
    @Override public boolean getEvent(String eventId, JSONObjectCallback callback) { return false; }
    @Override public boolean getEvents(JSONObjectCallback callback) { return false; }
    @Override public boolean updateEvent(JSONObject eventObj, JSONObjectCallback callback) { return false; }
    @Override public boolean createDatapoint(JSONObject dataObj, String eventId, StringCallback callback) { return false; }
    @Override public boolean getEventTypes(JSONObjectCallback callback) { return false; }
    @Override public boolean getCnnModelInfo(JSONObjectCallback callback) { return false; }
    @Override public boolean checkServerConnection() { return isLoggedIn(); }
    @Override public boolean getUserProfile(JSONObjectCallback callback) { return false; }
}
