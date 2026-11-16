package martinlobato.com.ar.clima;

import android.content.Context;
import android.content.SharedPreferences;

import martinlobato.com.ar.clima.util.UnitFormat;
import martinlobato.com.ar.clima.util.Units;

/** Acceso centralizado a las preferencias de la app. */
public final class Prefs {

    private static final String PREFS_NAME = "settings";
    private static final String KEY_THEME_MODE = "theme_mode";
    private static final String KEY_THEME_PALETTE = "theme_palette";
    private static final String KEY_LAST_LAT = "last_lat";
    private static final String KEY_LAST_LON = "last_lon";
    private static final String KEY_LAST_PLACE = "last_place";

    public static final String THEME_MODE_SYSTEM = "system";
    public static final String THEME_MODE_LIGHT = "light";
    public static final String THEME_MODE_DARK = "dark";

    public static final String PALETTE_AUTO = "auto";
    public static final String PALETTE_GREEN = "green";
    public static final String PALETTE_RED = "red";
    public static final String PALETTE_BLUE = "blue";
    public static final String PALETTE_CYAN = "cyan";

    private static final String KEY_UNIT_TEMP = "unit_temp";
    private static final String KEY_UNIT_WIND = "unit_wind";
    private static final String KEY_UNIT_PRESSURE = "unit_pressure";
    private static final String KEY_UNIT_PRECIP = "unit_precip";

    private Prefs() {
    }

    /** Unidades elegidas (por defecto métricas: °C, km/h, hPa, mm). */
    public static UnitFormat units(Context context) {
        SharedPreferences p = prefs(context);
        return new UnitFormat(
                Units.parse(Units.Temp.class, p.getString(KEY_UNIT_TEMP, null), Units.Temp.C),
                Units.parse(Units.Wind.class, p.getString(KEY_UNIT_WIND, null), Units.Wind.KMH),
                Units.parse(Units.Pressure.class, p.getString(KEY_UNIT_PRESSURE, null), Units.Pressure.HPA),
                Units.parse(Units.Precip.class, p.getString(KEY_UNIT_PRECIP, null), Units.Precip.MM));
    }

    public static void setUnits(Context context, UnitFormat units) {
        prefs(context).edit()
                .putString(KEY_UNIT_TEMP, units.temp.name())
                .putString(KEY_UNIT_WIND, units.wind.name())
                .putString(KEY_UNIT_PRESSURE, units.pressure.name())
                .putString(KEY_UNIT_PRECIP, units.precip.name())
                .apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Modo de tema: claro / oscuro / seguir al sistema (por defecto). */
    public static String getThemeMode(Context context) {
        return prefs(context).getString(KEY_THEME_MODE, THEME_MODE_SYSTEM);
    }

    public static void setThemeMode(Context context, String mode) {
        prefs(context).edit().putString(KEY_THEME_MODE, mode).apply();
    }

    /** Paleta de color: automática (Material You) por defecto, o una fija. */
    public static String getThemePalette(Context context) {
        return prefs(context).getString(KEY_THEME_PALETTE, PALETTE_AUTO);
    }

    public static void setThemePalette(Context context, String palette) {
        prefs(context).edit().putString(KEY_THEME_PALETTE, palette).apply();
    }

    /** Último lugar consultado: permite actualizar aunque no haya permiso o señal de ubicación. */
    public static boolean hasLastPlace(Context context) {
        return prefs(context).contains(KEY_LAST_LAT);
    }

    public static double getLastLat(Context context) {
        return Double.longBitsToDouble(prefs(context).getLong(KEY_LAST_LAT, 0));
    }

    public static double getLastLon(Context context) {
        return Double.longBitsToDouble(prefs(context).getLong(KEY_LAST_LON, 0));
    }

    public static String getLastPlace(Context context) {
        return prefs(context).getString(KEY_LAST_PLACE, null);
    }

    public static void setLastPlace(Context context, double lat, double lon, String place) {
        prefs(context).edit()
                .putLong(KEY_LAST_LAT, Double.doubleToLongBits(lat))
                .putLong(KEY_LAST_LON, Double.doubleToLongBits(lon))
                .putString(KEY_LAST_PLACE, place)
                .apply();
    }
}
