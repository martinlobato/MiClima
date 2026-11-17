package martinlobato.com.ar.clima.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Valores normales de un lugar: promedio de la máxima y la mínima de cada fecha del año,
 * calculado sobre varios años de historia y suavizado con una ventana de ±7 días (así un día
 * raro de un solo año no mueve la referencia).
 */
public final class ClimateNormals {

    /** Días de un año con 29 de febrero incluido. */
    static final int DAYS = 366;
    private static final int WINDOW = 7;
    private static final int[] MONTH_START = {0, 31, 60, 91, 121, 152, 182, 213, 244, 274, 305, 335};

    public double latitude;
    public double longitude;
    public long fetchedAt;
    /** Indexados por {@link #dayIndex}. NaN si no hubo datos. */
    public final double[] max = new double[DAYS];
    public final double[] min = new double[DAYS];

    /** Posición en el año (0..365) de una fecha, contando febrero siempre con 29 días. */
    public static int dayIndex(int month1to12, int day) {
        return MONTH_START[month1to12 - 1] + day - 1;
    }

    public double maxFor(int month1to12, int day) {
        return max[dayIndex(month1to12, day)];
    }

    public double minFor(int month1to12, int day) {
        return min[dayIndex(month1to12, day)];
    }

    /** Calcula los normales a partir de la respuesta diaria del archivo histórico de Open-Meteo. */
    public static ClimateNormals fromArchive(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        if (root.optBoolean("error", false)) {
            throw new JSONException("API error: " + root.optString("reason"));
        }
        JSONObject d = root.getJSONObject("daily");
        JSONArray times = d.getJSONArray("time");
        JSONArray maxArr = d.getJSONArray("temperature_2m_max");
        JSONArray minArr = d.getJSONArray("temperature_2m_min");
        int n = times.length();
        String[] dates = new String[n];
        double[] maxs = new double[n];
        double[] mins = new double[n];
        for (int i = 0; i < n; i++) {
            dates[i] = times.optString(i, "");
            maxs[i] = maxArr.optDouble(i, Double.NaN);
            mins[i] = minArr.optDouble(i, Double.NaN);
        }
        ClimateNormals normals = compute(dates, maxs, mins);
        normals.latitude = root.optDouble("latitude", Double.NaN);
        normals.longitude = root.optDouble("longitude", Double.NaN);
        return normals;
    }

    /**
     * @param dates fechas "yyyy-MM-dd"
     * @param maxs  máxima de cada fecha (NaN si falta)
     * @param mins  mínima de cada fecha (NaN si falta)
     */
    public static ClimateNormals compute(String[] dates, double[] maxs, double[] mins) {
        double[] sumMax = new double[DAYS];
        double[] sumMin = new double[DAYS];
        int[] countMax = new int[DAYS];
        int[] countMin = new int[DAYS];
        for (int i = 0; i < dates.length; i++) {
            String date = dates[i];
            if (date == null || date.length() < 10) continue;
            int month;
            int day;
            try {
                month = Integer.parseInt(date.substring(5, 7));
                day = Integer.parseInt(date.substring(8, 10));
            } catch (NumberFormatException e) {
                continue;
            }
            if (month < 1 || month > 12 || day < 1 || day > 31) continue;
            int idx = dayIndex(month, day);
            if (!Double.isNaN(maxs[i])) {
                sumMax[idx] += maxs[i];
                countMax[idx]++;
            }
            if (!Double.isNaN(mins[i])) {
                sumMin[idx] += mins[i];
                countMin[idx]++;
            }
        }
        ClimateNormals out = new ClimateNormals();
        smooth(sumMax, countMax, out.max);
        smooth(sumMin, countMin, out.min);
        return out;
    }

    /** Promedio de cada día con sus ±7 vecinos (el año es circular: diciembre sigue en enero). */
    private static void smooth(double[] sum, int[] count, double[] out) {
        for (int i = 0; i < DAYS; i++) {
            double total = 0;
            int samples = 0;
            for (int k = -WINDOW; k <= WINDOW; k++) {
                int j = ((i + k) % DAYS + DAYS) % DAYS;
                total += sum[j];
                samples += count[j];
            }
            out[i] = samples == 0 ? Double.NaN : total / samples;
        }
    }

    public String toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("lat", latitude);
        o.put("lon", longitude);
        o.put("fetchedAt", fetchedAt);
        o.put("max", array(max));
        o.put("min", array(min));
        return o.toString();
    }

    public static ClimateNormals fromJson(String json) throws JSONException {
        JSONObject o = new JSONObject(json);
        ClimateNormals n = new ClimateNormals();
        n.latitude = o.getDouble("lat");
        n.longitude = o.getDouble("lon");
        n.fetchedAt = o.getLong("fetchedAt");
        JSONArray maxArr = o.getJSONArray("max");
        JSONArray minArr = o.getJSONArray("min");
        for (int i = 0; i < DAYS; i++) {
            n.max[i] = maxArr.optDouble(i, Double.NaN);
            n.min[i] = minArr.optDouble(i, Double.NaN);
        }
        return n;
    }

    private static JSONArray array(double[] values) throws JSONException {
        JSONArray arr = new JSONArray();
        for (double v : values) {
            // JSON no admite NaN: los huecos se guardan como null.
            if (Double.isNaN(v)) arr.put(JSONObject.NULL);
            else arr.put(Math.round(v * 100) / 100.0);
        }
        return arr;
    }
}
