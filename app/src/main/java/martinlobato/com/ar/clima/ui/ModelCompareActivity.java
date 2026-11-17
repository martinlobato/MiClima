package martinlobato.com.ar.clima.ui;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.chip.Chip;
import com.google.android.material.color.MaterialColors;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import martinlobato.com.ar.clima.Prefs;
import martinlobato.com.ar.clima.R;
import martinlobato.com.ar.clima.data.ApiException;
import martinlobato.com.ar.clima.data.ModelComparison;
import martinlobato.com.ar.clima.data.PlaceStore;
import martinlobato.com.ar.clima.data.WeatherRepository;
import martinlobato.com.ar.clima.util.BackgroundExecutor;
import martinlobato.com.ar.clima.util.ChartScale;
import martinlobato.com.ar.clima.util.Fmt;
import martinlobato.com.ar.clima.util.ThemeUtils;
import martinlobato.com.ar.clima.util.UnitFormat;

/**
 * Compara el pronóstico horario de un día según cinco modelos meteorológicos: una línea por
 * modelo y un resumen de cuánto difieren. Cuanto más se parecen, más confiable es el pronóstico.
 */
public class ModelCompareActivity extends AppCompatActivity {

    private static final String EXTRA_DAY_TIME = "day_time";
    private static final String STATE_DAY_TIME = "day_time";
    private static final String STATE_VARIABLE = "variable";

    /** Un color fijo por modelo (mismo orden que {@link ModelComparison#MODELS}). */
    static final int[] MODEL_COLORS = {0xFF1E88E5, 0xFFFB8C00, 0xFF43A047, 0xFF8E24AA, 0xFFE53935};

    private enum Variable {
        TEMP(R.string.var_temp), RAIN(R.string.var_rain), WIND(R.string.var_wind);

        final int labelRes;

        Variable(int labelRes) {
            this.labelRes = labelRes;
        }
    }

    private martinlobato.com.ar.clima.databinding.ActivityModelCompareBinding binding;
    private ModelComparison data;
    private UnitFormat uf = UnitFormat.metric();
    private Variable variable = Variable.TEMP;
    private long dayTime;
    /** Índice de la primera hora de cada día presente en los datos. */
    private final List<Integer> dayStarts = new ArrayList<>();
    private int dayIndex;
    private int todayIndex;
    /** Rango [from, to) de horas del día mostrado. */
    private int from;
    private int to;
    private SimpleDateFormat hourFmt;

