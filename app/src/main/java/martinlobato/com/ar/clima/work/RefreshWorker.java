package martinlobato.com.ar.clima.work;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.IOException;

import martinlobato.com.ar.clima.data.Forecast;
import martinlobato.com.ar.clima.data.WeatherRepository;
import martinlobato.com.ar.clima.widget.WeatherWidgetProvider;

/**
 * Actualiza en segundo plano el pronóstico del lugar seleccionado, redibuja los widgets y,
 * si están activados, avisa de lluvia próxima y de las alertas configuradas.
 */
public class RefreshWorker extends Worker {

    public RefreshWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        try {
            Forecast forecast = WeatherRepository.refreshSelected(ctx);
            WeatherWidgetProvider.updateAll(ctx);
            if (forecast != null) {
                RainNotifier.check(ctx, forecast);
                AlertNotifier.check(ctx, forecast);
            }
            return Result.success();
        } catch (IOException e) {
            // Sin red o servidor caído: WorkManager reintenta con espera creciente.
            return Result.retry();
        } catch (Exception e) {
            return Result.failure();
        }
    }
}
