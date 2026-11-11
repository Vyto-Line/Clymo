package com.alex.dresscast;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Fetches today's forecast from Open-Meteo (free, no API key) and builds the advice. */
public class Weather {

    public static final String PREFS = "dresscast";

    public static class Period {
        public String name;
        public int code;
        public double feel;
        public int rain;
    }

    public static class Result {
        public String city;
        public int code;
        public double tMin, tMax, feelMin, feelMax, wind, uv;
        public int rainPct;
        public List<Period> periods = new ArrayList<>();
        public String outfit, title, body;
    }

    public static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Looks up a city name and stores its coordinates. Returns the resolved name. */
    public static String saveCity(Context ctx, String city) throws Exception {
        String url = "https://geocoding-api.open-meteo.com/v1/search?count=1&name="
                + URLEncoder.encode(city.trim(), "UTF-8");
        JSONObject json = new JSONObject(get(url));
        JSONArray results = json.optJSONArray("results");
        if (results == null || results.length() == 0) {
            throw new Exception("City not found: " + city);
        }
        JSONObject r = results.getJSONObject(0);
        String country = r.optString("country", "");
        String name = r.getString("name") + (country.isEmpty() ? "" : ", " + country);
        prefs(ctx).edit()
                .putString("city", name)
                .putFloat("lat", (float) r.getDouble("latitude"))
                .putFloat("lon", (float) r.getDouble("longitude"))
                .putBoolean("useGps", false)
                .apply();
        return name;
    }

    public static Result fetchToday(Context ctx) throws Exception {
        SharedPreferences p = prefs(ctx);
        if (p.getBoolean("useGps", false)) {
            try { Loc.updateFromLastKnown(ctx); } catch (Exception ignored) { }
        }
        if (!p.contains("lat")) saveCity(ctx, "Athens");
        float lat = p.getFloat("lat", 37.98f);
        float lon = p.getFloat("lon", 23.73f);

        String url = String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                        + "&hourly=apparent_temperature,precipitation_probability,weather_code"
                        + "&daily=weather_code,temperature_2m_max,temperature_2m_min,"
                        + "apparent_temperature_max,apparent_temperature_min,"
                        + "precipitation_sum,wind_speed_10m_max,uv_index_max"
                        + "&timezone=auto&forecast_days=1", lat, lon);
        JSONObject json = new JSONObject(get(url));
        JSONObject d = json.getJSONObject("daily");
        JSONObject h = json.getJSONObject("hourly");
        JSONArray hFeel = h.getJSONArray("apparent_temperature");
        JSONArray hRain = h.getJSONArray("precipitation_probability");
        JSONArray hCode = h.getJSONArray("weather_code");

        Result r = new Result();
        r.city = p.getString("city", "Athens, Greece");
        r.code = d.getJSONArray("weather_code").optInt(0, 0);
        r.tMax = d.getJSONArray("temperature_2m_max").optDouble(0, 0);
        r.tMin = d.getJSONArray("temperature_2m_min").optDouble(0, 0);
        r.wind = d.getJSONArray("wind_speed_10m_max").optDouble(0, 0);
        r.uv = d.getJSONArray("uv_index_max").optDouble(0, 0);
        double rainMm = d.getJSONArray("precipitation_sum").optDouble(0, 0);

        // Daytime hours (07:00–22:00) decide the outfit, not the night.
        double dayMin = 999, dayMax = -999;
        int dayRain = 0, worst = 0;
        for (int i = 7; i <= 22 && i < hFeel.length(); i++) {
            double f = hFeel.optDouble(i, Double.NaN);
            if (!Double.isNaN(f)) {
                dayMin = Math.min(dayMin, f);
                dayMax = Math.max(dayMax, f);
            }
            dayRain = Math.max(dayRain, hRain.optInt(i, 0));
            worst = Math.max(worst, hCode.optInt(i, 0));
        }
        if (dayMin > dayMax) {
            dayMin = d.getJSONArray("apparent_temperature_min").optDouble(0, r.tMin);
            dayMax = d.getJSONArray("apparent_temperature_max").optDouble(0, r.tMax);
        }
        r.feelMin = dayMin;
        r.feelMax = dayMax;
        r.rainPct = dayRain;

