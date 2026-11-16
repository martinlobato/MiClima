package martinlobato.com.ar.clima.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WeatherApiTest {

    @Test
    public void automaticModelSendsNoModelsParameter() {
        String url = WeatherApi.forecastUrl(-33.4489, -70.6693, WeatherModel.AUTO);
        assertTrue(url, url.startsWith("https://api.open-meteo.com/v1/forecast?latitude=-33.45&longitude=-70.67&"));
        assertTrue(url.contains("&forecast_days=16"));
        assertTrue(url.contains("&timezone=auto"));
        assertFalse(url.contains("models="));
    }

    @Test
    public void chosenModelIsAppended() {
        assertTrue(WeatherApi.forecastUrl(1, 2, WeatherModel.ECMWF).endsWith("&models=ecmwf_ifs025"));
        assertTrue(WeatherApi.forecastUrl(1, 2, WeatherModel.GFS).endsWith("&models=gfs_seamless"));
    }

    @Test
    public void unknownSavedModelFallsBackToAutomatic() {
        assertEquals(WeatherModel.ICON, WeatherModel.parse("ICON"));
        assertEquals(WeatherModel.AUTO, WeatherModel.parse("NO_EXISTE"));
        assertEquals(WeatherModel.AUTO, WeatherModel.parse(null));
    }

    @Test
    public void airQualityUrl() {
        String url = WeatherApi.airQualityUrl(-33.4489, -70.6693);
        assertTrue(url, url.startsWith(
                "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=-33.45&longitude=-70.67&"));
        assertTrue(url.contains("us_aqi"));
    }

    @Test
    public void searchUrlEncodesTheQuery() {
        assertEquals("https://geocoding-api.open-meteo.com/v1/search?name=Vi%C3%B1a+del+Mar&count=8"
                + "&language=es&format=json", GeocodingApi.searchUrl(" Viña del Mar ", "es"));
    }
}
