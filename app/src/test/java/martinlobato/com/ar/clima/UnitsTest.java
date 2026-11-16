package martinlobato.com.ar.clima;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Locale;

import martinlobato.com.ar.clima.util.Fmt;
import martinlobato.com.ar.clima.util.UnitFormat;
import martinlobato.com.ar.clima.util.Units;

public class UnitsTest {

    private Locale previous;

    @Before
    public void setUp() {
        previous = Locale.getDefault();
        Locale.setDefault(Locale.US);
    }

    @After
    public void tearDown() {
        Locale.setDefault(previous);
    }

    @Test
    public void conversions() {
        assertEquals(32, Units.temp(0, Units.Temp.F), 1e-9);
        assertEquals(77, Units.temp(25, Units.Temp.F), 1e-9);
        assertEquals(-40, Units.temp(-40, Units.Temp.F), 1e-9);
        assertEquals(10, Units.wind(36, Units.Wind.MS), 1e-9);
        assertEquals(10, Units.wind(18.52, Units.Wind.KN), 1e-9);
        assertEquals(10, Units.wind(16.09344, Units.Wind.MPH), 1e-9);
        assertEquals(760, Units.pressure(1013.25, Units.Pressure.MMHG), 0.01);
        assertEquals(29.92, Units.pressure(1013.25, Units.Pressure.INHG), 0.005);
        assertEquals(1, Units.precip(25.4, Units.Precip.IN), 1e-9);
        assertEquals(10, Units.distance(16.09344, Units.Precip.IN), 1e-9);
    }

    @Test
    public void metricFormatting() {
        UnitFormat u = UnitFormat.metric();
        assertEquals("23°", u.temp(22.6));
        assertEquals("13 km/h", u.wind(12.6));
        assertEquals("1013 hPa", u.pressure(1013.4));
        assertEquals("6.4 mm", u.precip(6.44));
        assertEquals("0 mm", u.precip(0));
        assertEquals("24 km", u.distance(24.14));
        assertEquals("8.2 km", u.distance(8.2));
        assertEquals("38%", UnitFormat.percent(38.2));
    }

    @Test
    public void imperialFormatting() {
        UnitFormat u = new UnitFormat(Units.Temp.F, Units.Wind.MPH, Units.Pressure.INHG, Units.Precip.IN);
        assertEquals("73°", u.temp(22.6));
        assertEquals("8 mph", u.wind(12.6));
        assertEquals("29.93 inHg", u.pressure(1013.4));
        assertEquals("0.25 in", u.precip(6.44));
        assertEquals("15 mi", u.distance(24.14));
    }

    @Test
    public void metersPerSecondKeepsOneDecimal() {
        UnitFormat u = new UnitFormat(Units.Temp.C, Units.Wind.MS, Units.Pressure.MMHG, Units.Precip.MM);
        assertEquals("3.5 m/s", u.wind(12.6));
        assertEquals("760 mmHg", u.pressure(1013.25));
    }

    @Test
    public void missingValuesAreDashes() {
        UnitFormat u = new UnitFormat(Units.Temp.F, Units.Wind.KN, Units.Pressure.INHG, Units.Precip.IN);
        assertEquals(Fmt.DASH, u.temp(Double.NaN));
        assertEquals(Fmt.DASH, u.wind(Double.NaN));
        assertEquals(Fmt.DASH, u.pressure(Double.NaN));
        assertEquals(Fmt.DASH, u.precip(Double.NaN));
        assertEquals(Fmt.DASH, u.distance(Double.NaN));
        assertEquals(Fmt.DASH, UnitFormat.percent(Double.NaN));
    }

    @Test
    public void parseFallsBackOnUnknownNames() {
        assertEquals(Units.Wind.KN, Units.parse(Units.Wind.class, "KN", Units.Wind.KMH));
        assertEquals(Units.Wind.KMH, Units.parse(Units.Wind.class, "FURLONGS", Units.Wind.KMH));
        assertEquals(Units.Temp.C, Units.parse(Units.Temp.class, null, Units.Temp.C));
    }
}
