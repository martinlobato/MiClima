package martinlobato.com.ar.clima.data;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Alertas configurables (helada, calor, viento, UV, aire): busca en las próximas 24 horas la
 * primera hora que cruza el umbral de cada alerta activada. Lógica pura, sin Android.
 */
public final class WeatherAlerts {

    /** Horas hacia adelante que se revisan. */
    public static final int HOURS_AHEAD = 24;

    private WeatherAlerts() {
    }

    public enum Type {
        /** Temperatura mínima igual o menor al umbral (°C). */
        FROST(0, false),
        /** Temperatura máxima igual o mayor al umbral (°C). */
        HEAT(32, true),
        /** Ráfagas iguales o mayores al umbral (km/h). */
        WIND(60, true),
        /** Índice UV igual o mayor al umbral. */
        UV(8, true),
        /** Índice de calidad del aire (AQI de EE. UU.) igual o mayor al umbral. */
        AIR(101, true);

        /** Umbral por defecto, en unidades métricas. */
        public final double defaultThreshold;
        /** true = alerta cuando el valor sube hasta el umbral; false = cuando baja. */
        public final boolean above;

        Type(double defaultThreshold, boolean above) {
            this.defaultThreshold = defaultThreshold;
            this.above = above;
        }
    }

    /** Una alerta disparada: la primera hora que cruza el umbral y el valor extremo del período. */
    public static final class Alert {
        public final Type type;
        public final long time;
        public final double extreme;

        Alert(Type type, long time, double extreme) {
            this.type = type;
            this.time = time;
            this.extreme = extreme;
        }
    }

    /**
     * @param enabled    alertas activadas, indexadas por {@code Type.ordinal()}
     * @param thresholds umbrales en unidades métricas, indexados igual
     */
    public static List<Alert> evaluate(Forecast f, long now, boolean[] enabled, double[] thresholds) {
        List<Alert> out = new ArrayList<>();
        if (f == null || f.hourly.size() == 0) return out;
        Forecast.Hourly h = f.hourly;
        long end = now + HOURS_AHEAD * 3_600_000L;
        int start = f.hourIndexAt(now);
        for (Type type : Type.values()) {
            if (!enabled[type.ordinal()]) continue;
            double threshold = thresholds[type.ordinal()];
            long first = 0;
            double extreme = Double.NaN;
            if (type == Type.AIR) {
                AirQuality air = f.air;
                if (air == null) continue;
                for (int i = 0; i < air.size(); i++) {
                    if (air.time[i] + 3_600_000L <= now || air.time[i] >= end) continue;
                    double v = air.aqi[i];
                    if (Double.isNaN(v) || v < threshold) continue;
                    if (first == 0) first = air.time[i];
                    if (!(v <= extreme)) extreme = v;
                }
            } else {
                double[] series = type == Type.WIND ? h.gusts : type == Type.UV ? h.uv : h.temp;
                for (int i = start; i < h.size() && h.time[i] < end; i++) {
                    double v = series[i];
                    if (Double.isNaN(v)) continue;
                    boolean crosses = type.above ? v >= threshold : v <= threshold;
                    if (!crosses) continue;
                    if (first == 0) first = h.time[i];
                    if (type.above ? !(v <= extreme) : !(v >= extreme)) extreme = v;
                }
            }
            if (first != 0) out.add(new Alert(type, first, extreme));
        }
        return out;
    }

    /**
     * Clave para no repetir un aviso: tipo + fecha local del evento. La misma helada de esta
     * noche se avisa una vez, aunque el worker corra varias veces.
     */
    public static String dedupeKey(Alert alert, Forecast f) {
        Calendar cal = Calendar.getInstance(f.timeZone());
        cal.setTimeInMillis(alert.time);
        return alert.type.name() + ":" + cal.get(Calendar.YEAR) + "-" + cal.get(Calendar.DAY_OF_YEAR);
    }
}
