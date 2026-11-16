package martinlobato.com.ar.clima.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/** Búsqueda de ciudades con la API de geocoding de Open-Meteo (sin API key). */
public final class GeocodingApi {

    private static final String BASE = "https://geocoding-api.open-meteo.com/v1/search";
    private static final int MAX_RESULTS = 8;

    private GeocodingApi() {
    }

    static String searchUrl(String query, String language) {
        try {
            return BASE + "?name=" + URLEncoder.encode(query.trim(), "UTF-8")
                    + "&count=" + MAX_RESULTS
                    + "&language=" + URLEncoder.encode(language, "UTF-8")
                    + "&format=json";
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e); // UTF-8 siempre existe
        }
    }

    /** Busca lugares por nombre (bloqueante). Lista vacía si no hay coincidencias. */
    public static List<Place> search(String query, String language) throws IOException, JSONException {
        return parse(Http.get(searchUrl(query, language)));
    }

    /** Convierte la respuesta de la API; sin la clave "results" significa "sin coincidencias". */
    public static List<Place> parse(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        if (root.optBoolean("error", false)) {
            throw new JSONException("API error: " + root.optString("reason"));
        }
        List<Place> out = new ArrayList<>();
        JSONArray results = root.optJSONArray("results");
        if (results == null) return out;
        for (int i = 0; i < results.length(); i++) {
            JSONObject r = results.optJSONObject(i);
            if (r == null || !r.has("latitude") || !r.has("longitude")) continue;
            String name = text(r, "name");
            if (name == null) continue;
            out.add(new Place(r.optLong("id", 0), name, text(r, "admin1"), text(r, "country"),
                    r.getDouble("latitude"), r.getDouble("longitude")));
        }
        return out;
    }

    private static String text(JSONObject o, String key) {
        if (o.isNull(key)) return null;
        String value = o.optString(key, "");
        return value.isEmpty() ? null : value;
    }
}
