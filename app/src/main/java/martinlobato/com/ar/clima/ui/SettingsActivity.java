package martinlobato.com.ar.clima.ui;

import androidx.fragment.app.Fragment;

import martinlobato.com.ar.clima.R;

/** Ajustes, abierto desde el ícono de la barra de título. */
public class SettingsActivity extends HostActivity {

    @Override
    protected Fragment createFragment() {
        return new SettingsFragment();
    }

    @Override
    protected int titleRes() {
        return R.string.settings_title;
    }
}
