package martinlobato.com.ar.clima.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Calendar;
import java.util.TimeZone;

/** Convierte la respuesta JSON de Open-Meteo (/v1/forecast, tiempos ISO locales) en un {@link Forecast}. */
public final class ForecastParser {

    private ForecastParser() {
    }

    public static Forecast parse(String json, long fetchedAt) throws JSONException {
        JSONObject root = new JSONObject(json);
        if (root.optBoolean("error", false)) {
            throw new JSONException("API error: " + root.optString("reason"));
        }

        Forecast f = new Forecast();
        f.fetchedAt = fetchedAt;
        f.latitude = root.getDouble("latitude");
        f.longitude = root.getDouble("longitude");
        f.timezoneId = root.optString("timezone", "GMT");
        f.utcOffsetSeconds = root.optInt("utc_offset_seconds", 0);
        Calendar cal = Calendar.getInstance(Forecast.resolveTimeZone(f.timezoneId, f.utcOffsetSeconds));

        JSONObject h = root.getJSONObject("hourly");
        f.hourly.time = times(h.getJSONArray("time"), cal);
        f.hourly.isDay = bools(h.optJSONArray("is_day"), f.hourly.size());
        int n = f.hourly.size();
        f.hourly.temp = nums(h, "temperature_2m", n);
        f.hourly.apparent = nums(h, "apparent_temperature", n);
        f.hourly.humidity = nums(h, "relative_humidity_2m", n);
        f.hourly.dewPoint = nums(h, "dew_point_2m", n);
        f.hourly.precipProb = nums(h, "precipitation_probability", n);
        f.hourly.precip = nums(h, "precipitation", n);
        f.hourly.code = nums(h, "weather_code", n);
        f.hourly.pressure = nums(h, "pressure_msl", n);
        f.hourly.cloud = nums(h, "cloud_cover", n);
        f.hourly.visibility = nums(h, "visibility", n);
        f.hourly.wind = nums(h, "wind_speed_10m", n);
        f.hourly.windDir = nums(h, "wind_direction_10m", n);
        f.hourly.gusts = nums(h, "wind_gusts_10m", n);
        f.hourly.uv = nums(h, "uv_index", n);
        f.hourly.snowfall = nums(h, "snowfall", n);
        f.hourly.snowDepth = nums(h, "snow_depth", n);
        f.hourly.freezingLevel = nums(h, "freezing_level_height", n);

        JSONObject d = root.getJSONObject("daily");
        f.daily.time = times(d.getJSONArray("time"), cal);
        int m = f.daily.size();
        f.daily.sunrise = times(d.optJSONArray("sunrise"), cal, m);
        f.daily.sunset = times(d.optJSONArray("sunset"), cal, m);
        f.daily.code = nums(d, "weather_code", m);
        f.daily.tempMax = nums(d, "temperature_2m_max", m);
        f.daily.tempMin = nums(d, "temperature_2m_min", m);
        f.daily.uvMax = nums(d, "uv_index_max", m);
        f.daily.precipSum = nums(d, "precipitation_sum", m);
        f.daily.precipProbMax = nums(d, "precipitation_probability_max", m);
        f.daily.windMax = nums(d, "wind_speed_10m_max", m);
        f.daily.gustsMax = nums(d, "wind_gusts_10m_max", m);
        f.daily.windDirDominant = nums(d, "wind_direction_10m_dominant", m);
        f.daily.snowfallSum = nums(d, "snowfall_sum", m);

        JSONObject c = root.optJSONObject("current");
        if (c != null) {
            f.current.time = parseTime(c.optString("time", null), cal);
            f.current.isDay = c.optInt("is_day", 1) == 1;
            f.current.temp = c.optDouble("temperature_2m", Double.NaN);
            f.current.apparent = c.optDouble("apparent_temperature", Double.NaN);
            f.current.humidity = c.optDouble("relative_humidity_2m", Double.NaN);
            f.current.precipitation = c.optDouble("precipitation", Double.NaN);
            f.current.code = c.optDouble("weather_code", Double.NaN);
            f.current.cloud = c.optDouble("cloud_cover", Double.NaN);
            f.current.pressure = c.optDouble("pressure_msl", Double.NaN);
            f.current.wind = c.optDouble("wind_speed_10m", Double.NaN);
            f.current.windDir = c.optDouble("wind_direction_10m", Double.NaN);
            f.current.gusts = c.optDouble("wind_gusts_10m", Double.NaN);
        }
        trimTrailingGaps(f);
        return f;
    }