    public static Intent intent(Context context, long dayTime) {
        return new Intent(context, ModelCompareActivity.class).putExtra(EXTRA_DAY_TIME, dayTime);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        setTheme(ThemeUtils.resolveThemeRes(this));
        super.onCreate(savedInstanceState);
        binding = martinlobato.com.ar.clima.databinding.ActivityModelCompareBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        final int basePadding = binding.scroll.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(binding.scroll, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), basePadding + bars.bottom);
            return insets;
        });

        uf = Prefs.units(this);
        dayTime = getIntent().getLongExtra(EXTRA_DAY_TIME, System.currentTimeMillis());
        if (savedInstanceState != null) {
            dayTime = savedInstanceState.getLong(STATE_DAY_TIME, dayTime);
            int ordinal = savedInstanceState.getInt(STATE_VARIABLE, 0);
            variable = Variable.values()[Math.max(0, Math.min(Variable.values().length - 1, ordinal))];
        }

        setupChips();
        binding.chart.setOnSelectionListener(this::renderModels);
        binding.btnPrevDay.setOnClickListener(v -> showDay(dayIndex - 1));
        binding.btnNextDay.setOnClickListener(v -> showDay(dayIndex + 1));
        binding.btnRetry.setOnClickListener(v -> load());
        load();
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

    // ------------------------------------------------------------------ carga

    private static final class LoadResult {
        final ModelComparison data;
        final Exception error;

        LoadResult(ModelComparison data, Exception error) {
            this.data = data;
            this.error = error;
        }
    }

    private void load() {
        final Context app = getApplicationContext();
        binding.progress.setVisibility(View.VISIBLE);
        binding.errorState.setVisibility(View.GONE);
        binding.content.setVisibility(View.GONE);
        BackgroundExecutor.execute(() -> {
            try {
                double[] coords = WeatherRepository.selectedCoordinates(app);
                if (coords == null) return new LoadResult(null, null);
                return new LoadResult(WeatherRepository.comparison(app,
                        PlaceStore.selectedCacheKey(app), coords[0], coords[1]), null);
            } catch (Exception e) {
                return new LoadResult(null, e);
            }
        }, result -> {
            if (isFinishing() || isDestroyed()) return;
            binding.progress.setVisibility(View.GONE);
            if (result.data == null || result.data.time.length == 0) {
                binding.errorState.setVisibility(View.VISIBLE);
                binding.tvError.setText(describe(result.error));
                return;
            }
            data = result.data;
            hourFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
            hourFmt.setTimeZone(data.timeZone());
            indexDays();
            binding.content.setVisibility(View.VISIBLE);
            showDay(dayIndexOf(dayTime));
        });
    }

    private String describe(@Nullable Exception error) {
        if (error instanceof ApiException && ((ApiException) error).isRateLimited()) {
            return getString(R.string.error_rate_limited);
        }
        if (error instanceof ApiException) return getString(R.string.error_service, error.getMessage());
        if (error instanceof java.io.IOException) return getString(R.string.error_no_connection);
        if (error == null) return "";
        return getString(R.string.error_detail, String.valueOf(error.getMessage()));
    }

    // ------------------------------------------------------------------ días

    private void indexDays() {
        dayStarts.clear();
        java.util.Calendar cal = java.util.Calendar.getInstance(data.timeZone());
        int prev = -1;
        for (int i = 0; i < data.time.length; i++) {
            cal.setTimeInMillis(data.time[i]);
            int day = cal.get(java.util.Calendar.DAY_OF_YEAR);
            if (day != prev) {
                dayStarts.add(i);
                prev = day;
            }
        }
        todayIndex = dayIndexOf(System.currentTimeMillis());
    }

    /** Día (posición en dayStarts) al que pertenece un instante; el primero si es anterior. */
    private int dayIndexOf(long millis) {
        int idx = 0;
        for (int d = 0; d < dayStarts.size(); d++) {
            if (data.time[dayStarts.get(d)] <= millis) idx = d;
        }
        return idx;
    }

    private void showDay(int index) {
        if (data == null || dayStarts.isEmpty()) return;
        dayIndex = Math.max(0, Math.min(dayStarts.size() - 1, index));
        from = dayStarts.get(dayIndex);
        to = dayIndex + 1 < dayStarts.size() ? dayStarts.get(dayIndex + 1) : data.time.length;
        dayTime = data.time[from];

        Date date = new Date(dayTime);
        SimpleDateFormat weekday = new SimpleDateFormat("EEEE", Locale.getDefault());
        weekday.setTimeZone(data.timeZone());
        DateFormat longDate = DateFormat.getDateInstance(DateFormat.LONG, Locale.getDefault());
        longDate.setTimeZone(data.timeZone());
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
        setNavEnabled(binding.btnPrevDay, dayIndex > todayIndex);
        setNavEnabled(binding.btnNextDay, dayIndex < dayStarts.size() - 1);
        renderChart();
    }

    private static void setNavEnabled(View button, boolean enabled) {
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : 0.3f);
    }

    // ------------------------------------------------------------------ gráfico y resumen

    /** Serie métrica [modelo][hora] de la variable elegida. */
    private double[][] source() {
        switch (variable) {
            case RAIN: return data.precip;
            case WIND: return data.wind;
            default: return data.temp;
        }
    }

    private double convert(double metric) {
        switch (variable) {
            case RAIN: return uf.precipValue(metric);
            case WIND: return uf.windValue(metric);
            default: return uf.tempValue(metric);
        }
    }

    private String format(double metric) {
        switch (variable) {
            case RAIN: return uf.precip(metric);
            case WIND: return uf.wind(metric);
            default: return uf.temp(metric);
        }
    }

    private void renderChart() {
        if (data == null) return;
        double[][] src = source();
        int n = to - from;
        double[][] lines = new double[src.length][n];
        double lo = Double.NaN;
        double hi = Double.NaN;
        for (int m = 0; m < src.length; m++) {
            for (int i = 0; i < n; i++) {
                double v = convert(src[m][from + i]);
                lines[m][i] = v;
                if (Double.isNaN(v)) continue;
                if (!(v >= lo)) lo = v;
                if (!(v <= hi)) hi = v;
            }
        }
        SimpleDateFormat shortHour = new SimpleDateFormat("HH", Locale.getDefault());
        shortHour.setTimeZone(data.timeZone());
        String[] labels = new String[n];
        for (int i = 0; i < n; i++) {
            labels[i] = shortHour.format(new Date(data.time[from + i]));
        }
        int nowIndex = -1;
        long now = System.currentTimeMillis();
        if (dayIndex == todayIndex) {
            for (int i = 0; i < n; i++) {
                if (data.time[from + i] <= now) nowIndex = i;
            }
        }
        ChartScale scale;
        HourlyChartView.AxisFormatter axis;
        if (variable == Variable.TEMP) {
            scale = ChartScale.of(lo, hi, 4);
            axis = Fmt::temp;
        } else {
            // Lluvia y viento parten de cero; con todo en cero igual se deja un eje legible.
            scale = ChartScale.of(0, Double.isNaN(hi) || hi <= 0 ? 1 : hi, 4);
            axis = variable == Variable.WIND ? uf::windAxis : Fmt::oneDecimal;
        }
        binding.chart.setComparison(lines, MODEL_COLORS, labels, scale, axis, nowIndex);
        String[] names = new String[ModelComparison.MODELS.length];
        for (int m = 0; m < names.length; m++) {
            String label = ModelComparison.MODELS[m].label;
            // "Météo-France" no entra al costado del gráfico: se abrevia.
            names[m] = label.length() > 6 ? label.substring(0, 5) + "." : label;
        }
        binding.chart.setLineNames(names);
        binding.chart.setContentDescription(getString(variable.labelRes));
        renderModels(-1);
    }

    /** Resumen del día de cada modelo; NaN para los que no tienen datos ese día. */
    private double[] dayStat(double[][] src, boolean wantMax, boolean sum) {
        double[] out = new double[src.length];
        for (int m = 0; m < src.length; m++) {
            double acc = Double.NaN;
            for (int i = from; i < to; i++) {
                double v = src[m][i];
                if (Double.isNaN(v)) continue;
                if (Double.isNaN(acc)) acc = v;
                else if (sum) acc += v;
                else acc = wantMax ? Math.max(acc, v) : Math.min(acc, v);
            }
            out[m] = acc;
        }
        return out;
    }

    private static double lowest(double[] values) {
        double out = Double.NaN;
        for (double v : values) {
            if (!Double.isNaN(v) && !(v >= out)) out = v;
        }
        return out;
    }

    private static double highest(double[] values) {
        double out = Double.NaN;
        for (double v : values) {
            if (!Double.isNaN(v) && !(v <= out)) out = v;
        }
        return out;
    }

    /**
     * Texto de dispersión + una fila por modelo. Sin hora seleccionada las filas muestran el
     * resumen del día; con una hora seleccionada, el valor de cada modelo a esa hora.
     */
    private void renderModels(int selected) {
        if (data == null) return;
        double[][] src = source();
        double[] primary;
        double[] secondary = null;
        String info;
        switch (variable) {
            case RAIN:
                primary = dayStat(src, true, true);
                if (Double.isNaN(highest(primary))) info = getString(R.string.compare_no_data);
                else if (highest(primary) < 0.1) info = getString(R.string.compare_rain_none);
                else info = getString(R.string.compare_rain_range, uf.precipNumber(lowest(primary)),
                            uf.precip(highest(primary)));
                break;
            case WIND:
                primary = dayStat(src, true, false);
                info = Double.isNaN(highest(primary)) ? getString(R.string.compare_no_data)
                        : getString(R.string.compare_wind_range, uf.windNumber(lowest(primary)),
                        uf.wind(highest(primary)));
                break;
            case TEMP:
            default:
                primary = dayStat(src, true, false);
                secondary = dayStat(src, false, false);
                double spread = ModelComparison.spread(primary);
                if (Double.isNaN(spread)) info = getString(R.string.compare_no_data);
                else if (spread < 1.5) info = getString(R.string.compare_temp_agree, uf.tempDiff(1.5));
                else info = getString(R.string.compare_temp_spread, uf.tempDiff(spread));
                break;
        }
        binding.tvChartInfo.setText(info);

        boolean hour = selected >= 0 && from + selected < to;
        binding.tvModelsTitle.setText(hour
                ? getString(R.string.compare_models_hour, hourFmt.format(new Date(data.time[from + selected])))
                : getString(R.string.compare_models_day));

        int onSurface = MaterialColors.getColor(binding.getRoot(), com.google.android.material.R.attr.colorOnSurface);
        int variant = MaterialColors.getColor(binding.getRoot(), com.google.android.material.R.attr.colorOnSurfaceVariant);
        float density = getResources().getDisplayMetrics().density;
        binding.listModels.removeAllViews();
        for (int m = 0; m < src.length; m++) {
            String value;
            if (hour) {
                value = format(src[m][from + selected]);
            } else if (Double.isNaN(primary[m])) {
                value = Fmt.DASH;
            } else if (variable == Variable.TEMP) {
                value = getString(R.string.compare_hi_lo, uf.temp(primary[m]), uf.temp(secondary[m]));
            } else if (variable == Variable.WIND) {
                value = getString(R.string.compare_max, uf.wind(primary[m]));
            } else {
                value = uf.precip(primary[m]);
            }

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(Math.round(44 * density));
            row.setPadding(Math.round(16 * density), 0, Math.round(16 * density), 0);

            View dot = new View(this);
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(MODEL_COLORS[m % MODEL_COLORS.length]);
            dot.setBackground(circle);
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(
                    Math.round(12 * density), Math.round(12 * density));
            dotParams.setMarginEnd(Math.round(12 * density));
            row.addView(dot, dotParams);

            TextView name = new TextView(this);
            name.setText(ModelComparison.MODELS[m].label);
            name.setTextColor(onSurface);
            name.setTextSize(14.5f);
            name.setTypeface(name.getTypeface(), Typeface.BOLD);
            row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView val = new TextView(this);
            val.setText(value);
            val.setTextColor(Fmt.DASH.equals(value) ? variant : onSurface);
            val.setTextSize(14.5f);
            row.addView(val);

            binding.listModels.addView(row);
        }
    }
}
