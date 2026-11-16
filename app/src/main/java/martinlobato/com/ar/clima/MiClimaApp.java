package martinlobato.com.ar.clima;

import android.app.Application;

import martinlobato.com.ar.clima.util.ThemeUtils;

/** Application: aplica el modo día/noche y registra el color dinámico (Material You). */
public class MiClimaApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        ThemeUtils.applyNightMode(this);
        ThemeUtils.registerDynamicColors(this);
    }
}
