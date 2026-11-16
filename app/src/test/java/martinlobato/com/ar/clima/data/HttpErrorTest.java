package martinlobato.com.ar.clima.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HttpErrorTest {

    @Test
    public void readsTheReasonOpenMeteoReports() {
        assertEquals("Daily API request limit exceeded. Please try again tomorrow.", Http.reasonFrom(
                "{\"reason\":\"Daily API request limit exceeded. Please try again tomorrow.\",\"error\":true}"));
        assertNull(Http.reasonFrom(""));
        assertNull(Http.reasonFrom("   "));
        // Respuesta que no es de Open-Meteo (página de un filtro): se muestra su texto.
        assertEquals("403 Forbidden Access denied", Http.reasonFrom(
                "<html><head><style>b{color:red}</style></head><body><h1>403 Forbidden</h1>\n<p>Access denied</p></body></html>"));
        assertEquals("{\"error\":true}", Http.reasonFrom("{\"error\":true}"));
    }

    @Test
    public void rateLimitIsRecognised() {
        assertTrue(new ApiException(429, null).isRateLimited());
        assertTrue(new ApiException(400, "Daily API request limit exceeded. Please try again tomorrow.").isRateLimited());
        assertFalse(new ApiException(400, "Parameter 'models' is invalid").isRateLimited());
        assertEquals("HTTP 400: Parameter 'models' is invalid",
                new ApiException(400, "Parameter 'models' is invalid").getMessage());
        assertEquals("HTTP 502", new ApiException(502, null).getMessage());
    }
}
