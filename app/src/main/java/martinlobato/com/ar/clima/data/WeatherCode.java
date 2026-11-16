package martinlobato.com.ar.clima.data;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import martinlobato.com.ar.clima.R;

/** Códigos de clima WMO (los que devuelve Open-Meteo) → descripción e ícono. */
public final class WeatherCode {

    private WeatherCode() {
    }

    @StringRes
    public static int description(double code) {
        if (Double.isNaN(code)) return R.string.wx_unknown;
        switch ((int) code) {
            case 0: return R.string.wx_clear;
            case 1: return R.string.wx_mainly_clear;
            case 2: return R.string.wx_partly_cloudy;
            case 3: return R.string.wx_overcast;
            case 45: case 48: return R.string.wx_fog;
            case 51: case 53: case 55: return R.string.wx_drizzle;
            case 56: case 57: return R.string.wx_freezing_drizzle;
            case 61: return R.string.wx_rain_light;
            case 63: return R.string.wx_rain;
            case 65: return R.string.wx_rain_heavy;
            case 66: case 67: return R.string.wx_freezing_rain;
            case 71: return R.string.wx_snow_light;
            case 73: return R.string.wx_snow;
            case 75: return R.string.wx_snow_heavy;
            case 77: return R.string.wx_snow_grains;
            case 80: case 81: return R.string.wx_showers;
            case 82: return R.string.wx_showers_heavy;
            case 85: case 86: return R.string.wx_snow_showers;
            case 95: return R.string.wx_thunderstorm;
            case 96: case 99: return R.string.wx_thunderstorm_hail;
            default: return R.string.wx_unknown;
        }
    }

    @DrawableRes
    public static int icon(double code, boolean isDay) {
        if (Double.isNaN(code)) return R.drawable.ic_wx_cloudy;
        int c = (int) code;
        if (c <= 1) return isDay ? R.drawable.ic_wx_clear_day : R.drawable.ic_wx_clear_night;
        if (c == 2) return isDay ? R.drawable.ic_wx_partly_day : R.drawable.ic_wx_partly_night;
        if (c == 3) return R.drawable.ic_wx_cloudy;
        if (c == 45 || c == 48) return R.drawable.ic_wx_fog;
        if (c >= 51 && c <= 57) return R.drawable.ic_wx_drizzle;
        if ((c >= 61 && c <= 67) || (c >= 80 && c <= 82)) return R.drawable.ic_wx_rain;
        if ((c >= 71 && c <= 77) || c == 85 || c == 86) return R.drawable.ic_wx_snow;
        if (c >= 95) return R.drawable.ic_wx_thunder;
        return R.drawable.ic_wx_cloudy;
    }
}
