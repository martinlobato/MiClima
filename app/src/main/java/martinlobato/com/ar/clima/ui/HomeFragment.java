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

import martinlobato.com.ar.clima.Prefs;
import martinlobato.com.ar.clima.R;
import martinlobato.com.ar.clima.data.Forecast;
import martinlobato.com.ar.clima.data.WeatherCode;
import martinlobato.com.ar.clima.data.WeatherRepository;
import martinlobato.com.ar.clima.databinding.FragmentHomeBinding;
import martinlobato.com.ar.clima.databinding.ItemDailyBinding;
import martinlobato.com.ar.clima.databinding.ItemDataCellBinding;
import martinlobato.com.ar.clima.databinding.ItemHourBinding;
import martinlobato.com.ar.clima.util.BackgroundExecutor;
import martinlobato.com.ar.clima.util.Fmt;
import martinlobato.com.ar.clima.util.LocationHelper;

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

        binding.swipeRefresh.setColorSchemeColors(
                MaterialColors.getColor(view, androidx.appcompat.R.attr.colorPrimary));
        binding.swipeRefresh.setOnRefreshListener(this::refresh);

        setupHourlyStrip();

        BackgroundExecutor.execute(() -> WeatherRepository.loadCached(appCtx), cached -> {
            if (binding == null) return;
            if (cached != null && forecast == null) {
                forecast = cached;
                render();
            }
            if (forecast == null || System.currentTimeMillis() - forecast.fetchedAt > STALE_MS) {
                refresh();
            }
        });
    }

    private void setupHourlyStrip() {
        hourlyLayout = new LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false);
        binding.rvHourly.setLayoutManager(hourlyLayout);
        binding.rvHourly.setAdapter(hourlyAdapter);
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

    private void fetch(final double lat, final double lon, @Nullable final String knownPlace) {
        BackgroundExecutor.execute(() -> {
            try {
                String place = knownPlace != null ? knownPlace : LocationHelper.placeName(appCtx, lat, lon);
                return WeatherRepository.fetch(appCtx, lat, lon, place);
            } catch (Exception e) {
                return null;
            }
        }, result -> {
            if (binding == null) return;
            setRefreshing(false);
            if (result != null) {
                forecast = result;
                render();
            } else if (forecast != null) {
                Toast.makeText(appCtx, R.string.error_refresh, Toast.LENGTH_LONG).show();
            } else {
                showEmpty(R.string.empty_error_title, R.string.empty_error_desc, false);
            }
        });
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
        binding.tvEmptyTitle.setText(titleRes);
        binding.tvEmptyDesc.setText(descRes);
        binding.btnEmptyAction.setText(askPermission
                ? R.string.empty_permission_action : R.string.action_retry);
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
        binding.tvStatus.setText(getString(R.string.status_updated, DateUtils.getRelativeTimeSpanString(
                f.fetchedAt, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE)
                .toString().toLowerCase(Locale.getDefault())));
        binding.ivCurrentIcon.setImageResource(WeatherCode.icon(code, isDay));
        binding.tvCurrentTemp.setText(Fmt.temp(temp));
        binding.tvCondition.setText(WeatherCode.description(code));
        binding.tvFeelsLike.setText(getString(R.string.feels_like, Fmt.temp(apparent)));
        binding.tvHiLo.setText(getString(R.string.hi_lo,
                Fmt.temp(d.tempMax[dayIdx]), Fmt.temp(d.tempMin[dayIdx])));

        binding.tvHourlyTitle.setText(getString(R.string.section_hourly, d.size()));
        hourlyAdapter.submit(f, hourIdx);
        hourlyLayout.scrollToPositionWithOffset(0, 0);
        binding.rvHourly.post(this::updateHourlyDayLabel);

        String[] compass = getResources().getStringArray(R.array.compass_points);
        String[] uvLevels = getResources().getStringArray(R.array.uv_levels);
        double uv = h.uv[hourIdx];
        double visibilityKm = h.visibility[hourIdx] / 1000.0;
        binding.gridDetails.removeAllViews();
        addCellRow(binding.gridDetails,
                R.string.label_feels_like, Fmt.temp(apparent),
                R.string.label_humidity, percent(humidity));
        addCellRow(binding.gridDetails,
                R.string.label_wind, Double.isNaN(wind) ? Fmt.DASH
                        : Double.isNaN(windDir) ? getString(R.string.fmt_kmh, Fmt.round(wind))
                        : getString(R.string.fmt_wind_dir, Fmt.round(wind), compass[Fmt.compassIndex(windDir)]),
                R.string.label_gusts, unit(R.string.fmt_kmh, Fmt.round(gusts), gusts));
        addCellRow(binding.gridDetails,
                R.string.label_uv, Double.isNaN(uv) ? Fmt.DASH
                        : getString(R.string.fmt_uv, Fmt.round(uv), uvLevels[Fmt.uvLevel(uv)]),
                R.string.label_pressure, unit(R.string.fmt_hpa, Fmt.round(pressure), pressure));
        addCellRow(binding.gridDetails,
                R.string.label_visibility, unit(R.string.fmt_km,
                        visibilityKm >= 10 ? Fmt.round(visibilityKm) : Fmt.oneDecimal(visibilityKm), visibilityKm),
                R.string.label_dew_point, Fmt.temp(h.dewPoint[hourIdx]));
        addCellRow(binding.gridDetails,
                R.string.label_cloud_cover, percent(cloud),
                R.string.label_precip_today, unit(R.string.fmt_mm,
                        Fmt.oneDecimal(d.precipSum[dayIdx]), d.precipSum[dayIdx]));

        renderDaily(f, dayIdx);
        renderSun(f, dayIdx);
    }

    private String percent(double value) {
        return unit(R.string.fmt_percent, Fmt.round(value), value);
    }

    private String unit(int formatRes, String formatted, double raw) {
        return Double.isNaN(raw) ? Fmt.DASH : getString(formatRes, formatted);
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
            row.tvRain.setText(Double.isNaN(d.precipProbMax[i]) ? ""
                    : getString(R.string.fmt_percent, Fmt.round(d.precipProbMax[i])));
            row.tvRain.setAlpha(d.precipProbMax[i] >= 20 ? 1f : 0.45f);
            row.tvMin.setText(Fmt.temp(d.tempMin[i]));
            row.tvMax.setText(Fmt.temp(d.tempMax[i]));
            row.rangeBar.setRange(d.tempMin[i], d.tempMax[i], globalMin, globalMax);
            final int dayIndex = i;
            row.dailyRoot.setOnClickListener(v -> showHoursOfDay(dayIndex, dayIndex == todayIdx));
            binding.listDaily.addView(row.getRoot());
        }
    }

    /** Tocar un día lleva la tira horaria al comienzo de ese día y la trae a la vista. */
    private void showHoursOfDay(int dayIndex, boolean isToday) {
        if (forecast == null) return;
        int position = 0;
        if (!isToday) {
            int hourIdx = forecast.firstHourIndexOfDay(dayIndex);
            if (hourIdx < 0) return;
            position = hourlyAdapter.positionOfHour(hourIdx);
        }
        hourlyLayout.scrollToPositionWithOffset(position, 0);
        binding.rvHourly.post(this::updateHourlyDayLabel);
        binding.scroll.smoothScrollTo(0, Math.max(0, binding.tvHourlyTitle.getTop()
                - binding.tvHourlyTitle.getHeight()));
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

    /** Agrega una fila de dos celdas "LABEL / valor" (mismo patrón visual que la grilla de MyLoc). */
    private void addCellRow(LinearLayout grid, int label1, String value1, int label2, String value2) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (grid.getChildCount() > 0) {
            rowParams.topMargin = dp(10);
        }
        row.setLayoutParams(rowParams);
        addCell(row, label1, value1, 0, dp(5));
        addCell(row, label2, value2, dp(5), 0);
        grid.addView(row);
    }

    private void addCell(LinearLayout row, int labelRes, String value, int marginStart, int marginEnd) {
        ItemDataCellBinding cell = ItemDataCellBinding.inflate(getLayoutInflater(), row, false);
        cell.tvLabel.setText(labelRes);
        cell.tvValue.setText(value);
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) cell.getRoot().getLayoutParams();
        lp.setMarginStart(marginStart);
        lp.setMarginEnd(marginEnd);
        row.addView(cell.getRoot());
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding.rvHourly.setAdapter(null);
        binding = null;
        refreshing = false;
    }
}
