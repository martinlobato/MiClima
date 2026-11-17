package martinlobato.com.ar.clima;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;


/**
 * Abre la app con un pronóstico en caché (sin red) y verifica que Inicio muestre las 16 jornadas
 * hora por hora. Además guarda una captura en build/screenshots para revisar el diseño.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "es-w411dp-h3400dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class HomeScreenTest {

    @Test
    public void homeShowsCachedForecastLight() throws Exception {
        runHome("home-light", Prefs.THEME_MODE_LIGHT, Prefs.PALETTE_BLUE);
    }

    @Test
    public void homeShowsCachedForecastDark() throws Exception {
        runHome("home-dark", Prefs.THEME_MODE_DARK, Prefs.PALETTE_GREEN);
    }

    private void runHome(String shotName, String mode, String palette) throws Exception {
        Context ctx = ApplicationProvider.getApplicationContext();
        Prefs.setThemeMode(ctx, mode);
        Prefs.setThemePalette(ctx, palette);
        martinlobato.com.ar.clima.util.ThemeUtils.applyNightMode(ctx);
        TestData.writeCache(ctx);

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // La caché se lee en un hilo de fondo y se entrega por el looper principal.
            for (int i = 0; i < 100; i++) {
                ShadowLooper.idleMainLooper();
                final boolean[] shown = new boolean[1];
                scenario.onActivity(a -> shown[0] =
                        a.findViewById(R.id.content).getVisibility() == View.VISIBLE);
                if (shown[0]) break;
                Thread.sleep(50);
            }
            ShadowLooper.idleMainLooper();

            scenario.onActivity(a -> {
                assertEquals(View.VISIBLE, a.findViewById(R.id.content).getVisibility());
                assertEquals("Santiago", ((TextView) a.findViewById(R.id.tvPlace)).getText().toString());

                // Tira horaria: todas las horas desde ahora hasta el fin del día 16, más los
                // 15 separadores de día. Como mínimo 15 días completos.
                RecyclerView rv = a.findViewById(R.id.rvHourly);
                int items = rv.getAdapter().getItemCount();
                assertTrue("horas en la tira: " + items, items >= 15 * 24 + 15);
                assertTrue(items <= 16 * 24 + 15);

                assertEquals(TestData.DAYS, ((LinearLayout) a.findViewById(R.id.listDaily)).getChildCount());
                assertEquals(5, ((LinearLayout) a.findViewById(R.id.gridDetails)).getChildCount());
                // Calidad del aire de la caché: AQI 64 = moderada.
                assertEquals(View.VISIBLE, a.findViewById(R.id.sectionAir).getVisibility());
                assertEquals("64", ((TextView) a.findViewById(R.id.tvAqi)).getText().toString());
                assertEquals("Moderada", ((TextView) a.findViewById(R.id.tvAqiLevel)).getText().toString());
                // Mejor momento, nieve y luna siempre; mar solo con costa (la caché sintética no tiene).
                assertEquals(View.VISIBLE, a.findViewById(R.id.sectionBest).getVisibility());
                String best = ((TextView) a.findViewById(R.id.tvBestTitle)).getText().toString();
                assertTrue(best, best.startsWith("Hoy de ") || best.startsWith("Mañana de ") || best.startsWith("Hoy y mañana"));
                assertEquals(View.VISIBLE, a.findViewById(R.id.sectionSnow).getVisibility());
                assertEquals(2, ((LinearLayout) a.findViewById(R.id.gridSnow)).getChildCount());
                assertEquals(View.GONE, a.findViewById(R.id.sectionSea).getVisibility());
                assertEquals(1, ((LinearLayout) a.findViewById(R.id.gridSun)).getChildCount());
                assertTrue(((TextView) a.findViewById(R.id.tvSunrise)).getText().toString().matches("\\d\\d:\\d\\d"));
                assertTrue(((TextView) a.findViewById(R.id.tvSunset)).getText().toString().matches("\\d\\d:\\d\\d"));
                assertFalse(((TextView) a.findViewById(R.id.tvMoonPhase)).getText().toString().isEmpty());
                assertTrue(((TextView) a.findViewById(R.id.tvMoonIllumination)).getText().toString().endsWith("iluminada"));
                String status = ((TextView) a.findViewById(R.id.tvStatus)).getText().toString();
                assertTrue(status, status.endsWith("· Open-Meteo"));
                String temp = ((TextView) a.findViewById(R.id.tvCurrentTemp)).getText().toString();
                assertTrue(temp, temp.matches("-?\\d+°"));

                TestData.saveScreenshot(a.getWindow().getDecorView(), shotName);
            });
        }
    }
}
