package martinlobato.com.ar.clima.ui;

import android.Manifest;
import android.content.Context;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import martinlobato.com.ar.clima.MainActivity;
import martinlobato.com.ar.clima.Prefs;
import martinlobato.com.ar.clima.R;
import martinlobato.com.ar.clima.data.AirQuality;
import martinlobato.com.ar.clima.data.ClimateNormals;
import martinlobato.com.ar.clima.data.ApiException;
import martinlobato.com.ar.clima.data.Forecast;
import martinlobato.com.ar.clima.data.Place;
import martinlobato.com.ar.clima.data.PlaceStore;
import martinlobato.com.ar.clima.data.WeatherCode;
import martinlobato.com.ar.clima.data.WeatherRepository;
import martinlobato.com.ar.clima.databinding.FragmentHomeBinding;
import martinlobato.com.ar.clima.databinding.ItemDailyBinding;
import martinlobato.com.ar.clima.databinding.ItemHourBinding;
import martinlobato.com.ar.clima.util.BackgroundExecutor;
import martinlobato.com.ar.clima.util.Fmt;
import martinlobato.com.ar.clima.util.LocationHelper;
import martinlobato.com.ar.clima.util.UnitFormat;
import martinlobato.com.ar.clima.widget.WeatherWidgetProvider;

/**
 * Inicio: condiciones actuales, pronóstico hora por hora de los 16 días, detalle de ahora,
 * lista de días y horarios del sol. Muestra primero la caché y actualiza si está vieja.
 */
public class HomeFragment extends Fragment {

    /** Pasado este tiempo la caché se considera vieja y se actualiza al abrir. */
    private static final long STALE_MS = 30 * 60_000L;

