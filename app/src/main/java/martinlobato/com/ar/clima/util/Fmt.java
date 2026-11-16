package martinlobato.com.ar.clima.util;

import java.util.Locale;

/** Formateo de valores numéricos del clima. Sin dependencias de Android (testeable en JVM). */
public final class Fmt {

    public static final String DASH = "—";

    private Fmt() {
    }

    /** Entero redondeado, o "—" si no hay dato. Evita mostrar "-0". */
    public static String round(double value) {
        if (Double.isNaN(value)) return DASH;
        return String.valueOf(Math.round(value));
    }

    /** Temperatura redondeada con símbolo de grado: "23°". */
    public static String temp(double value) {
        if (Double.isNaN(value)) return DASH;
        return Math.round(value) + "°";
    }

    /** Un decimal salvo que sea entero: "0.4", "12". */
    public static String oneDecimal(double value) {
        if (Double.isNaN(value)) return DASH;
        double r = Math.round(value * 10) / 10.0;
        if (r == Math.rint(r)) return String.valueOf((long) r);
        return String.format(Locale.getDefault(), "%.1f", r);
    }

    /** Índice (0..7) del punto cardinal para un rumbo en grados: 0=N, 1=NE, 2=E, … 7=NO. */
    public static int compassIndex(double degrees) {
        double d = ((degrees % 360) + 360) % 360;
        return (int) Math.floor((d + 22.5) / 45.0) % 8;
    }

    /** Nivel de UV según la escala de la OMS: 0 bajo, 1 moderado, 2 alto, 3 muy alto, 4 extremo. */
    public static int uvLevel(double uv) {
        long v = Math.round(uv);
        if (v <= 2) return 0;
        if (v <= 5) return 1;
        if (v <= 7) return 2;
        if (v <= 10) return 3;
        return 4;
    }

    /**
     * Categoría del índice de calidad del aire de EE. UU. (AQI): 0 buena, 1 moderada,
     * 2 dañina para grupos sensibles, 3 dañina, 4 muy dañina, 5 peligrosa.
     */
    public static int aqiLevel(double aqi) {
        if (aqi <= 50) return 0;
        if (aqi <= 100) return 1;
        if (aqi <= 150) return 2;
        if (aqi <= 200) return 3;
        if (aqi <= 300) return 4;
        return 5;
    }

    /**
     * Posición (0..1) de un AQI en una barra de 6 tramos de igual ancho, uno por categoría
     * (0–50, 50–100, 100–150, 150–200, 200–300, 300–500).
     */
    public static double aqiFraction(double aqi) {
        double[] bounds = {0, 50, 100, 150, 200, 300, 500};
        if (Double.isNaN(aqi) || aqi <= 0) return 0;
        if (aqi >= 500) return 1;
        for (int i = 0; i < 6; i++) {
            if (aqi <= bounds[i + 1]) {
                return (i + (aqi - bounds[i]) / (bounds[i + 1] - bounds[i])) / 6.0;
            }
        }
        return 1;
    }
}
