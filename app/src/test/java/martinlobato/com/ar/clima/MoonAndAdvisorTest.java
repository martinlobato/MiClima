package martinlobato.com.ar.clima;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Calendar;
import java.util.TimeZone;

import martinlobato.com.ar.clima.data.Marine;
import martinlobato.com.ar.clima.util.ActivityAdvisor;
import martinlobato.com.ar.clima.util.ActivityAdvisor.Activity;
import martinlobato.com.ar.clima.util.ActivityAdvisor.Hour;
import martinlobato.com.ar.clima.util.Moon;
import martinlobato.com.ar.clima.util.UnitFormat;
import martinlobato.com.ar.clima.util.Units;

public class MoonAndAdvisorTest {

    private static final long MIN = 60_000L;

    private static long at(String tz, int y, int mo, int d, int h, int mi) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone(tz));
        c.clear();
        c.set(y, mo - 1, d, h, mi, 0);
        return c.getTimeInMillis();
    }

    /** Fases conocidas (efemérides): nueva 9/11/2026, llena 24/11/2026, cuarto creciente 17/11. */
    @Test
    public void moonPhaseMatchesTheEphemeris() {
        Moon.Phase newMoon = Moon.phase(at("UTC", 2026, 11, 9, 7, 2));
        assertTrue("iluminación " + newMoon.illumination, newMoon.illumination < 0.01);
        assertEquals(0, newMoon.index());

        Moon.Phase full = Moon.phase(at("UTC", 2026, 11, 24, 14, 53));
        assertTrue("iluminación " + full.illumination, full.illumination > 0.99);
        assertEquals(4, full.index());

        Moon.Phase firstQuarter = Moon.phase(at("UTC", 2026, 11, 17, 11, 48));
        assertEquals(0.5, firstQuarter.illumination, 0.02);
        assertEquals(2, firstQuarter.index());

        Moon.Phase lastQuarter = Moon.phase(at("UTC", 2027, 1, 29, 10, 55));
        assertEquals(0.5, lastQuarter.illumination, 0.02);
        assertEquals(6, lastQuarter.index());

        // Mediodía del 5/1/2027 en Santiago: menguante fina, 4,5 % iluminada.
        Moon.Phase waning = Moon.phase(at("America/Santiago", 2027, 1, 5, 12, 0));
        assertEquals(0.045, waning.illumination, 0.01);
        assertEquals(7, waning.index());
    }

    /** Salidas y puestas contra una efeméride de referencia; tolerancia de 12 minutos. */
    @Test
    public void moonRiseAndSetMatchTheEphemeris() {
        String scl = "America/Santiago";
        Moon.Times t = Moon.times(at(scl, 2026, 11, 17, 0, 0), -33.45, -70.66);
        assertEquals(at(scl, 2026, 11, 17, 13, 21), t.rise, 12 * MIN);
        assertEquals(at(scl, 2026, 11, 17, 2, 13), t.set, 12 * MIN);

        t = Moon.times(at(scl, 2026, 11, 24, 0, 0), -33.45, -70.66);
        assertEquals(at(scl, 2026, 11, 24, 21, 6), t.rise, 12 * MIN);
        assertEquals(at(scl, 2026, 11, 24, 5, 58), t.set, 12 * MIN);

        t = Moon.times(at("Europe/Madrid", 2026, 6, 21, 0, 0), 40.42, -3.70);
        assertEquals(at("Europe/Madrid", 2026, 6, 21, 13, 55), t.rise, 12 * MIN);
        assertEquals(at("Europe/Madrid", 2026, 6, 21, 1, 44), t.set, 12 * MIN);

        // La Rioja, 10/3/2026: ese día la Luna se pone pero no sale.
        String lr = "America/Argentina/La_Rioja";
        t = Moon.times(at(lr, 2026, 3, 10, 0, 0), -29.41, -66.86);
        assertEquals(0L, t.rise);
        assertEquals(at(lr, 2026, 3, 10, 14, 3), t.set, 12 * MIN);
    }

    private static Hour hour(int hourOfDay, double apparent, double prob, double wind, double humidity, boolean day) {
        Hour h = new Hour();
        h.hourOfDay = hourOfDay;
        h.apparent = apparent;
        h.precipProb = prob;
        h.precip = 0;
        h.wind = wind;
        h.gusts = wind * 1.5;
        h.humidity = humidity;
        h.uv = 3;
        h.isDay = day;
        return h;
    }

    @Test
    public void scoresFollowTheRules() {
        // Mañana fresca y seca: ideal para correr.
        assertEquals(100, ActivityAdvisor.score(Activity.RUN, hour(8, 15, 0, 8, 50, true)));
        // Lluvia probable anula cualquier actividad al aire libre.
        assertEquals(0, ActivityAdvisor.score(Activity.RUN, hour(8, 15, 80, 8, 50, true)));
        assertEquals(0, ActivityAdvisor.score(Activity.BBQ, hour(14, 22, 70, 5, 50, true)));
        // De madrugada no se propone correr.
        assertEquals(0, ActivityAdvisor.score(Activity.RUN, hour(3, 15, 0, 8, 50, false)));
        // Calor fuerte baja mucho el puntaje.
        assertTrue(ActivityAdvisor.score(Activity.RUN, hour(15, 30, 0, 8, 30, true)) < 30);
        // Tender ropa: de noche no; con aire seco y brisa, sí.
        assertEquals(0, ActivityAdvisor.score(Activity.LAUNDRY, hour(22, 20, 0, 10, 40, false)));
        assertTrue(ActivityAdvisor.score(Activity.LAUNDRY, hour(14, 24, 0, 12, 35, true)) >= ActivityAdvisor.GREAT);
        assertTrue(ActivityAdvisor.score(Activity.LAUNDRY, hour(14, 24, 0, 12, 88, true)) < ActivityAdvisor.GOOD);
        // Bici con ráfagas fuertes: no.
        assertEquals(0, ActivityAdvisor.score(Activity.BIKE, hour(10, 18, 0, 35, 50, true)));
        // Asado fuera de horario: no.
        assertEquals(0, ActivityAdvisor.score(Activity.BBQ, hour(9, 22, 0, 5, 50, true)));
        assertEquals(100, ActivityAdvisor.score(Activity.BBQ, hour(20, 22, 0, 5, 50, true)));
    }

    @Test
    public void bestWindowIsTheHighestScoringStretch() {
        Hour[] day = new Hour[24];
        for (int h = 0; h < 24; h++) {
            // Madrugada fría, mañana agradable (7–10), mediodía caluroso, tarde templada cortada
            // por lluvia a las 19 y noche cálida. El mejor tramo es el de la mañana.
            double apparent = h < 7 ? 3 : h <= 10 ? 16 : h <= 17 ? 31 : h <= 20 ? 19 : 26;
            day[h] = hour(h, apparent, h == 19 ? 70 : 0, 8, 50, h >= 7 && h <= 20);
        }
        ActivityAdvisor.Window w = ActivityAdvisor.best(Activity.RUN, day);
        assertNotNull(w);
        assertEquals(7, w.from);
        assertEquals(11, w.to);
        assertEquals(100, w.score);
        assertEquals(7, w.peak);

        Hour[] rainy = new Hour[24];
        for (int h = 0; h < 24; h++) rainy[h] = hour(h, 15, 90, 8, 80, true);
        assertNull(ActivityAdvisor.best(Activity.RUN, rainy));
        assertNull(ActivityAdvisor.best(Activity.LAUNDRY, rainy));
        assertNull(ActivityAdvisor.best(Activity.BBQ, new Hour[0]));
    }

    /** Recortes de respuestas reales de marine-api.open-meteo.com: Viña del Mar y Santiago. */
    @Test
    public void marineHasDataOnTheCoastOnly() throws Exception {
        Marine coast = Marine.parse("{\"timezone\":\"America/Santiago\",\"utc_offset_seconds\":-10800,"
                + "\"current\":{\"time\":\"2026-11-16T22:45\",\"wave_height\":1.4,\"wave_period\":10.9,"
                + "\"wave_direction\":260,\"sea_surface_temperature\":19.0},"
                + "\"hourly\":{\"time\":[\"2026-11-16T00:00\",\"2026-11-16T01:00\"],\"wave_height\":[1.5,1.4],"
                + "\"wave_period\":[11.0,10.9],\"sea_surface_temperature\":[19.1,19.0]}}");
        assertTrue(coast.hasData());
        assertEquals(1.4, coast.currentWaveHeight, 1e-9);
        assertEquals(260, coast.currentWaveDirection, 0);
        assertEquals(10.9, coast.wavePeriod[1], 1e-9);
        assertEquals(1, coast.indexOfTime(coast.time[1]));

        Marine inland = Marine.parse("{\"timezone\":\"America/Santiago\",\"utc_offset_seconds\":-10800,"
                + "\"current\":{\"time\":\"2026-11-16T22:45\",\"wave_height\":null,\"wave_period\":null,"
                + "\"wave_direction\":null,\"sea_surface_temperature\":null},"
                + "\"hourly\":{\"time\":[\"2026-11-16T00:00\"],\"wave_height\":[null],\"wave_period\":[null],"
                + "\"sea_surface_temperature\":[null]}}");
        assertTrue(!inland.hasData());
    }

    @Test
    public void snowAltitudeAndWaveUnits() {
        java.util.Locale previous = java.util.Locale.getDefault();
        java.util.Locale.setDefault(java.util.Locale.US);
        try {
            UnitFormat metric = UnitFormat.metric();
            assertEquals("12 cm", metric.snow(12.04));
            assertEquals("3250 m", metric.altitude(3247));
            assertEquals("1.4 m", metric.waveHeight(1.42));
            UnitFormat imperial = new UnitFormat(Units.Temp.F, Units.Wind.MPH, Units.Pressure.INHG, Units.Precip.IN);
            assertEquals("4.7 in", imperial.snow(12));
            assertEquals("10650 ft", imperial.altitude(3247));
            assertEquals("4.7 ft", imperial.waveHeight(1.42));
            assertEquals("—", imperial.altitude(Double.NaN));
        } finally {
            java.util.Locale.setDefault(previous);
        }
    }
}
