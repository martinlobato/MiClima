package martinlobato.com.ar.clima;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import martinlobato.com.ar.clima.util.Fmt;

public class FmtTest {

    @Test
    public void temp() {
        assertEquals("23°", Fmt.temp(22.5));
        assertEquals("-3°", Fmt.temp(-3.4));
        assertEquals("0°", Fmt.temp(-0.4));
        assertEquals(Fmt.DASH, Fmt.temp(Double.NaN));
    }

    @Test
    public void compassIndex() {
        assertEquals(0, Fmt.compassIndex(0));
        assertEquals(0, Fmt.compassIndex(359));
        assertEquals(0, Fmt.compassIndex(22.4));
        assertEquals(1, Fmt.compassIndex(22.5));
        assertEquals(2, Fmt.compassIndex(90));
        assertEquals(5, Fmt.compassIndex(225));
        assertEquals(7, Fmt.compassIndex(315));
        assertEquals(0, Fmt.compassIndex(360));
        assertEquals(7, Fmt.compassIndex(-45));
    }

    @Test
    public void uvLevel() {
        assertEquals(0, Fmt.uvLevel(0));
        assertEquals(0, Fmt.uvLevel(2.4));
        assertEquals(1, Fmt.uvLevel(2.5));
        assertEquals(1, Fmt.uvLevel(5));
        assertEquals(2, Fmt.uvLevel(7));
        assertEquals(3, Fmt.uvLevel(10.4));
        assertEquals(4, Fmt.uvLevel(11));
    }

    @Test
    public void oneDecimal() {
        assertEquals("12", Fmt.oneDecimal(12.04));
        assertEquals("0", Fmt.oneDecimal(0));
        assertEquals(Fmt.DASH, Fmt.oneDecimal(Double.NaN));
    }
}
