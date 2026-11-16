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
