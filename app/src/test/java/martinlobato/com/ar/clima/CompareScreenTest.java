package martinlobato.com.ar.clima;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

import martinlobato.com.ar.clima.ui.DayDetailActivity;
import martinlobato.com.ar.clima.ui.HourlyChartView;
import martinlobato.com.ar.clima.ui.ModelCompareActivity;

/** Comparación de modelos y referencia de "lo normal", sobre datos guardados (sin red). */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "es-w411dp-h1100dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class CompareScreenTest {

    private Context ctx;

    @Before
    public void setUp() throws Exception {
        ctx = ApplicationProvider.getApplicationContext();
        Prefs.setThemeMode(ctx, Prefs.THEME_MODE_LIGHT);
        Prefs.setThemePalette(ctx, Prefs.PALETTE_BLUE);
        martinlobato.com.ar.clima.util.ThemeUtils.applyNightMode(ctx);
        TestData.writeCache(ctx);
        TestData.writeNormals(ctx);
        TestData.writeComparison(ctx);
    }

    private static void settle() throws Exception {
        for (int i = 0; i < 20; i++) {
            ShadowLooper.idleMainLooper();
            Thread.sleep(25);
        }
        ShadowLooper.idleMainLooper();
    }

    @Test
    public void showsOneLinePerModelAndTheirSpread() throws Exception {
        try (ActivityScenario<ModelCompareActivity> scenario = ActivityScenario.launch(
                ModelCompareActivity.intent(ctx, System.currentTimeMillis()))) {
            settle();
            scenario.onActivity(a -> {
                assertEquals(View.VISIBLE, a.findViewById(R.id.content).getVisibility());
                assertEquals("Hoy", text(a, R.id.tvDayTitle));
                LinearLayout models = a.findViewById(R.id.listModels);
                assertEquals(5, models.getChildCount());
                assertEquals("ECMWF", ((TextView) ((LinearLayout) models.getChildAt(0)).getChildAt(1)).getText().toString());
                // Máximas sintéticas: 21,0° a 28,6° → difieren unos 8°.
                assertTrue(text(a, R.id.tvChartInfo), text(a, R.id.tvChartInfo).startsWith("Los modelos difieren hasta "));
                assertEquals("Resumen del día por modelo", text(a, R.id.tvModelsTitle));
                TestData.saveScreenshot(a.getWindow().getDecorView(), "compare-temp");

                // Al elegir una hora, las filas muestran el valor de cada modelo a esa hora.
                ((HourlyChartView) a.findViewById(R.id.chart)).select(15);
                assertEquals("Valores a las 15:00", text(a, R.id.tvModelsTitle));

                // Lluvia: solo uno de los modelos prevé agua.
                ((Chip) ((ChipGroup) a.findViewById(R.id.chipGroupVariable)).getChildAt(1)).setChecked(true);
                assertTrue(text(a, R.id.tvChartInfo), text(a, R.id.tvChartInfo).startsWith("Lluvia del día: entre 0 y "));

                // Día 6: Météo-France (4 días) ya no tiene datos y su fila queda con "—".
                for (int i = 0; i < 5; i++) a.findViewById(R.id.btnNextDay).performClick();
                LinearLayout later = a.findViewById(R.id.listModels);
                assertEquals("—", ((TextView) ((LinearLayout) later.getChildAt(4)).getChildAt(2)).getText().toString());
            });
        }
    }

    @Test
    public void homeAndDayDetailShowTheNormalForTheDate() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            settle();
            scenario.onActivity(a -> {
                TextView normal = a.findViewById(R.id.tvNormal);
                assertEquals(View.VISIBLE, normal.getVisibility());
                // Normal sintético: 20°. La máxima de hoy en la caché es 18° → dentro o bajo lo normal.
                assertTrue(normal.getText().toString(), normal.getText().toString().contains("(20°)"));
            });
        }
        try (ActivityScenario<DayDetailActivity> scenario = ActivityScenario.launch(
                DayDetailActivity.intent(ctx, System.currentTimeMillis()))) {
            settle();
            scenario.onActivity(a -> {
                LinearLayout grid = a.findViewById(R.id.gridSummary);
                // Dos filas de siempre + normales (sol y luna van en su propia tarjeta).
                assertEquals(3, grid.getChildCount());
                String all = allText(grid);
                assertTrue(all, all.contains("20° ("));
                assertTrue(all, all.contains("10° ("));
                assertEquals(View.VISIBLE, a.findViewById(R.id.btnCompareModels).getVisibility());
            });
        }
    }

    private static String text(android.app.Activity a, int id) {
        return ((TextView) a.findViewById(id)).getText().toString();
    }

    private static String allText(View view) {
        if (view instanceof TextView) return ((TextView) view).getText() + "|";
        StringBuilder sb = new StringBuilder();
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) sb.append(allText(group.getChildAt(i)));
        }
        return sb.toString();
    }
}
