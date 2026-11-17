package martinlobato.com.ar.clima.data;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Pronóstico completo de un lugar: condiciones actuales, serie por hora (16 días) y por día.
 * Los valores que la API puede devolver como {@code null} se guardan como {@link Double#NaN}.
 * Todos los instantes son epoch millis; para mostrarlos usar {@link #timeZone()} (la zona del
 * lugar consultado, no la del teléfono).
 */
public final class Forecast {

    public double latitude;
    public double longitude;
    /** Id IANA de la zona horaria del lugar (por ejemplo "America/Santiago"). */
    public String timezoneId;
    public int utcOffsetSeconds;
    public long fetchedAt;
    /** Nombre del lugar para mostrar; puede ser null. */
    public String place;
    /** Modelo con el que se pidió este pronóstico. */
    public WeatherModel model = WeatherModel.AUTO;
    /** Calidad del aire; null si no se pudo obtener. */
    public AirQuality air;
    /** Estado del mar; null si no se pidió, falló o el lugar no tiene costa. */
    public Marine marine;

    public final Current current = new Current();
    public final Hourly hourly = new Hourly();
    public final Daily daily = new Daily();

    public static final class Current {
        public long time;
        public boolean isDay = true;
        public double temp = Double.NaN;
        public double apparent = Double.NaN;
        public double humidity = Double.NaN;
        public double precipitation = Double.NaN;
        public double code = Double.NaN;
        public double cloud = Double.NaN;
        public double pressure = Double.NaN;
        public double wind = Double.NaN;
        public double windDir = Double.NaN;
        public double gusts = Double.NaN;
    }

    public static final class Hourly {
        public long[] time = new long[0];
        public boolean[] isDay = new boolean[0];
        public double[] temp, apparent, humidity, dewPoint, precipProb, precip, code, pressure,
                cloud, visibility, wind, windDir, gusts, uv;
        /** Nieve caída en la hora (cm), nieve acumulada en el suelo (m) y altura de la isoterma 0 °C (m). */
        public double[] snowfall, snowDepth, freezingLevel;

        public int size() {
            return time.length;
        }
    }

    public static final class Daily {
        public long[] time = new long[0];
        public long[] sunrise = new long[0];
        public long[] sunset = new long[0];
        public double[] code, tempMax, tempMin, uvMax, precipSum, precipProbMax, windMax,
                gustsMax, windDirDominant;
        /** Nieve caída en el día (cm). */
        public double[] snowfallSum;

        public int size() {
            return time.length;
        }
    }

    public TimeZone timeZone() {
        return resolveTimeZone(timezoneId, utcOffsetSeconds);
    }

    static TimeZone resolveTimeZone(String id, int utcOffsetSeconds) {
        if (id != null) {
            TimeZone tz = TimeZone.getTimeZone(id);
            // getTimeZone devuelve GMT cuando no conoce el id; en ese caso usar el offset fijo.
            if (!"GMT".equals(tz.getID()) || "GMT".equals(id) || "UTC".equals(id)) {
                return tz;
            }
        }
        return new java.util.SimpleTimeZone(utcOffsetSeconds * 1000, id != null ? id : "local");
    }

    /** Índice de la hora en curso para {@code millis}: la última con inicio <= millis (0 si es anterior). */
    public int hourIndexAt(long millis) {
        return lastIndexAtOrBefore(hourly.time, millis);
    }

    /** Índice del día al que pertenece {@code millis} (0 si es anterior al primer día). */
    public int dayIndexAt(long millis) {
        return lastIndexAtOrBefore(daily.time, millis);
    }

    /** Primer índice horario que cae dentro del día {@code dayIndex}; -1 si no hay. */
    public int firstHourIndexOfDay(int dayIndex) {
        if (dayIndex < 0 || dayIndex >= daily.size()) return -1;
        long start = daily.time[dayIndex];
        for (int i = 0; i < hourly.size(); i++) {
            if (hourly.time[i] >= start) return i;
        }
        return -1;
    }

    /** ¿{@code a} y {@code b} caen en el mismo día calendario del lugar? */
    public boolean sameLocalDay(long a, long b) {
        Calendar ca = Calendar.getInstance(timeZone());
        ca.setTimeInMillis(a);
        Calendar cb = Calendar.getInstance(timeZone());
        cb.setTimeInMillis(b);
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR)
                && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR);
    }

    private static int lastIndexAtOrBefore(long[] times, long millis) {
        int idx = 0;
        for (int i = 0; i < times.length; i++) {
            if (times[i] <= millis) idx = i;
            else break;
        }
        return idx;
    }
}
