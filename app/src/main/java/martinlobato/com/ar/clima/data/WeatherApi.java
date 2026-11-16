package martinlobato.com.ar.clima.data;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
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
    private static final String DAILY = "weather_code,temperature_2m_max,temperature_2m_min,sunrise,"
            + "sunset,uv_index_max,precipitation_sum,precipitation_probability_max,"
            + "wind_speed_10m_max,wind_gusts_10m_max,wind_direction_10m_dominant";

    private WeatherApi() {
    }

    static String forecastUrl(double lat, double lon) {
        return String.format(Locale.US,
                "%s?latitude=%.2f&longitude=%.2f&current=%s&hourly=%s&daily=%s"
                        + "&timezone=auto&forecast_days=%d",
                BASE, lat, lon, CURRENT, HOURLY, DAILY, FORECAST_DAYS);
    }

    /** Devuelve el JSON crudo del pronóstico. Lanza IOException ante error de red o HTTP != 200. */
    public static String fetchForecast(double lat, double lon) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(forecastUrl(lat, lon)).openConnection();
        try {
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(15_000);
            conn.setRequestProperty("Accept", "application/json");
            int status = conn.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + status);
            }
            try (InputStream in = conn.getInputStream()) {
                return readAll(in);
            }
        } finally {
            conn.disconnect();
        }
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
