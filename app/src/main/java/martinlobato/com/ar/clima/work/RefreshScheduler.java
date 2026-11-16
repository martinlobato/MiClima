package martinlobato.com.ar.clima.work;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

import martinlobato.com.ar.clima.Prefs;

/** Programa (o cancela) la actualización periódica según lo elegido en Ajustes. */
public final class RefreshScheduler {

    private static final String WORK_NAME = "refresh_forecast";

    private RefreshScheduler() {
    }

    /** Aplica el intervalo guardado en preferencias. Llamar al iniciar la app y al cambiarlo. */
    public static void apply(Context context) {
        int hours = Prefs.getRefreshHours(context);
        WorkManager wm = WorkManager.getInstance(context.getApplicationContext());
        if (hours <= 0) {
            wm.cancelUniqueWork(WORK_NAME);
            return;
        }
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(RefreshWorker.class, hours, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build();
        // UPDATE conserva el trabajo ya programado y solo cambia su intervalo si es distinto.
        wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request);
    }
}
