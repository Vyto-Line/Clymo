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

    public static class Result {
        public String title;
        public String body;
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
        String name = r.getString("name") + ", " + r.optString("country", "");
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("city", name)
                .putFloat("lat", (float) r.getDouble("latitude"))
                .putFloat("lon", (float) r.getDouble("longitude"))
                .apply();
        return name;
    }

    public static Result fetchToday(Context ctx) throws Exception {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!p.contains("lat")) saveCity(ctx, "Athens");
        float lat = p.getFloat("lat", 37.98f);
        float lon = p.getFloat("lon", 23.73f);
        String city = p.getString("city", "Athens, Greece");

        String url = String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                        + "&daily=weather_code,temperature_2m_max,temperature_2m_min,"
                        + "apparent_temperature_max,apparent_temperature_min,"
                        + "precipitation_probability_max,precipitation_sum,"
                        + "wind_speed_10m_max,uv_index_max"
                        + "&timezone=auto&forecast_days=1", lat, lon);
        JSONObject d = new JSONObject(get(url)).getJSONObject("daily");

        int code = d.getJSONArray("weather_code").getInt(0);
        double tMax = d.getJSONArray("temperature_2m_max").getDouble(0);
        double tMin = d.getJSONArray("temperature_2m_min").getDouble(0);
        double feelMax = d.getJSONArray("apparent_temperature_max").getDouble(0);
        double feelMin = d.getJSONArray("apparent_temperature_min").getDouble(0);
        int rainPct = d.getJSONArray("precipitation_probability_max").optInt(0, 0);
        double rainMm = d.getJSONArray("precipitation_sum").optDouble(0, 0);
        double wind = d.getJSONArray("wind_speed_10m_max").optDouble(0, 0);
        double uv = d.getJSONArray("uv_index_max").optDouble(0, 0);

        Result res = new Result();
        res.title = String.format(Locale.US, "%s %s, %.0f°–%.0f°C",
                emoji(code), describe(code), tMin, tMax);

        StringBuilder b = new StringBuilder();
        b.append(city).append('\n');
        b.append(String.format(Locale.US,
                "Feels like %.0f°–%.0f°C · Rain %d%% · Wind %.0f km/h · UV %.0f\n\n",
                feelMin, feelMax, rainPct, wind, uv));
        b.append("👕 Wear: ").append(outfit(feelMin, feelMax, code, rainPct, rainMm, wind, uv));
        res.body = b.toString();
        return res;
    }

    static String outfit(double feelMin, double feelMax, int code, int rainPct,
                         double rainMm, double wind, double uv) {
        List<String> items = new ArrayList<>();
        double avg = (feelMin + feelMax) / 2;

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

        if (feelMax - feelMin >= 9) {
            items.add("dress in layers, it's cool early and warmer later");
        }
        boolean snow = (code >= 71 && code <= 77) || code == 85 || code == 86;
        if (snow) {
            items.add("waterproof boots");
        } else if (rainPct >= 50 || rainMm >= 1) {
            items.add("umbrella or rain jacket, waterproof shoes");
        } else if (rainPct >= 25) {
            items.add("maybe a small umbrella just in case");
        }
        if (wind >= 35) items.add("windproof jacket (it's windy)");
        if (uv >= 6) items.add("sunglasses, sunscreen and a cap");
        else if (uv >= 3 && avg >= 18) items.add("sunglasses");

        return String.join("; ", items) + ".";
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
