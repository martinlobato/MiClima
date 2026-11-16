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

    private static final String CACHE_FILE = "forecast_cache.json";

    private WeatherRepository() {
    }

    /** Último pronóstico guardado, o null si no hay (o si el archivo está dañado). */
    public static Forecast loadCached(Context context) {
        File file = new File(context.getFilesDir(), CACHE_FILE);
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
            return f;
        } catch (IOException | JSONException e) {
            return null;
        }
    }

    /** Baja el pronóstico (bloqueante), lo guarda en caché y lo devuelve. */
    public static Forecast fetch(Context context, double lat, double lon, String place)
            throws IOException, JSONException {
        String raw = WeatherApi.fetchForecast(lat, lon);
        long now = System.currentTimeMillis();
        Forecast f = ForecastParser.parse(raw, now);
        f.place = place;

        JSONObject wrapper = new JSONObject();
        wrapper.put("fetchedAt", now);
        wrapper.put("place", place == null ? JSONObject.NULL : place);
        wrapper.put("raw", raw);
        File tmp = new File(context.getFilesDir(), CACHE_FILE + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(wrapper.toString().getBytes(StandardCharsets.UTF_8));
        }
        File dest = new File(context.getFilesDir(), CACHE_FILE);
        if (!tmp.renameTo(dest)) {
            throw new IOException("No se pudo guardar la caché");
        }
        Prefs.setLastPlace(context, lat, lon, place);
        return f;
    }
}
