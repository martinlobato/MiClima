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
import androidx.core.content.ContextCompat;
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
import martinlobato.com.ar.clima.data.Marine;
import martinlobato.com.ar.clima.data.Place;
import martinlobato.com.ar.clima.data.PlaceStore;
import martinlobato.com.ar.clima.data.WeatherCode;
import martinlobato.com.ar.clima.data.WeatherRepository;
import martinlobato.com.ar.clima.databinding.FragmentHomeBinding;
import martinlobato.com.ar.clima.databinding.ItemDailyBinding;
import martinlobato.com.ar.clima.databinding.ItemDataCellBinding;
import martinlobato.com.ar.clima.databinding.ItemHourBinding;
import martinlobato.com.ar.clima.util.BackgroundExecutor;
import martinlobato.com.ar.clima.util.Fmt;
import martinlobato.com.ar.clima.util.ActivityAdvisor;
import martinlobato.com.ar.clima.util.LocationHelper;
import martinlobato.com.ar.clima.util.Moon;
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

    /** Id de página de la ubicación actual del teléfono (los lugares guardados usan su id). */
    public static final long CURRENT_LOCATION = Long.MIN_VALUE;
    private static final String ARG_PLACE_ID = "place_id";

    /** Página de clima de un lugar guardado, o de la ubicación actual con {@link #CURRENT_LOCATION}. */
    public static HomeFragment newInstance(long placeId) {
        HomeFragment f = new HomeFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_PLACE_ID, placeId);
        f.setArguments(args);
        return f;
    }

    /** Lugar de esta página según sus argumentos; null = ubicación actual (o lugar ya quitado). */
    @Nullable
    private Place placeFromArguments() {
        long id = getArguments() == null ? CURRENT_LOCATION : getArguments().getLong(ARG_PLACE_ID, CURRENT_LOCATION);
        if (id == CURRENT_LOCATION) return null;
        for (Place p : PlaceStore.list(appCtx)) {
            if (p.id == id) return p;
        }
        return null;
    }

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
        place = placeFromArguments();
        cacheKey = place == null ? WeatherRepository.KEY_CURRENT_LOCATION : place.cacheKey();

        binding.swipeRefresh.setColorSchemeColors(
                MaterialColors.getColor(view, androidx.appcompat.R.attr.colorPrimary));
        binding.swipeRefresh.setOnRefreshListener(this::refresh);

        setupHourlyStrip();
        setupActivityChips();

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
                ((MainActivity) getActivity()).openPlaces(true);
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
        int rainColor = ContextCompat.getColor(requireContext(), R.color.wx_rain);
        int cloudColor = ContextCompat.getColor(requireContext(), R.color.wx_cloud);
        int primary = MaterialColors.getColor(binding.getRoot(), androidx.appcompat.R.attr.colorPrimary);
        // La presión sube o baja: se compara con 3 h antes (±1 hPa para no marcar ruido).
        int trend = 0;
        if (hourIdx >= 3 && !Double.isNaN(pressure) && !Double.isNaN(h.pressure[hourIdx - 3])) {
            double delta = pressure - h.pressure[hourIdx - 3];
            trend = delta >= 1 ? 1 : delta <= -1 ? -1 : 0;
        }
        String pressureText = uf.pressure(pressure)
                + (trend > 0 ? " ↑" : trend < 0 ? " ↓" : "");
        double rainNext = h.precipProb[Math.min(hourIdx + 1, h.size() - 1)];
        double visKm = h.visibility[hourIdx] / 1000.0;

        binding.gridDetails.removeAllViews();
        // La sensación ya está en la tarjeta principal: acá va la probabilidad de lluvia.
        ItemDataCellBinding[] r = addCellRow(binding.gridDetails,
                R.string.label_rain_next_hour, UnitFormat.percent(rainNext),
                R.string.label_humidity, UnitFormat.percent(humidity));
        r[0].gauge.set(GaugeView.Mode.RING, rainNext / 100, rainColor);
        r[1].gauge.set(GaugeView.Mode.RING, humidity / 100, primary);
        r = addCellRow(binding.gridDetails,
                R.string.label_wind, Double.isNaN(wind) || Double.isNaN(windDir) ? uf.wind(wind)
                        : uf.wind(wind) + " " + compass[Fmt.compassIndex(windDir)],
                R.string.label_gusts, uf.wind(gusts));
        r[0].gauge.setCompass(windDir);
        r[1].gauge.setScale(gusts / 100, GaugeView.WIND_COLORS, GaugeView.WIND_STOPS);
        r = addCellRow(binding.gridDetails,
                R.string.label_uv, Double.isNaN(uv) ? Fmt.DASH
                        : getString(R.string.fmt_uv, Fmt.round(uv), uvLevels[Fmt.uvLevel(uv)]),
                R.string.label_pressure, pressureText);
        r[0].gauge.setScale(uv / 11, GaugeView.UV_COLORS, GaugeView.UV_STOPS);
        r[1].gauge.set(GaugeView.Mode.NEEDLE, (pressure - 980) / 60, primary).setTrend(trend);
        r = addCellRow(binding.gridDetails,
                R.string.label_visibility, uf.distance(visKm),
                R.string.label_dew_point, uf.temp(h.dewPoint[hourIdx]));
        r[0].gauge.set(GaugeView.Mode.BAR, visKm / 20, primary);
        r[1].gauge.set(GaugeView.Mode.THERMO, TempColors.fraction(h.dewPoint[hourIdx]),
                TempColors.forCelsius(h.dewPoint[hourIdx]));
        r = addCellRow(binding.gridDetails,
                R.string.label_cloud_cover, UnitFormat.percent(cloud),
                R.string.label_precip_today, uf.precip(d.precipSum[dayIdx]));
        r[0].gauge.set(GaugeView.Mode.RING, cloud / 100, cloudColor);
        // El vaso se llena con 20 mm (lluvia fuerte para un día).
        r[1].gauge.set(GaugeView.Mode.GLASS, d.precipSum[dayIdx] / 20, rainColor);

        renderRainSoon(f, hourIdx);
        renderHighlights(f, now, hourIdx);
        renderNormal();
        loadNormals();
        renderBest(f, now);
        renderAir(f, now);
        renderSnow(f, hourIdx, dayIdx);
        renderSea(f, now);
        renderDaily(f, dayIdx);
        renderSun(f, dayIdx);
        renderMoon(f, now, dayIdx);
    }

    // ---------------------------------------------------------------- mejor momento

    private static final ActivityAdvisor.Activity[] ACTIVITIES = ActivityAdvisor.Activity.values();
    private static final int[] ACTIVITY_LABELS = {R.string.activity_run, R.string.activity_bike,
            R.string.activity_laundry, R.string.activity_bbq};

    private void setupActivityChips() {
        android.content.res.ColorStateList background =
                androidx.appcompat.content.res.AppCompatResources.getColorStateList(requireContext(), R.color.chip_bg);
        android.content.res.ColorStateList text =
                androidx.appcompat.content.res.AppCompatResources.getColorStateList(requireContext(), R.color.chip_text);
        android.content.res.ColorStateList stroke = android.content.res.ColorStateList.valueOf(
                MaterialColors.getColor(binding.getRoot(), com.google.android.material.R.attr.colorOutlineVariant));
        String current = Prefs.getBestActivity(appCtx);
        for (int i = 0; i < ACTIVITIES.length; i++) {
            final ActivityAdvisor.Activity activity = ACTIVITIES[i];
            com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(requireContext());
            chip.setId(View.generateViewId());
            chip.setText(ACTIVITY_LABELS[i]);
            chip.setCheckable(true);
            chip.setCheckedIconVisible(false);
            chip.setChipBackgroundColor(background);
            chip.setTextColor(text);
            chip.setChipStrokeColor(stroke);
            chip.setChipStrokeWidth(getResources().getDisplayMetrics().density);
            chip.setChecked(activity.name().equals(current));
            chip.setOnCheckedChangeListener((button, checked) -> {
                if (!checked) return;
                Prefs.setBestActivity(appCtx, activity.name());
                if (forecast != null) renderBest(forecast, System.currentTimeMillis());
            });
            binding.chipGroupActivity.addView(chip);
        }
    }

    private ActivityAdvisor.Activity selectedActivity() {
        String name = Prefs.getBestActivity(appCtx);
        for (ActivityAdvisor.Activity a : ACTIVITIES) {
            if (a.name().equals(name)) return a;
        }
        return ActivityAdvisor.Activity.RUN;
    }

    /** Condiciones de las horas [from, to) en el formato que usa ActivityAdvisor. */
    private static ActivityAdvisor.Hour[] advisorHours(Forecast f, int from, int to) {
        java.util.Calendar cal = java.util.Calendar.getInstance(f.timeZone());
        ActivityAdvisor.Hour[] out = new ActivityAdvisor.Hour[Math.max(0, to - from)];
        Forecast.Hourly h = f.hourly;
        for (int i = from; i < to; i++) {
            ActivityAdvisor.Hour a = new ActivityAdvisor.Hour();
            cal.setTimeInMillis(h.time[i]);
            a.hourOfDay = cal.get(java.util.Calendar.HOUR_OF_DAY);
            a.apparent = h.apparent[i];
            a.precipProb = h.precipProb[i];
            a.precip = h.precip[i];
            a.wind = h.wind[i];
            a.gusts = h.gusts[i];
            a.humidity = h.humidity[i];
            a.uv = h.uv[i];
            a.isDay = h.isDay[i];
            out[i - from] = a;
        }
        return out;
    }

    /** Mejor tramo de lo que queda de hoy o, si no hay ninguno, de mañana. */
    private void renderBest(Forecast f, long now) {
        if (binding == null) return;
        binding.sectionBest.setVisibility(View.VISIBLE);
        ActivityAdvisor.Activity activity = selectedActivity();
        int dayIdx = f.dayIndexAt(now);
        SimpleDateFormat hourFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
        hourFmt.setTimeZone(f.timeZone());
        for (int offset = 0; offset < 2 && dayIdx + offset < f.daily.size(); offset++) {
            int first = offset == 0 ? f.hourIndexAt(now) : f.firstHourIndexOfDay(dayIdx + offset);
            int end = dayIdx + offset + 1 < f.daily.size() ? f.firstHourIndexOfDay(dayIdx + offset + 1) : f.hourly.size();
            if (first < 0 || end <= first) continue;
            ActivityAdvisor.Window w = ActivityAdvisor.best(activity, advisorHours(f, first, end));
            if (w == null) continue;
            int from = first + w.from;
            int to = first + w.to;
            String start = hourFmt.format(new Date(f.hourly.time[from]));
            String stop = hourFmt.format(new Date(f.hourly.time[to - 1] + DateUtils.HOUR_IN_MILLIS));
            binding.tvBestTitle.setText(getString(offset == 0 ? R.string.best_today : R.string.best_tomorrow, start, stop));
            double apparentMin = Double.NaN;
            double apparentMax = Double.NaN;
            double rainMax = 0;
            for (int i = from; i < to; i++) {
                double a = f.hourly.apparent[i];
                if (!Double.isNaN(a)) {
                    if (!(a >= apparentMin)) apparentMin = a;
                    if (!(a <= apparentMax)) apparentMax = a;
                }
                if (f.hourly.precipProb[i] > rainMax) rainMax = f.hourly.precipProb[i];
            }
            String feels = Double.isNaN(apparentMin) ? Fmt.DASH
                    : uf.temp(apparentMin).equals(uf.temp(apparentMax)) ? uf.temp(apparentMin)
                    : uf.temp(apparentMin) + "–" + uf.temp(apparentMax);
            String detail = getString(w.score >= ActivityAdvisor.GREAT ? R.string.best_great
                    : R.string.best_good, feels, UnitFormat.percent(rainMax));
            // En tramos largos se dice además cuál es la mejor hora.
            if (to - from > 3) {
                detail = getString(R.string.best_peak, detail,
                        hourFmt.format(new Date(f.hourly.time[first + w.peak])));
            }
            binding.tvBestDetail.setText(detail);
            renderBestStrip(f, activity, first, end, from - first, to - first);
            return;
        }
        binding.tvBestTitle.setText(R.string.best_none);
        binding.tvBestDetail.setText(R.string.best_none_detail);
        int first = f.hourIndexAt(now);
        renderBestStrip(f, activity, first, Math.min(f.hourly.size(), first + 24), -1, -1);
    }

    /** Franja de puntajes por hora de [first, end), con el mejor tramo [from, to) enmarcado. */
    private void renderBestStrip(Forecast f, ActivityAdvisor.Activity activity, int first, int end,
                                 int from, int to) {
        // Al menos 12 casillas: si queda poco del día, se completa con las horas siguientes.
        end = Math.min(f.hourly.size(), Math.max(end, first + 12));
        if (end <= first) {
            binding.bestStrip.setVisibility(View.GONE);
            binding.tvBestStripNote.setVisibility(View.GONE);
            return;
        }
        ActivityAdvisor.Hour[] hours = advisorHours(f, first, end);
        int[] scores = new int[hours.length];
        String[] labels = new String[hours.length];
        for (int k = 0; k < hours.length; k++) {
            scores[k] = ActivityAdvisor.score(activity, hours[k]);
            if (hours[k].hourOfDay % 6 == 0) labels[k] = String.format(Locale.getDefault(), "%02d", hours[k].hourOfDay);
        }
        binding.bestStrip.setData(scores, labels, from, to);
        binding.bestStrip.setVisibility(View.VISIBLE);
        binding.tvBestStripNote.setVisibility(View.VISIBLE);
    }

    // ---------------------------------------------------------------- nieve y cordillera

    private void renderSnow(Forecast f, int hourIdx, int dayIdx) {
        Forecast.Hourly h = f.hourly;
        if (h.freezingLevel == null || allNaN(h.freezingLevel)) {
            binding.sectionSnow.setVisibility(View.GONE);
            return;
        }
        binding.sectionSnow.setVisibility(View.VISIBLE);
        double week = Double.NaN;
        String next = getString(R.string.snow_none);
        SimpleDateFormat dayFmt = new SimpleDateFormat("EEE d", Locale.getDefault());
        dayFmt.setTimeZone(f.timeZone());
        boolean nextFound = false;
        for (int d = dayIdx; d < f.daily.size(); d++) {
            double v = f.daily.snowfallSum[d];
            if (Double.isNaN(v)) continue;
            if (d < dayIdx + 7) week = (Double.isNaN(week) ? 0 : week) + v;
            if (!nextFound && v >= 0.1) {
                nextFound = true;
                String day = d == dayIdx ? getString(R.string.today)
                        : d == dayIdx + 1 ? getString(R.string.tomorrow)
                        : dayFmt.format(new Date(f.daily.time[d])).replace(".", "");
                next = getString(R.string.fmt_info, day, uf.snow(v));
            }
        }
        double depthCm = h.snowDepth[hourIdx] * 100;
        double freezing = h.freezingLevel[hourIdx];
        binding.snowLine.setFreezingLevel(freezing, Double.isNaN(freezing) ? ""
                : getString(R.string.fmt_freezing_label, uf.altitude(freezing)));
        binding.gridSnow.removeAllViews();
        addCellRow(binding.gridSnow,
                R.string.label_freezing_level, uf.altitude(h.freezingLevel[hourIdx]),
                R.string.label_snow_depth, uf.snow(depthCm));
        addCellRow(binding.gridSnow,
                R.string.label_snow_week, uf.snow(week),
                R.string.label_snow_next, next);
        binding.tvSnowNote.setText(R.string.snow_note);
    }

    private static boolean allNaN(double[] values) {
        for (double v : values) {
            if (!Double.isNaN(v)) return false;
        }
        return true;
    }

    // ---------------------------------------------------------------- mar

    private void renderSea(Forecast f, long now) {
        Marine m = f.marine;
        if (m == null) {
            binding.sectionSea.setVisibility(View.GONE);
            return;
        }
        double height = m.currentWaveHeight;
        double period = m.currentWavePeriod;
        double direction = m.currentWaveDirection;
        double water = m.currentSeaTemp;
        if (m.currentTime <= 0 || Math.abs(now - m.currentTime) > 2 * DateUtils.HOUR_IN_MILLIS) {
            // Guardado de otra hora: se toma la hora en curso de la serie.
            int i = -1;
            for (int k = 0; k < m.size() && m.time[k] <= now; k++) i = k;
            if (i < 0) {
                binding.sectionSea.setVisibility(View.GONE);
                return;
            }
            height = m.waveHeight[i];
            period = m.wavePeriod[i];
            direction = Double.NaN;
            water = m.seaTemp[i];
        }
        double maxToday = Double.NaN;
        for (int k = 0; k < m.size(); k++) {
            if (f.sameLocalDay(m.time[k], now) && !Double.isNaN(m.waveHeight[k]) && !(m.waveHeight[k] <= maxToday)) {
                maxToday = m.waveHeight[k];
            }
        }
        String[] compass = getResources().getStringArray(R.array.compass_points);
        String waves = Double.isNaN(direction) ? uf.waveHeight(height)
                : uf.waveHeight(height) + " " + compass[Fmt.compassIndex(direction)];
        binding.sectionSea.setVisibility(View.VISIBLE);
        binding.waveView.setWaveHeight(height);
        binding.gridSea.removeAllViews();
        addCellRow(binding.gridSea,
                R.string.label_waves, waves,
                R.string.label_wave_period, Double.isNaN(period) ? Fmt.DASH
                        : getString(R.string.fmt_seconds, Fmt.round(period)));
        addCellRow(binding.gridSea,
                R.string.label_sea_temp, uf.temp(water),
                R.string.label_waves_max, uf.waveHeight(maxToday));
    }

    // ---------------------------------------------------------------- luna

    private void renderMoon(Forecast f, long now, int dayIdx) {
        Moon.Phase phase = Moon.phase(now);
        Moon.Times times = Moon.times(f.daily.time[dayIdx], f.latitude, f.longitude);
        SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
        timeFmt.setTimeZone(f.timeZone());
        binding.moonPhase.setPhase(phase.cycle, f.latitude);
        String name = getResources().getStringArray(R.array.moon_phases)[phase.index()];
        binding.tvMoonPhase.setText(name);
        binding.tvMoonIllumination.setText(getString(R.string.fmt_moon_illuminated,
                UnitFormat.percent(phase.illumination * 100)));
        binding.moonPhase.setContentDescription(name);

        // Primero el evento que ocurre antes en el día (como en el reloj).
        String rise = times.rise > 0 ? timeFmt.format(new Date(times.rise)) : getString(R.string.moon_no_event);
        String set = times.set > 0 ? timeFmt.format(new Date(times.set)) : getString(R.string.moon_no_event);
        boolean setFirst = times.set > 0 && (times.rise <= 0 || times.set < times.rise);
        binding.tvMoonFirstLabel.setText(setFirst ? R.string.label_moonset_long : R.string.label_moonrise_long);
        binding.tvMoonFirst.setText(setFirst ? set : rise);
        binding.tvMoonSecondLabel.setText(setFirst ? R.string.label_moonrise_long : R.string.label_moonset_long);
        binding.tvMoonSecond.setText(setFirst ? rise : set);
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
                double[] coords = place != null ? new double[]{place.latitude, place.longitude}
                        : Prefs.hasLastPlace(appCtx)
                        ? new double[]{Prefs.getLastLat(appCtx), Prefs.getLastLon(appCtx)} : null;
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
        // Anillo contra la guía de la OMS (24 h / 8 h): lleno = el doble de la guía.
        ItemDataCellBinding[] r = addCellRow(binding.gridAir,
                R.string.label_pm25, ugm3(pm25), R.string.label_pm10, ugm3(pm10));
        pollutantGauge(r[0], pm25, 15);
        pollutantGauge(r[1], pm10, 45);
        r = addCellRow(binding.gridAir, R.string.label_ozone, ugm3(ozone), R.string.label_no2, ugm3(no2));
        pollutantGauge(r[0], ozone, 100);
        pollutantGauge(r[1], no2, 25);
    }

    /** Verde bajo la guía, amarillo hasta el doble, rojo por encima. */
    private static void pollutantGauge(ItemDataCellBinding cell, double value, double guideline) {
        int color = value <= guideline ? 0xFF3BA55C : value <= 2 * guideline ? 0xFFF2A93B : 0xFFE0453A;
        cell.gauge.set(GaugeView.Mode.RING, value / (2 * guideline), color);
    }

    private String ugm3(double value) {
        return Double.isNaN(value) ? Fmt.DASH : getString(R.string.fmt_ugm3, Fmt.round(value));
    }

    /**
     * Porcentaje de lluvia de la lista de días: bajo 20 % no se muestra, desde 60 % va resaltado.
     */
    private void styleRain(android.widget.TextView tv, double prob) {
        if (Double.isNaN(prob) || prob < 20) {
            tv.setText("");
            return;
        }
        tv.setText(getString(R.string.fmt_rain_drop, UnitFormat.percent(prob)));
        tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.wx_rain));
        tv.setAlpha(prob >= 60 ? 1f : 0.8f);
        tv.setTypeface(null, prob >= 60 ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
    }

    // ---------------------------------------------------------------- lluvia próxima

    private static final int RAIN_HOURS = 24;

    private void renderRainSoon(Forecast f, int hourIdx) {
        Forecast.Hourly h = f.hourly;
        int n = Math.min(RAIN_HOURS, h.size() - hourIdx);
        if (n <= 0) {
            binding.cardRainSoon.setVisibility(View.GONE);
            return;
        }
        double[] prob = new double[n];
        double[] mm = new double[n];
        String[] labels = new String[n];
        SimpleDateFormat hourFmt = new SimpleDateFormat("HH", Locale.getDefault());
        hourFmt.setTimeZone(f.timeZone());
        int first = -1;
        double total = 0;
        for (int k = 0; k < n; k++) {
            int i = hourIdx + k;
            prob[k] = h.precipProb[i];
            mm[k] = h.precip[i];
            if (k % 3 == 0) labels[k] = k == 0 ? getString(R.string.now) : hourFmt.format(new Date(h.time[i]));
            if (first < 0 && (h.precipProb[i] >= 50 || h.precip[i] >= 0.5)) first = k;
            if (!Double.isNaN(h.precip[i])) total += h.precip[i];
        }
        binding.rainBars.setData(prob, mm, labels);
        String summary;
        if (first < 0) {
            summary = getString(R.string.rain_soon_none, n);
        } else if (first == 0) {
            summary = getString(R.string.rain_soon_now, uf.precip(total));
        } else {
            SimpleDateFormat fmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
            fmt.setTimeZone(f.timeZone());
            summary = getString(R.string.rain_soon_from, fmt.format(new Date(h.time[hourIdx + first])), uf.precip(total));
        }
        binding.tvRainSoon.setText(summary);
        binding.cardRainSoon.setVisibility(View.VISIBLE);
    }

    /**
     * Aviso arriba de todo con lo importante del día: lluvia en las próximas 6 h (o lloviendo
     * ahora) y las alertas de las próximas 24 h con los umbrales de Ajustes. Se muestra aunque
     * las notificaciones estén apagadas; oculto si no hay nada.
     */
    private void renderHighlights(Forecast f, long now, int hourIdx) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        SimpleDateFormat hourFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
        hourFmt.setTimeZone(f.timeZone());
        SimpleDateFormat whenFmt = new SimpleDateFormat("EEE HH:mm", Locale.getDefault());
        whenFmt.setTimeZone(f.timeZone());
        Forecast.Hourly h = f.hourly;
        if (h.precipProb[hourIdx] >= 60 || h.precip[hourIdx] >= 0.5) {
            lines.add(getString(R.string.highlight_rain_now));
        } else {
            martinlobato.com.ar.clima.data.RainAlert.Event rain =
                    martinlobato.com.ar.clima.data.RainAlert.find(f, now);
            if (rain != null) {
                lines.add(getString(R.string.highlight_rain_at, hourFmt.format(new Date(rain.time)),
                        Double.isNaN(rain.probability) ? uf.precip(rain.precipitation)
                                : UnitFormat.percent(rain.probability)));
            }
        }
        martinlobato.com.ar.clima.data.WeatherAlerts.Type[] types =
                martinlobato.com.ar.clima.data.WeatherAlerts.Type.values();
        boolean[] enabled = new boolean[types.length];
        double[] thresholds = new double[types.length];
        for (martinlobato.com.ar.clima.data.WeatherAlerts.Type t : types) {
            enabled[t.ordinal()] = true;
            thresholds[t.ordinal()] = Prefs.getAlertThreshold(appCtx, t);
        }
        for (martinlobato.com.ar.clima.data.WeatherAlerts.Alert a
                : martinlobato.com.ar.clima.data.WeatherAlerts.evaluate(f, now, enabled, thresholds)) {
            String when = a.time <= now + DateUtils.HOUR_IN_MILLIS ? getString(R.string.highlight_now)
                    : f.sameLocalDay(a.time, now) ? hourFmt.format(new Date(a.time))
                    : whenFmt.format(new Date(a.time)).replace(".", "");
            lines.add(getString(R.string.highlight_alert, getString(ALERT_TITLES[a.type.ordinal()]),
                    martinlobato.com.ar.clima.work.AlertNotifier.formatValue(appCtx, a.type, a.extreme), when));
        }
        binding.listHighlights.removeAllViews();
        if (lines.isEmpty()) {
            binding.cardHighlights.setVisibility(View.GONE);
            return;
        }
        int color = ContextCompat.getColor(requireContext(), R.color.highlight_text);
        float density = getResources().getDisplayMetrics().density;
        for (int i = 0; i < lines.size(); i++) {
            android.widget.TextView tv = new android.widget.TextView(requireContext());
            tv.setText(lines.get(i));
            tv.setTextColor(color);
            tv.setTextSize(14.5f);
            tv.setTypeface(null, android.graphics.Typeface.BOLD);
            if (i > 0) tv.setPadding(0, Math.round(6 * density), 0, 0);
            binding.listHighlights.addView(tv);
        }
        binding.cardHighlights.setVisibility(View.VISIBLE);
    }

    private static final int[] ALERT_TITLES = {R.string.alert_frost_title, R.string.alert_heat_title,
            R.string.alert_wind_title, R.string.alert_uv_title, R.string.alert_air_title};

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
            styleRain(row.tvRain, d.precipProbMax[i]);
            row.tvMin.setText(uf.temp(d.tempMin[i]));
            row.tvMax.setText(uf.temp(d.tempMax[i]));
            row.rangeBar.setRange(d.tempMin[i], d.tempMax[i], globalMin, globalMax);
            row.rangeBar.setColors(TempColors.forCelsius(d.tempMin[i]), TempColors.forCelsius(d.tempMax[i]));
            // En "Hoy" un punto marca la temperatura de ahora entre la mínima y la máxima.
            if (i == todayIdx) {
                row.rangeBar.setMarker(f.hourly.temp[f.hourIndexAt(System.currentTimeMillis())], globalMin, globalMax);
            }
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

        binding.sunArc.setDay(f.daily.time[dayIdx], sunrise, sunset, System.currentTimeMillis());
        binding.tvSunrise.setText(valid ? timeFmt.format(new Date(sunrise)) : Fmt.DASH);
        binding.tvSunset.setText(valid ? timeFmt.format(new Date(sunset)) : Fmt.DASH);

        binding.gridSun.removeAllViews();
        addCellRow(binding.gridSun,
                R.string.label_day_length, valid
                        ? getString(R.string.fmt_day_length, (int) (minutes / 60), (int) (minutes % 60))
                        : Fmt.DASH,
                R.string.label_uv, Double.isNaN(f.daily.uvMax[dayIdx]) ? Fmt.DASH
                        : getString(R.string.fmt_uv, Fmt.round(f.daily.uvMax[dayIdx]),
                        getResources().getStringArray(R.array.uv_levels)[Fmt.uvLevel(f.daily.uvMax[dayIdx])]));
    }

    private static ItemDataCellBinding[] addCellRow(LinearLayout grid, int label1, String value1, int label2, String value2) {
        return CellGrid.addRow(grid, label1, value1, label2, value2);
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
