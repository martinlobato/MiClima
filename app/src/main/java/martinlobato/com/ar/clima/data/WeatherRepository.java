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
            return f;
        } catch (IOException | JSONException e) {
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

        JSONObject wrapper = new JSONObject();
        wrapper.put("fetchedAt", now);
        wrapper.put("place", place == null ? JSONObject.NULL : place);
        wrapper.put("model", model.name());
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
        //noinspection ResultOfMethodCallIgnored
        cacheFile(context, key).delete();
    }
}
