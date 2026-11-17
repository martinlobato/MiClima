package martinlobato.com.ar.clima.ui;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.chip.Chip;
import com.google.android.material.color.MaterialColors;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import martinlobato.com.ar.clima.Prefs;
import martinlobato.com.ar.clima.R;
import martinlobato.com.ar.clima.data.ClimateNormals;
import martinlobato.com.ar.clima.data.Forecast;
import martinlobato.com.ar.clima.data.PlaceStore;
import martinlobato.com.ar.clima.data.WeatherCode;
import martinlobato.com.ar.clima.data.WeatherRepository;
import martinlobato.com.ar.clima.databinding.ActivityDayDetailBinding;
import martinlobato.com.ar.clima.util.BackgroundExecutor;
import martinlobato.com.ar.clima.util.ChartScale;
import martinlobato.com.ar.clima.util.Moon;
import martinlobato.com.ar.clima.util.Fmt;
import martinlobato.com.ar.clima.util.ThemeUtils;
import martinlobato.com.ar.clima.util.UnitFormat;

/**
 * Detalle de un día: resumen, gráfico horario de la variable elegida (temperatura, lluvia,
 * viento, UV, humedad o presión) y tabla hora por hora con todas las variables.
 * Trabaja sobre el pronóstico en caché; no hace llamadas de red.
 */
public class DayDetailActivity extends AppCompatActivity {

    private static final String EXTRA_DAY_TIME = "day_time";
    private static final String STATE_DAY_TIME = "day_time";
    private static final String STATE_VARIABLE = "variable";

    /** Variable graficada; el orden es el de los chips. */
    private enum Variable {
        TEMP(R.string.var_temp),
        RAIN(R.string.var_rain),
        WIND(R.string.var_wind),
        UV(R.string.var_uv),
        HUMIDITY(R.string.var_humidity),
        PRESSURE(R.string.var_pressure),
        AIR(R.string.var_air),
        SNOW(R.string.var_snow);

        final int labelRes;

        Variable(int labelRes) {
            this.labelRes = labelRes;
        }
    }

    private ActivityDayDetailBinding binding;
    private Forecast forecast;
    private long dayTime;
    private int dayIndex;
    private int todayIndex;
    /** Índices (en la serie horaria) de las horas del día mostrado. */
    private int[] hours = new int[0];
    private Variable variable = Variable.TEMP;
    private SimpleDateFormat hourFmt;
    private UnitFormat uf = UnitFormat.metric();
    /** Normales climáticos del lugar, si ya se calcularon; null si no. */
    private volatile ClimateNormals normals;
    private String[] compass;
    private String[] uvLevels;

