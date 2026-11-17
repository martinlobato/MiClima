package martinlobato.com.ar.clima.work;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

import martinlobato.com.ar.clima.Prefs;
import martinlobato.com.ar.clima.R;
import martinlobato.com.ar.clima.data.ClimateNormals;
import martinlobato.com.ar.clima.data.Forecast;
import martinlobato.com.ar.clima.data.PlaceStore;
import martinlobato.com.ar.clima.data.WeatherCode;
import martinlobato.com.ar.clima.data.WeatherRepository;
import martinlobato.com.ar.clima.util.UnitFormat;
import martinlobato.com.ar.clima.widget.WeatherWidgetProvider;

/**
 * Resumen diario: a la hora elegida actualiza el pronóstico (si hay red) y publica una
 * notificación con el día. Se reprograma solo para el día siguiente.
 */
public class DailySummaryWorker extends Worker {

    private static final String WORK_NAME = "daily_summary";

    public DailySummaryWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    /** Programa (o cancela) el próximo resumen según Ajustes. */
    public static void schedule(Context context) {
        WorkManager wm = WorkManager.getInstance(context.getApplicationContext());
        if (!Prefs.isSummaryEnabled(context)) {
            wm.cancelUniqueWork(WORK_NAME);
            return;
        }
        long delay = delayUntil(System.currentTimeMillis(), Prefs.getSummaryMinutes(context),
                java.util.TimeZone.getDefault());
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(DailySummaryWorker.class)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build();
        wm.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request);
    }

    /** Milisegundos desde {@code now} hasta la próxima vez que el reloj marque {@code minutesOfDay}. */
    static long delayUntil(long now, int minutesOfDay, java.util.TimeZone tz) {
        Calendar cal = Calendar.getInstance(tz);
        cal.setTimeInMillis(now);
        cal.set(Calendar.HOUR_OF_DAY, minutesOfDay / 60);
        cal.set(Calendar.MINUTE, minutesOfDay % 60);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= now + 60_000L) {
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }
        return cal.getTimeInMillis() - now;
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        try {
            Forecast f;
            try {
                f = WeatherRepository.refreshSelected(ctx);
                WeatherWidgetProvider.updateAll(ctx);
            } catch (Exception e) {
                f = null; // sin red: se usa lo guardado
            }
            if (f == null) {
                f = WeatherRepository.loadCached(ctx, PlaceStore.selectedCacheKey(ctx));
            }
            if (f != null) {
                String[] texts = buildSummary(ctx, f, System.currentTimeMillis());
                if (texts != null) AlertNotifier.showSummary(ctx, texts[0], texts[1]);
            }
        } finally {
            schedule(ctx); // el de mañana
        }
        return Result.success();
    }

    /** {título, texto} del resumen de hoy, o null si el pronóstico no cubre hoy. */
    static String[] buildSummary(Context ctx, Forecast f, long now) {
        if (f.daily.size() == 0 || f.hourly.size() == 0 || now > f.hourly.time[f.hourly.size() - 1]) return null;
        UnitFormat uf = Prefs.units(ctx);
        int d = f.dayIndexAt(now);
        String place = f.place != null ? f.place : ctx.getString(R.string.place_unknown);
        String title = ctx.getString(R.string.summary_title, place,
                ctx.getString(WeatherCode.description(f.daily.code[d])));
        String text = ctx.getString(R.string.summary_text, uf.temp(f.daily.tempMax[d]),
                uf.temp(f.daily.tempMin[d]), UnitFormat.percent(f.daily.precipProbMax[d]));
        double[] coords = WeatherRepository.selectedCoordinates(ctx);
        ClimateNormals normals = coords == null ? null
                : WeatherRepository.cachedNormals(ctx, PlaceStore.selectedCacheKey(ctx), coords[0], coords[1]);
        if (normals != null) {
            Calendar cal = Calendar.getInstance(f.timeZone());
            cal.setTimeInMillis(f.daily.time[d]);
            double normal = normals.maxFor(cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));
            double diff = f.daily.tempMax[d] - normal;
            if (!Double.isNaN(diff) && Math.abs(diff) >= 1.5) {
                text = ctx.getString(R.string.summary_normal, text, uf.tempDiff(diff),
                        ctx.getString(diff > 0 ? R.string.summary_above : R.string.summary_below));
            }
        }
        return new String[]{title, text};
    }
}
