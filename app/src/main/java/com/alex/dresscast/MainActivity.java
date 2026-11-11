package com.alex.dresscast;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Simple screen: set your city, preview today's advice. */
public class MainActivity extends Activity {

    private TextView status, preview;
    private EditText cityInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView label = new TextView(this);
        label.setText("Your city");
        label.setTextSize(16);
        root.addView(label);

        cityInput = new EditText(this);
        cityInput.setSingleLine(true);
        cityInput.setText(getSharedPreferences(Weather.PREFS, Context.MODE_PRIVATE)
                .getString("city", "Athens"));
        root.addView(cityInput);

        Button save = new Button(this);
        save.setText("Save city & show today's weather");
        save.setOnClickListener(v -> saveAndPreview());
        root.addView(save);

        status = new TextView(this);
        status.setPadding(0, pad / 2, 0, pad / 2);
        root.addView(status);

        preview = new TextView(this);
        preview.setTextSize(17);
        preview.setGravity(Gravity.START);
        root.addView(preview);

        setContentView(root);

        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }

        long next = Scheduler.scheduleNext(this);
        status.setText("Next notification: " + DateFormat.format("EEE d MMM, HH:mm", next));
        loadPreview(false, null);
    }

    private void saveAndPreview() {
        loadPreview(true, cityInput.getText().toString());
    }

    private void loadPreview(boolean notifyToo, String newCity) {
        preview.setText("Loading…");
        new Thread(() -> {
            String title, body;
            try {
                if (newCity != null && !newCity.trim().isEmpty()) {
                    String resolved = Weather.saveCity(this, newCity);
                    runOnUiThread(() -> cityInput.setText(resolved));
                }
                Weather.Result r = Weather.fetchToday(this);
                title = r.title;
                body = r.body;
                if (notifyToo) Scheduler.notify(this, r.title, r.body);
            } catch (Exception e) {
                title = "Error";
                body = String.valueOf(e.getMessage());
            }
            final String text = title + "\n\n" + body;
            runOnUiThread(() -> preview.setText(text));
        }).start();
    }
}