    /** @param dayTime instante (epoch millis) cualquiera dentro del día a mostrar */
    public static Intent intent(Context context, long dayTime) {
        return new Intent(context, DayDetailActivity.class).putExtra(EXTRA_DAY_TIME, dayTime);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        setTheme(ThemeUtils.resolveThemeRes(this));
        super.onCreate(savedInstanceState);
        binding = ActivityDayDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        // Con edge-to-edge el contenido llega hasta abajo: se deja libre la barra de navegación.
        final int basePadding = binding.scroll.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(binding.scroll, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), basePadding + bars.bottom);
            return insets;
        });

        dayTime = getIntent().getLongExtra(EXTRA_DAY_TIME, System.currentTimeMillis());
        if (savedInstanceState != null) {
            dayTime = savedInstanceState.getLong(STATE_DAY_TIME, dayTime);
            int ordinal = savedInstanceState.getInt(STATE_VARIABLE, 0);
            variable = Variable.values()[Math.max(0, Math.min(Variable.values().length - 1, ordinal))];
        }
        uf = Prefs.units(this);
        compass = getResources().getStringArray(R.array.compass_points);
        uvLevels = getResources().getStringArray(R.array.uv_levels);

        setupChips();
        binding.chart.setOnSelectionListener(this::updateChartInfo);
        binding.btnCompareModels.setOnClickListener(v ->
                startActivity(ModelCompareActivity.intent(this, dayTime)));
        binding.btnPrevDay.setOnClickListener(v -> showDay(dayIndex - 1));
        binding.btnNextDay.setOnClickListener(v -> showDay(dayIndex + 1));

        final Context app = getApplicationContext();
        BackgroundExecutor.execute(() -> {
            // Los normales solo se leen del guardado: esta pantalla no usa la red.
            double[] coords = WeatherRepository.selectedCoordinates(app);
            String key = PlaceStore.selectedCacheKey(app);
            normals = coords == null ? null : WeatherRepository.cachedNormals(app, key, coords[0], coords[1]);
            return WeatherRepository.loadCached(app, key);
        }, cached -> {
            if (isFinishing() || isDestroyed()) return;
            if (cached == null || cached.daily.size() == 0 || cached.hourly.size() == 0) {
                finish();
                return;
            }
            forecast = cached;
            hourFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
            hourFmt.setTimeZone(forecast.timeZone());
            todayIndex = forecast.dayIndexAt(System.currentTimeMillis());
            binding.toolbar.setTitle(forecast.place != null ? forecast.place : getString(R.string.place_unknown));
            binding.content.setVisibility(View.VISIBLE);
            showDay(forecast.dayIndexAt(dayTime));
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(STATE_DAY_TIME, dayTime);
        outState.putInt(STATE_VARIABLE, variable.ordinal());
    }

    private void setupChips() {
        ColorStateList background = AppCompatResources.getColorStateList(this, R.color.chip_bg);
        ColorStateList text = AppCompatResources.getColorStateList(this, R.color.chip_text);
        ColorStateList stroke = ColorStateList.valueOf(MaterialColors.getColor(binding.getRoot(),
                com.google.android.material.R.attr.colorOutlineVariant));
        for (final Variable v : Variable.values()) {
            Chip chip = new Chip(this);
            chip.setId(View.generateViewId());
            chip.setText(v.labelRes);
            chip.setCheckable(true);
            chip.setCheckedIconVisible(false);
            chip.setChipBackgroundColor(background);
            chip.setTextColor(text);
            chip.setChipStrokeColor(stroke);
            chip.setChipStrokeWidth(getResources().getDisplayMetrics().density);
            chip.setEnsureMinTouchTargetSize(true);
            chip.setChecked(v == variable);
            chip.setOnCheckedChangeListener((button, checked) -> {
                if (checked && variable != v) {
                    variable = v;
                    renderChart();
                }
            });
            binding.chipGroupVariable.addView(chip);
        }
    }

    // ------------------------------------------------------------------ día

    private void showDay(int index) {
        if (forecast == null) return;
        Forecast.Daily d = forecast.daily;
        dayIndex = Math.max(0, Math.min(d.size() - 1, index));
        dayTime = d.time[dayIndex];

        // Horas del día: desde su medianoche hasta la del día siguiente (23–25 según cambio de hora).
        long start = d.time[dayIndex];
        long end = dayIndex + 1 < d.size() ? d.time[dayIndex + 1] : start + 25 * DateUtils.HOUR_IN_MILLIS;
        int first = -1;
        int count = 0;
        for (int i = 0; i < forecast.hourly.size(); i++) {
            long t = forecast.hourly.time[i];
            if (t >= start && t < end && forecast.sameLocalDay(t, start)) {
                if (first < 0) first = i;
                count++;
            }
        }
        hours = new int[count];
        for (int i = 0; i < count; i++) {
            hours[i] = first + i;
        }

        renderHeader();
        renderChart();
        renderTable();
    }

    private void renderHeader() {
        Forecast.Daily d = forecast.daily;
        Date date = new Date(d.time[dayIndex]);
        SimpleDateFormat weekday = new SimpleDateFormat("EEEE", Locale.getDefault());
        weekday.setTimeZone(forecast.timeZone());
        DateFormat longDate = DateFormat.getDateInstance(DateFormat.LONG, Locale.getDefault());
        longDate.setTimeZone(forecast.timeZone());

        String title;
        if (dayIndex == todayIndex) {
            title = getString(R.string.today);
        } else if (dayIndex == todayIndex + 1) {
            title = getString(R.string.tomorrow);
        } else {
            String name = weekday.format(date);
            title = name.isEmpty() ? name : name.substring(0, 1).toUpperCase(Locale.getDefault()) + name.substring(1);
        }
        binding.tvDayTitle.setText(title);
        binding.tvDayDate.setText(longDate.format(date));

        // No se navega hacia días ya pasados que puedan quedar en una caché vieja.
        setNavEnabled(binding.btnPrevDay, dayIndex > todayIndex);
        setNavEnabled(binding.btnNextDay, dayIndex < d.size() - 1);

        binding.ivDayIcon.setImageResource(WeatherCode.icon(d.code[dayIndex], true));
        binding.tvDayCondition.setText(WeatherCode.description(d.code[dayIndex]));
        binding.tvDayHiLo.setText(getString(R.string.info_hi_lo,
                uf.temp(d.tempMax[dayIndex]), uf.temp(d.tempMin[dayIndex])));

        long sunrise = d.sunrise[dayIndex];
        long sunset = d.sunset[dayIndex];
        double prob = d.precipProbMax[dayIndex];
        double mm = d.precipSum[dayIndex];
        String rain = Double.isNaN(prob) && Double.isNaN(mm) ? Fmt.DASH
                : Double.isNaN(mm) ? percent(prob)
                : Double.isNaN(prob) ? uf.precip(mm)
                : getString(R.string.fmt_info, percent(prob), uf.precip(mm));

        binding.gridSummary.removeAllViews();
        CellGrid.addRow(binding.gridSummary,
                R.string.label_rain, rain,
                R.string.label_uv_max, uvText(d.uvMax[dayIndex]));
        CellGrid.addRow(binding.gridSummary,
                R.string.label_wind_max, windText(d.windMax[dayIndex], d.windDirDominant[dayIndex]),
                R.string.label_gusts, kmh(d.gustsMax[dayIndex]));
        ClimateNormals n = normals;
        if (n != null) {
            java.util.Calendar cal = java.util.Calendar.getInstance(forecast.timeZone());
            cal.setTimeInMillis(d.time[dayIndex]);
            int month = cal.get(java.util.Calendar.MONTH) + 1;
            int day = cal.get(java.util.Calendar.DAY_OF_MONTH);
            CellGrid.addRow(binding.gridSummary,
                    R.string.label_normal_max, normalCell(n.maxFor(month, day), d.tempMax[dayIndex]),
                    R.string.label_normal_min, normalCell(n.minFor(month, day), d.tempMin[dayIndex]));
        }

        // Sol y luna del día: los mismos gráficos que en Inicio.
        binding.daySunArc.setDay(d.time[dayIndex], sunrise, sunset, System.currentTimeMillis());
        binding.tvDaySunrise.setText(getString(R.string.fmt_sunrise_short,
                sunrise > 0 ? hourFmt.format(new Date(sunrise)) : Fmt.DASH));
        binding.tvDaySunset.setText(getString(R.string.fmt_sunset_short,
                sunset > 0 ? hourFmt.format(new Date(sunset)) : Fmt.DASH));
        Moon.Phase moonPhase = Moon.phase(d.time[dayIndex] + 12 * DateUtils.HOUR_IN_MILLIS);
        Moon.Times moonTimes = Moon.times(d.time[dayIndex], forecast.latitude, forecast.longitude);
        binding.dayMoon.setPhase(moonPhase.cycle, forecast.latitude);
        binding.tvDayMoonPhase.setText(getString(R.string.fmt_info,
                getResources().getStringArray(R.array.moon_phases)[moonPhase.index()],
                getString(R.string.fmt_moon_illuminated, UnitFormat.percent(moonPhase.illumination * 100))));
        binding.tvDayMoonTimes.setText(getString(R.string.fmt_moon_times,
                moonTimes.rise > 0 ? hourFmt.format(new Date(moonTimes.rise)) : Fmt.DASH,
                moonTimes.set > 0 ? hourFmt.format(new Date(moonTimes.set)) : Fmt.DASH));
    }

    /** "24° (+3°)": el valor normal y cuánto se aparta el pronóstico de ese día. */
    private String normalCell(double normal, double forecastValue) {
        if (Double.isNaN(normal)) return Fmt.DASH;
        if (Double.isNaN(forecastValue)) return uf.temp(normal);
        double diff = forecastValue - normal;
        return getString(R.string.fmt_normal_cell, uf.temp(normal),
                (diff >= 0 ? "+" : "−") + uf.tempDiff(diff));
    }

    private static void setNavEnabled(View button, boolean enabled) {
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : 0.3f);
    }

    // ------------------------------------------------------------------ gráfico

    private double[] series(double[] source) {
        double[] out = new double[hours.length];
        for (int i = 0; i < hours.length; i++) {
            out[i] = source[hours[i]];
        }
        return out;
    }

    /** Pasa una serie métrica a la unidad elegida (para dibujarla y escalar el eje). */
    private static double[] convert(double[] metric, java.util.function.DoubleUnaryOperator fn) {
        double[] out = new double[metric.length];
        for (int i = 0; i < metric.length; i++) {
            out[i] = fn.applyAsDouble(metric[i]);
        }
        return out;
    }

    /** AQI de cada hora del día (NaN donde el pronóstico de aire, de ~5 días, no llega). */
    private double[] airSeries() {
        double[] out = new double[hours.length];
        for (int i = 0; i < hours.length; i++) {
            out[i] = forecast.air == null ? Double.NaN : forecast.air.aqiAt(forecast.hourly.time[hours[i]]);
        }
        return out;
    }

    private String aqiLevel(double aqi) {
        return getResources().getStringArray(R.array.aqi_levels)[Fmt.aqiLevel(aqi)];
    }

    private void renderChart() {
        if (forecast == null) return;
        Forecast.Hourly h = forecast.hourly;
        String[] labels = new String[hours.length];
        SimpleDateFormat shortHour = new SimpleDateFormat("HH", Locale.getDefault());
        shortHour.setTimeZone(forecast.timeZone());
        for (int i = 0; i < hours.length; i++) {
            labels[i] = shortHour.format(new Date(h.time[hours[i]]));
        }
        int nowIndex = -1;
        if (dayIndex == todayIndex && hours.length > 0) {
            int now = forecast.hourIndexAt(System.currentTimeMillis()) - hours[0];
            if (now >= 0 && now < hours.length) nowIndex = now;
        }

        double[] values;
        double[] secondary = null;
        boolean bars = false;
        ChartScale scale;
        HourlyChartView.AxisFormatter axis;
        int legendRes = R.string.legend_tap;
        switch (variable) {
            case RAIN:
                values = series(h.precipProb);
                bars = true;
                scale = ChartScale.fixed(0, 100, 25);
                axis = UnitFormat::percent;
                legendRes = R.string.legend_rain;
                break;
            case WIND:
                values = convert(series(h.wind), uf::windValue);
                secondary = convert(series(h.gusts), uf::windValue);
                scale = ChartScale.of(0, hi(uf.windValue(10), hi(max(values), max(secondary))), 4);
                axis = uf::windAxis;
                legendRes = R.string.legend_gusts;
                break;
            case UV:
                values = series(h.uv);
                bars = true;
                scale = ChartScale.of(0, hi(11, max(values)), 4);
                axis = Fmt::round;
                break;
            case HUMIDITY:
                values = series(h.humidity);
                scale = ChartScale.fixed(0, 100, 25);
                axis = UnitFormat::percent;
                break;
            case SNOW:
                values = convert(series(h.snowfall), uf::snowValue);
                bars = true;
                scale = ChartScale.of(0, hi(uf.snowValue(1), max(values)), 4);
                axis = Fmt::oneDecimal;
                legendRes = R.string.legend_snow;
                break;
            case AIR:
                values = airSeries();
                bars = true;
                scale = ChartScale.of(0, hi(100, max(values)), 4);
                axis = Fmt::round;
                legendRes = R.string.legend_air;
                break;
            case PRESSURE:
                values = convert(series(h.pressure), uf::pressureValue);
                scale = ChartScale.of(min(values), max(values), 4);
                axis = uf::pressureAxis;
                break;
            case TEMP:
            default:
                values = convert(series(h.temp), uf::tempValue);
                secondary = convert(series(h.apparent), uf::tempValue);
                scale = ChartScale.of(lo(min(values), min(secondary)), hi(max(values), max(secondary)), 4);
                axis = Fmt::temp;
                legendRes = R.string.legend_apparent;
                break;
        }
        binding.chart.setData(values, secondary, labels, bars, scale, axis, nowIndex);
        binding.tvChartLegend.setText(legendRes);
        binding.chart.setContentDescription(getString(variable.labelRes));
        updateChartInfo(-1);
    }

    /** Texto sobre el gráfico: resumen del día, o el detalle de la hora seleccionada. */
    private void updateChartInfo(int selected) {
        if (forecast == null) return;
        Forecast.Hourly h = forecast.hourly;
        Forecast.Daily d = forecast.daily;
        String text;
        if (selected < 0 || selected >= hours.length) {
            switch (variable) {
                case RAIN:
                    text = getString(R.string.info_rain_day, percent(max(series(h.precipProb))),
                            uf.precip(d.precipSum[dayIndex]));
                    break;
                case WIND:
                    text = getString(R.string.info_wind_day, kmh(max(series(h.wind))), kmh(max(series(h.gusts))));
                    break;
                case SNOW: {
                    double lo = min(series(h.freezingLevel));
                    double top = max(series(h.freezingLevel));
                    String total = uf.snow(d.snowfallSum[dayIndex]);
                    text = Double.isNaN(lo) ? getString(R.string.info_snow_day_no_level, total)
                            : getString(R.string.info_snow_day, total, uf.altitudeNumber(lo), uf.altitude(top));
                    break;
                }
                case AIR: {
                    double worst = max(airSeries());
                    text = Double.isNaN(worst) ? getString(R.string.info_air_none)
                            : getString(R.string.info_air_day, Fmt.round(worst), aqiLevel(worst));
                    break;
                }
                case UV:
                    text = getString(R.string.info_uv_day, uvText(max(series(h.uv))));
                    break;
                case HUMIDITY:
                    text = getString(R.string.info_range, percent(min(series(h.humidity))),
                            percent(max(series(h.humidity))));
                    break;
                case PRESSURE:
                    text = getString(R.string.info_range, uf.pressureNumber(min(series(h.pressure))),
                            uf.pressure(max(series(h.pressure))));
                    break;
                case TEMP:
                default:
                    text = getString(R.string.info_hi_lo, uf.temp(max(series(h.temp))), uf.temp(min(series(h.temp))));
                    break;
            }
        } else {
            int i = hours[selected];
            String hour = hourFmt.format(new Date(h.time[i]));
            switch (variable) {
                case RAIN:
                    text = getString(R.string.fmt_info3, hour, percent(h.precipProb[i]),
                            uf.precip(h.precip[i]));
                    break;
                case WIND:
                    text = getString(R.string.fmt_info3, hour, windText(h.wind[i], h.windDir[i]),
                            getString(R.string.info_gusts, kmh(h.gusts[i])));
                    break;
                case SNOW:
                    text = getString(R.string.fmt_info, hour, getString(R.string.info_snow_hour,
                            uf.snow(h.snowfall[i]), uf.altitude(h.freezingLevel[i])));
                    break;
                case AIR: {
                    double aqi = airSeries()[selected];
                    text = getString(R.string.fmt_info, hour, Double.isNaN(aqi) ? Fmt.DASH
                            : getString(R.string.info_air_hour, Fmt.round(aqi), aqiLevel(aqi)));
                    break;
                }
                case UV:
                    text = getString(R.string.fmt_info, hour, uvText(h.uv[i]));
                    break;
                case HUMIDITY:
                    text = getString(R.string.fmt_info3, hour, percent(h.humidity[i]),
                            getString(R.string.info_dew, uf.temp(h.dewPoint[i])));
                    break;
                case PRESSURE:
                    text = getString(R.string.fmt_info, hour, uf.pressure(h.pressure[i]));
                    break;
                case TEMP:
                default:
                    text = getString(R.string.fmt_info3, hour, uf.temp(h.temp[i]),
                            getString(R.string.feels_like, uf.temp(h.apparent[i])));
                    break;
            }
        }
        binding.tvChartInfo.setText(text);
    }

    // ------------------------------------------------------------------ tabla

    private void renderTable() {
        Forecast.Hourly h = forecast.hourly;
        binding.tableFixed.removeAllViews();
        binding.tableScroll.removeAllViews();
        binding.tableScroller.scrollTo(0, 0);

        int onSurface = MaterialColors.getColor(binding.getRoot(), com.google.android.material.R.attr.colorOnSurface);
        int variant = MaterialColors.getColor(binding.getRoot(), com.google.android.material.R.attr.colorOnSurfaceVariant);
        int stripe = ColorUtils.setAlphaComponent(MaterialColors.getColor(binding.getRoot(),
                com.google.android.material.R.attr.colorSurfaceContainerHigh), 150);
        int highlight = MaterialColors.getColor(binding.getRoot(),
                com.google.android.material.R.attr.colorPrimaryContainer);
        int nowHour = dayIndex == todayIndex ? forecast.hourIndexAt(System.currentTimeMillis()) : -1;

        String[] headers = {getString(R.string.col_temp), getString(R.string.col_apparent),
                getString(R.string.col_rain_prob), uf.precip.label,
                getString(R.string.col_wind, uf.wind.label), getString(R.string.col_gusts, uf.wind.label),
                getString(R.string.col_humidity), getString(R.string.col_dew), getString(R.string.col_uv),
                getString(R.string.col_pressure, uf.pressure.label), getString(R.string.col_cloud),
                getString(R.string.col_visibility, uf.precip.distanceLabel)};

        // Encabezado.
        LinearLayout fixedHeader = newRow(dp(48), 0);
        fixedHeader.addView(newCell(getString(R.string.col_hour), dp(92), variant, true, 11.5f));
        binding.tableFixed.addView(fixedHeader);
        LinearLayout header = newRow(dp(48), 0);
        for (String title : headers) {
            header.addView(newCell(title, dp(60), variant, true, 11.5f));
        }
        binding.tableScroll.addView(header);

        for (int row = 0; row < hours.length; row++) {
            int i = hours[row];
            int background = i == nowHour ? highlight : (row % 2 == 0 ? stripe : 0);

            LinearLayout fixed = newRow(dp(42), background);
            fixed.setGravity(Gravity.CENTER_VERTICAL);
            TextView hour = newCell(hourFmt.format(new Date(h.time[i])), dp(58), onSurface, true, 13.5f);
            fixed.addView(hour);
            ImageView icon = new ImageView(this);
            icon.setLayoutParams(new LinearLayout.LayoutParams(dp(22), dp(22)));
            icon.setImageResource(WeatherCode.icon(h.code[i], h.isDay[i]));
            icon.setContentDescription(getString(WeatherCode.description(h.code[i])));
            fixed.addView(icon);
            View spacer = new View(this);
            spacer.setLayoutParams(new LinearLayout.LayoutParams(dp(12), 1));
            fixed.addView(spacer);
            binding.tableFixed.addView(fixed);

            String[] cells = {
                    uf.temp(h.temp[i]),
                    uf.temp(h.apparent[i]),
                    percent(h.precipProb[i]),
                    uf.precipNumber(h.precip[i]),
                    Double.isNaN(h.wind[i]) || Double.isNaN(h.windDir[i]) ? uf.windNumber(h.wind[i])
                            : uf.windNumber(h.wind[i]) + " " + compass[Fmt.compassIndex(h.windDir[i])],
                    uf.windNumber(h.gusts[i]),
                    percent(h.humidity[i]),
                    uf.temp(h.dewPoint[i]),
                    Fmt.round(h.uv[i]),
                    uf.pressureNumber(h.pressure[i]),
                    percent(h.cloud[i]),
                    uf.distanceNumber(h.visibility[i] / 1000.0)};
            LinearLayout line = newRow(dp(42), background);
            for (int c = 0; c < cells.length; c++) {
                line.addView(newCell(cells[c], dp(60), onSurface, c == 0, 13.5f));
            }
            binding.tableScroll.addView(line);
        }
    }

    private LinearLayout newRow(int height, int background) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
        if (background != 0) {
            row.setBackgroundColor(background);
        }
        return row;
    }

    private TextView newCell(String text, int width, int color, boolean bold, float sizeSp) {
        TextView tv = new TextView(this);
        tv.setLayoutParams(new LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.MATCH_PARENT));
        tv.setGravity(Gravity.CENTER);
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(sizeSp);
        tv.setMaxLines(2);
        if (bold) {
            tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
        }
        return tv;
    }

    // ------------------------------------------------------------------ formato

    private static String percent(double value) {
        return UnitFormat.percent(value);
    }

    private String kmh(double value) {
        return uf.wind(value);
    }

    private String windText(double speed, double direction) {
        if (Double.isNaN(speed) || Double.isNaN(direction)) return uf.wind(speed);
        return uf.wind(speed) + " " + compass[Fmt.compassIndex(direction)];
    }

    private String uvText(double uv) {
        if (Double.isNaN(uv)) return Fmt.DASH;
        return getString(R.string.fmt_uv, Fmt.round(uv), uvLevels[Fmt.uvLevel(uv)]);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /** Mayor de dos valores, ignorando el que no tenga dato. */
    private static double hi(double a, double b) {
        return Double.isNaN(a) ? b : Double.isNaN(b) ? a : Math.max(a, b);
    }

    /** Menor de dos valores, ignorando el que no tenga dato. */
    private static double lo(double a, double b) {
        return Double.isNaN(a) ? b : Double.isNaN(b) ? a : Math.min(a, b);
    }

    /** Máximo ignorando valores sin dato; NaN si no hay ninguno. */
    private static double max(double[] values) {
        double out = Double.NaN;
        for (double v : values) {
            if (!Double.isNaN(v) && !(v <= out)) out = v;
        }
        return out;
    }

    /** Mínimo ignorando valores sin dato; NaN si no hay ninguno. */
    private static double min(double[] values) {
        double out = Double.NaN;
        for (double v : values) {
            if (!Double.isNaN(v) && !(v >= out)) out = v;
        }
        return out;
    }
}
