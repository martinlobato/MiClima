package martinlobato.com.ar.clima.util;

/**
 * Escala "linda" para el eje Y de un gráfico: límites y paso redondeados a 1, 2 o 5 × 10^n,
 * de modo que las marcas caigan en números fáciles de leer. Sin dependencias de Android.
 */
public final class ChartScale {

    public final double min;
    public final double max;
    public final double step;

    private ChartScale(double min, double max, double step) {
        this.min = min;
        this.max = max;
        this.step = step;
    }

    /**
     * @param dataMin     menor valor a mostrar
     * @param dataMax     mayor valor a mostrar
     * @param targetTicks cantidad aproximada de intervalos deseados (>= 1)
     */
    public static ChartScale of(double dataMin, double dataMax, int targetTicks) {
        if (Double.isNaN(dataMin) || Double.isNaN(dataMax)) {
            return new ChartScale(0, 1, 1);
        }
        if (dataMax < dataMin) {
            double t = dataMin;
            dataMin = dataMax;
            dataMax = t;
        }
        double span = dataMax - dataMin;
        if (span <= 0) {
            // Serie constante: se abre una unidad alrededor para que la línea no quede en el borde.
            span = Math.max(1, Math.abs(dataMin) * 0.1);
        }
        double step = niceStep(span / Math.max(1, targetTicks));
        double min = Math.floor(dataMin / step) * step;
        double max = Math.ceil(dataMax / step) * step;
        if (max <= min) {
            max = min + step;
        }
        return new ChartScale(min, max, step);
    }

    /** Escala con límites fijos (por ejemplo 0–100 para porcentajes). */
    public static ChartScale fixed(double min, double max, double step) {
        return new ChartScale(min, max, step);
    }

    /** Cantidad de marcas, incluyendo ambos extremos. */
    public int tickCount() {
        return (int) Math.round((max - min) / step) + 1;
    }

    public double tick(int index) {
        return min + index * step;
    }

    /** Posición relativa (0 = mínimo, 1 = máximo) de un valor dentro de la escala. */
    public double fraction(double value) {
        return (value - min) / (max - min);
    }

    static double niceStep(double rough) {
        if (rough <= 0 || Double.isNaN(rough) || Double.isInfinite(rough)) return 1;
        double exp = Math.pow(10, Math.floor(Math.log10(rough)));
        double f = rough / exp;
        double nice;
        if (f <= 1) nice = 1;
        else if (f <= 2) nice = 2;
        else if (f <= 5) nice = 5;
        else nice = 10;
        return nice * exp;
    }
}
