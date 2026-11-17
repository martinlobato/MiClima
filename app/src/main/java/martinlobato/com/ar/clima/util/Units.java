package martinlobato.com.ar.clima.util;

/**
 * Unidades de medida elegibles en Ajustes y sus conversiones. La API siempre entrega valores
 * métricos (°C, km/h, hPa, mm, km); acá se convierten solo para mostrarlos, así la caché sigue
 * sirviendo al cambiar de unidad. Sin dependencias de Android.
 */
public final class Units {

    private Units() {
    }

    public enum Temp {
        C("°C"), F("°F");

        public final String label;

        Temp(String label) {
            this.label = label;
        }
    }

    public enum Wind {
        KMH("km/h"), MS("m/s"), KN("kn"), MPH("mph");

        public final String label;

        Wind(String label) {
            this.label = label;
        }
    }

    public enum Pressure {
        HPA("hPa"), MMHG("mmHg"), INHG("inHg");

        public final String label;

        Pressure(String label) {
            this.label = label;
        }
    }

    /** Unidad de precipitación; también define la de distancias (mm → km, in → mi). */
    public enum Precip {
        MM("mm", "km"), IN("in", "mi");

        public final String label;
        public final String distanceLabel;

        Precip(String label, String distanceLabel) {
            this.label = label;
            this.distanceLabel = distanceLabel;
        }
    }

    public static double temp(double celsius, Temp unit) {
        return unit == Temp.F ? celsius * 9.0 / 5.0 + 32.0 : celsius;
    }

    public static double wind(double kmh, Wind unit) {
        switch (unit) {
            case MS: return kmh / 3.6;
            case KN: return kmh / 1.852;
            case MPH: return kmh / 1.609344;
            default: return kmh;
        }
    }

    public static double pressure(double hpa, Pressure unit) {
        switch (unit) {
            case MMHG: return hpa * 0.750061683;
            case INHG: return hpa * 0.0295299831;
            default: return hpa;
        }
    }

    public static double precip(double mm, Precip unit) {
        return unit == Precip.IN ? mm / 25.4 : mm;
    }

    public static double distance(double km, Precip unit) {
        return unit == Precip.IN ? km / 1.609344 : km;
    }

    /** Inversa de {@link #temp}: de la unidad elegida a °C. */
    public static double tempToCelsius(double value, Temp unit) {
        return unit == Temp.F ? (value - 32.0) * 5.0 / 9.0 : value;
    }

    /** Inversa de {@link #wind}: de la unidad elegida a km/h. */
    public static double windToKmh(double value, Wind unit) {
        switch (unit) {
            case MS: return value * 3.6;
            case KN: return value * 1.852;
            case MPH: return value * 1.609344;
            default: return value;
        }
    }

    /** Valor del enum por nombre, o {@code fallback} si el nombre guardado no existe. */
    public static <E extends Enum<E>> E parse(Class<E> type, String name, E fallback) {
        if (name == null) return fallback;
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
