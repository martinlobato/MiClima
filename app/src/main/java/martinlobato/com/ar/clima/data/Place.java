package martinlobato.com.ar.clima.data;

/** Lugar guardado por el usuario (ciudad buscada). La ubicación actual no es un Place. */
public final class Place {

    public final long id;
    public final String name;
    /** Provincia/región; puede ser null. */
    public final String region;
    /** País; puede ser null. */
    public final String country;
    public final double latitude;
    public final double longitude;

    public Place(long id, String name, String region, String country, double latitude, double longitude) {
        this.id = id;
        this.name = name;
        this.region = region;
        this.country = country;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    /** "Provincia de La Rioja, Argentina" (omite lo que falte o repita el nombre). */
    public String subtitle() {
        StringBuilder sb = new StringBuilder();
        if (region != null && !region.isEmpty() && !region.equals(name)) {
            sb.append(region);
        }
        if (country != null && !country.isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(country);
        }
        return sb.toString();
    }

    /** Clave de la caché de pronóstico de este lugar. */
    public String cacheKey() {
        return "p" + id;
    }
}
