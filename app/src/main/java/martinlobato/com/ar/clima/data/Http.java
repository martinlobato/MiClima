package martinlobato.com.ar.clima.data;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** GET HTTP mínimo con HttpURLConnection. Llamar siempre fuera del hilo principal. */
final class Http {

    private Http() {
    }

    /**
     * Devuelve el cuerpo como texto. Lanza {@link ApiException} si el servicio responde con un
     * estado distinto de 200 (con el motivo que informe) e IOException ante errores de red.
     */
    static String get(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(20_000);
            conn.setRequestProperty("Accept", "application/json");
            int status = conn.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new ApiException(status, reasonFrom(readAll(conn.getErrorStream(), 4096)));
            }
            try (InputStream in = conn.getInputStream()) {
                return readAll(in, Integer.MAX_VALUE);
            }
        } finally {
            conn.disconnect();
        }
    }

    private static String readAll(InputStream in, int limit) throws IOException {
        if (in == null) return "";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while (out.size() < limit && (n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    /** Open-Meteo informa los errores como {"error":true,"reason":"..."}; null si no viene así. */
    static String reasonFrom(String body) {
        if (body == null || body.isEmpty()) return null;
        try {
            String reason = new org.json.JSONObject(body).optString("reason", "");
            return reason.isEmpty() ? null : reason;
        } catch (org.json.JSONException e) {
            return null;
        }
    }
}
