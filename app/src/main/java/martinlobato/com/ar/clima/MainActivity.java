package martinlobato.com.ar.clima;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import martinlobato.com.ar.clima.databinding.ActivityMainBinding;
import martinlobato.com.ar.clima.ui.HomeFragment;
import martinlobato.com.ar.clima.ui.PlacesFragment;
import martinlobato.com.ar.clima.ui.SettingsFragment;
import martinlobato.com.ar.clima.util.ThemeUtils;

/** Activity anfitriona: barra superior + contenedor de fragments + navegación inferior. */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(ThemeUtils.resolveThemeRes(this));
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);

        binding.bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            Fragment fragment = id == R.id.nav_settings ? new SettingsFragment()
                    : id == R.id.nav_places ? new PlacesFragment() : new HomeFragment();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragmentContainer, fragment)
                    .commit();
            applyToolbar(id);
            return true;
        });

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragmentContainer, new HomeFragment())
                    .commit();
            applyToolbar(R.id.nav_home);
        }
    }

    // Tras recreate() (cambio de tema) el fragment se restaura solo; falta la toolbar.
    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        applyToolbar(binding.bottomNav.getSelectedItemId());
    }

    /** Vuelve a Inicio (por ejemplo después de elegir un lugar). */
    public void openHome() {
        binding.bottomNav.setSelectedItemId(R.id.nav_home);
    }

    /** Abre la pestaña Lugares (por ejemplo desde el estado vacío de Inicio). */
    public void openPlaces() {
        binding.bottomNav.setSelectedItemId(R.id.nav_places);
    }

    private void applyToolbar(int navId) {
        int title = navId == R.id.nav_settings ? R.string.settings_title
                : navId == R.id.nav_places ? R.string.places_title : R.string.app_name;
        int logo = navId == R.id.nav_settings ? R.drawable.ic_toolbar_settings
                : navId == R.id.nav_places ? R.drawable.ic_toolbar_places : R.drawable.ic_toolbar_logo;
        binding.toolbar.setLogo(logo);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(title);
        }
    }
}
