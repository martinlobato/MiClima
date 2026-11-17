package martinlobato.com.ar.clima.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Pronóstico horario del mismo lugar según varios modelos, para compararlos. Las series están
 * indexadas [modelo][hora], con los modelos en el orden de {@link #MODELS}; NaN donde un modelo
 * no llega (cada uno cubre distinta cantidad de días).
 */
public final class ModelComparison {

    /** Modelos comparados: todos los de {@link WeatherModel} menos el automático. */
    public static final WeatherModel[] MODELS = {WeatherModel.ECMWF, WeatherModel.GFS,
            WeatherModel.ICON, WeatherModel.GEM, WeatherModel.METEOFRANCE};

    public String timezoneId;
    public int utcOffsetSeconds;
    public long fetchedAt;
    public long[] time = new long[0];
    public double[][] temp = new double[0][];
    public double[][] precip = new double[0][];
    public double[][] wind = new double[0][];

    public TimeZone timeZone() {
        return Forecast.resolveTimeZone(timezoneId, utcOffsetSeconds);
    }

    /** Parsea la respuesta con varios modelos: cada variable viene como {@code variable_modelo}. */
    public static ModelComparison parse(String json, long fetchedAt) throws JSONException {
        JSONObject root = new JSONObject(json);
        if (root.optBoolean("error", false)) {
            throw new JSONException("API error: " + root.optString("reason"));
        }
        ModelComparison c = new ModelComparison();
        c.fetchedAt = fetchedAt;
        c.timezoneId = root.optString("timezone", "GMT");
        c.utcOffsetSeconds = root.optInt("utc_offset_seconds", 0);
        Calendar cal = Calendar.getInstance(c.timeZone());

        JSONObject h = root.getJSONObject("hourly");
        JSONArray times = h.getJSONArray("time");
        int n = times.length();
        c.time = new long[n];
        for (int i = 0; i < n; i++) {
            c.time[i] = ForecastParser.parseTime(times.optString(i, null), cal);
        }
        c.temp = new double[MODELS.length][];
        c.precip = new double[MODELS.length][];
        c.wind = new double[MODELS.length][];
        for (int m = 0; m < MODELS.length; m++) {
            String suffix = "_" + MODELS[m].apiId;
            c.temp[m] = nums(h, "temperature_2m" + suffix, n);
            c.precip[m] = nums(h, "precipitation" + suffix, n);
            c.wind[m] = nums(h, "wind_speed_10m" + suffix, n);
        }
        return c;
    }

    private static double[] nums(JSONObject parent, String key, int size) {
        JSONArray arr = parent.optJSONArray(key);
        double[] out = new double[size];
        for (int i = 0; i < size; i++) {
            out[i] = (arr != null && i < arr.length()) ? arr.optDouble(i, Double.NaN) : Double.NaN;
        }
        return out;
    }

    /**
     * Mayor diferencia entre modelos de un valor resumen (por ejemplo, la máxima del día de cada
     * modelo); NaN si menos de dos modelos tienen dato.
     */
    public static double spread(double[] perModel) {
        double lo = Double.NaN;
        double hi = Double.NaN;
        int count = 0;
        for (double v : perModel) {
            if (Double.isNaN(v)) continue;
            count++;
            if (!(v >= lo)) lo = v;
            if (!(v <= hi)) hi = v;
        }
        return count < 2 ? Double.NaN : hi - lo;
    }
}
