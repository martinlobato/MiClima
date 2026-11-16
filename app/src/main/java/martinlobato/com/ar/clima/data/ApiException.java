package martinlobato.com.ar.clima.data;

import java.io.IOException;

/** El servicio respondió con un error HTTP; {@link #reason} es lo que informó, si informó algo. */
public class ApiException extends IOException {

    public final int status;
    /** Motivo informado por el servicio (campo "reason" de Open-Meteo); puede ser null. */
    public final String reason;

    public ApiException(int status, String reason) {
        super("HTTP " + status + (reason == null ? "" : ": " + reason));
        this.status = status;
        this.reason = reason;
    }

    /** ¿El servicio rechazó la consulta por exceso de pedidos (límite por conexión)? */
    public boolean isRateLimited() {
        return status == 429 || (reason != null && reason.toLowerCase(java.util.Locale.ROOT).contains("limit"));
    }
}
