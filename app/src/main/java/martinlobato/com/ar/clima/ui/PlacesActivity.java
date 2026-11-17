package martinlobato.com.ar.clima.ui;

import android.content.Context;
import android.content.Intent;

import androidx.fragment.app.Fragment;

import martinlobato.com.ar.clima.R;

/** Lista de lugares y búsqueda de ciudades, abierta desde la barra inferior. */
public class PlacesActivity extends HostActivity {

    static final String EXTRA_SEARCH = "search";

    /** @param search true = entra con el buscador enfocado y el teclado abierto */
    public static Intent intent(Context context, boolean search) {
        return new Intent(context, PlacesActivity.class).putExtra(EXTRA_SEARCH, search);
    }

    @Override
    protected Fragment createFragment() {
        return PlacesFragment.newInstance(getIntent().getBooleanExtra(EXTRA_SEARCH, false));
    }

    @Override
    protected int titleRes() {
        return R.string.places_title;
    }
}
