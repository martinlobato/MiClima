package martinlobato.com.ar.clima;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONException;
import org.junit.Test;

import java.util.List;

import martinlobato.com.ar.clima.data.GeocodingApi;
import martinlobato.com.ar.clima.data.Place;

public class GeocodingApiTest {

    // Recorte de una respuesta real de geocoding-api.open-meteo.com para "Chilecito".
    private static final String SAMPLE = "{\"results\":["
            + "{\"id\":3861445,\"name\":\"Chilecito\",\"latitude\":-29.16163,\"longitude\":-67.49934,"
            + "\"elevation\":1107.0,\"feature_code\":\"PPLA2\",\"country_code\":\"AR\",\"timezone\":"
            + "\"America/Argentina/La_Rioja\",\"population\":42248,\"country\":\"Argentina\","
            + "\"admin1\":\"Provincia de La Rioja\",\"admin2\":\"Departamento de Chilecito\"},"
            + "{\"id\":3895111,\"name\":\"Chilecito\",\"latitude\":-30.75077,\"longitude\":-70.84716,"
            + "\"country_code\":\"CL\",\"country\":\"Chile\"},"
            + "{\"id\":1,\"name\":\"Sin coordenadas\"}"
            + "],\"generationtime_ms\":0.5}";

    @Test
    public void parsesResults() throws Exception {
        List<Place> places = GeocodingApi.parse(SAMPLE);
        assertEquals(2, places.size()); // el que no trae coordenadas se descarta
        Place p = places.get(0);
        assertEquals(3861445L, p.id);
        assertEquals("Chilecito", p.name);
        assertEquals("Provincia de La Rioja", p.region);
        assertEquals("Argentina", p.country);
        assertEquals(-29.16163, p.latitude, 1e-9);
        assertEquals(-67.49934, p.longitude, 1e-9);
        assertEquals("Provincia de La Rioja, Argentina", p.subtitle());
        assertEquals("p3861445", p.cacheKey());

        assertNull(places.get(1).region);
        assertEquals("Chile", places.get(1).subtitle());
    }

    @Test
    public void noMatchesIsAnEmptyList() throws Exception {
        assertTrue(GeocodingApi.parse("{\"generationtime_ms\":0.12}").isEmpty());
    }

    @Test(expected = JSONException.class)
    public void apiErrorIsRejected() throws Exception {
        GeocodingApi.parse("{\"error\":true,\"reason\":\"Parameter count must be between 1 and 100.\"}");
    }
}
