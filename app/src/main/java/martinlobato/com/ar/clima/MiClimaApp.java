package martinlobato.com.ar.clima;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.work.Configuration;

import martinlobato.com.ar.clima.util.ThemeUtils;
import martinlobato.com.ar.clima.work.DailySummaryWorker;
import martinlobato.com.ar.clima.work.RefreshScheduler;

/**
 * Application: aplica el modo día/noche, registra el color dinámico (Material You) y programa
 * la actualización en segundo plano.
 */
public class MiClimaApp extends Application implements Configuration.Provider {

    @Override
    public void onCreate() {
        super.onCreate();
        ThemeUtils.applyNightMode(this);
        ThemeUtils.registerDynamicColors(this);
        RefreshScheduler.apply(this);
        DailySummaryWorker.schedule(this);
    }

    /** WorkManager se inicializa a demanda con esta configuración (ver AndroidManifest). */
    @NonNull
    @Override
    public Configuration getWorkManagerConfiguration() {
        return new Configuration.Builder().build();
    }
}
