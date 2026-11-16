package martinlobato.com.ar.clima;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

import java.util.List;

import martinlobato.com.ar.clima.data.Place;
import martinlobato.com.ar.clima.data.PlaceStore;
import martinlobato.com.ar.clima.data.WeatherRepository;
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
    public void placesTabListsSavedPlacesAndSelectingOneShowsItOnHome() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            settle();
            scenario.onActivity(a ->
                    ((BottomNavigationView) a.findViewById(R.id.bottomNav)).setSelectedItemId(R.id.nav_places));
            settle();
            scenario.onActivity(a -> {
                LinearLayout list = a.findViewById(R.id.listPlaces);
                // Ubicación actual + 2 guardados.
                assertEquals(3, list.getChildCount());
                assertEquals("Ubicación actual", name(list, 0));
                assertEquals("Ciudad de La Rioja", name(list, 1));
                assertEquals("Chilecito", name(list, 2));
                // Con caché se ve la temperatura; Chilecito todavía no tiene.
                assertEquals(View.VISIBLE, list.getChildAt(1).findViewById(R.id.tvPlaceTemp).getVisibility());
                assertEquals(View.GONE, list.getChildAt(2).findViewById(R.id.tvPlaceTemp).getVisibility());
                TestData.saveScreenshot(a.getWindow().getDecorView(), "places-light");

                list.getChildAt(1).performClick();
            });
            settle();
            scenario.onActivity(a -> {
                assertEquals(LA_RIOJA.id, PlaceStore.selected(ctx).id);
                assertEquals(R.id.nav_home,
                        ((BottomNavigationView) a.findViewById(R.id.bottomNav)).getSelectedItemId());
                assertEquals("Ciudad de La Rioja", ((TextView) a.findViewById(R.id.tvPlace)).getText().toString());
            });
        }
    }

    @Test
    public void deletingAPlaceRemovesItsRow() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            settle();
            scenario.onActivity(a ->
                    ((BottomNavigationView) a.findViewById(R.id.bottomNav)).setSelectedItemId(R.id.nav_places));
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
                // 24.6 °C de la caché sintética = 76 °F.
                assertEquals("76°", ((TextView) a.findViewById(R.id.tvCurrentTemp)).getText().toString());
                LinearLayout grid = a.findViewById(R.id.gridDetails);
                String all = allText(grid);
                assertTrue(all, all.contains("mph"));
                assertTrue(all, all.contains("inHg"));
                assertTrue(all, all.contains(" mi"));
                assertTrue(all, all.contains(" in"));

                ((BottomNavigationView) a.findViewById(R.id.bottomNav)).setSelectedItemId(R.id.nav_settings);
            });
            settle();
            scenario.onActivity(a -> TestData.saveScreenshot(a.getWindow().getDecorView(), "settings-light"));
        } finally {
            Prefs.setUnits(ctx, UnitFormat.metric());
        }
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
