package martinlobato.com.ar.clima.util;

/**
 * Luna calculada en el teléfono, sin red: fase, porcentaje iluminado y horas de salida y puesta.
 * Fórmulas de baja precisión (las de la biblioteca SunCalc): error de pocos minutos, suficiente
 * para una app de clima. Sin dependencias de Android.
 */
public final class Moon {

    private static final double RAD = Math.PI / 180;
    private static final double DAY_MS = 86_400_000.0;
    private static final double J1970 = 2440588;
    private static final double J2000 = 2451545;
    private static final double OBLIQUITY = RAD * 23.4397;
    private static final long HOUR_MS = 3_600_000L;

    private Moon() {
    }

    /** Fase e iluminación en un instante. */
    public static final class Phase {
        /** Fracción iluminada del disco, 0..1. */
        public final double illumination;
        /** Edad dentro del ciclo, 0..1: 0 nueva, 0,25 cuarto creciente, 0,5 llena, 0,75 menguante. */
        public final double cycle;

        Phase(double illumination, double cycle) {
            this.illumination = illumination;
            this.cycle = cycle;
        }

        /**
         * Nombre de la fase en 8 pasos: 0 nueva, 1 creciente, 2 cuarto creciente, 3 gibosa
         * creciente, 4 llena, 5 gibosa menguante, 6 cuarto menguante, 7 menguante.
         */
        public int index() {
            return (int) Math.floor(cycle * 8 + 0.5) % 8;
        }
    }

    /** Salida y puesta dentro de un día; 0 en la que no ocurre ese día. */
    public static final class Times {
        public final long rise;
        public final long set;

        Times(long rise, long set) {
            this.rise = rise;
            this.set = set;
        }
    }

    private static double toDays(long millis) {
        return millis / DAY_MS - 0.5 + J1970 - J2000;
    }

    private static double rightAscension(double l, double b) {
        return Math.atan2(Math.sin(l) * Math.cos(OBLIQUITY) - Math.tan(b) * Math.sin(OBLIQUITY), Math.cos(l));
    }

    private static double declination(double l, double b) {
        return Math.asin(Math.sin(b) * Math.cos(OBLIQUITY) + Math.cos(b) * Math.sin(OBLIQUITY) * Math.sin(l));
    }

    /** {ascensión recta, declinación, distancia en km} de la Luna. */
    private static double[] moonCoords(double d) {
        double l = RAD * (218.316 + 13.176396 * d);
        double m = RAD * (134.963 + 13.064993 * d);
        double f = RAD * (93.272 + 13.229350 * d);
        double lon = l + RAD * 6.289 * Math.sin(m);
        double lat = RAD * 5.128 * Math.sin(f);
        double dist = 385001 - 20905 * Math.cos(m);
        return new double[]{rightAscension(lon, lat), declination(lon, lat), dist};
    }

    /** {ascensión recta, declinación} del Sol. */
    private static double[] sunCoords(double d) {
        double m = RAD * (357.5291 + 0.98560028 * d);
        double c = RAD * (1.9148 * Math.sin(m) + 0.02 * Math.sin(2 * m) + 0.0003 * Math.sin(3 * m));
        double l = m + c + RAD * 102.9372 + Math.PI;
        return new double[]{rightAscension(l, 0), declination(l, 0)};
    }

    public static Phase phase(long millis) {
        double d = toDays(millis);
        double[] s = sunCoords(d);
        double[] m = moonCoords(d);
        double sunDistance = 149598000;
        double phi = Math.acos(Math.sin(s[1]) * Math.sin(m[1])
                + Math.cos(s[1]) * Math.cos(m[1]) * Math.cos(s[0] - m[0]));
        double inc = Math.atan2(sunDistance * Math.sin(phi), m[2] - sunDistance * Math.cos(phi));
        double angle = Math.atan2(Math.cos(s[1]) * Math.sin(s[0] - m[0]),
                Math.sin(s[1]) * Math.cos(m[1]) - Math.cos(s[1]) * Math.sin(m[1]) * Math.cos(s[0] - m[0]));
        double illumination = (1 + Math.cos(inc)) / 2;
        double cycle = 0.5 + 0.5 * inc * (angle < 0 ? -1 : 1) / Math.PI;
        return new Phase(illumination, cycle);
    }

    /** Altura de la Luna sobre el horizonte (radianes), con refracción atmosférica. */
    private static double altitude(long millis, double latitude, double longitude) {
        double lw = RAD * -longitude;
        double phi = RAD * latitude;
        double d = toDays(millis);
        double[] c = moonCoords(d);
        double hourAngle = RAD * (280.16 + 360.9856235 * d) - lw - c[0];
        double h = Math.asin(Math.sin(phi) * Math.sin(c[1]) + Math.cos(phi) * Math.cos(c[1]) * Math.cos(hourAngle));
        double hr = Math.max(h, 0);
        return h + 0.0002967 / Math.tan(hr + 0.00312536 / (hr + 0.08901179));
    }

    /**
     * Salida y puesta de la Luna en las 24 horas que empiezan en {@code dayStart} (medianoche
     * local del lugar). Busca los cruces del horizonte ajustando una parábola cada dos horas.
     */
    public static Times times(long dayStart, double latitude, double longitude) {
        double hc = 0.133 * RAD;
        double h0 = altitude(dayStart, latitude, longitude) - hc;
        double rise = Double.NaN;
        double set = Double.NaN;
        for (int i = 1; i <= 24; i += 2) {
            double h1 = altitude(dayStart + i * HOUR_MS, latitude, longitude) - hc;
            double h2 = altitude(dayStart + (i + 1) * HOUR_MS, latitude, longitude) - hc;
            double a = (h0 + h2) / 2 - h1;
            double b = (h2 - h0) / 2;
            double xe = -b / (2 * a);
            double ye = (a * xe + b) * xe + h1;
            double disc = b * b - 4 * a * h1;
            int roots = 0;
            double x1 = 0;
            double x2 = 0;
            if (disc >= 0) {
                double dx = Math.sqrt(disc) / (Math.abs(a) * 2);
                x1 = xe - dx;
                x2 = xe + dx;
                if (Math.abs(x1) <= 1) roots++;
                if (Math.abs(x2) <= 1) roots++;
                if (x1 < -1) x1 = x2;
            }
            if (roots == 1) {
                if (h0 < 0) rise = i + x1;
                else set = i + x1;
            } else if (roots == 2) {
                rise = i + (ye < 0 ? x2 : x1);
                set = i + (ye < 0 ? x1 : x2);
            }
            if (!Double.isNaN(rise) && !Double.isNaN(set)) break;
            h0 = h2;
        }
        return new Times(
                Double.isNaN(rise) ? 0 : dayStart + Math.round(rise * HOUR_MS),
                Double.isNaN(set) ? 0 : dayStart + Math.round(set * HOUR_MS));
    }
}