    /**
     * Algunos modelos cubren menos de 16 días: la API igual devuelve todas las fechas, con null
     * al final. Se recortan esas horas y días vacíos para no mostrar filas de "—".
     */
    private static void trimTrailingGaps(Forecast f) {
        Forecast.Hourly h = f.hourly;
        int hours = h.size();
        while (hours > 0 && Double.isNaN(h.temp[hours - 1])) hours--;
        if (hours > 0 && hours < h.size()) {
            h.time = Arrays.copyOf(h.time, hours);
            h.isDay = Arrays.copyOf(h.isDay, hours);
            h.temp = Arrays.copyOf(h.temp, hours);
            h.apparent = Arrays.copyOf(h.apparent, hours);
            h.humidity = Arrays.copyOf(h.humidity, hours);
            h.dewPoint = Arrays.copyOf(h.dewPoint, hours);
            h.precipProb = Arrays.copyOf(h.precipProb, hours);
            h.precip = Arrays.copyOf(h.precip, hours);
            h.code = Arrays.copyOf(h.code, hours);
            h.pressure = Arrays.copyOf(h.pressure, hours);
            h.cloud = Arrays.copyOf(h.cloud, hours);
            h.visibility = Arrays.copyOf(h.visibility, hours);
            h.wind = Arrays.copyOf(h.wind, hours);
            h.windDir = Arrays.copyOf(h.windDir, hours);
            h.gusts = Arrays.copyOf(h.gusts, hours);
            h.uv = Arrays.copyOf(h.uv, hours);
            h.snowfall = Arrays.copyOf(h.snowfall, hours);
            h.snowDepth = Arrays.copyOf(h.snowDepth, hours);
            h.freezingLevel = Arrays.copyOf(h.freezingLevel, hours);
        }
        Forecast.Daily d = f.daily;
        int days = d.size();
        while (days > 0 && Double.isNaN(d.tempMax[days - 1]) && Double.isNaN(d.tempMin[days - 1])) days--;
        if (days > 0 && days < d.size()) {
            d.time = Arrays.copyOf(d.time, days);
            d.sunrise = Arrays.copyOf(d.sunrise, days);
            d.sunset = Arrays.copyOf(d.sunset, days);
            d.code = Arrays.copyOf(d.code, days);
            d.tempMax = Arrays.copyOf(d.tempMax, days);
            d.tempMin = Arrays.copyOf(d.tempMin, days);
            d.uvMax = Arrays.copyOf(d.uvMax, days);
            d.precipSum = Arrays.copyOf(d.precipSum, days);
            d.precipProbMax = Arrays.copyOf(d.precipProbMax, days);
            d.windMax = Arrays.copyOf(d.windMax, days);
            d.gustsMax = Arrays.copyOf(d.gustsMax, days);
            d.windDirDominant = Arrays.copyOf(d.windDirDominant, days);
            d.snowfallSum = Arrays.copyOf(d.snowfallSum, days);
        }
    }

    private static double[] nums(JSONObject parent, String key, int size) {
        JSONArray arr = parent.optJSONArray(key);
        double[] out = new double[size];
        for (int i = 0; i < size; i++) {
            out[i] = (arr != null && i < arr.length()) ? arr.optDouble(i, Double.NaN) : Double.NaN;
        }
        return out;
    }

    private static boolean[] bools(JSONArray arr, int size) {
        boolean[] out = new boolean[size];
        for (int i = 0; i < size; i++) {
            out[i] = arr == null || i >= arr.length() || arr.optInt(i, 1) == 1;
        }
        return out;
    }

    private static long[] times(JSONArray arr, Calendar cal) {
        return times(arr, cal, arr.length());
    }

    private static long[] times(JSONArray arr, Calendar cal, int size) {
        long[] out = new long[size];
        for (int i = 0; i < size; i++) {
            out[i] = (arr != null && i < arr.length() && !arr.isNull(i))
                    ? parseTime(arr.optString(i, null), cal) : 0L;
        }
        return out;
    }

    /** Parsea "yyyy-MM-dd" o "yyyy-MM-ddTHH:mm" como hora local de la zona de {@code cal}. */
    static long parseTime(String iso, Calendar cal) {
        if (iso == null || iso.length() < 10) return 0L;
        try {
            int year = Integer.parseInt(iso.substring(0, 4));
            int month = Integer.parseInt(iso.substring(5, 7));
            int day = Integer.parseInt(iso.substring(8, 10));
            int hour = 0;
            int minute = 0;
            if (iso.length() >= 16) {
                hour = Integer.parseInt(iso.substring(11, 13));
                minute = Integer.parseInt(iso.substring(14, 16));
            }
            cal.clear();
            cal.set(year, month - 1, day, hour, minute, 0);
            return cal.getTimeInMillis();
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    static long parseTime(String iso, TimeZone tz) {
        return parseTime(iso, Calendar.getInstance(tz));
    }
}
