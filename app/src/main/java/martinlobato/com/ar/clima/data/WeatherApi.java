package martinlobato.com.ar.clima.data;

import java.io.IOException;
import java.util.Locale;

/** Cliente HTTP mínimo de Open-Meteo (sin API key). Llamar siempre fuera del hilo principal. */
public final class WeatherApi {

    public static final int FORECAST_DAYS = 16;

    private static final String BASE = "https://api.open-meteo.com/v1/forecast";
    private static final String CURRENT = "temperature_2m,relative_humidity_2m,apparent_temperature,"
            + "is_day,precipitation,weather_code,cloud_cover,pressure_msl,wind_speed_10m,"
            + "wind_direction_10m,wind_gusts_10m";
    private static final String HOURLY = "temperature_2m,apparent_temperature,relative_humidity_2m,"
            + "dew_point_2m,precipitation_probability,precipitation,weather_code,pressure_msl,"
            + "cloud_cover,visibility,wind_speed_10m,wind_direction_10m,wind_gusts_10m,uv_index,is_day";
    private static final String HOURLY_SNOW = "snowfall,snow_depth,freezing_level_height";
    private static final String DAILY = "weather_code,temperature_2m_max,temperature_2m_min,sunrise,"
            + "sunset,uv_index_max,precipitation_sum,precipitation_probability_max,"
            + "wind_speed_10m_max,wind_gusts_10m_max,wind_direction_10m_dominant";
    private static final String DAILY_SNOW = "snowfall_sum";

    private static final String AIR_BASE = "https://air-quality-api.open-meteo.com/v1/air-quality";
    private static final String AIR_CURRENT = "us_aqi,pm2_5,pm10,ozone,nitrogen_dioxide";
    private static final String AIR_HOURLY = "us_aqi,pm2_5,pm10,ozone";

    private static final String MARINE_BASE = "https://marine-api.open-meteo.com/v1/marine";
    private static final String ARCHIVE_BASE = "https://archive-api.open-meteo.com/v1/archive";

    private WeatherApi() {
    }

    static String forecastUrl(double lat, double lon, WeatherModel model) {
        return forecastUrl(lat, lon, model, true);
    }

    /**
     * @param extended false = sin las variables de nieve, por si el servicio llegara a rechazarlas
     *                 (se agregaron después y no se pudieron verificar en vivo)
     */
    static String forecastUrl(double lat, double lon, WeatherModel model, boolean extended) {
        String hourly = extended ? HOURLY + "," + HOURLY_SNOW : HOURLY;
        String daily = extended ? DAILY + "," + DAILY_SNOW : DAILY;
        String url = String.format(Locale.US,
                "%s?latitude=%.2f&longitude=%.2f&current=%s&hourly=%s&daily=%s"
                        + "&timezone=auto&forecast_days=%d",
                BASE, lat, lon, CURRENT, hourly, daily, FORECAST_DAYS);
        return model.apiId == null ? url : url + "&models=" + model.apiId;
    }

    static String airQualityUrl(double lat, double lon) {
        return String.format(Locale.US,
                "%s?latitude=%.2f&longitude=%.2f&current=%s&hourly=%s&timezone=auto&forecast_days=5",
                AIR_BASE, lat, lon, AIR_CURRENT, AIR_HOURLY);
    }

    static String marineUrl(double lat, double lon) {
        return String.format(Locale.US,
                "%s?latitude=%.2f&longitude=%.2f&current=wave_height,wave_period,wave_direction,"
                        + "sea_surface_temperature&hourly=wave_height,wave_period,sea_surface_temperature"
                        + "&timezone=auto&forecast_days=7",
                MARINE_BASE, lat, lon);
    }

    /** Devuelve el JSON crudo del estado del mar (API aparte de Open-Meteo). */
    public static String fetchMarine(double lat, double lon) throws IOException {
        return Http.get(marineUrl(lat, lon));
    }

    /** URL para comparar modelos: mismas horas, una serie por modelo y variable. */
    static String comparisonUrl(double lat, double lon) {
        StringBuilder models = new StringBuilder();
        for (WeatherModel m : ModelComparison.MODELS) {
            if (models.length() > 0) models.append(',');
            models.append(m.apiId);
        }
        return String.format(Locale.US,
                "%s?latitude=%.2f&longitude=%.2f&hourly=temperature_2m,precipitation,wind_speed_10m"
                        + "&timezone=auto&forecast_days=%d&models=%s",
                BASE, lat, lon, FORECAST_DAYS, models);
    }

    /** URL del archivo histórico: máxima y mínima diarias de los años indicados (inclusive). */
    static String archiveUrl(double lat, double lon, int firstYear, int lastYear) {
        return String.format(Locale.US,
                "%s?latitude=%.2f&longitude=%.2f&start_date=%d-01-01&end_date=%d-12-31"
                        + "&daily=temperature_2m_max,temperature_2m_min&timezone=auto",
                ARCHIVE_BASE, lat, lon, firstYear, lastYear);
    }

    public static String fetchComparison(double lat, double lon) throws IOException {
        return Http.get(comparisonUrl(lat, lon));
    }

    public static String fetchArchive(double lat, double lon, int firstYear, int lastYear) throws IOException {
        return Http.get(archiveUrl(lat, lon, firstYear, lastYear));
    }

    /** Devuelve el JSON crudo del pronóstico. Lanza IOException ante error de red o HTTP != 200. */
    public static String fetchForecast(double lat, double lon, WeatherModel model) throws IOException {
        try {
            return Http.get(forecastUrl(lat, lon, model, true));
        } catch (ApiException e) {
            // 400 = parámetro no aceptado (por ejemplo una variable que un modelo no conoce):
            // se repite sin las variables de nieve, que son las únicas opcionales.
            if (e.status != 400) throw e;
            return Http.get(forecastUrl(lat, lon, model, false));
        }
    }

    /** Devuelve el JSON crudo de calidad del aire (API aparte de Open-Meteo). */
    public static String fetchAirQuality(double lat, double lon) throws IOException {
        return Http.get(airQualityUrl(lat, lon));
    }
}
