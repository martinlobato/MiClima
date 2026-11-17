package martinlobato.com.ar.clima.work;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import martinlobato.com.ar.clima.MainActivity;
import martinlobato.com.ar.clima.Prefs;
import martinlobato.com.ar.clima.R;
import martinlobato.com.ar.clima.data.Forecast;
import martinlobato.com.ar.clima.data.WeatherAlerts;
import martinlobato.com.ar.clima.util.Fmt;
import martinlobato.com.ar.clima.util.ThemeUtils;
import martinlobato.com.ar.clima.util.UnitFormat;

/** Notificaciones de las alertas configurables y del resumen diario. */
public final class AlertNotifier {

    private static final String CHANNEL_ALERTS = "alerts";
    private static final String CHANNEL_SUMMARY = "summary";
    /** IDs: 10 + tipo para las alertas, 30 para el resumen (el aviso de lluvia usa 1). */
    private static final int ID_ALERT_BASE = 10;
    private static final int ID_SUMMARY = 30;

    private static final int[] TITLES = {R.string.alert_frost_title, R.string.alert_heat_title,
            R.string.alert_wind_title, R.string.alert_uv_title, R.string.alert_air_title};

    private AlertNotifier() {
    }

    /** Revisa el pronóstico recién bajado y avisa las alertas nuevas. */
    public static void check(Context context, Forecast forecast) {
        if (!RainNotifier.canNotify(context) || forecast == null) return;
        WeatherAlerts.Type[] types = WeatherAlerts.Type.values();
        boolean[] enabled = new boolean[types.length];
        double[] thresholds = new double[types.length];
        boolean any = false;
        for (WeatherAlerts.Type t : types) {
            enabled[t.ordinal()] = Prefs.isAlertEnabled(context, t);
            thresholds[t.ordinal()] = Prefs.getAlertThreshold(context, t);
            any |= enabled[t.ordinal()];
        }
        if (!any) return;
        List<WeatherAlerts.Alert> alerts = WeatherAlerts.evaluate(forecast, System.currentTimeMillis(),
                enabled, thresholds);
        for (WeatherAlerts.Alert alert : alerts) {
            String key = WeatherAlerts.dedupeKey(alert, forecast);
            if (Prefs.wasAlertNotified(context, key)) continue;
            show(context, forecast, alert);
            Prefs.markAlertNotified(context, key);
        }
    }

    /** Texto del valor extremo en las unidades elegidas. */
    static String formatValue(Context context, WeatherAlerts.Type type, double value) {
        UnitFormat uf = Prefs.units(context);
        switch (type) {
            case FROST:
            case HEAT:
                return uf.temp(value);
            case WIND:
                return uf.wind(value);
            default:
                return Fmt.round(value);
        }
    }

    @SuppressWarnings("MissingPermission") // se verifica con canNotify() antes de llegar acá
    private static void show(Context context, Forecast forecast, WeatherAlerts.Alert alert) {
        ensureChannel(context, CHANNEL_ALERTS, R.string.alerts_channel, NotificationManager.IMPORTANCE_DEFAULT);
        SimpleDateFormat when = new SimpleDateFormat("EEE HH:mm", Locale.getDefault());
        when.setTimeZone(forecast.timeZone());
        String place = forecast.place != null ? forecast.place : context.getString(R.string.place_unknown);
        String text = context.getString(R.string.alert_text, formatValue(context, alert.type, alert.extreme),
                when.format(new Date(alert.time)).replace(".", ""), place);
        notify(context, CHANNEL_ALERTS, ID_ALERT_BASE + alert.type.ordinal(),
                context.getString(TITLES[alert.type.ordinal()]), text, R.drawable.ic_wx_thunder);
    }

    /** Publica el resumen diario. */
    @SuppressWarnings("MissingPermission")
    static void showSummary(Context context, String title, String text) {
        if (!RainNotifier.canNotify(context)) return;
        ensureChannel(context, CHANNEL_SUMMARY, R.string.summary_channel, NotificationManager.IMPORTANCE_LOW);
        notify(context, CHANNEL_SUMMARY, ID_SUMMARY, title, text, R.drawable.ic_wx_partly_day);
    }

    @SuppressWarnings("MissingPermission")
    private static void notify(Context context, String channel, int id, String title, String text, int icon) {
        Intent open = new Intent(context, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pending = PendingIntent.getActivity(context, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channel)
                .setSmallIcon(icon)
                .setColor(ThemeUtils.currentPrimaryColor(context))
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pending)
                .setAutoCancel(true);
        NotificationManagerCompat.from(context).notify(id, builder.build());
    }

    private static void ensureChannel(Context context, String id, int nameRes, int importance) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null || nm.getNotificationChannel(id) != null) return;
        nm.createNotificationChannel(new NotificationChannel(id, context.getString(nameRes), importance));
    }
}
