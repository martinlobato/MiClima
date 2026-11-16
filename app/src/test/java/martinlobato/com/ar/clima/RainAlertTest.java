package martinlobato.com.ar.clima;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

import martinlobato.com.ar.clima.data.Forecast;
import martinlobato.com.ar.clima.data.RainAlert;

public class RainAlertTest {

    private static final long HOUR = 60 * 60 * 1000L;
    private static final long T0 = 1_800_000_000_000L;

    /** Pronóstico de 12 horas a partir de T0, seco salvo lo que se cargue después. */
    private static Forecast dry() {
        Forecast f = new Forecast();
        int n = 12;
        f.hourly.time = new long[n];
        f.hourly.precipProb = new double[n];
        f.hourly.precip = new double[n];
        for (int i = 0; i < n; i++) f.hourly.time[i] = T0 + i * HOUR;
        return f;
    }

    @Test
    public void findsTheFirstRainyHourAhead() {
        Forecast f = dry();
        f.hourly.precipProb[4] = 75;
        f.hourly.precipProb[5] = 90;
        RainAlert.Event e = RainAlert.find(f, T0 + HOUR + 10 * 60 * 1000L); // hora en curso: 1
        assertNotNull(e);
        assertEquals(T0 + 4 * HOUR, e.time);
        assertEquals(75, e.probability, 0);
    }

    @Test
    public void amountAloneCountsWhenProbabilityIsMissing() {
        Forecast f = dry();
        Arrays.fill(f.hourly.precipProb, Double.NaN);
        f.hourly.precip[3] = 0.8;
        RainAlert.Event e = RainAlert.find(f, T0);
        assertNotNull(e);
        assertEquals(T0 + 3 * HOUR, e.time);
        assertTrue(Double.isNaN(e.probability));
        assertEquals(0.8, e.precipitation, 1e-9);
    }

    @Test
    public void noAlertWhenDryLowChanceTooFarOrAlreadyRaining() {
        Forecast f = dry();
        f.hourly.precipProb[2] = 59;
        f.hourly.precip[2] = 0.4;
        assertNull(RainAlert.find(f, T0));

        Forecast far = dry();
        far.hourly.precipProb[7] = 95; // a 7 horas: fuera de la ventana de 6
        assertNull(RainAlert.find(far, T0));
        assertNotNull(RainAlert.find(far, T0 + HOUR));

        Forecast raining = dry();
        raining.hourly.precipProb[0] = 80;
        raining.hourly.precipProb[1] = 90;
        assertNull(RainAlert.find(raining, T0 + 5 * 60 * 1000L));
    }

    @Test
    public void staleOrEmptyForecastGivesNothing() {
        Forecast f = dry();
        f.hourly.precipProb[11] = 99;
        assertNull(RainAlert.find(f, T0 - HOUR));
        assertNull(RainAlert.find(f, T0 + 13 * HOUR));
        assertNull(RainAlert.find(new Forecast(), T0));
        assertNull(RainAlert.find(null, T0));
    }

    @Test
    public void theSameRainIsNotAnnouncedTwice() {
        long event = T0 + 4 * HOUR;
        assertTrue(RainAlert.shouldNotify(event, 0, 0, T0));
        // Misma lluvia (el pronóstico la corrió una hora), avisada hace 3 horas: no se repite.
        assertFalse(RainAlert.shouldNotify(event + HOUR, event, T0, T0 + 3 * HOUR));
        // Otra lluvia, 8 horas después de la anterior: sí.
        assertTrue(RainAlert.shouldNotify(event + 8 * HOUR, event, T0, T0 + 3 * HOUR));
        // La misma hora del día siguiente ya es otra lluvia.
        assertTrue(RainAlert.shouldNotify(event + 24 * HOUR, event, T0, T0 + 20 * HOUR));
    }
}
