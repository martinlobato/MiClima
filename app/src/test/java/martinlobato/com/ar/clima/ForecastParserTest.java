package martinlobato.com.ar.clima;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.TimeZone;

import martinlobato.com.ar.clima.data.Forecast;
import martinlobato.com.ar.clima.data.ForecastParser;

public class ForecastParserTest {

    private Forecast f;

    @Before
    public void setUp() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/forecast_sample.json")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            f = ForecastParser.parse(new String(out.toByteArray(), StandardCharsets.UTF_8), 1234L);
        }
    }

    private static long santiago(int y, int mo, int d, int h, int mi) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("America/Santiago"));
        c.clear();
        c.set(y, mo - 1, d, h, mi, 0);
        return c.getTimeInMillis();
    }

    @Test
    public void parsesHeaderAndSizes() {
        assertEquals(-33.5, f.latitude, 1e-9);
        assertEquals("America/Santiago", f.timezoneId);
        assertEquals(1234L, f.fetchedAt);
        assertEquals(72, f.hourly.size());
        assertEquals(3, f.daily.size());
        assertEquals(72, f.hourly.temp.length);
        assertEquals(3, f.daily.tempMax.length);
    }

    @Test
    public void timesAreLocalToThePlaceNotTheDevice() {
        assertEquals(santiago(2026, 11, 16, 0, 0), f.hourly.time[0]);
        assertEquals(santiago(2026, 11, 17, 5, 0), f.hourly.time[29]);
        assertEquals(santiago(2026, 11, 18, 0, 0), f.daily.time[2]);
        assertEquals(santiago(2026, 11, 16, 6, 34), f.daily.sunrise[0]);
        assertEquals(santiago(2026, 11, 16, 20, 21), f.daily.sunset[0]);
        assertEquals(santiago(2026, 11, 16, 16, 15), f.current.time);
    }

    @Test
    public void parsesValues() {
        assertEquals(26.3, f.current.temp, 1e-9);
        assertEquals(2, f.current.code, 0);
        assertTrue(f.current.isDay);
        assertEquals(228, f.current.windDir, 0);
        assertEquals(15.5, f.hourly.temp[1], 1e-9);
        assertEquals(61, f.hourly.code[3], 0);
        assertFalse(f.hourly.isDay[3]);
        assertTrue(f.hourly.isDay[12]);
        assertEquals(24.1, f.daily.tempMax[1], 1e-9);
        assertEquals(80, f.daily.precipProbMax[1], 0);
    }

    @Test
    public void nullsBecomeNaN() {
        assertEquals(6.35, f.hourly.uv[47], 1e-9);
        assertTrue(Double.isNaN(f.hourly.uv[48]));
        assertTrue(Double.isNaN(f.hourly.visibility[60]));
        assertTrue(Double.isNaN(f.daily.uvMax[2]));
    }

    @Test
    public void indexLookups() {
        assertEquals(16, f.hourIndexAt(santiago(2026, 11, 16, 16, 40)));
        assertEquals(0, f.hourIndexAt(santiago(2026, 11, 15, 10, 0)));
        assertEquals(71, f.hourIndexAt(santiago(2026, 12, 1, 0, 0)));
        assertEquals(0, f.dayIndexAt(santiago(2026, 11, 16, 23, 59)));
        assertEquals(1, f.dayIndexAt(santiago(2026, 11, 17, 0, 0)));
        assertEquals(24, f.firstHourIndexOfDay(1));
        assertEquals(-1, f.firstHourIndexOfDay(3));
        assertTrue(f.sameLocalDay(santiago(2026, 11, 16, 0, 5), santiago(2026, 11, 16, 23, 55)));
        assertFalse(f.sameLocalDay(santiago(2026, 11, 16, 23, 55), santiago(2026, 11, 17, 0, 5)));
    }

    @Test(expected = JSONException.class)
    public void apiErrorPayloadIsRejected() throws Exception {
        ForecastParser.parse("{\"error\":true,\"reason\":\"Daily API request limit exceeded.\"}", 0L);
    }

    @Test
    public void missingOptionalSeriesBecomeNaN() throws Exception {
        Forecast g = ForecastParser.parse("{\"latitude\":1,\"longitude\":2,\"timezone\":\"Etc/Nowhere\","
                + "\"utc_offset_seconds\":7200,"
                + "\"hourly\":{\"time\":[\"2026-01-01T00:00\"],\"temperature_2m\":[3.5]},"
                + "\"daily\":{\"time\":[\"2026-01-01\"]}}", 0L);
        assertEquals(3.5, g.hourly.temp[0], 1e-9);
        assertTrue(Double.isNaN(g.hourly.uv[0]));
        assertTrue(g.hourly.isDay[0]);
        assertEquals(0L, g.daily.sunrise[0]);
        // Zona desconocida: se usa el offset fijo informado por la API (UTC+2).
        assertEquals(7_200_000, g.timeZone().getRawOffset());
        assertEquals(1767218400000L, g.hourly.time[0]); // 2025-12-31T22:00:00Z
    }

    /** Respuesta real de Open-Meteo (Santiago, URL completa de la app, modelo automático). */
    @Test
    public void parsesARealResponse() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/forecast_real_santiago.json")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            Forecast real = ForecastParser.parse(new String(out.toByteArray(), StandardCharsets.UTF_8), 1L);
            assertEquals(16 * 24, real.hourly.size());
            assertEquals(16, real.daily.size());
            assertEquals("America/Santiago", real.timezoneId);
            assertEquals(16.9, real.current.temp, 1e-9);
            assertEquals(15.6, real.hourly.temp[0], 1e-9);
            assertEquals(santiago(2026, 11, 16, 6, 30), real.daily.sunrise[0]);
            assertFalse(Double.isNaN(real.hourly.uv[383]));
            assertFalse(Double.isNaN(real.daily.windDirDominant[15]));
        }
    }
}
