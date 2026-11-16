package martinlobato.com.ar.clima.util;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.util.List;
import java.util.Locale;

/** Ubicación aproximada de una sola lectura + nombre del lugar. */
public final class LocationHelper {

    private static final long FRESH_MS = 30 * 60_000L;

    private LocationHelper() {
    }

    public interface Callback {
        /** {@code location} es null si no se pudo obtener ninguna. Se llama en el hilo principal. */
        void onLocation(@Nullable Location location);
    }

    public static boolean hasPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Última ubicación conocida si es reciente; si no, pide una lectura nueva. Si Google Play
     * Services no está disponible cae a la última conocida del LocationManager del sistema.
     */
    @SuppressLint("MissingPermission")
    public static void getLocation(Context context, Callback callback) {
        final Context app = context.getApplicationContext();
        final FusedLocationProviderClient client;
        try {
            client = LocationServices.getFusedLocationProviderClient(app);
        } catch (RuntimeException e) {
            callback.onLocation(frameworkLastKnown(app));
            return;
        }
        client.getLastLocation().addOnCompleteListener(lastTask -> {
            final Location last = lastTask.isSuccessful() ? lastTask.getResult() : null;
            if (last != null && System.currentTimeMillis() - last.getTime() < FRESH_MS) {
                callback.onLocation(last);
                return;
            }
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                    .addOnCompleteListener(curTask -> {
                        Location cur = curTask.isSuccessful() ? curTask.getResult() : null;
                        if (cur != null) {
                            callback.onLocation(cur);
                        } else if (last != null) {
                            callback.onLocation(last);
                        } else {
                            callback.onLocation(frameworkLastKnown(app));
                        }
                    });
        });
    }

    @SuppressLint("MissingPermission")
    @Nullable
    private static Location frameworkLastKnown(Context context) {
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return null;
        Location best = null;
        try {
            for (String provider : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(provider);
                if (l != null && (best == null || l.getTime() > best.getTime())) {
                    best = l;
                }
            }
        } catch (SecurityException | IllegalArgumentException ignored) {
            // Sin permiso o proveedor inexistente: no hay ubicación.
        }
        return best;
    }

    /** Nombre de la localidad para unas coordenadas (bloqueante: usa red). Null si no se pudo. */
    @Nullable
    @SuppressWarnings("deprecation")
    public static String placeName(Context context, double lat, double lon) {
        if (!Geocoder.isPresent()) return null;
        try {
            List<Address> results = new Geocoder(context, Locale.getDefault()).getFromLocation(lat, lon, 1);
            if (results == null || results.isEmpty()) return null;
            Address a = results.get(0);
            if (a.getLocality() != null) return a.getLocality();
            if (a.getSubAdminArea() != null) return a.getSubAdminArea();
            return a.getAdminArea();
        } catch (Exception e) {
            return null;
        }
    }
}
