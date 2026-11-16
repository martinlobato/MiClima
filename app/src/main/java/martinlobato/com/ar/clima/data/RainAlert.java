package martinlobato.com.ar.clima.data;

/**
 * Detecta lluvia próxima en el pronóstico y decide si corresponde avisar.
 * Sin dependencias de Android (testeable en JVM).
 */
public final class RainAlert {

    /** Horas hacia adelante que se miran. */
    public static final int HOURS_AHEAD = 6;
    /** A partir de esta probabilidad (%) una hora cuenta como "lluvia probable". */
    static final double MIN_PROBABILITY = 60;
    /** O a partir de esta precipitación (mm en la hora), aunque no haya probabilidad informada. */
    static final double MIN_PRECIPITATION = 0.5;

    private static final long HOUR_MS = 60 * 60 * 1000L;

    private RainAlert() {
    }

    /** Primera hora próxima con lluvia probable. */
    public static final class Event {
        /** Inicio de la hora con lluvia (epoch millis). */
        public final long time;
        /** Probabilidad en %, o NaN si el modelo no la informa. */
        public final double probability;
        /** Precipitación de esa hora en mm, o NaN. */
        public final double precipitation;

        Event(long time, double probability, double precipitation) {
            this.time = time;
            this.probability = probability;
            this.precipitation = precipitation;
        }
    }

    /**
     * Busca lluvia en las próximas {@link #HOURS_AHEAD} horas.
     *
     * @return la primera hora con lluvia probable, o null si no hay, si ya está lloviendo
     * (no hace falta avisar) o si el pronóstico no cubre este momento
     */
    public static Event find(Forecast f, long now) {
        if (f == null || f.hourly.size() == 0) return null;
        Forecast.Hourly h = f.hourly;
        if (now < h.time[0] || now >= h.time[h.size() - 1] + HOUR_MS) return null;
        int current = f.hourIndexAt(now);
        if (isRainy(h, current)) return null;
        for (int i = current + 1; i <= current + HOURS_AHEAD && i < h.size(); i++) {
            if (isRainy(h, i)) {
                return new Event(h.time[i], h.precipProb[i], h.precip[i]);
            }
        }
        return null;
    }

    private static boolean isRainy(Forecast.Hourly h, int i) {
        return h.precipProb[i] >= MIN_PROBABILITY || h.precip[i] >= MIN_PRECIPITATION;
    }

    /**
     * ¿Hay que notificar este evento? No se repite el aviso de la misma lluvia: se considera la
     * misma si empieza a menos de 3 horas de la última avisada y ese aviso fue hace menos de 12.
     */
    public static boolean shouldNotify(long eventTime, long lastEventTime, long lastNotifiedAt, long now) {
        if (lastNotifiedAt <= 0) return true;
        boolean sameRain = Math.abs(eventTime - lastEventTime) < 3 * HOUR_MS;
        return !(sameRain && now - lastNotifiedAt < 12 * HOUR_MS);
    }
}
