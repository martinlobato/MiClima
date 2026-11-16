package martinlobato.com.ar.clima.util;

import android.app.Application;
import android.content.Context;

import androidx.annotation.ColorInt;
import androidx.annotation.StyleRes;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.DynamicColorsOptions;

import martinlobato.com.ar.clima.Prefs;
import martinlobato.com.ar.clima.R;

/**
 * Resuelve y aplica el tema visual (modo claro/oscuro/sistema + paleta de color)
 * según lo elegido en Configuración. Se combina con Material You (color dinámico)
 * cuando la paleta elegida es "Automático".
 */
public final class ThemeUtils {

    private ThemeUtils() {
    }

    /** Aplica el modo día/noche global según la preferencia guardada. Llamar en Application.onCreate. */
    public static void applyNightMode(Context context) {
        String mode = Prefs.getThemeMode(context);
        int nightMode;
        if (Prefs.THEME_MODE_LIGHT.equals(mode)) {
            nightMode = AppCompatDelegate.MODE_NIGHT_NO;
        } else if (Prefs.THEME_MODE_DARK.equals(mode)) {
            nightMode = AppCompatDelegate.MODE_NIGHT_YES;
        } else {
            nightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        }
        AppCompatDelegate.setDefaultNightMode(nightMode);
    }

    /**
     * Registra el color dinámico (Material You) para todas las Activities, aplicándolo
     * solo cuando la paleta elegida sea "Automático". Llamar en Application.onCreate.
     */
    public static void registerDynamicColors(Application application) {
        DynamicColorsOptions options = new DynamicColorsOptions.Builder()
                .setPrecondition((activity, theme) ->
                        Prefs.PALETTE_AUTO.equals(Prefs.getThemePalette(activity)))
                .build();
        DynamicColors.applyToActivitiesIfAvailable(application, options);
    }

    /** ¿El dispositivo soporta color dinámico (Android 12+)? */
    public static boolean isDynamicColorAvailable(Context context) {
        return DynamicColors.isDynamicColorAvailable();
    }

    /**
     * Estilo base a aplicar en cada Activity según la paleta elegida (con {@code setTheme}
     * antes de {@code super.onCreate}). Si la paleta es "Automático" se usa Verde como base;
     * el color dinámico la sobreescribe en tiempo de ejecución cuando está disponible.
     */
    @StyleRes
    public static int resolveThemeRes(Context context) {
        String palette = Prefs.getThemePalette(context);
        if (Prefs.PALETTE_RED.equals(palette)) {
            return R.style.Theme_MiClima_Red;
        } else if (Prefs.PALETTE_BLUE.equals(palette)) {
            return R.style.Theme_MiClima_Blue;
        } else if (Prefs.PALETTE_CYAN.equals(palette)) {
            return R.style.Theme_MiClima_Cyan;
        }
        return R.style.Theme_MiClima_Green;
    }

    /**
     * Color primario "plano" (sin depender de un contexto con theme) para la paleta elegida.
     * Se usa en el widget de captura rápida, cuyo RemoteViews se infla fuera de una Activity.
     * "Automático" no tiene un color fijo (depende del fondo de pantalla) así que cae a Verde.
     */
    @ColorInt
    public static int currentPrimaryColor(Context context) {
        String palette = Prefs.getThemePalette(context);
        int colorRes;
        if (Prefs.PALETTE_RED.equals(palette)) {
            colorRes = R.color.red_primary;
        } else if (Prefs.PALETTE_BLUE.equals(palette)) {
            colorRes = R.color.blue_primary;
        } else if (Prefs.PALETTE_CYAN.equals(palette)) {
            colorRes = R.color.cyan_primary;
        } else {
            colorRes = R.color.green_primary;
        }
        return ContextCompat.getColor(context, colorRes);
    }
}
