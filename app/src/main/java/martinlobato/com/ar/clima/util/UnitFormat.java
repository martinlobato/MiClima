package martinlobato.com.ar.clima.util;

import java.util.Locale;

/**
 * Formatea valores métricos de la API en las unidades elegidas por el usuario.
 * Todos los métodos reciben el valor tal como viene de la API (°C, km/h, hPa, mm, km) y
 * devuelven "—" cuando no hay dato. Sin dependencias de Android.
 */
public final class UnitFormat {

    public final Units.Temp temp;
    public final Units.Wind wind;
    public final Units.Pressure pressure;
    public final Units.Precip precip;

    public UnitFormat(Units.Temp temp, Units.Wind wind, Units.Pressure pressure, Units.Precip precip) {
        this.temp = temp;
        this.wind = wind;
        this.pressure = pressure;
        this.precip = precip;
    }

    public static UnitFormat metric() {
        return new UnitFormat(Units.Temp.C, Units.Wind.KMH, Units.Pressure.HPA, Units.Precip.MM);
    }

    // ---- temperatura

    public double tempValue(double celsius) {
        return Units.temp(celsius, temp);
    }

    /** "23°" (sin letra de unidad: se elige una sola vez en Ajustes). */
    public String temp(double celsius) {
        return Fmt.temp(tempValue(celsius));
    }

    /** Diferencia de temperaturas (no una temperatura): en °F solo se escala, sin sumar 32. */
    public String tempDiff(double celsiusDelta) {
        if (Double.isNaN(celsiusDelta)) return Fmt.DASH;
        double v = temp == Units.Temp.F ? celsiusDelta * 9.0 / 5.0 : celsiusDelta;
        return Math.abs(Math.round(v)) + "°";
    }

    // ---- viento

    public double windValue(double kmh) {
        return Units.wind(kmh, wind);
    }

    /** Número ya convertido: m/s lleva un decimal porque sus valores son chicos. */
    public String windNumber(double kmh) {
        return windAxis(windValue(kmh));
    }

    /** Formatea un valor de viento que ya está en la unidad elegida (ejes de gráficos). */
    public String windAxis(double converted) {
        return wind == Units.Wind.MS ? Fmt.oneDecimal(converted) : Fmt.round(converted);
    }

    public String wind(double kmh) {
        return withUnit(windNumber(kmh), wind.label, kmh);
    }

    // ---- presión

    public double pressureValue(double hpa) {
        return Units.pressure(hpa, pressure);
    }

    public String pressureNumber(double hpa) {
        return pressureAxis(pressureValue(hpa));
    }

    /** Formatea un valor de presión que ya está en la unidad elegida (ejes de gráficos). */
    public String pressureAxis(double converted) {
        return pressure == Units.Pressure.INHG ? twoDecimals(converted) : Fmt.round(converted);
    }

    public String pressure(double hpa) {
        return withUnit(pressureNumber(hpa), pressure.label, hpa);
    }

    // ---- precipitación

    public double precipValue(double mm) {
        return Units.precip(mm, precip);
    }

    public String precipNumber(double mm) {
        double v = Units.precip(mm, precip);
        return precip == Units.Precip.IN ? twoDecimals(v) : Fmt.oneDecimal(v);
    }

    public String precip(double mm) {
        return withUnit(precipNumber(mm), precip.label, mm);
    }

    // ---- distancia (visibilidad)

    public String distanceNumber(double km) {
        double v = Units.distance(km, precip);
        if (Double.isNaN(v)) return Fmt.DASH;
        return v >= 10 ? Fmt.round(v) : Fmt.oneDecimal(v);
    }

    public String distance(double km) {
        return withUnit(distanceNumber(km), precip.distanceLabel, km);
    }

    // ---- nieve, alturas y olas (siguen a la unidad de lluvia: métrico o imperial)

    private boolean imperial() {
        return precip == Units.Precip.IN;
    }

    /** Nieve caída, recibida en cm: "12 cm" o "4.7 in". */
    public String snow(double cm) {
        if (Double.isNaN(cm)) return Fmt.DASH;
        return imperial() ? Fmt.oneDecimal(cm / 2.54) + " in" : Fmt.oneDecimal(cm) + " cm";
    }

    /** Nieve en la unidad elegida (cm o in), para graficar. */
    public double snowValue(double cm) {
        return imperial() ? cm / 2.54 : cm;
    }

    public String snowNumber(double cm) {
        if (Double.isNaN(cm)) return Fmt.DASH;
        return Fmt.oneDecimal(imperial() ? cm / 2.54 : cm);
    }

    public String snowLabel() {
        return imperial() ? "in" : "cm";
    }

    /** Altura sobre el nivel del mar, recibida en metros, redondeada a decenas: "3250 m" o "10660 ft". */
    public String altitude(double meters) {
        if (Double.isNaN(meters)) return Fmt.DASH;
        return altitudeNumber(meters) + " " + altitudeLabel();
    }

    public double altitudeValue(double meters) {
        return imperial() ? meters * 3.28084 : meters;
    }

    public String altitudeNumber(double meters) {
        if (Double.isNaN(meters)) return Fmt.DASH;
        return String.valueOf(Math.round(altitudeValue(meters) / 10.0) * 10);
    }

    public String altitudeLabel() {
        return imperial() ? "ft" : "m";
    }

    /** Altura de ola, recibida en metros: "1.4 m" o "4.6 ft". */
    public String waveHeight(double meters) {
        if (Double.isNaN(meters)) return Fmt.DASH;
        return Fmt.oneDecimal(waveValue(meters)) + " " + altitudeLabel();
    }

    public double waveValue(double meters) {
        return imperial() ? meters * 3.28084 : meters;
    }

    // ---- porcentaje

    public static String percent(double value) {
        return Double.isNaN(value) ? Fmt.DASH : Fmt.round(value) + "%";
    }

    private static String withUnit(String number, String unit, double raw) {
        return Double.isNaN(raw) ? Fmt.DASH : number + " " + unit;
    }

    private static String twoDecimals(double value) {
        if (Double.isNaN(value)) return Fmt.DASH;
        return String.format(Locale.getDefault(), "%.2f", value);
    }
}
