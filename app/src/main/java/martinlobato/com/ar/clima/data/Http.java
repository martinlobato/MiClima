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
            // Identificación genérica de la app (sin datos del usuario ni del dispositivo): algunos
            // filtros rechazan el User-Agent por defecto de Android.
            conn.setRequestProperty("User-Agent", "MiClima/1.0 (Android)");
            int status = conn.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                String server = conn.getHeaderField("Server");
                String reason = reasonFrom(readAll(conn.getErrorStream(), 4096));
                throw new ApiException(status, reason == null && server != null
                        ? "servidor " + server : reason);
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

    /**
     * Motivo del error: el campo "reason" de Open-Meteo, o si la respuesta no es de Open-Meteo
     * (por ejemplo la página de un filtro de red), el comienzo de su texto. Null si está vacía.
     */
    static String reasonFrom(String body) {
        if (body == null || body.trim().isEmpty()) return null;
        try {
            String reason = new org.json.JSONObject(body).optString("reason", "");
            if (!reason.isEmpty()) return reason;
        } catch (org.json.JSONException e) {
            // No es JSON: se muestra el texto.
        }
        String text = body.replaceAll("(?s)<(script|style)[^>]*>.*?</\\1>", " ")
                .replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
        if (text.isEmpty()) return null;
        return text.length() > 160 ? text.substring(0, 160) + "…" : text;
    }
}
