package martinlobato.com.ar.clima.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import martinlobato.com.ar.clima.Prefs;
import martinlobato.com.ar.clima.R;
import martinlobato.com.ar.clima.databinding.FragmentSettingsBinding;
import martinlobato.com.ar.clima.databinding.ItemPaletteSwatchBinding;
import martinlobato.com.ar.clima.util.ThemeUtils;

/** Ajustes: tema visual (modo + paleta), mismo sistema que MyLoc/Ubica. */
public class SettingsFragment extends Fragment {

    private FragmentSettingsBinding binding;
    private Context appCtx;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        appCtx = requireContext().getApplicationContext();
        setupThemeMode();
        setupThemePalette();
    }

    private void setupThemeMode() {
        String mode = Prefs.getThemeMode(appCtx);
        int checkedId = Prefs.THEME_MODE_LIGHT.equals(mode) ? R.id.btnModeLight
                : Prefs.THEME_MODE_DARK.equals(mode) ? R.id.btnModeDark : R.id.btnModeSystem;
        binding.toggleGroupThemeMode.check(checkedId);
        binding.toggleGroupThemeMode.addOnButtonCheckedListener((group, id, isChecked) -> {
            if (!isChecked) return;
            String newMode;
            if (id == R.id.btnModeLight) {
                newMode = Prefs.THEME_MODE_LIGHT;
            } else if (id == R.id.btnModeDark) {
                newMode = Prefs.THEME_MODE_DARK;
            } else {
                newMode = Prefs.THEME_MODE_SYSTEM;
            }
            if (newMode.equals(Prefs.getThemeMode(appCtx))) return;
            Prefs.setThemeMode(appCtx, newMode);
            ThemeUtils.applyNightMode(appCtx);
            requireActivity().recreate();
        });
    }

    private void setupThemePalette() {
        boolean dynamicAvailable = ThemeUtils.isDynamicColorAvailable(appCtx);
        binding.swatchAuto.swatchRoot.setEnabled(dynamicAvailable);
        binding.swatchAuto.swatchRoot.setAlpha(dynamicAvailable ? 1f : 0.4f);
        binding.tvThemePaletteDesc.setVisibility(dynamicAvailable ? View.GONE : View.VISIBLE);

        String current = Prefs.getThemePalette(appCtx);
        // Sin Material You, "Automático" se resuelve como Verde (ver ThemeUtils.resolveThemeRes).
        if (Prefs.PALETTE_AUTO.equals(current) && !dynamicAvailable) {
            current = Prefs.PALETTE_GREEN;
        }
        setupSwatch(binding.swatchAuto, R.color.auto_palette_swatch, R.string.theme_palette_auto, Prefs.PALETTE_AUTO, current);
        setupSwatch(binding.swatchGreen, R.color.green_primary, R.string.theme_palette_green, Prefs.PALETTE_GREEN, current);
        setupSwatch(binding.swatchRed, R.color.red_primary, R.string.theme_palette_red, Prefs.PALETTE_RED, current);
        setupSwatch(binding.swatchBlue, R.color.blue_primary, R.string.theme_palette_blue, Prefs.PALETTE_BLUE, current);
        setupSwatch(binding.swatchCyan, R.color.cyan_primary, R.string.theme_palette_cyan, Prefs.PALETTE_CYAN, current);
    }

    private void setupSwatch(ItemPaletteSwatchBinding swatch, int colorRes, int labelRes,
                             final String paletteValue, String current) {
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(ContextCompat.getColor(appCtx, colorRes));
        swatch.swatchCircle.setBackground(circle);
        swatch.swatchLabel.setText(labelRes);
        swatch.swatchCheck.setVisibility(paletteValue.equals(current) ? View.VISIBLE : View.GONE);
        swatch.swatchRoot.setOnClickListener(v -> {
            Prefs.setThemePalette(appCtx, paletteValue);
            requireActivity().recreate();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
