package martinlobato.com.ar.clima.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Lugares guardados y lugar seleccionado. Se guardan como JSON en SharedPreferences (son pocos).
 * El lugar seleccionado es un {@link Place} guardado o, si es null, la ubicación actual del teléfono.
 */
public final class PlaceStore {

    private static final String PREFS_NAME = "places";
    private static final String KEY_PLACES = "places";
    private static final String KEY_SELECTED = "selected_id";
    /** Valor de "seleccionado" que significa ubicación actual. */
    private static final long CURRENT_LOCATION = Long.MIN_VALUE;

    private PlaceStore() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static List<Place> list(Context context) {
        List<Place> out = new ArrayList<>();
        String raw = prefs(context).getString(KEY_PLACES, null);
        if (raw == null) return out;
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                out.add(new Place(o.getLong("id"), o.getString("name"),
                        o.isNull("region") ? null : o.optString("region", null),
                        o.isNull("country") ? null : o.optString("country", null),
                        o.getDouble("lat"), o.getDouble("lon")));
            }
        } catch (JSONException e) {
            out.clear(); // datos dañados: se empieza de nuevo
        }
        return out;
    }

    private static void save(Context context, List<Place> places) {
        JSONArray arr = new JSONArray();
        try {
            for (Place p : places) {
                JSONObject o = new JSONObject();
                o.put("id", p.id);
                o.put("name", p.name);
                o.put("region", p.region == null ? JSONObject.NULL : p.region);
                o.put("country", p.country == null ? JSONObject.NULL : p.country);
                o.put("lat", p.latitude);
                o.put("lon", p.longitude);
                arr.put(o);
            }
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
        prefs(context).edit().putString(KEY_PLACES, arr.toString()).apply();
    }

    /** Agrega el lugar si no estaba (por id) en la posición indicada, o al final si es -1. */
    public static void add(Context context, Place place, int position) {
        List<Place> places = list(context);
        for (Place p : places) {
            if (p.id == place.id) return;
        }
        if (position < 0 || position > places.size()) {
            places.add(place);
        } else {
            places.add(position, place);
        }
        save(context, places);
    }

    /** Quita el lugar y su caché. Si era el seleccionado, se vuelve a la ubicación actual. */
    public static void remove(Context context, long id) {
        List<Place> places = list(context);
        for (int i = 0; i < places.size(); i++) {
            if (places.get(i).id == id) {
                WeatherRepository.deleteCache(context, places.get(i).cacheKey());
                places.remove(i);
                break;
            }
        }
        save(context, places);
        if (prefs(context).getLong(KEY_SELECTED, CURRENT_LOCATION) == id) {
            select(context, null);
        }
    }

    /** Lugar seleccionado, o null si es la ubicación actual. */
    @Nullable
    public static Place selected(Context context) {
        long id = prefs(context).getLong(KEY_SELECTED, CURRENT_LOCATION);
        if (id == CURRENT_LOCATION) return null;
        for (Place p : list(context)) {
            if (p.id == id) return p;
        }
        return null;
    }

    /** Selecciona un lugar guardado, o la ubicación actual con null. */
    public static void select(Context context, @Nullable Place place) {
        prefs(context).edit().putLong(KEY_SELECTED, place == null ? CURRENT_LOCATION : place.id).apply();
    }

    /** Clave de caché del lugar seleccionado. */
    public static String selectedCacheKey(Context context) {
        Place p = selected(context);
        return p == null ? WeatherRepository.KEY_CURRENT_LOCATION : p.cacheKey();
    }
}
