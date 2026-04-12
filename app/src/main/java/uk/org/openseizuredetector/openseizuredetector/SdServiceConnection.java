package uk.org.openseizuredetector.openseizuredetector;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

/**
 * SdServiceConnection - The Bridge between UI and Engine.
 * Transposed from Source 48cc2332 & 6a44565d.
 */
public class SdServiceConnection implements ServiceConnection {
    private static final String TAG = "SdServiceConnection";
    public boolean mBound = false;
    public SdServer mSdServer;
    // Alias for backward compatibility with display-side code
    public SdServer mSdService; 
    private Context mContext;

    public SdServiceConnection(Context context) {
        this.mContext = context;
    }

    @Override
    public void onServiceConnected(ComponentName className, IBinder service) {
        SdServer.SdBinder binder = (SdServer.SdBinder) service;
        mSdServer = binder.getService();
        mSdService = mSdServer; // Set alias
        mBound = true;
        Log.i(TAG, "onServiceConnected: Bound to SdServer");
    }

    @Override
    public void onServiceDisconnected(ComponentName arg0) {
        mBound = false;
        mSdServer = null;
        mSdService = null;
        Log.i(TAG, "onServiceDisconnected: Unbound from SdServer");
    }

    /**
     * doBindService - Transposed logic from 48cc2332
     */
    public void doBindService() {
        if (!mBound && mContext != null) {
            Intent intent = new Intent(mContext, SdServer.class);
            mContext.bindService(intent, this, Context.BIND_AUTO_CREATE);
        }
    }
}
