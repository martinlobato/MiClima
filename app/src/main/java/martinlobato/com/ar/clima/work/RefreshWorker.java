package martinlobato.com.ar.clima.work;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.IOException;

import martinlobato.com.ar.clima.data.WeatherRepository;
import martinlobato.com.ar.clima.widget.WeatherWidgetProvider;

/** Actualiza en segundo plano el pronóstico del lugar seleccionado y redibuja los widgets. */
public class RefreshWorker extends Worker {

    public RefreshWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        try {
            WeatherRepository.refreshSelected(ctx);
            WeatherWidgetProvider.updateAll(ctx);
            return Result.success();
        } catch (IOException e) {
            // Sin red o servidor caído: WorkManager reintenta con espera creciente.
            return Result.retry();
        } catch (Exception e) {
            return Result.failure();
        }
    }
}
