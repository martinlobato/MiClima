package martinlobato.com.ar.clima;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;

import androidx.viewpager2.widget.ViewPager2;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowLooper;

import java.util.List;

import martinlobato.com.ar.clima.data.Place;
import martinlobato.com.ar.clima.data.PlaceStore;
import martinlobato.com.ar.clima.data.WeatherRepository;
import martinlobato.com.ar.clima.ui.HomeFragment;
import martinlobato.com.ar.clima.ui.PlacesActivity;
import martinlobato.com.ar.clima.ui.SettingsActivity;
import martinlobato.com.ar.clima.util.UnitFormat;
import martinlobato.com.ar.clima.util.Units;

/** Lugares guardados, selección, borrado y unidades, sobre cachés sintéticas (sin red). */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "es-w411dp-h1500dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PlacesScreenTest {

    private static final Place LA_RIOJA = new Place(3848950, "Ciudad de La Rioja",
            "Provincia de La Rioja", "Argentina", -29.41328, -66.85637);
    private static final Place CHILECITO = new Place(3861445, "Chilecito",
            "Provincia de La Rioja", "Argentina", -29.16163, -67.49934);

    private Context ctx;

    @Before
    public void setUp() throws Exception {
        // Sin red en los tests: los lugares sin caché quedan con "Sin datos todavía".
        martinlobato.com.ar.clima.ui.PlacesFragment.fetchMissingPlaces = false;
        ctx = ApplicationProvider.getApplicationContext();
        Prefs.setThemeMode(ctx, Prefs.THEME_MODE_LIGHT);
        Prefs.setThemePalette(ctx, Prefs.PALETTE_CYAN);
        martinlobato.com.ar.clima.util.ThemeUtils.applyNightMode(ctx);
        TestData.writeCache(ctx, WeatherRepository.KEY_CURRENT_LOCATION, "Santiago");
        TestData.writeCache(ctx, LA_RIOJA.cacheKey(), LA_RIOJA.name);
        PlaceStore.add(ctx, LA_RIOJA, -1);
        PlaceStore.add(ctx, CHILECITO, -1);
    }

    private static void settle() throws Exception {
        for (int i = 0; i < 20; i++) {
            ShadowLooper.idleMainLooper();
            Thread.sleep(25);
        }
        ShadowLooper.idleMainLooper();
    }

    @Test
    public void storeKeepsOrderSelectionAndRemoval() {
        List<Place> places = PlaceStore.list(ctx);
        assertEquals(2, places.size());
        assertEquals("Ciudad de La Rioja", places.get(0).name);
        assertEquals("Provincia de La Rioja", places.get(0).region);
        assertNull(PlaceStore.selected(ctx));
        assertEquals(WeatherRepository.KEY_CURRENT_LOCATION, PlaceStore.selectedCacheKey(ctx));

        PlaceStore.add(ctx, LA_RIOJA, -1); // repetido: no se duplica
        assertEquals(2, PlaceStore.list(ctx).size());

        PlaceStore.select(ctx, LA_RIOJA);
        assertEquals(LA_RIOJA.id, PlaceStore.selected(ctx).id);
        assertEquals("p3848950", PlaceStore.selectedCacheKey(ctx));
        assertNotNull(WeatherRepository.loadCached(ctx, LA_RIOJA.cacheKey()));

        // Quitar el seleccionado vuelve a la ubicación actual y borra su caché.
        PlaceStore.remove(ctx, LA_RIOJA.id);
        assertEquals(1, PlaceStore.list(ctx).size());
        assertNull(PlaceStore.selected(ctx));
        assertNull(WeatherRepository.loadCached(ctx, LA_RIOJA.cacheKey()));

        // Deshacer: vuelve a su posición original.
        PlaceStore.add(ctx, LA_RIOJA, 0);
        assertEquals("Ciudad de La Rioja", PlaceStore.list(ctx).get(0).name);
    }

    @Test
    public void placesListShowsSavedPlacesAndSelectingOneOpensItsPage() throws Exception {
        try (ActivityScenario<PlacesActivity> scenario = ActivityScenario.launch(PlacesActivity.intent(ctx, false))) {
            settle();
            scenario.onActivity(a -> {
                LinearLayout list = a.findViewById(R.id.listPlaces);
                // Ubicación actual + 2 guardados.
                assertEquals(3, list.getChildCount());
                assertEquals("Ubicación actual", name(list, 0));
                assertEquals("Ciudad de La Rioja", name(list, 1));
                assertEquals("Chilecito", name(list, 2));
                // Con caché: temperatura, máx/mín de hoy y hora local; Chilecito todavía no tiene.
                View rioja = list.getChildAt(1);
                assertEquals(View.VISIBLE, rioja.findViewById(R.id.tvPlaceTemp).getVisibility());
                String hiLo = ((TextView) rioja.findViewById(R.id.tvPlaceHiLo)).getText().toString();
                assertTrue(hiLo, hiLo.matches("-?\\d+° / -?\\d+°"));
                String time = ((TextView) rioja.findViewById(R.id.tvPlaceTime)).getText().toString();
                assertTrue(time, time.endsWith("· hora local"));
                // Sin caché: un guion y "Sin datos todavía" en lugar de un hueco.
                assertEquals("—", ((TextView) list.getChildAt(2).findViewById(R.id.tvPlaceTemp)).getText().toString());
                assertEquals("Sin datos todavía",
                        ((TextView) list.getChildAt(2).findViewById(R.id.tvPlaceHiLo)).getText().toString());
                // Cada lugar guardado tiene su botón de Google Maps.
                assertEquals(View.VISIBLE, rioja.findViewById(R.id.btnPlaceMap).getVisibility());
                TestData.saveScreenshot(a.getWindow().getDecorView(), "places-light");

                rioja.performClick();
                assertTrue(a.isFinishing());
            });
        }
        assertEquals(LA_RIOJA.id, PlaceStore.selected(ctx).id);

        // Inicio se abre en la página de ese lugar.
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            settle();
            scenario.onActivity(a -> {
                ViewPager2 pager = a.findViewById(R.id.pager);
                assertEquals(3, pager.getAdapter().getItemCount());
                assertEquals(1, pager.getCurrentItem());
                assertEquals("Ciudad de La Rioja", text(page(a, LA_RIOJA.id), R.id.tvPlace));
            });
        }
    }

    @Test
    public void mapButtonOpensGoogleMapsAtThePlace() throws Exception {
        try (ActivityScenario<PlacesActivity> scenario = ActivityScenario.launch(PlacesActivity.intent(ctx, false))) {
            settle();
            scenario.onActivity(a -> {
                LinearLayout list = a.findViewById(R.id.listPlaces);
                list.getChildAt(2).findViewById(R.id.btnPlaceMap).performClick();
                Intent sent = ((ShadowActivity) Shadow.extract(a)).getNextStartedActivity();
                assertNotNull(sent);
                assertEquals(Intent.ACTION_VIEW, sent.getAction());
                assertEquals("com.google.android.apps.maps", sent.getPackage());
                assertEquals("geo:-29.16163,-67.49934?q=-29.16163,-67.49934(Chilecito)", sent.getData().toString());
            });
        }
    }

    @Test
    public void swipingBetweenPlacesChangesTheSelectedOne() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            waitForPlace(scenario, HomeFragment.CURRENT_LOCATION);
            scenario.onActivity(a -> {
                ViewPager2 pager = a.findViewById(R.id.pager);
                assertEquals(0, pager.getCurrentItem());
                assertEquals("Santiago", text(page(a, HomeFragment.CURRENT_LOCATION), R.id.tvPlace));
                pager.setCurrentItem(1, false);
            });
            waitForPlace(scenario, LA_RIOJA.id);
            scenario.onActivity(a -> {
                assertEquals(LA_RIOJA.id, PlaceStore.selected(ctx).id);
                assertEquals("Ciudad de La Rioja", text(page(a, LA_RIOJA.id), R.id.tvPlace));
                // Ajustes está en la barra de título; la barra inferior tiene lista y búsqueda.
                assertNotNull(((androidx.appcompat.widget.Toolbar) a.findViewById(R.id.toolbar))
                        .getMenu().findItem(R.id.action_settings));
                a.findViewById(R.id.btnSearch).performClick();
                Intent sent = ((ShadowActivity) Shadow.extract(a)).getNextStartedActivity();
                assertEquals(PlacesActivity.class.getName(), sent.getComponent().getClassName());
                assertTrue(sent.getBooleanExtra("search", false));
                TestData.saveScreenshot(a.getWindow().getDecorView(), "home-pager");
            });
        }
    }

    @Test
    public void deletingAPlaceRemovesItsRow() throws Exception {
        try (ActivityScenario<PlacesActivity> scenario = ActivityScenario.launch(PlacesActivity.intent(ctx, false))) {
            settle();
            scenario.onActivity(a -> {
                LinearLayout list = a.findViewById(R.id.listPlaces);
                list.getChildAt(2).findViewById(R.id.btnPlaceAction).performClick();
            });
            settle();
            scenario.onActivity(a -> {
                assertEquals(2, ((LinearLayout) a.findViewById(R.id.listPlaces)).getChildCount());
                assertEquals(1, PlaceStore.list(ctx).size());
            });
        }
    }

    @Test
    public void homeUsesChosenUnits() throws Exception {
        Prefs.setUnits(ctx, new UnitFormat(Units.Temp.F, Units.Wind.MPH, Units.Pressure.INHG, Units.Precip.IN));
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            settle();
            scenario.onActivity(a -> {
                View home = page(a, HomeFragment.CURRENT_LOCATION);
                // 24.6 °C de la caché sintética = 76 °F.
                assertEquals("76°", text(home, R.id.tvCurrentTemp));
                String all = allText(home.findViewById(R.id.gridDetails));
                assertTrue(all, all.contains("mph"));
                assertTrue(all, all.contains("inHg"));
                assertTrue(all, all.contains(" mi"));
                assertTrue(all, all.contains(" in"));
            });
        }
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            settle();
            scenario.onActivity(a -> TestData.saveScreenshot(a.getWindow().getDecorView(), "settings-light"));
        } finally {
            Prefs.setUnits(ctx, UnitFormat.metric());
        }
    }

    /** Espera (hasta ~3 s) a que la página del lugar haya leído su caché y mostrado el nombre. */
    private static void waitForPlace(ActivityScenario<MainActivity> scenario, long id) throws Exception {
        for (int i = 0; i < 120; i++) {
            ShadowLooper.idleMainLooper();
            final boolean[] ready = new boolean[1];
            scenario.onActivity(a -> {
                androidx.fragment.app.Fragment f = a.getSupportFragmentManager().findFragmentByTag("f" + id);
                ready[0] = f != null && f.getView() != null
                        && ((TextView) f.requireView().findViewById(R.id.tvPlace)).getText().length() > 0;
            });
            if (ready[0]) return;
            Thread.sleep(25);
        }
    }

    /** Vista de la página de un lugar (FragmentStateAdapter etiqueta cada página como "f" + id). */
    private static View page(MainActivity a, long id) {
        return a.getSupportFragmentManager().findFragmentByTag("f" + id).requireView();
    }

    private static String text(View root, int id) {
        return ((TextView) root.findViewById(id)).getText().toString();
    }

    private static String name(LinearLayout list, int index) {
        return ((TextView) list.getChildAt(index).findViewById(R.id.tvPlaceName)).getText().toString();
    }

    private static String allText(View view) {
        if (view instanceof TextView) return ((TextView) view).getText() + "|";
        StringBuilder sb = new StringBuilder();
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                sb.append(allText(group.getChildAt(i)));
            }
        }
        return sb.toString();
    }
}
