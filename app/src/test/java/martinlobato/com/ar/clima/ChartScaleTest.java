package martinlobato.com.ar.clima;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import martinlobato.com.ar.clima.util.ChartScale;

public class ChartScaleTest {

    @Test
    public void roundsToNiceSteps() {
        ChartScale s = ChartScale.of(11.3, 27.8, 4);
        assertEquals(5, s.step, 1e-9);
        assertEquals(10, s.min, 1e-9);
        assertEquals(30, s.max, 1e-9);
        assertEquals(5, s.tickCount());
        assertEquals(20, s.tick(2), 1e-9);
        assertEquals(0.5, s.fraction(20), 1e-9);
    }

    @Test
    public void handlesNegativesAndSmallRanges() {
        ChartScale s = ChartScale.of(-3.2, 4.1, 4);
        assertEquals(2, s.step, 1e-9);
        assertEquals(-4, s.min, 1e-9);
        assertEquals(6, s.max, 1e-9);

        ChartScale p = ChartScale.of(1012.4, 1014.9, 4);
        assertEquals(1, p.step, 1e-9);
        assertEquals(1012, p.min, 1e-9);
        assertEquals(1015, p.max, 1e-9);
    }

    @Test
    public void constantSeriesStillHasARange() {
        ChartScale s = ChartScale.of(20, 20, 4);
        assertTrue(s.max > s.min);
        assertTrue(s.min <= 20 && s.max >= 20);
    }

    @Test
    public void swappedOrMissingInputs() {
        ChartScale s = ChartScale.of(30, 10, 4);
        assertEquals(10, s.min, 1e-9);
        assertEquals(30, s.max, 1e-9);

        ChartScale n = ChartScale.of(Double.NaN, Double.NaN, 4);
        assertTrue(n.max > n.min);
    }
}
