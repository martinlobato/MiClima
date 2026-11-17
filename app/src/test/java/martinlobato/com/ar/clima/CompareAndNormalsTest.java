package martinlobato.com.ar.clima;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import martinlobato.com.ar.clima.data.ClimateNormals;
import martinlobato.com.ar.clima.data.ModelComparison;
import martinlobato.com.ar.clima.util.UnitFormat;
import martinlobato.com.ar.clima.util.Units;

public class CompareAndNormalsTest {

    private static String resource(String name) throws Exception {
        try (InputStream in = CompareAndNormalsTest.class.getResourceAsStream(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    /** Respuesta real de Open-Meteo con los cinco modelos (Santiago, 2 días). */
    @Test
    public void parsesARealMultiModelResponse() throws Exception {
        ModelComparison c = ModelComparison.parse(resource("/compare_real_santiago.json"), 7L);
        assertEquals(7L, c.fetchedAt);
        assertEquals("America/Santiago", c.timezoneId);
        assertEquals(48, c.time.length);
        assertEquals(5, c.temp.length);
        assertEquals(3_600_000L, c.time[1] - c.time[0]);
        for (int m = 0; m < 5; m++) {
            assertEquals(48, c.temp[m].length);
            assertFalse("modelo " + m, Double.isNaN(c.temp[m][0]));
            assertFalse(Double.isNaN(c.wind[m][47]));
            assertFalse(Double.isNaN(c.precip[m][10]));
        }
        // Orden de MODELS: ECMWF primero, GFS segundo (valores de la respuesta).
        assertEquals(15.8, c.temp[0][0], 1e-9);
        assertEquals(17.3, c.temp[1][0], 1e-9);
    }

    @Test
    public void spreadIgnoresMissingModels() {
        assertEquals(4.0, ModelComparison.spread(new double[]{22, Double.NaN, 26, 24}), 1e-9);
        assertTrue(Double.isNaN(ModelComparison.spread(new double[]{22, Double.NaN})));
        assertTrue(Double.isNaN(ModelComparison.spread(new double[]{})));
    }

    @Test
    public void normalsAverageEachDateOverTheYearsWithAWindow() {
        // Dos años con la misma temperatura todos los días salvo un día aislado muy caluroso.
        java.util.List<String> dates = new java.util.ArrayList<>();
        java.util.List<Double> maxs = new java.util.ArrayList<>();
        for (int year = 2023; year <= 2024; year++) {
            java.util.Calendar cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
            cal.clear();
            cal.set(year, 0, 1);
            while (cal.get(java.util.Calendar.YEAR) == year) {
                String d = String.format(java.util.Locale.US, "%04d-%02d-%02d", year,
                        cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH));
                dates.add(d);
                maxs.add(d.equals("2023-07-10") ? 35.0 : 20.0);
                cal.add(java.util.Calendar.DAY_OF_MONTH, 1);
            }
        }
        int n = dates.size();
        double[] max = new double[n];
        double[] min = new double[n];
        for (int i = 0; i < n; i++) {
            max[i] = maxs.get(i);
            min[i] = 10;
        }
        ClimateNormals normals = ClimateNormals.compute(dates.toArray(new String[0]), max, min);
        assertEquals(20.0, normals.maxFor(1, 1), 1e-9);
        assertEquals(10.0, normals.minFor(12, 31), 1e-9);
        // El día aislado pesa 1 entre 30 muestras (15 días × 2 años): sube medio grado, no 15.
        assertEquals(20.5, normals.maxFor(7, 10), 1e-9);
        assertEquals(20.0, normals.maxFor(7, 18), 1e-9);
        // 29 de febrero: un solo año lo tiene, pero la ventana toma a sus vecinos.
        assertEquals(20.0, normals.maxFor(2, 29), 1e-9);
        assertEquals(59, ClimateNormals.dayIndex(2, 29));
        assertEquals(60, ClimateNormals.dayIndex(3, 1));
        assertEquals(365, ClimateNormals.dayIndex(12, 31));
    }

    /** Diez años reales de Santiago: el verano es más cálido que el invierno, y sobrevive al guardado. */
    @Test
    public void normalsFromARealArchiveResponse() throws Exception {
        ClimateNormals n = ClimateNormals.fromArchive(resource("/archive_real_santiago.json"));
        double january = n.maxFor(1, 15);
        double july = n.maxFor(7, 15);
        assertTrue("enero " + january, january > 27 && january < 34);
        assertTrue("julio " + july, july > 11 && july < 19);
        assertTrue(n.minFor(7, 15) < n.minFor(1, 15));
        assertTrue(n.minFor(1, 15) < january);

        n.latitude = -33.45;
        n.longitude = -70.66;
        n.fetchedAt = 99L;
        ClimateNormals back = ClimateNormals.fromJson(n.toJson());
        assertEquals(99L, back.fetchedAt);
        assertEquals(january, back.maxFor(1, 15), 0.006);
        assertEquals(n.minFor(11, 17), back.minFor(11, 17), 0.006);
    }

    @Test
    public void temperatureDifferencesDoNotAddTheFahrenheitOffset() {
        assertEquals("3°", UnitFormat.metric().tempDiff(3.2));
        assertEquals("3°", UnitFormat.metric().tempDiff(-3.2));
        UnitFormat f = new UnitFormat(Units.Temp.F, Units.Wind.MPH, Units.Pressure.INHG, Units.Precip.IN);
        assertEquals("9°", f.tempDiff(5));
        assertEquals("41°", f.temp(5));
    }
}
