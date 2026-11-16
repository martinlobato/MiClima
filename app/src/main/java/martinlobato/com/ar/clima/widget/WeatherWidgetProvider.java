package martinlobato.com.ar.clima.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import martinlobato.com.ar.clima.MainActivity;
import martinlobato.com.ar.clima.Prefs;
import martinlobato.com.ar.clima.R;
import martinlobato.com.ar.clima.data.Forecast;
import martinlobato.com.ar.clima.data.PlaceStore;
import martinlobato.com.ar.clima.data.WeatherCode;
import martinlobato.com.ar.clima.data.WeatherRepository;
import martinlobato.com.ar.clima.util.BackgroundExecutor;
import martinlobato.com.ar.clima.util.ThemeUtils;
import martinlobato.com.ar.clima.util.UnitFormat;

/**
 * Widget de pantalla de inicio: clima actual del lugar seleccionado y las próximas horas.
 * Se dibuja siempre desde la caché; quien baja datos nuevos (Inicio o RefreshWorker) pide
 * que se redibuje.
 */
public class WeatherWidgetProvider extends AppWidgetProvider {

    /** Separación en horas entre las casillas de pronóstico. */
    private static final int STEP_HOURS = 3;
    private static final int[] SLOT_HOUR = {R.id.widgetHour1, R.id.widgetHour2, R.id.widgetHour3,
            R.id.widgetHour4, R.id.widgetHour5};
    private static final int[] SLOT_ICON = {R.id.widgetIcon1, R.id.widgetIcon2, R.id.widgetIcon3,
            R.id.widgetIcon4, R.id.widgetIcon5};
    private static final int[] SLOT_TEMP = {R.id.widgetTemp1, R.id.widgetTemp2, R.id.widgetTemp3,
            R.id.widgetTemp4, R.id.widgetTemp5};

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        // Leer la caché es E/S: se hace fuera del hilo principal y se avisa al terminar.
        final PendingResult pending = goAsync();
        final Context app = context.getApplicationContext();
        BackgroundExecutor.run(() -> {
            try {
                updateAll(app);
            } finally {
                pending.finish();
            }
        });
    }

    /** Pide redibujar los widgets desde cualquier hilo (el trabajo se hace en segundo plano). */
    public static void requestUpdate(Context context) {
        final Context app = context.getApplicationContext();
        BackgroundExecutor.run(() -> updateAll(app));
    }

    /** Redibuja todos los widgets con la caché actual. Bloqueante: no llamar en el hilo principal. */
    public static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        if (manager == null) return;
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, WeatherWidgetProvider.class));
        if (ids == null || ids.length == 0) return;
        Forecast forecast = WeatherRepository.loadCached(context, PlaceStore.selectedCacheKey(context));
        RemoteViews views = buildViews(context, forecast, System.currentTimeMillis());
        for (int id : ids) {
            manager.updateAppWidget(id, views);
        }
    }

    /** Arma el contenido del widget; sin pronóstico vigente muestra el mensaje de "abrí la app". */
    public static RemoteViews buildViews(Context context, @Nullable Forecast f, long now) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_weather);
        Intent open = new Intent(context, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        views.setOnClickPendingIntent(R.id.widgetRoot, PendingIntent.getActivity(context, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        boolean usable = f != null && f.hourly.size() > 0 && f.daily.size() > 0
                && now <= f.hourly.time[f.hourly.size() - 1];
        views.setViewVisibility(R.id.widgetContent, usable ? View.VISIBLE : View.GONE);
        views.setViewVisibility(R.id.widgetEmpty, usable ? View.GONE : View.VISIBLE);
        if (!usable) return views;

        UnitFormat uf = Prefs.units(context);
        int accent = ThemeUtils.currentPrimaryColor(context);
        int iconColor = ContextCompat.getColor(context, R.color.widget_text);
        Forecast.Hourly h = f.hourly;
        int hourIdx = f.hourIndexAt(now);
        int dayIdx = f.dayIndexAt(now);

        views.setTextViewText(R.id.widgetPlace,
                f.place != null ? f.place : context.getString(R.string.place_unknown));
        views.setTextViewText(R.id.widgetTemp, uf.temp(h.temp[hourIdx]));
        views.setTextViewText(R.id.widgetCondition, context.getString(WeatherCode.description(h.code[hourIdx])));
        views.setTextViewText(R.id.widgetHiLo, context.getString(R.string.hi_lo,
                uf.temp(f.daily.tempMax[dayIdx]), uf.temp(f.daily.tempMin[dayIdx])));
        views.setImageViewResource(R.id.widgetIcon, WeatherCode.icon(h.code[hourIdx], h.isDay[hourIdx]));
        views.setInt(R.id.widgetIcon, "setColorFilter", accent);

        SimpleDateFormat hourFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
        hourFmt.setTimeZone(f.timeZone());
        for (int slot = 0; slot < SLOT_HOUR.length; slot++) {
            int i = hourIdx + (slot + 1) * STEP_HOURS;
            boolean has = i < h.size();
            views.setViewVisibility(SLOT_HOUR[slot], has ? View.VISIBLE : View.INVISIBLE);
            views.setViewVisibility(SLOT_ICON[slot], has ? View.VISIBLE : View.INVISIBLE);
            views.setViewVisibility(SLOT_TEMP[slot], has ? View.VISIBLE : View.INVISIBLE);
            if (!has) continue;
            views.setTextViewText(SLOT_HOUR[slot], hourFmt.format(new Date(h.time[i])));
            views.setImageViewResource(SLOT_ICON[slot], WeatherCode.icon(h.code[i], h.isDay[i]));
            views.setInt(SLOT_ICON[slot], "setColorFilter", iconColor);
            views.setTextViewText(SLOT_TEMP[slot], uf.temp(h.temp[i]));
        }
        return views;
    }
}
