package martinlobato.com.ar.clima;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import martinlobato.com.ar.clima.data.AirQuality;
import martinlobato.com.ar.clima.data.Forecast;
import martinlobato.com.ar.clima.data.ForecastParser;
import martinlobato.com.ar.clima.util.Fmt;

public class AirQualityTest {

    // Recorte de una respuesta real de air-quality-api.open-meteo.com (Santiago).
    private static final String SAMPLE = "{\"latitude\":-33.4,\"longitude\":-70.7,\"utc_offset_seconds\":-10800,"
            + "\"timezone\":\"America/Santiago\","
            + "\"current\":{\"time\":\"2026-11-16T20:00\",\"interval\":3600,\"us_aqi\":64,\"pm2_5\":17.0,"
            + "\"pm10\":21.5,\"ozone\":55.0,\"nitrogen_dioxide\":21.4},"
            + "\"hourly\":{\"time\":[\"2026-11-16T00:00\",\"2026-11-16T01:00\",\"2026-11-16T02:00\"],"
            + "\"us_aqi\":[45,47,null],\"pm2_5\":[15.3,15.8,16.0],\"pm10\":[19.0,19.5,19.7],"
            + "\"ozone\":[36.0,32.0,29.0]}}";

    @Test
    public void parsesCurrentAndHourly() throws Exception {
        AirQuality a = AirQuality.parse(SAMPLE);
        assertEquals(64, a.currentAqi, 0);
        assertEquals(21.4, a.currentNo2, 1e-9);
        assertEquals(3, a.size());
        assertEquals(47, a.aqi[1], 0);
        assertTrue(Double.isNaN(a.aqi[2]));
        assertEquals(15.8, a.pm25[1], 1e-9);

        // Las horas son locales del lugar: 01:00 en Santiago (UTC-3) = 04:00 UTC.
        java.util.Calendar utc = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
        utc.clear();
        utc.set(2026, java.util.Calendar.NOVEMBER, 16, 4, 0, 0);
        assertEquals(utc.getTimeInMillis(), a.time[1]);
        assertEquals(1, a.indexOfTime(a.time[1]));
        assertEquals(47, a.aqiAt(a.time[1]), 0);
        assertEquals(-1, a.indexOfTime(a.time[1] + 1));
        assertTrue(Double.isNaN(a.aqiAt(a.time[2] + 3_600_000L)));
    }

    @Test
    public void aqiLevelsAndBarPosition() {
        assertEquals(0, Fmt.aqiLevel(0));
        assertEquals(0, Fmt.aqiLevel(50));
        assertEquals(1, Fmt.aqiLevel(51));
        assertEquals(2, Fmt.aqiLevel(150));
        assertEquals(3, Fmt.aqiLevel(151));
        assertEquals(4, Fmt.aqiLevel(300));
        assertEquals(5, Fmt.aqiLevel(301));

        assertEquals(0, Fmt.aqiFraction(0), 1e-9);
        assertEquals(1 / 12.0, Fmt.aqiFraction(25), 1e-9);
        assertEquals(1 / 6.0, Fmt.aqiFraction(50), 1e-9);
        assertEquals(4.5 / 6.0, Fmt.aqiFraction(250), 1e-9);
        assertEquals(1, Fmt.aqiFraction(500), 1e-9);
        assertEquals(1, Fmt.aqiFraction(900), 1e-9);
        assertEquals(0, Fmt.aqiFraction(Double.NaN), 1e-9);
    }

    /** Un modelo que cubre menos días devuelve null al final: esas horas y días se recortan. */
    @Test
    public void trailingEmptyDaysAndHoursAreTrimmed() throws Exception {
        Forecast f = ForecastParser.parse("{\"latitude\":1,\"longitude\":2,\"timezone\":\"GMT\","
                + "\"hourly\":{\"time\":[\"2026-01-01T00:00\",\"2026-01-01T01:00\",\"2026-01-02T00:00\","
                + "\"2026-01-03T00:00\"],\"temperature_2m\":[3.5,null,4.5,null],\"uv_index\":[null,null,null,null]},"
                + "\"daily\":{\"time\":[\"2026-01-01\",\"2026-01-02\",\"2026-01-03\"],"
                + "\"temperature_2m_max\":[10,11,null],\"temperature_2m_min\":[1,2,null]}}", 0L);
        // El hueco del medio se conserva; solo se corta la cola vacía.
        assertEquals(3, f.hourly.size());
        assertEquals(3, f.hourly.uv.length);
        assertTrue(Double.isNaN(f.hourly.temp[1]));
        assertEquals(4.5, f.hourly.temp[2], 1e-9);
        assertEquals(2, f.daily.size());
        assertEquals(2, f.daily.sunrise.length);
        assertEquals(11, f.daily.tempMax[1], 1e-9);
    }
}
