package martinlobato.com.ar.clima.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;

/**
 * Estado del mar (Open-Meteo Marine): altura y período de olas y temperatura del agua.
 * Para lugares sin mar cerca la API devuelve todo null: en ese caso {@link #hasData()} es false.
 */
public final class Marine {

    public long[] time = new long[0];
    /** Altura de ola en metros. */
    public double[] waveHeight = new double[0];
    /** Período en segundos. */
    public double[] wavePeriod = new double[0];
    /** Temperatura del agua en °C. */
    public double[] seaTemp = new double[0];

    public long currentTime;
    public double currentWaveHeight = Double.NaN;
    public double currentWavePeriod = Double.NaN;
    public double currentWaveDirection = Double.NaN;
    public double currentSeaTemp = Double.NaN;

    public int size() {
        return time.length;
    }

    /** ¿Hay algún dato de mar? (false = lugar sin costa). */
    public boolean hasData() {
        if (!Double.isNaN(currentWaveHeight) || !Double.isNaN(currentSeaTemp)) return true;
        for (double v : waveHeight) {
            if (!Double.isNaN(v)) return true;
        }
        return false;
    }

    /** Índice de la hora que empieza exactamente en {@code millis}, o -1. */
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

    public static Marine parse(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        if (root.optBoolean("error", false)) {
            throw new JSONException("API error: " + root.optString("reason"));
        }
        Calendar cal = Calendar.getInstance(Forecast.resolveTimeZone(
                root.optString("timezone", "GMT"), root.optInt("utc_offset_seconds", 0)));
        Marine m = new Marine();
        JSONObject h = root.optJSONObject("hourly");
        if (h != null && h.optJSONArray("time") != null) {
            JSONArray times = h.getJSONArray("time");
            int n = times.length();
            m.time = new long[n];
            for (int i = 0; i < n; i++) {
                m.time[i] = ForecastParser.parseTime(times.optString(i, null), cal);
            }
            m.waveHeight = nums(h, "wave_height", n);
            m.wavePeriod = nums(h, "wave_period", n);
            m.seaTemp = nums(h, "sea_surface_temperature", n);
        }
        JSONObject c = root.optJSONObject("current");
        if (c != null) {
            m.currentTime = ForecastParser.parseTime(c.optString("time", null), cal);
            m.currentWaveHeight = c.optDouble("wave_height", Double.NaN);
            m.currentWavePeriod = c.optDouble("wave_period", Double.NaN);
            m.currentWaveDirection = c.optDouble("wave_direction", Double.NaN);
            m.currentSeaTemp = c.optDouble("sea_surface_temperature", Double.NaN);
        }
        return m;
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
