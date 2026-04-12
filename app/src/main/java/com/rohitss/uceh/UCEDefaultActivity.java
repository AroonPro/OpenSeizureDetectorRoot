/*
  UCEDefaultActivity - en_GB
  Final Integral Version - Repaired for 2024 LSA Audit.
  Universal Crash Handler UI - Ensuring error transparency in legacy environments.
*/

package com.rohitss.uceh;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import uk.org.openseizuredetector.openseizuredetector.OsdUtil;

/**
 * UCEDefaultActivity - en_GB
 * Error reporting interface that activates upon a system crash.
 * * R-Free Architecture:
 * Uses OsdUtil to fetch layout and view IDs, bypassing R-class symbol errors.
 * * LSA Audit 2024 Context:
 * Provides the "Forensic Evidence" collector. If the 2012 Eggshell cracks,
 * this activity captures the stack trace for legal verification.
 */
public class UCEDefaultActivity extends AppCompatActivity {
    private final String TAG = "UCEDefaultActivity";
    private String strCurrentErrorLog;
    private OsdUtil mUtil;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialise mUtil for R-Free lookups
        mUtil = new OsdUtil(getApplicationContext(), new Handler());

        // R-Free Layout Binding
        int layoutId = mUtil.getResId("uceh_default_activity", "layout");
        if (layoutId != 0) {
            setContentView(layoutId);
        }

        setupUI();
    }

    private void setupUI() {
        // en_GB: Dynamic binding of the error log view
        int tvDetailsId = mUtil.getResId("tv_error_details", "id");
        TextView tvDetails = findViewById(tvDetailsId);

        strCurrentErrorLog = getIntent().getStringExtra("UCEMessage");

        if (tvDetails != null && strCurrentErrorLog != null) {
            tvDetails.setText(strCurrentErrorLog);
        }

        // R-Free Button Binding: Close/Restart
        int btnCloseId = mUtil.getResId("btn_close", "id");
        View btnClose = findViewById(btnCloseId);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> finish());
        }

        // R-Free Button Binding: Copy to Clipboard (for Audit evidence)
        int btnCopyId = mUtil.getResId("btn_copy", "id");
        View btnCopy = findViewById(btnCopyId);
        if (btnCopy != null) {
            btnCopy.setOnClickListener(v -> {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("OSD Error Log", strCurrentErrorLog);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, "Error log copied for LSA Audit", Toast.LENGTH_SHORT).show();
            });
        }
    }
}