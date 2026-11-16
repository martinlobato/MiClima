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
}
