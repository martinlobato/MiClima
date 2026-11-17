package martinlobato.com.ar.clima.ui;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import martinlobato.com.ar.clima.databinding.ActivityHostBinding;
import martinlobato.com.ar.clima.util.ThemeUtils;

/** Base de las pantallas con barra de título, botón atrás y un único fragment. */
abstract class HostActivity extends AppCompatActivity {

    protected abstract Fragment createFragment();

    protected abstract int titleRes();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        setTheme(ThemeUtils.resolveThemeRes(this));
        super.onCreate(savedInstanceState);
        ActivityHostBinding binding = ActivityHostBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(titleRes());
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        ViewCompat.setOnApplyWindowInsetsListener(binding.fragmentContainer, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, 0, 0, bars.bottom);
            return insets;
        });
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(binding.fragmentContainer.getId(), createFragment())
                    .commit();
        }
    }
}
