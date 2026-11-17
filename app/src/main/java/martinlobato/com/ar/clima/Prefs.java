package martinlobato.com.ar.clima;

import android.content.Context;
import android.content.SharedPreferences;

import martinlobato.com.ar.clima.data.WeatherAlerts;
import martinlobato.com.ar.clima.data.WeatherModel;
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

    private static final String KEY_MODEL = "weather_model";
    private static final String KEY_REFRESH_HOURS = "refresh_hours";

    private static final String KEY_RAIN_ALERT = "rain_alert_enabled";
    private static final String KEY_RAIN_EVENT = "rain_alert_event_time";
    private static final String KEY_RAIN_NOTIFIED = "rain_alert_notified_at";

    private Prefs() {
    }

    // ---- alertas configurables

    public static boolean isAlertEnabled(Context context, WeatherAlerts.Type type) {
        return prefs(context).getBoolean("alert_on_" + type.name(), false);
    }

    public static void setAlertEnabled(Context context, WeatherAlerts.Type type, boolean enabled) {
        prefs(context).edit().putBoolean("alert_on_" + type.name(), enabled).apply();
    }

    /** Umbral de la alerta en unidades métricas (°C, km/h, índice). */
    public static double getAlertThreshold(Context context, WeatherAlerts.Type type) {
        return prefs(context).getFloat("alert_limit_" + type.name(), (float) type.defaultThreshold);
    }

    public static void setAlertThreshold(Context context, WeatherAlerts.Type type, double value) {
        prefs(context).edit().putFloat("alert_limit_" + type.name(), (float) value).apply();
    }

    /** ¿Ya se avisó este evento? (clave de WeatherAlerts.dedupeKey). */
    public static boolean wasAlertNotified(Context context, String key) {
        return ("|" + prefs(context).getString("alert_sent", "") + "|").contains("|" + key + "|");
    }

    /** Recuerda el evento avisado; guarda solo los últimos 40 para no crecer sin límite. */
    public static void markAlertNotified(Context context, String key) {
        String current = prefs(context).getString("alert_sent", "");
        java.util.List<String> keys = new java.util.ArrayList<>(
                java.util.Arrays.asList(current.isEmpty() ? new String[0] : current.split("\\|")));
        keys.add(key);
        while (keys.size() > 40) keys.remove(0);
        prefs(context).edit().putString("alert_sent", android.text.TextUtils.join("|", keys)).apply();
    }

    // ---- resumen diario

    public static boolean isSummaryEnabled(Context context) {
        return prefs(context).getBoolean("summary_on", false);
    }

    public static void setSummaryEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean("summary_on", enabled).apply();
    }

    /** Hora del resumen en minutos desde medianoche; por defecto 7:30. */
    public static int getSummaryMinutes(Context context) {
        return prefs(context).getInt("summary_minutes", 7 * 60 + 30);
    }

    public static void setSummaryMinutes(Context context, int minutes) {
        prefs(context).edit().putInt("summary_minutes", minutes).apply();
    }

    /** Actividad elegida en "Mejor momento para…" (nombre del enum de ActivityAdvisor). */
    public static String getBestActivity(Context context) {
        return prefs(context).getString("best_activity", "RUN");
    }

    public static void setBestActivity(Context context, String activity) {
        prefs(context).edit().putString("best_activity", activity).apply();
    }

    /** ¿Ya se comprobó que este lugar guardado no tiene mar cerca? (evita volver a preguntar). */
    public static boolean isInland(Context context, String placeKey) {
        return prefs(context).getBoolean("inland_" + placeKey, false);
    }

    public static void setInland(Context context, String placeKey) {
        prefs(context).edit().putBoolean("inland_" + placeKey, true).apply();
    }

    /** Aviso de lluvia próxima; apagado por defecto porque requiere permiso de notificaciones. */
    public static boolean isRainAlertEnabled(Context context) {
        return prefs(context).getBoolean(KEY_RAIN_ALERT, false);
    }

    public static void setRainAlertEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_RAIN_ALERT, enabled).apply();
    }

    /** Inicio de la última lluvia avisada (epoch millis); 0 si nunca se avisó. */
    public static long getLastRainEventTime(Context context) {
        return prefs(context).getLong(KEY_RAIN_EVENT, 0);
    }

    /** Momento del último aviso de lluvia (epoch millis); 0 si nunca se avisó. */
    public static long getLastRainNotifiedAt(Context context) {
        return prefs(context).getLong(KEY_RAIN_NOTIFIED, 0);
    }

    public static void setLastRainNotification(Context context, long eventTime, long notifiedAt) {
        prefs(context).edit().putLong(KEY_RAIN_EVENT, eventTime).putLong(KEY_RAIN_NOTIFIED, notifiedAt).apply();
    }

    /** Modelo meteorológico elegido; por defecto, el automático de Open-Meteo. */
    public static WeatherModel getModel(Context context) {
        return WeatherModel.parse(prefs(context).getString(KEY_MODEL, null));
    }

    public static void setModel(Context context, WeatherModel model) {
        prefs(context).edit().putString(KEY_MODEL, model.name()).apply();
    }

    /** Cada cuántas horas se actualiza en segundo plano; 0 = nunca. Por defecto, 3. */
    public static int getRefreshHours(Context context) {
        return prefs(context).getInt(KEY_REFRESH_HOURS, 3);
    }

    public static void setRefreshHours(Context context, int hours) {
        prefs(context).edit().putInt(KEY_REFRESH_HOURS, hours).apply();
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