        String[] names = {"Morning", "Afternoon", "Evening"};
        int[][] spans = {{7, 11}, {12, 17}, {18, 22}};
        for (int k = 0; k < names.length; k++) {
            double sum = 0;
            int n = 0, rain = 0, code = 0;
            for (int i = spans[k][0]; i <= spans[k][1] && i < hFeel.length(); i++) {
                double f = hFeel.optDouble(i, Double.NaN);
                if (!Double.isNaN(f)) { sum += f; n++; }
                rain = Math.max(rain, hRain.optInt(i, 0));
                code = Math.max(code, hCode.optInt(i, 0));
            }
            if (n == 0) continue;
            Period pe = new Period();
            pe.name = names[k];
            pe.feel = sum / n;
            pe.rain = rain;
            pe.code = code;
            r.periods.add(pe);
        }

        r.outfit = outfit(r, rainMm, worst);
        r.title = String.format(Locale.US, "%s %s · %.0f° / %.0f°",
                emoji(r.code), describe(r.code), r.tMax, r.tMin);

        StringBuilder b = new StringBuilder();
        for (int i = 0; i < r.periods.size(); i++) {
            Period pe = r.periods.get(i);
            if (i > 0) b.append("  ·  ");
            b.append(pe.name).append(' ').append(emoji(pe.code))
                    .append(String.format(Locale.US, " %.0f°", pe.feel));
        }
        b.append(String.format(Locale.US, "\nRain %d%% · Wind %.0f km/h · UV %.0f\n\n👕 ",
                r.rainPct, r.wind, r.uv));
        b.append(r.outfit).append("\n📍 ").append(r.city);
        r.body = b.toString();
        return r;
    }

    static String outfit(Result r, double rainMm, int worstCode) {
        List<String> items = new ArrayList<>();
        double avg = (r.feelMin + r.feelMax) / 2;

        if (avg < 0) {
            items.add("thermal layer, warm sweater and a heavy winter coat");
            items.add("hat, scarf and gloves");
        } else if (avg < 7) {
            items.add("sweater and a warm coat");
            items.add("scarf");
        } else if (avg < 13) {
            items.add("long sleeves with a jacket or light coat");
        } else if (avg < 18) {
            items.add("long sleeves or a light sweater, jeans");
        } else if (avg < 24) {
            items.add("t-shirt with a light layer");
        } else if (avg < 29) {
            items.add("t-shirt and light trousers or shorts");
        } else {
            items.add("very light, breathable clothes (linen, shorts)");
        }

        if (r.feelMax - r.feelMin >= 8) {
            items.add(String.format(Locale.US,
                    "dress in layers, it goes from %.0f° to %.0f° during the day",
                    r.feelMin, r.feelMax));
        }

        List<String> wet = new ArrayList<>();
        for (Period pe : r.periods) {
            if (pe.rain >= 50) wet.add(pe.name.toLowerCase(Locale.US));
        }
        boolean snow = (worstCode >= 71 && worstCode <= 77) || worstCode == 85 || worstCode == 86;
        if (snow) {
            items.add("waterproof boots, snow is expected");
        } else if (!wet.isEmpty() && wet.size() < r.periods.size()) {
            items.add("umbrella, rain is likely in the " + String.join(" and ", wet));
        } else if (r.rainPct >= 50 || rainMm >= 1) {
            items.add("umbrella or rain jacket and waterproof shoes");
        } else if (r.rainPct >= 25) {
            items.add("maybe a small umbrella just in case");
        }
        if (r.wind >= 35) items.add("windproof jacket, it's windy");
        if (r.uv >= 6) items.add("sunglasses, sunscreen and a cap");
        else if (r.uv >= 3 && avg >= 18) items.add("sunglasses");

        String s = String.join("; ", items) + ".";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    static String describe(int c) {
        if (c == 0) return "Clear";
        if (c <= 2) return "Partly cloudy";
        if (c == 3) return "Overcast";
        if (c <= 48) return "Foggy";
        if (c <= 57) return "Drizzle";
        if (c <= 67) return "Rain";
        if (c <= 77) return "Snow";
        if (c <= 82) return "Showers";
        if (c <= 86) return "Snow showers";
        return "Thunderstorms";
    }

    static String emoji(int c) {
        if (c == 0) return "☀️";
        if (c <= 2) return "🌤️";
        if (c == 3) return "☁️";
        if (c <= 48) return "🌫️";
        if (c <= 67 || (c >= 80 && c <= 82)) return "🌧️";
        if (c <= 77 || c == 85 || c == 86) return "❄️";
        return "⛈️";
    }

    static String get(String urlStr) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(urlStr).openConnection();
        con.setConnectTimeout(15000);
        con.setReadTimeout(15000);
        try (BufferedReader r = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            return sb.toString();
        } finally {
            con.disconnect();
        }
    }
}
