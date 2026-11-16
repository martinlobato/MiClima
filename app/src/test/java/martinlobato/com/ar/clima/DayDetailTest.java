package martinlobato.com.ar.clima;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

import martinlobato.com.ar.clima.ui.DayDetailActivity;
import martinlobato.com.ar.clima.ui.HourlyChartView;

/** Abre el detalle de un día sobre una caché sintética: gráfico por variable y tabla horaria. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "es-w411dp-h2200dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class DayDetailTest {

    private static final long DAY_MS = 24 * 60 * 60 * 1000L;

    private ActivityScenario<DayDetailActivity> launch(long dayTime, String mode, String palette) throws Exception {
        Context ctx = ApplicationProvider.getApplicationContext();
        Prefs.setThemeMode(ctx, mode);
        Prefs.setThemePalette(ctx, palette);
        martinlobato.com.ar.clima.util.ThemeUtils.applyNightMode(ctx);
        TestData.writeCache(ctx);
        ActivityScenario<DayDetailActivity> scenario =
                ActivityScenario.launch(DayDetailActivity.intent(ctx, dayTime));
        for (int i = 0; i < 100; i++) {
            ShadowLooper.idleMainLooper();
            final boolean[] shown = new boolean[1];
            scenario.onActivity(a -> shown[0] = a.findViewById(R.id.content).getVisibility() == View.VISIBLE);
            if (shown[0]) break;
            Thread.sleep(50);
        }
        ShadowLooper.idleMainLooper();
        return scenario;
    }

    @Test
    public void showsChartAndFullHourlyTable() throws Exception {
        // Día 4 de la caché sintética: lluvioso (código 61).
        long day = System.currentTimeMillis() + 4 * DAY_MS;
        try (ActivityScenario<DayDetailActivity> scenario = launch(day, Prefs.THEME_MODE_LIGHT, Prefs.PALETTE_BLUE)) {
            scenario.onActivity(a -> {
                assertEquals("Santiago", a.getSupportActionBar().getTitle().toString());
                assertEquals("Lluvia débil", text(a, R.id.tvDayCondition));

                // Tabla: encabezado + 24 horas, en la columna fija y en la que scrollea.
                assertEquals(25, ((LinearLayout) a.findViewById(R.id.tableFixed)).getChildCount());
                LinearLayout table = a.findViewById(R.id.tableScroll);
                assertEquals(25, table.getChildCount());
                assertEquals(12, ((LinearLayout) table.getChildAt(1)).getChildCount());

                ChipGroup chips = a.findViewById(R.id.chipGroupVariable);
                assertEquals(7, chips.getChildCount());
                assertTrue(text(a, R.id.tvChartInfo), text(a, R.id.tvChartInfo).startsWith("Máx "));

                // Seleccionar una hora en el gráfico muestra su detalle.
                HourlyChartView chart = a.findViewById(R.id.chart);
                chart.select(15);
                assertTrue(text(a, R.id.tvChartInfo), text(a, R.id.tvChartInfo).startsWith("15:00 · "));
                TestData.saveScreenshot(a.getWindow().getDecorView(), "day-temp-light");

                // Cambiar de variable redibuja y vuelve al resumen del día.
                ((Chip) chips.getChildAt(1)).setChecked(true);
                assertEquals(-1, chart.getSelectedIndex());
                assertTrue(text(a, R.id.tvChartInfo), text(a, R.id.tvChartInfo).startsWith("Probabilidad máx. "));
            });
            ShadowLooper.idleMainLooper();
            scenario.onActivity(a ->
                    TestData.saveScreenshot(a.getWindow().getDecorView(), "day-rain-light"));
        }
    }

    @Test
    public void navigatesBetweenDaysWithinTheForecast() throws Exception {
        try (ActivityScenario<DayDetailActivity> scenario =
                     launch(System.currentTimeMillis(), Prefs.THEME_MODE_DARK, Prefs.PALETTE_GREEN)) {
            scenario.onActivity(a -> {
                assertEquals("Hoy", text(a, R.id.tvDayTitle));
                // Hoy sí hay datos de aire.
                ((Chip) ((ChipGroup) a.findViewById(R.id.chipGroupVariable)).getChildAt(6)).setChecked(true);
                assertTrue(text(a, R.id.tvChartInfo), text(a, R.id.tvChartInfo).startsWith("AQI máx. "));
                assertFalse(a.findViewById(R.id.btnPrevDay).isEnabled());
                assertTrue(a.findViewById(R.id.btnNextDay).isEnabled());

                a.findViewById(R.id.btnNextDay).performClick();
                assertEquals("Mañana", text(a, R.id.tvDayTitle));
                assertTrue(a.findViewById(R.id.btnPrevDay).isEnabled());

                String before = text(a, R.id.tvDayDate);
                for (int i = 0; i < 20; i++) {
                    a.findViewById(R.id.btnNextDay).performClick();
                }
                // Llega al último día y no pasa de ahí.
                assertFalse(a.findViewById(R.id.btnNextDay).isEnabled());
                assertNotEquals(before, text(a, R.id.tvDayDate));
                assertEquals(25, ((LinearLayout) a.findViewById(R.id.tableScroll)).getChildCount());

                // Día 16: el pronóstico de aire (5 días) no llega.
                ChipGroup group = a.findViewById(R.id.chipGroupVariable);
                ((Chip) group.getChildAt(6)).setChecked(true);
                assertEquals("Sin datos de calidad del aire para este día", text(a, R.id.tvChartInfo));
                ((Chip) group.getChildAt(2)).setChecked(true);
            });
            ShadowLooper.idleMainLooper();
            scenario.onActivity(a ->
                    TestData.saveScreenshot(a.getWindow().getDecorView(), "day-wind-dark"));
        }
    }

    private static String text(android.app.Activity a, int id) {
        return ((TextView) a.findViewById(id)).getText().toString();
    }
}
