package org.dpdns.alwaysup.subflow;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;

/** Test APK only; deliberately uses no target APK or Kotlin classes. */
public final class Bin94CsvReceiverActivity extends Activity {
    private static final String LABEL = "SubFlow QA CSV receiver";
    private static final String EXPECTED_HASH = "QA_EXPECTED_SHA256";

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(android.graphics.Color.WHITE);
        int padding = Math.round(24 * getResources().getDisplayMetrics().density);
        screen.setPadding(padding, padding, padding, padding);
        screen.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(padding + insets.getSystemWindowInsetLeft(),
                    padding + insets.getSystemWindowInsetTop(),
                    padding + insets.getSystemWindowInsetRight(),
                    padding + insets.getSystemWindowInsetBottom());
            return insets;
        });
        getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        setContentView(screen);
        addText(screen, LABEL);
        File saved = new File(getFilesDir(), "bin94-received.csv");
        try {
            require("org.dpdns.alwaysup.subflow.debug.test".equals(getPackageName()));
            Intent send = getIntent();
            require(Intent.ACTION_SEND.equals(send.getAction()));
            require("text/csv".equals(send.getType()));
            require((send.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
            @SuppressWarnings("deprecation")
            Uri uri = send.getParcelableExtra(Intent.EXTRA_STREAM);
            require(uri != null && "content".equals(uri.getScheme())
                    && "org.dpdns.alwaysup.subflow.debug.fileprovider".equals(uri.getAuthority()));
            String expected = send.getStringExtra(EXPECTED_HASH);
            require(expected != null && expected.matches("[0-9a-f]{64}"));
            // Reading here exercises the actual cross-UID temporary URI grant.
            try (InputStream input = getContentResolver().openInputStream(uri);
                 FileOutputStream output = new FileOutputStream(saved)) {
                require(input != null);
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                }
                output.getFD().sync();
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long size = 0;
            try (FileInputStream input = new FileInputStream(saved)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, count);
                    size += count;
                }
            }
            StringBuilder hex = new StringBuilder();
            for (byte value : digest.digest()) {
                hex.append(Character.forDigit((value & 255) >>> 4, 16));
                hex.append(Character.forDigit(value & 15, 16));
            }
            require(size > 0 && expected.equals(hex.toString()));
            addText(screen, "Saved and verified " + size + " bytes");
            addText(screen, "SHA-256: " + hex);
        } catch (Exception failure) {
            saved.delete();
            addText(screen, "CSV receive failed: " + failure.getClass().getSimpleName());
        }
    }

    private void addText(LinearLayout screen, String value) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextColor(android.graphics.Color.BLACK);
        text.setTextSize(18);
        screen.addView(text, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private static void require(boolean condition) {
        if (!condition) throw new IllegalArgumentException("Invalid QA CSV request");
    }
}
