package martinlobato.com.ar.clima.ui;

/**
 * Color de una temperatura en °C, de frío (azul) a calor (rojo), interpolado entre puntos fijos.
 * Se usa en las barras de rango de la lista de días y en los termómetros, siempre sobre el valor
 * métrico (no depende de la unidad elegida).
 */
final class TempColors {

    private static final float[] AT = {-10f, 0f, 8f, 15f, 21f, 27f, 33f, 40f};
    private static final int[] COLORS = {
            0xFF6A5ACD, 0xFF3F7FE0, 0xFF2FA8D8, 0xFF3BB38A, 0xFFB8C63A, 0xFFF2A93B, 0xFFEB6A35, 0xFFD2343A,
    };

    private TempColors() {
    }

    static int forCelsius(double c) {
        if (Double.isNaN(c)) return COLORS[3];
        if (c <= AT[0]) return COLORS[0];
        for (int i = 1; i < AT.length; i++) {
            if (c <= AT[i]) {
                float t = (float) ((c - AT[i - 1]) / (AT[i] - AT[i - 1]));
                return blend(COLORS[i - 1], COLORS[i], t);
            }
        }
        return COLORS[COLORS.length - 1];
    }

    /** Fracción 0..1 de {@code c} en la escala -10..40 °C (para el termómetro). */
    static double fraction(double c) {
        return Double.isNaN(c) ? Double.NaN : Math.max(0, Math.min(1, (c + 10) / 50.0));
    }

    static int blend(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return 0xFF000000
                | Math.round(ar + (br - ar) * t) << 16
                | Math.round(ag + (bg - ag) * t) << 8
                | Math.round(ab + (bb - ab) * t);
    }
}
