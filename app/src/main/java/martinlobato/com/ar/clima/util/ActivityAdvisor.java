package martinlobato.com.ar.clima.util;

/**
 * "Mejor momento para…": puntúa cada hora (0–100) para una actividad al aire libre según
 * temperatura, lluvia, viento, humedad, UV y luz, y busca el mejor tramo del día.
 * Reglas simples y explicables, no un modelo. Sin dependencias de Android.
 */
public final class ActivityAdvisor {

    /** Puntaje mínimo para considerar que una hora "sirve". */
    public static final int GOOD = 60;
    /** A partir de este promedio el tramo se califica como muy bueno. */
    public static final int GREAT = 80;

    private ActivityAdvisor() {
    }

    public enum Activity {
        RUN, BIKE, LAUNDRY, BBQ
    }

    /** Condiciones de una hora, en unidades métricas (NaN si falta el dato). */
    public static final class Hour {
        public double apparent = Double.NaN;
        public double precipProb = Double.NaN;
        public double precip = Double.NaN;
        public double wind = Double.NaN;
        public double gusts = Double.NaN;
        public double humidity = Double.NaN;
        public double uv = Double.NaN;
        public boolean isDay = true;
        /** Hora local del día, 0–23. */
        public int hourOfDay;
    }

    /** Mejor tramo encontrado: [from, to) en índices del arreglo recibido, y su puntaje medio. */
    public static final class Window {
        public final int from;
        public final int to;
        public final int score;
        /** Hora del tramo con mejor puntaje (la primera, si hay empate). */
        public final int peak;

        Window(int from, int to, int score, int peak) {
            this.from = from;
            this.to = to;
            this.score = score;
            this.peak = peak;
        }
    }

    /** 1 dentro de [lo, hi]; baja linealmente hasta 0 en {@code min} y {@code max}. */
    static double comfort(double value, double min, double lo, double hi, double max) {
        if (Double.isNaN(value)) return 0.7; // sin dato: ni se premia ni se descarta
        if (value <= min || value >= max) return 0;
        if (value < lo) return (value - min) / (lo - min);
        if (value > hi) return (max - value) / (max - hi);
        return 1;
    }

    private static boolean raining(Hour h) {
        return h.precipProb >= 60 || h.precip >= 0.3;
    }

    private static double rainFactor(Hour h) {
        if (raining(h)) return 0;
        if (h.precipProb >= 30) return 0.6;
        return 1;
    }

    public static int score(Activity activity, Hour h) {
        double s;
        switch (activity) {
            case RUN:
                if (h.hourOfDay < 6 || h.hourOfDay > 22) return 0;
                s = comfort(h.apparent, 0, 10, 20, 32) * rainFactor(h);
                if (h.wind > 30) s *= 0.6;
                if (h.uv >= 8) s *= 0.6;
                break;
            case BIKE:
                if (h.hourOfDay < 6 || h.hourOfDay > 22) return 0;
                if (h.gusts > 45) return 0;
                s = comfort(h.apparent, 2, 12, 24, 34) * rainFactor(h);
                if (h.wind > 25) s *= 0.5;
                if (!h.isDay) s *= 0.5;
                if (h.uv >= 9) s *= 0.7;
                break;
            case LAUNDRY:
                // Secar ropa: de día, sin lluvia, aire seco y algo de viento ayudan.
                if (!h.isDay || raining(h) || h.precipProb >= 40) return 0;
                s = comfort(h.apparent, 5, 16, 40, 50);
                s *= Double.isNaN(h.humidity) ? 0.7 : comfort(h.humidity, -1, 0, 50, 90);
                if (h.wind >= 8 && h.wind <= 30) s = Math.min(1, s * 1.15);
                if (h.gusts > 50) s *= 0.5;
                break;
            case BBQ:
            default:
                if (h.hourOfDay < 11 || h.hourOfDay > 22) return 0;
                s = comfort(h.apparent, 8, 17, 29, 36) * rainFactor(h);
                if (h.wind > 25) s *= 0.5;
                if (h.gusts > 45) s *= 0.5;
                break;
        }
        return (int) Math.round(Math.max(0, Math.min(1, s)) * 100);
    }

    /**
     * Mejor tramo continuo de horas con puntaje ≥ {@link #GOOD}. Entre varios, gana el de mayor
     * promedio; a igual promedio, el más largo. Null si ninguna hora sirve.
     */
    public static Window best(Activity activity, Hour[] hours) {
        Window best = null;
        int i = 0;
        while (i < hours.length) {
            if (score(activity, hours[i]) < GOOD) {
                i++;
                continue;
            }
            int from = i;
            int total = 0;
            int peak = i;
            int peakScore = -1;
            while (i < hours.length && score(activity, hours[i]) >= GOOD) {
                int sc = score(activity, hours[i]);
                total += sc;
                if (sc > peakScore) {
                    peakScore = sc;
                    peak = i;
                }
                i++;
            }
            int mean = Math.round(total / (float) (i - from));
            if (best == null || mean > best.score
                    || (mean == best.score && i - from > best.to - best.from)) {
                best = new Window(from, i, mean, peak);
            }
        }
        return best;
    }
}
