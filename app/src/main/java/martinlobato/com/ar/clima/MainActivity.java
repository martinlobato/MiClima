package martinlobato.com.ar.clima;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.viewpager2.widget.ViewPager2;

import java.util.ArrayList;
import java.util.List;

import martinlobato.com.ar.clima.data.Place;
import martinlobato.com.ar.clima.data.PlaceStore;
import martinlobato.com.ar.clima.databinding.ActivityMainBinding;
import martinlobato.com.ar.clima.ui.HomeFragment;
import martinlobato.com.ar.clima.ui.PlacePagerAdapter;
import martinlobato.com.ar.clima.ui.PlacesActivity;
import martinlobato.com.ar.clima.ui.SettingsActivity;
import martinlobato.com.ar.clima.util.ThemeUtils;
import martinlobato.com.ar.clima.widget.WeatherWidgetProvider;

/**
 * Activity principal: el clima de cada lugar en una página que se pasa deslizando, con
 * Ajustes en la barra de título y abajo la lista de lugares, el indicador de página y la búsqueda.
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private PlacePagerAdapter adapter;
    /** Al volver de Ajustes se recrea la pantalla para aplicar tema y unidades. */
    private boolean returningFromSettings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(ThemeUtils.resolveThemeRes(this));
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);

        // La barra inferior deja libre la barra de navegación del sistema.
        final int baseBottom = binding.bottomBar.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomBar, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), baseBottom + bars.bottom);
            return insets;
        });

        adapter = new PlacePagerAdapter(this);
        binding.pager.setAdapter(adapter);
        binding.pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                binding.pageDots.setCurrent(position);
                // El lugar a la vista es el "seleccionado": lo usan el detalle de día, los
                // widgets y la actualización en segundo plano.
                long id = adapter.idAt(position);
                Place shown = id == HomeFragment.CURRENT_LOCATION ? null : findPlace(id);
                Place before = PlaceStore.selected(MainActivity.this);
                if ((before == null ? HomeFragment.CURRENT_LOCATION : before.id) != id) {
                    PlaceStore.select(MainActivity.this, shown);
                    WeatherWidgetProvider.requestUpdate(MainActivity.this);
                }
            }
        });
        binding.btnPlacesList.setOnClickListener(v -> openPlaces(false));
        binding.btnSearch.setOnClickListener(v -> openPlaces(true));

        loadPages();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        if (returningFromSettings) {
            returningFromSettings = false;
            recreate();
            return;
        }
        // Al volver de Lugares puede haber lugares nuevos, quitados u otro elegido.
        loadPages();
    }

    private Place findPlace(long id) {
        for (Place p : PlaceStore.list(this)) {
            if (p.id == id) return p;
        }
        return null;
    }

    /** Arma las páginas (ubicación actual + guardados) y muestra la del lugar seleccionado. */
    private void loadPages() {
        List<Long> ids = new ArrayList<>();
        ids.add(HomeFragment.CURRENT_LOCATION);
        for (Place p : PlaceStore.list(this)) {
            ids.add(p.id);
        }
        adapter.setIds(ids);
        binding.pageDots.setPages(ids.size(), true);
        Place selected = PlaceStore.selected(this);
        int position = Math.max(0, adapter.positionOf(selected == null ? HomeFragment.CURRENT_LOCATION : selected.id));
        binding.pager.setCurrentItem(position, false);
        binding.pageDots.setCurrent(position);
    }

    /** Abre la lista de lugares; con {@code search} entra directo a buscar una ciudad. */
    public void openPlaces(boolean search) {
        startActivity(PlacesActivity.intent(this, search));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            returningFromSettings = true;
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
