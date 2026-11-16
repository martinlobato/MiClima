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
import java.util.Locale;

import martinlobato.com.ar.clima.MainActivity;
import martinlobato.com.ar.clima.Prefs;
import martinlobato.com.ar.clima.R;
import martinlobato.com.ar.clima.data.Forecast;
import martinlobato.com.ar.clima.data.RainAlert;
import martinlobato.com.ar.clima.util.ThemeUtils;
import martinlobato.com.ar.clima.util.UnitFormat;

/** Aviso de lluvia próxima: una notificación por cada lluvia que se acerca. */
public final class RainNotifier {

    private static final String CHANNEL_ID = "rain";
    private static final int NOTIFICATION_ID = 1;

    private RainNotifier() {
    }

    /** ¿El sistema permite mostrar notificaciones de la app? */
    public static boolean canNotify(Context context) {
        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    /**
     * Revisa el pronóstico recién bajado y avisa si viene lluvia y todavía no se avisó.
     * No hace nada si el aviso está apagado en Ajustes o las notificaciones están bloqueadas.
     */
    public static void check(Context context, Forecast forecast) {
        if (!Prefs.isRainAlertEnabled(context) || !canNotify(context)) return;
        long now = System.currentTimeMillis();
        RainAlert.Event event = RainAlert.find(forecast, now);
        if (event == null) return;
        if (!RainAlert.shouldNotify(event.time, Prefs.getLastRainEventTime(context),
                Prefs.getLastRainNotifiedAt(context), now)) {
            return;
        }
        show(context, forecast, event);
        Prefs.setLastRainNotification(context, event.time, now);
    }

    @SuppressWarnings("MissingPermission") // se verifica con canNotify() antes de llegar acá
    private static void show(Context context, Forecast forecast, RainAlert.Event event) {
        ensureChannel(context);
        SimpleDateFormat hourFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
        hourFmt.setTimeZone(forecast.timeZone());
        String title = context.getString(R.string.rain_alert_title, hourFmt.format(new Date(event.time)));

        UnitFormat uf = Prefs.units(context);
        String place = forecast.place != null ? forecast.place : context.getString(R.string.place_unknown);
        String text = Double.isNaN(event.probability)
                ? context.getString(R.string.rain_alert_text_amount, uf.precip(event.precipitation), place)
                : context.getString(R.string.rain_alert_text, UnitFormat.percent(event.probability), place);

        Intent open = new Intent(context, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pending = PendingIntent.getActivity(context, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_wx_rain)
                .setColor(ThemeUtils.currentPrimaryColor(context))
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build());
    }

    private static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null || nm.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                context.getString(R.string.rain_alert_channel), NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(context.getString(R.string.rain_alert_desc));
        nm.createNotificationChannel(channel);
    }
}
