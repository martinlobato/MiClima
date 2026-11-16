package martinlobato.com.ar.clima.data;

/**
 * Modelo meteorológico que usa Open-Meteo para armar el pronóstico (parámetro {@code models}).
 * AUTO deja que Open-Meteo combine el mejor modelo disponible para cada lugar.
 * No todos los modelos traen todas las variables ni los 16 días: lo que falte se muestra como "—".
 */
public enum WeatherModel {
    AUTO(null, "Open-Meteo"),
    ECMWF("ecmwf_ifs025", "ECMWF"),
    GFS("gfs_seamless", "GFS"),
    ICON("icon_seamless", "ICON"),
    GEM("gem_seamless", "GEM"),
    METEOFRANCE("meteofrance_seamless", "Météo-France");

    /** Valor del parámetro {@code models}; null = no se envía (selección automática). */
    public final String apiId;
    /** Nombre corto para mostrar junto a "Actualizado…". */
    public final String label;

    WeatherModel(String apiId, String label) {
        this.apiId = apiId;
        this.label = label;
    }

    public static WeatherModel parse(String name) {
        if (name != null) {
            try {
                return valueOf(name);
            } catch (IllegalArgumentException ignored) {
                // Nombre guardado por una versión con otros modelos: se vuelve al automático.
            }
        }
        return AUTO;
    }
}
