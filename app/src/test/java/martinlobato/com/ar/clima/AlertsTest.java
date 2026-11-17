package martinlobato.com.ar.clima;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

import java.util.Arrays;
import java.util.List;
import java.util.TimeZone;

import martinlobato.com.ar.clima.data.AirQuality;
import martinlobato.com.ar.clima.data.Forecast;
import martinlobato.com.ar.clima.data.WeatherAlerts;
import martinlobato.com.ar.clima.data.WeatherAlerts.Type;
import martinlobato.com.ar.clima.data.WeatherRepository;
import martinlobato.com.ar.clima.ui.AlertsActivity;
import martinlobato.com.ar.clima.util.UnitFormat;
import martinlobato.com.ar.clima.util.Units;
import martinlobato.com.ar.clima.work.DailySummaryWorkerAccess;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "es-w411dp-h1400dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class AlertsTest {

    private static final long HOUR = 3_600_000L;
    private static final long T0 = 1_800_000_000_000L;

    private Context ctx;

    @Before
    public void setUp() {
        ctx = ApplicationProvider.getApplicationContext();
        Prefs.setThemeMode(ctx, Prefs.THEME_MODE_LIGHT);
        Prefs.setThemePalette(ctx, Prefs.PALETTE_GREEN);
        martinlobato.com.ar.clima.util.ThemeUtils.applyNightMode(ctx);
    }

    /** 36 horas templadas y tranquilas a partir de T0, para modificar en cada caso. */
    private static Forecast mild() {
        Forecast f = new Forecast();
        f.timezoneId = "UTC";
        int n = 36;
        f.hourly.time = new long[n];
        f.hourly.temp = new double[n];
        f.hourly.gusts = new double[n];
        f.hourly.uv = new double[n];
        for (int i = 0; i < n; i++) {
            f.hourly.time[i] = T0 + i * HOUR;
            f.hourly.temp[i] = 15;
            f.hourly.gusts[i] = 20;
            f.hourly.uv[i] = 3;
        }
        return f;
    }

    private static boolean[] on(Type... types) {
        boolean[] out = new boolean[Type.values().length];
        for (Type t : types) out[t.ordinal()] = true;
        return out;
    }

    private static double[] defaults() {
        double[] out = new double[Type.values().length];
        for (Type t : Type.values()) out[t.ordinal()] = t.defaultThreshold;
        return out;
    }

    @Test
    public void detectsEachEnabledAlertWithinTwentyFourHours() {
        Forecast f = mild();
        f.hourly.temp[6] = -1;
        f.hourly.temp[7] = -2.5;
        f.hourly.gusts[10] = 72;
        f.hourly.uv[13] = 9;
        f.hourly.temp[30] = 35; // a 30 horas: fuera de la ventana
        List<WeatherAlerts.Alert> alerts = WeatherAlerts.evaluate(f, T0,
                on(Type.FROST, Type.HEAT, Type.WIND, Type.UV), defaults());
        assertEquals(3, alerts.size());
        assertEquals(Type.FROST, alerts.get(0).type);
        assertEquals(T0 + 6 * HOUR, alerts.get(0).time);
        assertEquals(-2.5, alerts.get(0).extreme, 1e-9);
        assertEquals(Type.WIND, alerts.get(1).type);
        assertEquals(72, alerts.get(1).extreme, 1e-9);
        assertEquals(Type.UV, alerts.get(2).type);

        // Desactivadas no se evalúan; con umbral propio sí cambia el resultado.
        assertTrue(WeatherAlerts.evaluate(f, T0, on(), defaults()).isEmpty());
        double[] limits = defaults();
        limits[Type.HEAT.ordinal()] = 15;
        assertEquals(1, WeatherAlerts.evaluate(f, T0, on(Type.HEAT), limits).size());
    }

    @Test
    public void airQualityAlertUsesTheAirForecast() {
        Forecast f = mild();
        assertTrue(WeatherAlerts.evaluate(f, T0, on(Type.AIR), defaults()).isEmpty());
        f.air = new AirQuality();
        f.air.time = new long[]{T0, T0 + HOUR, T0 + 2 * HOUR};
        f.air.aqi = new double[]{60, 120, 155};
        List<WeatherAlerts.Alert> alerts = WeatherAlerts.evaluate(f, T0, on(Type.AIR), defaults());
        assertEquals(1, alerts.size());
        assertEquals(T0 + HOUR, alerts.get(0).time);
        assertEquals(155, alerts.get(0).extreme, 1e-9);
    }

    @Test
    public void sameEventIsNotifiedOnce() {
        Forecast f = mild();
        f.hourly.temp[6] = -1;
        WeatherAlerts.Alert a = WeatherAlerts.evaluate(f, T0, on(Type.FROST), defaults()).get(0);
        String key = WeatherAlerts.dedupeKey(a, f);
        assertFalse(Prefs.wasAlertNotified(ctx, key));
        Prefs.markAlertNotified(ctx, key);
        assertTrue(Prefs.wasAlertNotified(ctx, key));
        // Otro tipo el mismo día, o el mismo tipo otro día, son eventos distintos.
        assertFalse(Prefs.wasAlertNotified(ctx, key.replace("FROST", "HEAT")));
        f.hourly.temp[6] = 15;
        f.hourly.temp[29] = -1;
        WeatherAlerts.Alert next = WeatherAlerts.evaluate(f, T0 + 10 * HOUR, on(Type.FROST), defaults()).get(0);
        assertNotEquals(key, WeatherAlerts.dedupeKey(next, f));
        // La lista guardada no crece sin límite.
        for (int i = 0; i < 60; i++) Prefs.markAlertNotified(ctx, "X:" + i);
        assertFalse(Prefs.wasAlertNotified(ctx, key));
        assertTrue(Prefs.wasAlertNotified(ctx, "X:59"));
    }

    @Test
    public void summaryIsScheduledForTheNextOccurrenceOfTheChosenTime() {
        TimeZone tz = TimeZone.getTimeZone("America/Santiago");
        java.util.Calendar c = java.util.Calendar.getInstance(tz);
        c.clear();
        c.set(2026, 10, 17, 6, 0, 0);
        long six = c.getTimeInMillis();
        assertEquals(90 * 60_000L, DailySummaryWorkerAccess.delayUntil(six, 7 * 60 + 30, tz));
        c.set(2026, 10, 17, 8, 0, 0);
        long eight = c.getTimeInMillis();
        assertEquals(23 * HOUR + 30 * 60_000L, DailySummaryWorkerAccess.delayUntil(eight, 7 * 60 + 30, tz));
    }

    @Test
    public void summaryTextIncludesHighLowRainAndNormal() throws Exception {
        TestData.writeCache(ctx);
        TestData.writeNormals(ctx);
        Forecast f = WeatherRepository.loadCached(ctx, WeatherRepository.KEY_CURRENT_LOCATION);
        String[] texts = DailySummaryWorkerAccess.buildSummary(ctx, f, System.currentTimeMillis());
        assertNotNull(texts);
        assertTrue(texts[0], texts[0].startsWith("Hoy en Santiago: "));
        assertTrue(texts[1], texts[1].startsWith("Máx "));
        assertTrue(texts[1], texts[1].contains(" · Lluvia "));
        // Caché: máxima de hoy 18° contra un normal de 20° → "2° bajo lo normal".
        assertTrue(texts[1], texts[1].endsWith("2° bajo de lo normal"));
    }

    @Test
    public void screenShowsThresholdsInTheChosenUnits() throws Exception {
        Prefs.setUnits(ctx, new UnitFormat(Units.Temp.F, Units.Wind.MPH, Units.Pressure.HPA, Units.Precip.MM));
        try (ActivityScenario<AlertsActivity> scenario = ActivityScenario.launch(AlertsActivity.class)) {
            ShadowLooper.idleMainLooper();
            scenario.onActivity(a -> {
                LinearLayout list = a.findViewById(R.id.listAlerts);
                assertEquals(5, list.getChildCount());
                // 0 °C = 32 °F; 60 km/h ≈ 37 mph.
                assertEquals("Si la temperatura baja a 32 °F o menos",
                        ((TextView) list.findViewWithTag("rule_FROST")).getText().toString());
                assertEquals("Si las ráfagas llegan a 37 mph o más",
                        ((TextView) list.findViewWithTag("rule_WIND")).getText().toString());
                assertEquals("Si el índice AQI llega a 101 o más",
                        ((TextView) list.findViewWithTag("rule_AIR")).getText().toString());
                assertEquals("07:30", ((TextView) a.findViewById(R.id.tvSummaryTime)).getText().toString());
                TestData.saveScreenshot(a.getWindow().getDecorView(), "alerts");
            });
        } finally {
            Prefs.setUnits(ctx, UnitFormat.metric());
        }
        assertEquals(-40, Units.tempToCelsius(-40, Units.Temp.F), 1e-9);
        assertEquals(100, Units.tempToCelsius(212, Units.Temp.F), 1e-9);
        assertEquals(36, Units.windToKmh(10, Units.Wind.MS), 1e-9);
        assertEquals(Arrays.asList(Type.values()).size(), 5);
    }
}
