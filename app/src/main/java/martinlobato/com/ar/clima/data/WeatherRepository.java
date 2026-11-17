package martinlobato.com.ar.clima.data;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import martinlobato.com.ar.clima.Prefs;

/**
 * Pronóstico con caché en disco: la app abre al instante con el último dato guardado y
 * funciona sin conexión. Se guarda la respuesta cruda de la API junto con cuándo se bajó.
 */
public final class WeatherRepository {

    /** Clave de caché de la ubicación actual del teléfono. */
    public static final String KEY_CURRENT_LOCATION = "gps";

    /** Una caché por lugar: la ubicación actual y cada lugar guardado ({@link Place#cacheKey()}). */
    private static File cacheFile(Context context, String key) {
        String name = KEY_CURRENT_LOCATION.equals(key) ? "forecast_cache.json" : "forecast_" + key + ".json";
        return new File(context.getFilesDir(), name);
    }

    private WeatherRepository() {
    }

    /** Último pronóstico guardado, o null si no hay (o si el archivo está dañado). */
    public static Forecast loadCached(Context context, String key) {
        File file = cacheFile(context, key);
        if (!file.exists()) return null;
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int read = 0;
            while (read < data.length) {
                int n = in.read(data, read, data.length - read);
                if (n < 0) break;
                read += n;
            }
            JSONObject wrapper = new JSONObject(new String(data, 0, read, StandardCharsets.UTF_8));
            Forecast f = ForecastParser.parse(wrapper.getString("raw"), wrapper.getLong("fetchedAt"));
            f.place = wrapper.isNull("place") ? null : wrapper.optString("place", null);
            f.model = WeatherModel.parse(wrapper.optString("model", null));
            f.air = parseAir(wrapper.isNull("air") ? null : wrapper.optString("air", null));
            f.marine = parseMarine(wrapper.isNull("marine") ? null : wrapper.optString("marine", null));
            return f;
        } catch (IOException | JSONException e) {
            return null;
        }
    }

    /** Estado del mar, o null si no hay texto, no se puede leer o el lugar no tiene costa. */
    private static Marine parseMarine(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        try {
            Marine m = Marine.parse(raw);
            return m.hasData() ? m : null;
        } catch (JSONException e) {
            return null;
        }
    }

    private static AirQuality parseAir(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        try {
            return AirQuality.parse(raw);
        } catch (JSONException e) {
            return null;
        }
    }

    /**
     * Baja el pronóstico (bloqueante) con el modelo elegido en Ajustes, lo guarda en caché y lo
     * devuelve. La calidad del aire se pide aparte y es opcional: si falla, el pronóstico sigue.
     */
    public static Forecast fetch(Context context, String key, double lat, double lon, String place)
            throws IOException, JSONException {
        WeatherModel model = Prefs.getModel(context);
        String raw = WeatherApi.fetchForecast(lat, lon, model);
        long now = System.currentTimeMillis();
        Forecast f = ForecastParser.parse(raw, now);
        f.place = place;
        f.model = model;

        String airRaw = null;
        try {
            airRaw = WeatherApi.fetchAirQuality(lat, lon);
            f.air = AirQuality.parse(airRaw);
        } catch (IOException | JSONException e) {
            airRaw = null;
        }

        // Mar: también opcional. Para un lugar guardado sin costa no se vuelve a preguntar;
        // la ubicación actual se mueve, así que ahí se consulta siempre.
        String marineRaw = null;
        boolean fixedPlace = !KEY_CURRENT_LOCATION.equals(key);
        if (!(fixedPlace && Prefs.isInland(context, key))) {
            try {
                marineRaw = WeatherApi.fetchMarine(lat, lon);
                f.marine = parseMarine(marineRaw);
                if (f.marine == null) {
                    marineRaw = null;
                    if (fixedPlace) Prefs.setInland(context, key);
                }
            } catch (IOException e) {
                marineRaw = null;
            }
        }

        JSONObject wrapper = new JSONObject();
        wrapper.put("fetchedAt", now);
        wrapper.put("place", place == null ? JSONObject.NULL : place);
        wrapper.put("model", model.name());
        wrapper.put("marine", marineRaw == null ? JSONObject.NULL : marineRaw);
        wrapper.put("air", airRaw == null ? JSONObject.NULL : airRaw);
        wrapper.put("raw", raw);
        File dest = cacheFile(context, key);
        File tmp = new File(dest.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(wrapper.toString().getBytes(StandardCharsets.UTF_8));
        }
        if (!tmp.renameTo(dest)) {
            throw new IOException("No se pudo guardar la caché");
        }
        if (KEY_CURRENT_LOCATION.equals(key)) {
            // Permite actualizar la ubicación actual aunque después no haya permiso o señal.
            Prefs.setLastPlace(context, lat, lon, place);
        }
        return f;
    }

    /**
     * Actualiza el lugar seleccionado sin leer la ubicación (para segundo plano): un lugar
     * guardado usa sus coordenadas; la ubicación actual, las últimas conocidas.
     *
     * @return el pronóstico nuevo, o null si todavía no hay ningún lugar que actualizar
     */
    public static Forecast refreshSelected(Context context) throws IOException, JSONException {
        Place place = PlaceStore.selected(context);
        if (place != null) {
            return fetch(context, place.cacheKey(), place.latitude, place.longitude, place.name);
        }
        if (!Prefs.hasLastPlace(context)) return null;
        return fetch(context, KEY_CURRENT_LOCATION, Prefs.getLastLat(context), Prefs.getLastLon(context),
                Prefs.getLastPlace(context));
    }

    public static void deleteCache(Context context, String key) {
        for (String prefix : new String[]{"", "compare_", "normals_"}) {
            //noinspection ResultOfMethodCallIgnored
            extraFile(context, prefix, key).delete();
        }
    }

    /** Archivos por lugar además del pronóstico: comparación de modelos y normales. */
    private static File extraFile(Context context, String prefix, String key) {
        return prefix.isEmpty() ? cacheFile(context, key)
                : new File(context.getFilesDir(), prefix + key + ".json");
    }

    private static String readText(File file) throws IOException {
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int read = 0;
            while (read < data.length) {
                int n = in.read(data, read, data.length - read);
                if (n < 0) break;
                read += n;
            }
            return new String(data, 0, read, StandardCharsets.UTF_8);
        }
    }

    private static void writeText(File dest, String text) throws IOException {
        File tmp = new File(dest.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
        if (!tmp.renameTo(dest)) {
            throw new IOException("No se pudo guardar " + dest.getName());
        }
    }

    // ------------------------------------------------------------------ comparación de modelos

    /** La comparación guardada sirve durante este tiempo; después se vuelve a bajar. */
    private static final long COMPARISON_TTL_MS = 3 * 60 * 60 * 1000L;

    /**
     * Comparación de modelos del lugar (bloqueante): usa la guardada si es reciente; si no, la baja.
     */
    public static ModelComparison comparison(Context context, String key, double lat, double lon)
            throws IOException, JSONException {
        File file = extraFile(context, "compare_", key);
        long now = System.currentTimeMillis();
        if (file.exists()) {
            try {
                JSONObject wrapper = new JSONObject(readText(file));
                long fetchedAt = wrapper.getLong("fetchedAt");
                if (now - fetchedAt < COMPARISON_TTL_MS
                        && Math.abs(wrapper.optDouble("lat", 999) - lat) < 0.05
                        && Math.abs(wrapper.optDouble("lon", 999) - lon) < 0.05) {
                    return ModelComparison.parse(wrapper.getString("raw"), fetchedAt);
                }
            } catch (IOException | JSONException e) {
                // Guardado dañado: se baja de nuevo.
            }
        }
        String raw = WeatherApi.fetchComparison(lat, lon);
        ModelComparison c = ModelComparison.parse(raw, now);
        JSONObject wrapper = new JSONObject();
        wrapper.put("fetchedAt", now);
        wrapper.put("lat", lat);
        wrapper.put("lon", lon);
        wrapper.put("raw", raw);
        writeText(file, wrapper.toString());
        return c;
    }

    // ------------------------------------------------------------------ normales climáticos

    /** Años de historia que se promedian. */
    private static final int NORMALS_YEARS = 10;
    /** Los normales casi no cambian: se recalculan cada tanto por si se sumó un año. */
    private static final long NORMALS_TTL_MS = 120L * 24 * 60 * 60 * 1000;

    /** Normales guardados del lugar, o null si no hay o corresponden a otras coordenadas. */
    public static ClimateNormals cachedNormals(Context context, String key, double lat, double lon) {
        File file = extraFile(context, "normals_", key);
        if (!file.exists()) return null;
        try {
            ClimateNormals n = ClimateNormals.fromJson(readText(file));
            // La ubicación actual puede haberse movido: los normales valen cerca de donde se pidieron.
            if (Math.abs(n.latitude - lat) > 0.15 || Math.abs(n.longitude - lon) > 0.15) return null;
            return n;
        } catch (IOException | JSONException e) {
            return null;
        }
    }

    /** Normales del lugar (bloqueante): los guardados si sirven; si no, baja la historia y calcula. */
    public static ClimateNormals normals(Context context, String key, double lat, double lon)
            throws IOException, JSONException {
        long now = System.currentTimeMillis();
        ClimateNormals cached = cachedNormals(context, key, lat, lon);
        if (cached != null && now - cached.fetchedAt < NORMALS_TTL_MS) return cached;
        int lastYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) - 1;
        ClimateNormals n = ClimateNormals.fromArchive(
                WeatherApi.fetchArchive(lat, lon, lastYear - NORMALS_YEARS + 1, lastYear));
        // Se guardan las coordenadas pedidas (no las de la grilla) para poder compararlas después.
        n.latitude = lat;
        n.longitude = lon;
        n.fetchedAt = now;
        writeText(extraFile(context, "normals_", key), n.toJson());
        return n;
    }

    /**
     * Coordenadas del lugar seleccionado sin leer la ubicación: las del lugar guardado o las
     * últimas conocidas de la ubicación actual. Null si todavía no hay ninguna.
     */
    public static double[] selectedCoordinates(Context context) {
        Place place = PlaceStore.selected(context);
        if (place != null) return new double[]{place.latitude, place.longitude};
        if (!Prefs.hasLastPlace(context)) return null;
        return new double[]{Prefs.getLastLat(context), Prefs.getLastLon(context)};
    }
}
