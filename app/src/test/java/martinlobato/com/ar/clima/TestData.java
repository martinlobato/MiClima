package martinlobato.com.ar.clima;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Datos sintéticos y utilidades compartidas por los tests de pantalla. */
final class TestData {

    static final int DAYS = 16;
    static final String TZ = "America/Santiago";

    private TestData() {
    }

    static void saveScreenshot(View view, String name) {
        try {
            Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
            view.draw(new Canvas(bitmap));
            File dir = new File("build/screenshots");
            dir.mkdirs();
            try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Escribe una caché recién bajada con 16 días de datos sintéticos a partir de hoy. */
    static void writeCache(Context ctx) throws Exception {
        writeCache(ctx, "gps", "Santiago");
    }

    /** Igual que {@link #writeCache(Context)} pero para la caché de un lugar dado. */
    static void writeCache(Context ctx, String key, String placeName) throws Exception {
        TimeZone tz = TimeZone.getTimeZone(TZ);
        SimpleDateFormat hourFmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US);
        SimpleDateFormat dayFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        hourFmt.setTimeZone(tz);
        dayFmt.setTimeZone(tz);
        Calendar cal = Calendar.getInstance(tz);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        int[] codes = {0, 1, 2, 3, 61, 63, 80, 95, 45, 71, 2, 0, 3, 51, 1, 0};
        String[] hourlyKeys = {"temperature_2m", "apparent_temperature", "relative_humidity_2m",
                "dew_point_2m", "precipitation_probability", "precipitation", "weather_code",
                "pressure_msl", "cloud_cover", "visibility", "wind_speed_10m", "wind_direction_10m",
                "wind_gusts_10m", "uv_index", "is_day"};
        JSONObject hourly = new JSONObject();
        JSONArray hTime = new JSONArray();
        for (String k : hourlyKeys) hourly.put(k, new JSONArray());
        JSONObject daily = new JSONObject();
        String[] dailyKeys = {"time", "weather_code", "temperature_2m_max", "temperature_2m_min",
                "sunrise", "sunset", "uv_index_max", "precipitation_sum",
                "precipitation_probability_max", "wind_speed_10m_max", "wind_gusts_10m_max",
                "wind_direction_10m_dominant"};
        for (String k : dailyKeys) daily.put(k, new JSONArray());

        for (int d = 0; d < DAYS; d++) {
            int code = codes[d];
            boolean wet = code >= 51;
            double min = 9 + (d * 5) % 7;
            double max = min + 9 + (d * 3) % 6;
            String day = dayFmt.format(cal.getTime());
            daily.getJSONArray("time").put(day);
            daily.getJSONArray("weather_code").put(code);
            daily.getJSONArray("temperature_2m_max").put(max);
            daily.getJSONArray("temperature_2m_min").put(min);
            daily.getJSONArray("sunrise").put(day + "T06:34");
            daily.getJSONArray("sunset").put(day + "T20:21");
            daily.getJSONArray("uv_index_max").put(wet ? 4.2 : 9.6);
            daily.getJSONArray("precipitation_sum").put(wet ? 6.4 : 0);
            daily.getJSONArray("precipitation_probability_max").put(wet ? 85 : 5);
            daily.getJSONArray("wind_speed_10m_max").put(22.3);
            daily.getJSONArray("wind_gusts_10m_max").put(44.1);
            daily.getJSONArray("wind_direction_10m_dominant").put(225);
            for (int h = 0; h < 24; h++) {
                hTime.put(hourFmt.format(cal.getTime()));
                double t = min + (max - min) * Math.max(0, Math.sin((h - 6) * Math.PI / 16));
                hourly.getJSONArray("temperature_2m").put(Math.round(t * 10) / 10.0);
                hourly.getJSONArray("apparent_temperature").put(Math.round((t - 1.2) * 10) / 10.0);
                hourly.getJSONArray("relative_humidity_2m").put(35 + (h * 7) % 40);
                hourly.getJSONArray("dew_point_2m").put(6.3);
                hourly.getJSONArray("precipitation_probability").put(wet ? 40 + (h * 5) % 50 : (h % 3) * 5);
                hourly.getJSONArray("precipitation").put(wet ? 0.4 : 0);
                hourly.getJSONArray("weather_code").put(code);
                hourly.getJSONArray("pressure_msl").put(1013.4);
                hourly.getJSONArray("cloud_cover").put(wet ? 90 : 20);
                hourly.getJSONArray("visibility").put(wet ? 8200 : 24140);
                hourly.getJSONArray("wind_speed_10m").put(6 + h % 12);
                hourly.getJSONArray("wind_direction_10m").put(225);
                hourly.getJSONArray("wind_gusts_10m").put(28.4);
                hourly.getJSONArray("uv_index").put(h >= 7 && h <= 20 ? 6.4 : 0);
                hourly.getJSONArray("is_day").put(h >= 7 && h <= 20 ? 1 : 0);
                cal.add(Calendar.HOUR_OF_DAY, 1);
            }
        }
        hourly.put("time", hTime);

        JSONObject current = new JSONObject();
        current.put("time", hourFmt.format(new Date()));
        current.put("temperature_2m", 24.6);
        current.put("relative_humidity_2m", 38);
        current.put("apparent_temperature", 23.9);
        current.put("is_day", 1);
        current.put("precipitation", 0);
        current.put("weather_code", 2);
        current.put("cloud_cover", 35);
        current.put("pressure_msl", 1013.4);
        current.put("wind_speed_10m", 12.6);
        current.put("wind_direction_10m", 225);
        current.put("wind_gusts_10m", 28.4);

        JSONObject root = new JSONObject();
        root.put("latitude", -33.45);
        root.put("longitude", -70.66);
        root.put("timezone", TZ);
        root.put("utc_offset_seconds", tz.getOffset(System.currentTimeMillis()) / 1000);
        root.put("current", current);
        root.put("hourly", hourly);
        root.put("daily", daily);

        JSONObject wrapper = new JSONObject();
        wrapper.put("fetchedAt", System.currentTimeMillis());
        wrapper.put("place", placeName);
        wrapper.put("raw", root.toString());
        try (FileOutputStream out = new FileOutputStream(new File(ctx.getFilesDir(), "gps".equals(key) ? "forecast_cache.json" : "forecast_" + key + ".json"))) {
            out.write(wrapper.toString().getBytes(StandardCharsets.UTF_8));
        }
    }
}
