package martinlobato.com.ar.clima;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import martinlobato.com.ar.clima.databinding.ActivityMainBinding;
import martinlobato.com.ar.clima.ui.HomeFragment;
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
            if (item.getItemId() == R.id.nav_settings) {
                showFragment(new SettingsFragment(), getString(R.string.settings_title),
                        R.drawable.ic_toolbar_settings);
            } else {
                showHome();
            }
            return true;
        });

        if (savedInstanceState == null) {
            showHome();
        }
    }

    // Tras recreate() (cambio de tema) el fragment se restaura solo; falta la toolbar.
    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        boolean settings = binding.bottomNav.getSelectedItemId() == R.id.nav_settings;
        applyToolbar(getString(settings ? R.string.settings_title : R.string.app_name),
                settings ? R.drawable.ic_toolbar_settings : R.drawable.ic_toolbar_logo);
    }

    private void showHome() {
        showFragment(new HomeFragment(), getString(R.string.app_name), R.drawable.ic_toolbar_logo);
    }

    private void showFragment(Fragment fragment, String title, int logoResId) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();
        applyToolbar(title, logoResId);
    }

    private void applyToolbar(String title, int logoResId) {
        binding.toolbar.setLogo(logoResId);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(title);
        }
    }
}
