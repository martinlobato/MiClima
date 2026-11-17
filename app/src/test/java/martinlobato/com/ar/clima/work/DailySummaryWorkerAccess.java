package martinlobato.com.ar.clima.work;

import android.content.Context;

import java.util.TimeZone;

import martinlobato.com.ar.clima.data.Forecast;

/** Acceso de los tests a los métodos internos del resumen diario. */
public final class DailySummaryWorkerAccess {

    private DailySummaryWorkerAccess() {
    }

    public static long delayUntil(long now, int minutesOfDay, TimeZone tz) {
        return DailySummaryWorker.delayUntil(now, minutesOfDay, tz);
    }

    public static String[] buildSummary(Context ctx, Forecast f, long now) {
        return DailySummaryWorker.buildSummary(ctx, f, now);
    }
}
