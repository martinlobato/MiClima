package martinlobato.com.ar.clima.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;

/**
 * Calidad del aire por hora (Open-Meteo Air Quality, ~5 días): índice AQI de EE. UU. y
 * concentraciones en µg/m³. Los valores sin dato son NaN.
 */
public final class AirQuality {

    public long[] time = new long[0];
    public double[] aqi = new double[0];
    public double[] pm25 = new double[0];
    public double[] pm10 = new double[0];
    public double[] ozone = new double[0];

    /** Valores actuales (NaN si la respuesta no los trae). */
    public double currentAqi = Double.NaN;
    public double currentPm25 = Double.NaN;
    public double currentPm10 = Double.NaN;
    public double currentOzone = Double.NaN;
    public double currentNo2 = Double.NaN;
    public long currentTime;

    public int size() {
        return time.length;
    }

    /** Índice de la hora que empieza exactamente en {@code millis}, o -1 si no está en la serie. */
    public int indexOfTime(long millis) {
        int lo = 0;
        int hi = time.length - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (time[mid] < millis) lo = mid + 1;
            else if (time[mid] > millis) hi = mid - 1;
            else return mid;
        }
        return -1;
    }

    /** AQI de la hora que empieza en {@code millis}; NaN si no hay dato. */
    public double aqiAt(long millis) {
        int i = indexOfTime(millis);
        return i < 0 ? Double.NaN : aqi[i];
    }

    public static AirQuality parse(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        if (root.optBoolean("error", false)) {
            throw new JSONException("API error: " + root.optString("reason"));
        }
        Calendar cal = Calendar.getInstance(Forecast.resolveTimeZone(
                root.optString("timezone", "GMT"), root.optInt("utc_offset_seconds", 0)));
        AirQuality a = new AirQuality();

        JSONObject h = root.optJSONObject("hourly");
        if (h != null && h.optJSONArray("time") != null) {
            JSONArray times = h.getJSONArray("time");
            int n = times.length();
            a.time = new long[n];
            for (int i = 0; i < n; i++) {
                a.time[i] = ForecastParser.parseTime(times.optString(i, null), cal);
            }
            a.aqi = nums(h, "us_aqi", n);
            a.pm25 = nums(h, "pm2_5", n);
            a.pm10 = nums(h, "pm10", n);
            a.ozone = nums(h, "ozone", n);
        }

        JSONObject c = root.optJSONObject("current");
        if (c != null) {
            a.currentTime = ForecastParser.parseTime(c.optString("time", null), cal);
            a.currentAqi = c.optDouble("us_aqi", Double.NaN);
            a.currentPm25 = c.optDouble("pm2_5", Double.NaN);
            a.currentPm10 = c.optDouble("pm10", Double.NaN);
            a.currentOzone = c.optDouble("ozone", Double.NaN);
            a.currentNo2 = c.optDouble("nitrogen_dioxide", Double.NaN);
        }
        return a;
    }

    private static double[] nums(JSONObject parent, String key, int size) {
        JSONArray arr = parent.optJSONArray(key);
        double[] out = new double[size];
        for (int i = 0; i < size; i++) {
            out[i] = (arr != null && i < arr.length()) ? arr.optDouble(i, Double.NaN) : Double.NaN;
        }
        return out;
    }
}