    private FragmentHomeBinding binding;
    private Context appCtx;
    private Forecast forecast;
    private boolean refreshing;
    private UnitFormat uf = UnitFormat.metric();
    /** Lugar guardado que se está mostrando, o null si es la ubicación actual. */
    @Nullable
    private Place place;
    private String cacheKey = WeatherRepository.KEY_CURRENT_LOCATION;
    /** Normales climáticos del lugar mostrado; null hasta que se calculan. */
    @Nullable
    private ClimateNormals normals;
    private boolean normalsRequested;
    private final HourlyAdapter hourlyAdapter = new HourlyAdapter();
    private LinearLayoutManager hourlyLayout;
    private ActivityResultLauncher<String> permissionLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> refresh());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        appCtx = requireContext().getApplicationContext();
        uf = Prefs.units(appCtx);
        place = PlaceStore.selected(appCtx);
        cacheKey = place == null ? WeatherRepository.KEY_CURRENT_LOCATION : place.cacheKey();

        binding.swipeRefresh.setColorSchemeColors(
                MaterialColors.getColor(view, androidx.appcompat.R.attr.colorPrimary));
        binding.swipeRefresh.setOnRefreshListener(this::refresh);

        setupHourlyStrip();

        BackgroundExecutor.execute(() -> WeatherRepository.loadCached(appCtx, cacheKey), cached -> {
            if (binding == null) return;
            if (cached != null && forecast == null) {
                forecast = cached;
                render();
            }
            // También se actualiza si en Ajustes se eligió otro modelo que el de la caché.
            if (forecast == null || System.currentTimeMillis() - forecast.fetchedAt > STALE_MS
                    || forecast.model != Prefs.getModel(appCtx)) {
                refresh();
            }
        });
    }

    private void setupHourlyStrip() {
        hourlyLayout = new LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false);
        binding.rvHourly.setLayoutManager(hourlyLayout);
        binding.rvHourly.setAdapter(hourlyAdapter);
        hourlyAdapter.setOnDayClickListener(this::openDay);
        binding.rvHourly.setItemAnimator(null);

        // Alto exacto = alto de una celda de hora, para que las fichas de día (match_parent)
        // ocupen toda la tira; con wrap_content quedarían achicadas arriba.
        ItemHourBinding probe = ItemHourBinding.inflate(getLayoutInflater(), binding.rvHourly, false);
        probe.tvHour.setText("00:00");
        probe.tvTemp.setText("00°");
        probe.tvRain.setText("00%");
        probe.tvWind.setText("00");
        int unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        probe.getRoot().measure(unspecified, unspecified);
        binding.rvHourly.getLayoutParams().height = probe.getRoot().getMeasuredHeight();

        binding.rvHourly.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                updateHourlyDayLabel();
            }
        });
    }

    // ---------------------------------------------------------------- carga de datos

    private void refresh() {
        if (binding == null || refreshing) return;
        if (place != null) {
            // Lugar guardado: no hace falta permiso ni lectura de ubicación.
            setRefreshing(true);
            fetch(place.latitude, place.longitude, place.name);
            return;
        }
        if (!LocationHelper.hasPermission(appCtx)) {
            refreshFromSavedPlace(R.string.empty_permission_title, R.string.empty_permission_desc, true);
            return;
        }
        setRefreshing(true);
        LocationHelper.getLocation(appCtx, location -> {
            if (binding == null) return;
            if (location != null) {
                fetch(location.getLatitude(), location.getLongitude(), null);
            } else {
                setRefreshing(false);
                refreshFromSavedPlace(R.string.empty_error_title, R.string.empty_location_desc, false);
            }
        });
    }

    /** Sin ubicación actual: actualiza el último lugar conocido o, si no hay, explica qué falta. */
    private void refreshFromSavedPlace(int emptyTitle, int emptyDesc, boolean askPermission) {
        if (Prefs.hasLastPlace(appCtx)) {
            setRefreshing(true);
            fetch(Prefs.getLastLat(appCtx), Prefs.getLastLon(appCtx), Prefs.getLastPlace(appCtx));
        } else {
            setRefreshing(false);
            showEmpty(emptyTitle, emptyDesc, askPermission);
        }
    }

    /** Resultado de una bajada: el pronóstico, o el error que la impidió. */
    private static final class FetchResult {
        final Forecast forecast;
        final Exception error;

        FetchResult(Forecast forecast, Exception error) {
            this.forecast = forecast;
            this.error = error;
        }
    }

    private void fetch(final double lat, final double lon, @Nullable final String knownPlace) {
        BackgroundExecutor.execute(() -> {
            try {
                String name = knownPlace != null ? knownPlace : LocationHelper.placeName(appCtx, lat, lon);
                return new FetchResult(WeatherRepository.fetch(appCtx, cacheKey, lat, lon, name), null);
            } catch (Exception e) {
                return new FetchResult(null, e);
            }
        }, result -> {
            if (binding == null) return;
            setRefreshing(false);
            if (result.forecast != null) {
                forecast = result.forecast;
                render();
                WeatherWidgetProvider.requestUpdate(appCtx);
            } else if (forecast != null) {
                Toast.makeText(appCtx, getString(R.string.error_refresh) + "\n" + describe(result.error),
                        Toast.LENGTH_LONG).show();
            } else {
                showEmpty(R.string.empty_error_title, R.string.empty_error_desc, false);
                binding.tvEmptyDetail.setText(describe(result.error));
                binding.tvEmptyDetail.setVisibility(View.VISIBLE);
            }
        });
    }

    /** Explica por qué falló la bajada, con el motivo real para poder diagnosticarlo. */
    private String describe(@Nullable Exception error) {
        if (error instanceof ApiException) {
            ApiException api = (ApiException) error;
            if (api.isRateLimited()) return getString(R.string.error_rate_limited);
            return getString(R.string.error_service, error.getMessage());
        }
        if (error instanceof java.net.UnknownHostException || error instanceof java.net.ConnectException
                || error instanceof java.net.SocketTimeoutException) {
            return getString(R.string.error_no_connection);
        }
        if (error == null) return "";
        String message = error.getMessage();
        return getString(R.string.error_detail, error.getClass().getSimpleName()
                + (message == null ? "" : ": " + message));
    }

    private void setRefreshing(boolean value) {
        refreshing = value;
        if (binding != null) {
            binding.swipeRefresh.setRefreshing(value);
        }
    }

    private void showEmpty(int titleRes, int descRes, boolean askPermission) {
        if (forecast != null) return; // hay datos en pantalla: no taparlos
        binding.content.setVisibility(View.GONE);
        binding.emptyState.setVisibility(View.VISIBLE);
        binding.tvEmptyDetail.setVisibility(View.GONE);
        binding.tvEmptyTitle.setText(titleRes);
        binding.tvEmptyDesc.setText(descRes);
        binding.btnEmptyAction.setText(askPermission
                ? R.string.empty_permission_action : R.string.action_retry);
        // Sin permiso de ubicación igual se puede usar la app buscando una ciudad.
        binding.btnEmptySearch.setVisibility(askPermission ? View.VISIBLE : View.GONE);
        binding.btnEmptySearch.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openPlaces();
            }
        });
        binding.btnEmptyAction.setOnClickListener(v -> {
            if (askPermission) {
                permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION);
            } else {
                refresh();
            }
        });
    }

    // ---------------------------------------------------------------- render

    private void render() {
        final Forecast f = forecast;
        if (binding == null || f == null || f.hourly.size() == 0 || f.daily.size() == 0) return;
        binding.emptyState.setVisibility(View.GONE);
        binding.content.setVisibility(View.VISIBLE);

        long now = System.currentTimeMillis();
        int hourIdx = f.hourIndexAt(now);
        int dayIdx = f.dayIndexAt(now);
        Forecast.Current c = f.current;
        Forecast.Hourly h = f.hourly;
        Forecast.Daily d = f.daily;
        // Si la caché es de otra hora, "ahora" sale de la serie horaria y no del bloque current.
        boolean currentIsFresh = c.time > 0 && Math.abs(now - c.time) < DateUtils.HOUR_IN_MILLIS;
        double temp = currentIsFresh ? c.temp : h.temp[hourIdx];
        double apparent = currentIsFresh ? c.apparent : h.apparent[hourIdx];
        double code = currentIsFresh ? c.code : h.code[hourIdx];
        boolean isDay = currentIsFresh ? c.isDay : h.isDay[hourIdx];
        double humidity = currentIsFresh ? c.humidity : h.humidity[hourIdx];
        double wind = currentIsFresh ? c.wind : h.wind[hourIdx];
        double windDir = currentIsFresh ? c.windDir : h.windDir[hourIdx];
        double gusts = currentIsFresh ? c.gusts : h.gusts[hourIdx];
        double pressure = currentIsFresh ? c.pressure : h.pressure[hourIdx];
        double cloud = currentIsFresh ? c.cloud : h.cloud[hourIdx];

        binding.tvPlace.setText(f.place != null ? f.place : getString(R.string.place_unknown));
        String ago = DateUtils.getRelativeTimeSpanString(
                f.fetchedAt, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE)
                .toString().toLowerCase(Locale.getDefault());
        binding.tvStatus.setText(getString(R.string.status_updated_model, ago, f.model.label));
        binding.ivCurrentIcon.setImageResource(WeatherCode.icon(code, isDay));
        binding.tvCurrentTemp.setText(uf.temp(temp));
        binding.tvCondition.setText(WeatherCode.description(code));
        binding.tvFeelsLike.setText(getString(R.string.feels_like, uf.temp(apparent)));
        binding.tvHiLo.setText(getString(R.string.hi_lo,
                uf.temp(d.tempMax[dayIdx]), uf.temp(d.tempMin[dayIdx])));

        binding.tvHourlyTitle.setText(getString(R.string.section_hourly, d.size()));
        hourlyAdapter.submit(f, hourIdx, uf);
        hourlyLayout.scrollToPositionWithOffset(0, 0);
        binding.rvHourly.post(this::updateHourlyDayLabel);

        String[] compass = getResources().getStringArray(R.array.compass_points);
        String[] uvLevels = getResources().getStringArray(R.array.uv_levels);
        double uv = h.uv[hourIdx];
        binding.gridDetails.removeAllViews();
        addCellRow(binding.gridDetails,
                R.string.label_feels_like, uf.temp(apparent),
                R.string.label_humidity, UnitFormat.percent(humidity));
        addCellRow(binding.gridDetails,
                R.string.label_wind, Double.isNaN(wind) || Double.isNaN(windDir) ? uf.wind(wind)
                        : uf.wind(wind) + " " + compass[Fmt.compassIndex(windDir)],
                R.string.label_gusts, uf.wind(gusts));
        addCellRow(binding.gridDetails,
                R.string.label_uv, Double.isNaN(uv) ? Fmt.DASH
                        : getString(R.string.fmt_uv, Fmt.round(uv), uvLevels[Fmt.uvLevel(uv)]),
                R.string.label_pressure, uf.pressure(pressure));
        addCellRow(binding.gridDetails,
                R.string.label_visibility, uf.distance(h.visibility[hourIdx] / 1000.0),
                R.string.label_dew_point, uf.temp(h.dewPoint[hourIdx]));
        addCellRow(binding.gridDetails,
                R.string.label_cloud_cover, UnitFormat.percent(cloud),
                R.string.label_precip_today, uf.precip(d.precipSum[dayIdx]));

        renderNormal();
        loadNormals();
        renderAir(f, now);
        renderDaily(f, dayIdx);
        renderSun(f, dayIdx);
    }

    /** "Máxima de hoy 3° sobre lo normal para la fecha (24°)"; oculto si aún no hay normales. */
    private void renderNormal() {
        Forecast f = forecast;
        if (binding == null || f == null || normals == null || f.daily.size() == 0) {
            if (binding != null) binding.tvNormal.setVisibility(View.GONE);
            return;
        }
        int dayIdx = f.dayIndexAt(System.currentTimeMillis());
        java.util.Calendar cal = java.util.Calendar.getInstance(f.timeZone());
        cal.setTimeInMillis(f.daily.time[dayIdx]);
        double normal = normals.maxFor(cal.get(java.util.Calendar.MONTH) + 1,
                cal.get(java.util.Calendar.DAY_OF_MONTH));
        double today = f.daily.tempMax[dayIdx];
        if (Double.isNaN(normal) || Double.isNaN(today)) {
            binding.tvNormal.setVisibility(View.GONE);
            return;
        }
        double diff = today - normal;
        // Menos de un grado y medio de diferencia se considera normal.
        String text = Math.abs(diff) < 1.5 ? getString(R.string.normal_equal, uf.temp(normal))
                : getString(diff > 0 ? R.string.normal_above : R.string.normal_below,
                uf.tempDiff(diff), uf.temp(normal));
        binding.tvNormal.setText(text);
        binding.tvNormal.setVisibility(View.VISIBLE);
    }

    /**
     * Busca los normales del lugar una vez por pantalla: usa los guardados o, si faltan, baja diez
     * años de historia. Si falla no se muestra nada; no es un dato imprescindible.
     */
    private void loadNormals() {
        if (normalsRequested) return;
        normalsRequested = true;
        final String key = cacheKey;
        BackgroundExecutor.execute(() -> {
            try {
                double[] coords = WeatherRepository.selectedCoordinates(appCtx);
                return coords == null ? null : WeatherRepository.normals(appCtx, key, coords[0], coords[1]);
            } catch (Exception e) {
                return null;
            }
        }, result -> {
            if (binding == null || result == null) return;
            normals = result;
            renderNormal();
        });
    }

    /** Tarjeta de calidad del aire; se oculta si no hay datos para este momento. */
    private void renderAir(Forecast f, long now) {
        AirQuality air = f.air;
        double aqi = Double.NaN;
        double pm25 = Double.NaN;
        double pm10 = Double.NaN;
        double ozone = Double.NaN;
        double no2 = Double.NaN;
        if (air != null) {
            if (air.currentTime > 0 && Math.abs(now - air.currentTime) < 2 * DateUtils.HOUR_IN_MILLIS) {
                aqi = air.currentAqi;
                pm25 = air.currentPm25;
                pm10 = air.currentPm10;
                ozone = air.currentOzone;
                no2 = air.currentNo2;
            } else if (air.size() > 0 && now < air.time[air.size() - 1] + DateUtils.HOUR_IN_MILLIS) {
                // Caché de otra hora: se toma la hora en curso de la serie horaria.
                int i = 0;
                for (int k = 0; k < air.size() && air.time[k] <= now; k++) i = k;
                aqi = air.aqi[i];
                pm25 = air.pm25[i];
                pm10 = air.pm10[i];
                ozone = air.ozone[i];
            }
        }
        if (Double.isNaN(aqi)) {
            binding.sectionAir.setVisibility(View.GONE);
            return;
        }
        binding.sectionAir.setVisibility(View.VISIBLE);
        binding.tvAqi.setText(Fmt.round(aqi));
        binding.tvAqiLevel.setText(getResources().getStringArray(R.array.aqi_levels)[Fmt.aqiLevel(aqi)]);
        binding.aqiBar.setAqi(aqi);
        binding.gridAir.removeAllViews();
        addCellRow(binding.gridAir, R.string.label_pm25, ugm3(pm25), R.string.label_pm10, ugm3(pm10));
        addCellRow(binding.gridAir, R.string.label_ozone, ugm3(ozone), R.string.label_no2, ugm3(no2));
    }

    private String ugm3(double value) {
        return Double.isNaN(value) ? Fmt.DASH : getString(R.string.fmt_ugm3, Fmt.round(value));
    }

    private void renderDaily(final Forecast f, int todayIdx) {
        Forecast.Daily d = f.daily;
        binding.tvDailyTitle.setText(getString(R.string.section_daily, d.size() - todayIdx));

        double globalMin = Double.NaN;
        double globalMax = Double.NaN;
        for (int i = todayIdx; i < d.size(); i++) {
            if (!Double.isNaN(d.tempMin[i]) && !(d.tempMin[i] >= globalMin)) globalMin = d.tempMin[i];
            if (!Double.isNaN(d.tempMax[i]) && !(d.tempMax[i] <= globalMax)) globalMax = d.tempMax[i];
        }

        SimpleDateFormat dayFmt = new SimpleDateFormat("EEE d", Locale.getDefault());
        dayFmt.setTimeZone(f.timeZone());
        binding.listDaily.removeAllViews();
        for (int i = todayIdx; i < d.size(); i++) {
            ItemDailyBinding row = ItemDailyBinding.inflate(getLayoutInflater(), binding.listDaily, false);
            row.tvDay.setText(i == todayIdx ? getString(R.string.today)
                    : i == todayIdx + 1 ? getString(R.string.tomorrow)
                    : dayFmt.format(new Date(d.time[i])).replace(".", ""));
            row.ivIcon.setImageResource(WeatherCode.icon(d.code[i], true));
            row.ivIcon.setContentDescription(getString(WeatherCode.description(d.code[i])));
            row.tvRain.setText(Double.isNaN(d.precipProbMax[i]) ? "" : UnitFormat.percent(d.precipProbMax[i]));
            row.tvRain.setAlpha(d.precipProbMax[i] >= 20 ? 1f : 0.45f);
            row.tvMin.setText(uf.temp(d.tempMin[i]));
            row.tvMax.setText(uf.temp(d.tempMax[i]));
            row.rangeBar.setRange(d.tempMin[i], d.tempMax[i], globalMin, globalMax);
            final long dayTime = d.time[i];
            row.dailyRoot.setOnClickListener(v -> openDay(dayTime));
            binding.listDaily.addView(row.getRoot());
        }
    }

    /** Tocar un día (en la lista o en una ficha de la tira horaria) abre su detalle. */
    private void openDay(long dayTime) {
        startActivity(DayDetailActivity.intent(requireContext(), dayTime));
    }

    private void updateHourlyDayLabel() {
        if (binding == null || forecast == null) return;
        int first = hourlyLayout.findFirstVisibleItemPosition();
        if (first == RecyclerView.NO_POSITION) return;
        long time = hourlyAdapter.timeAt(first);
        long now = System.currentTimeMillis();
        String label;
        if (forecast.sameLocalDay(time, now)) {
            label = getString(R.string.today);
        } else if (forecast.sameLocalDay(time, now + DateUtils.DAY_IN_MILLIS)) {
            label = getString(R.string.tomorrow);
        } else {
            SimpleDateFormat fmt = new SimpleDateFormat("EEEE d", Locale.getDefault());
            fmt.setTimeZone(forecast.timeZone());
            label = fmt.format(new Date(time));
        }
        if (!label.contentEquals(binding.tvHourlyDay.getText())) {
            binding.tvHourlyDay.setText(label);
        }
    }

    private void renderSun(Forecast f, int dayIdx) {
        long sunrise = f.daily.sunrise[dayIdx];
        long sunset = f.daily.sunset[dayIdx];
        SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
        timeFmt.setTimeZone(f.timeZone());
        boolean valid = sunrise > 0 && sunset > sunrise;
        long minutes = valid ? (sunset - sunrise) / DateUtils.MINUTE_IN_MILLIS : 0;

        binding.gridSun.removeAllViews();
        addCellRow(binding.gridSun,
                R.string.label_sunrise, valid ? timeFmt.format(new Date(sunrise)) : Fmt.DASH,
                R.string.label_sunset, valid ? timeFmt.format(new Date(sunset)) : Fmt.DASH);
        addCellRow(binding.gridSun,
                R.string.label_day_length, valid
                        ? getString(R.string.fmt_day_length, (int) (minutes / 60), (int) (minutes % 60))
                        : Fmt.DASH,
                R.string.label_uv, Double.isNaN(f.daily.uvMax[dayIdx]) ? Fmt.DASH
                        : getString(R.string.fmt_uv, Fmt.round(f.daily.uvMax[dayIdx]),
                        getResources().getStringArray(R.array.uv_levels)[Fmt.uvLevel(f.daily.uvMax[dayIdx])]));
    }

    private static void addCellRow(LinearLayout grid, int label1, String value1, int label2, String value2) {
        CellGrid.addRow(grid, label1, value1, label2, value2);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding.rvHourly.setAdapter(null);
        binding = null;
        refreshing = false;
        normalsRequested = false;
    }
}
