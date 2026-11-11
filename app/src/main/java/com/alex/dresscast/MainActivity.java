package com.alex.dresscast;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Date;
import java.util.Locale;

/** Main screen: today's forecast, what to wear, and settings. */
public class MainActivity extends Activity {

    static final int BLUE = 0xFF3B7DDD, BLUE_DARK = 0xFF2A5CB8, BLUE_SOFT = 0xFFE3ECFA;
    static final int BG = 0xFFEEF2F8, TEXT = 0xFF1C2433, MUTED = 0xFF6B7588;
    static final int REQ_NOTIF = 1, REQ_LOC = 2;

    private SharedPreferences prefs;
    private float dp;
    private TextView hdrCity, hdrIcon, hdrTemp, hdrDesc, hdrFeels;
    private TextView wearText, locText, timeText, nextText;
    private LinearLayout periodsRow;

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = Weather.prefs(this);
        dp = getResources().getDisplayMetrics().density;

        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        setContentView(buildUi());
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) c.setSystemBarsAppearance(
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        }

        updateSchedule();
        if (prefs.getBoolean("useGps", false) && Loc.hasPerm(this)) locate();
        else refresh(false);
    }

    // ---------- UI ----------

    @SuppressWarnings("deprecation")
    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = px(16);
        col.setPadding(pad, pad, pad, pad);
        scroll.addView(col);
        // Keep content clear of the status and navigation bars (edge-to-edge on Android 15+).
        scroll.setOnApplyWindowInsetsListener((v, in) -> {
            col.setPadding(pad, pad + in.getSystemWindowInsetTop(), pad, pad + in.getSystemWindowInsetBottom());
            return in;
        });

        col.addView(text("DressCast", 28, TEXT, true));
        TextView date = text(DateFormat.format("EEEE, d MMMM", new Date()).toString(), 15, MUTED, false);
        date.setPadding(0, 0, 0, px(14));
        col.addView(date);

        // Header card with today's summary
        LinearLayout hdr = new LinearLayout(this);
        hdr.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF5B9BF0, BLUE_DARK});
        g.setCornerRadius(px(24));
        hdr.setBackground(g);
        hdr.setElevation(px(3));
        hdr.setPadding(px(20), px(18), px(20), px(20));
        hdrCity = text("📍 " + prefs.getString("city", "Athens, Greece"), 15, Color.WHITE, false);
        hdr.addView(hdrCity);
        LinearLayout big = new LinearLayout(this);
        big.setGravity(Gravity.CENTER_VERTICAL);
        hdrIcon = text("", 50, Color.WHITE, false);
        hdrTemp = text("--°", 46, Color.WHITE, true);
        hdrTemp.setPadding(px(10), 0, 0, 0);
        big.addView(hdrIcon);
        big.addView(hdrTemp);
        hdr.addView(big);
        hdrDesc = text("Loading…", 19, Color.WHITE, true);
        hdr.addView(hdrDesc);
        hdrFeels = text("", 14, 0xE6FFFFFF, false);
        hdrFeels.setPadding(0, px(4), 0, 0);
        hdr.addView(hdrFeels);
        col.addView(hdr, blockLp());

        // Morning / afternoon / evening
        LinearLayout pc = card("Through the day");
        periodsRow = new LinearLayout(this);
        pc.addView(periodsRow);
        col.addView(pc, blockLp());

        // What to wear
        LinearLayout wc = card("👕 What to wear");
        wearText = text("…", 17, TEXT, false);
        wearText.setLineSpacing(px(3), 1f);
        wc.addView(wearText);
        col.addView(wc, blockLp());

        // Settings
        LinearLayout sc = card("Settings");
        sc.addView(text("Location", 14, MUTED, true));
        locText = text(placeLabel(prefs.getString("city", "Athens, Greece")), 16, TEXT, false);
        locText.setPadding(0, px(2), 0, 0);
        sc.addView(locText);
        LinearLayout btns = new LinearLayout(this);
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, -2, 1f);
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, -2, 1f);
        half2.leftMargin = px(8);
        btns.addView(button("📍 My location", true, v -> onLocationClick()), half);
        btns.addView(button("✏️ Type a city", false, v -> askCity()), half2);
        LinearLayout.LayoutParams btnsLp = new LinearLayout.LayoutParams(-1, -2);
        btnsLp.topMargin = px(10);
        btnsLp.bottomMargin = px(18);
        sc.addView(btns, btnsLp);

        sc.addView(text("Daily notification", 14, MUTED, true));
        LinearLayout trow = new LinearLayout(this);
        trow.setGravity(Gravity.CENTER_VERTICAL);
        timeText = text("08:00", 30, TEXT, true);
        trow.addView(timeText, new LinearLayout.LayoutParams(0, -2, 1f));
        trow.addView(button("🕗 Change time", false, v -> askTime()));
        sc.addView(trow);
        nextText = text("", 13, MUTED, false);
        sc.addView(nextText);
        LinearLayout.LayoutParams testLp = new LinearLayout.LayoutParams(-1, -2);
        testLp.topMargin = px(14);
        sc.addView(button("🔔 Send a test notification", false, v -> refresh(true)), testLp);
        col.addView(sc, blockLp());

        TextView credit = text("Weather data by Open-Meteo.com", 12, MUTED, false);
        credit.setGravity(Gravity.CENTER);
        col.addView(credit, new LinearLayout.LayoutParams(-1, -2));
        return scroll;
    }

    private void show(Weather.Result r) {
        hdrCity.setText("📍 " + r.city);
        hdrIcon.setText(Weather.emoji(r.code));
        hdrTemp.setText(String.format(Locale.US, "%.0f°", r.tMax));
        hdrDesc.setText(String.format(Locale.US, "%s · low %.0f°", Weather.describe(r.code), r.tMin));
        hdrFeels.setText(String.format(Locale.US, "Feels like %.0f°–%.0f° · Wind %.0f km/h · UV %.0f",
                r.feelMin, r.feelMax, r.wind, r.uv));

        periodsRow.removeAllViews();
        for (Weather.Period p : r.periods) {
            periodsRow.addView(periodView(p), new LinearLayout.LayoutParams(0, -2, 1f));
        }
        wearText.setText(r.outfit);
        locText.setText(placeLabel(r.city));
    }

    private View periodView(Weather.Period p) {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setGravity(Gravity.CENTER_HORIZONTAL);
        v.addView(text(p.name, 14, MUTED, false));
        v.addView(text(Weather.emoji(p.code), 30, TEXT, false));
        v.addView(text(String.format(Locale.US, "%.0f°", p.feel), 20, TEXT, true));
        v.addView(text("💧 " + p.rain + "%", 13, p.rain >= 50 ? BLUE_DARK : MUTED, p.rain >= 50));
        return v;
    }

    private String placeLabel(String city) {
        return (prefs.getBoolean("useGps", false) ? "Using your location: " : "") + city;
    }

    // ---------- Actions ----------

    private void refresh(boolean notifyToo) {
        hdrDesc.setText("Loading…");
        new Thread(() -> {
            try {
                Weather.Result r = Weather.fetchToday(this);
                if (notifyToo) Scheduler.notify(this, r.title, r.outfit, r.body);
                runOnUiThread(() -> show(r));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    hdrDesc.setText("Couldn't load the weather");
                    hdrFeels.setText("Check your internet connection. (" + e.getMessage() + ")");
                });
            }
        }).start();
    }

    private void updateSchedule() {
        int h = prefs.getInt("hour", 8), m = prefs.getInt("minute", 0);
        timeText.setText(String.format(Locale.US, "%02d:%02d", h, m));
        long next = Scheduler.scheduleNext(this);
        nextText.setText("Next one: " + DateFormat.format("EEEE d MMM, HH:mm", next));
    }

    private void askTime() {
        new TimePickerDialog(this, (picker, h, m) -> {
            prefs.edit().putInt("hour", h).putInt("minute", m).commit();
            updateSchedule();
        }, prefs.getInt("hour", 8), prefs.getInt("minute", 0), true).show();
    }

    private void askCity() {
        EditText in = new EditText(this);
        in.setSingleLine(true);
        in.setHint("e.g. Thessaloniki");
        in.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setPadding(px(20), px(8), px(20), 0);
        wrap.addView(in, new LinearLayout.LayoutParams(-1, -2));
        new AlertDialog.Builder(this)
                .setTitle("Type a city")
                .setView(wrap)
                .setPositiveButton("Save", (d, w) -> saveCity(in.getText().toString()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveCity(String name) {
        if (name.trim().isEmpty()) return;
        locText.setText("Searching…");
        new Thread(() -> {
            try {
                Weather.saveCity(this, name);
                runOnUiThread(() -> refresh(false));
            } catch (Exception e) {
                runOnUiThread(() -> locText.setText("❌ " + e.getMessage()));
            }
        }).start();
    }

    private void onLocationClick() {
        if (Loc.hasPerm(this)) locate();
        else requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOC);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code != REQ_LOC) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) locate();
        else locText.setText("Location permission was refused. You can type a city instead.");
    }

    private void locate() {
        locText.setText("Finding your location…");
        Loc.current(this, loc -> {
            if (loc == null) {
                locText.setText("Couldn't get your location. Is location turned on?");
                refresh(false);
                return;
            }
            new Thread(() -> {
                double lat = loc.getLatitude(), lon = loc.getLongitude();
                Loc.save(this, lat, lon, Loc.placeName(this, lat, lon));
                runOnUiThread(() -> refresh(false));
            }).start();
        });
    }

    // ---------- Small view helpers ----------

    private int px(float v) { return Math.round(v * dp); }

    private LinearLayout.LayoutParams blockLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = px(14);
        return lp;
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private LinearLayout card(String title) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(px(20));
        c.setBackground(bg);
        c.setElevation(px(1));
        c.setPadding(px(18), px(16), px(18), px(18));
        TextView t = text(title, 17, TEXT, true);
        t.setPadding(0, 0, 0, px(10));
        c.addView(t);
        return c;
    }

    private Button button(String label, boolean primary, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTextColor(primary ? Color.WHITE : BLUE_DARK);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(primary ? BLUE : BLUE_SOFT);
        bg.setCornerRadius(px(14));
        b.setBackground(bg);
        b.setStateListAnimator(null);
        b.setPadding(px(14), px(10), px(14), px(10));
        b.setOnClickListener(l);
        return b;
    }
}
