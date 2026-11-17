package martinlobato.com.ar.clima.ui;

import android.Manifest;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import java.util.Locale;

import martinlobato.com.ar.clima.Prefs;
import martinlobato.com.ar.clima.R;
import martinlobato.com.ar.clima.data.WeatherAlerts;
import martinlobato.com.ar.clima.databinding.ActivityAlertsBinding;
import martinlobato.com.ar.clima.util.Fmt;
import martinlobato.com.ar.clima.util.ThemeUtils;
import martinlobato.com.ar.clima.util.UnitFormat;
import martinlobato.com.ar.clima.util.Units;
import martinlobato.com.ar.clima.work.DailySummaryWorker;
import martinlobato.com.ar.clima.work.RainNotifier;

/** Avisos configurables (un umbral por alerta) y resumen diario a la hora elegida. */
public class AlertsActivity extends AppCompatActivity {

    private static final int[] NAMES = {R.string.alert_frost, R.string.alert_heat, R.string.alert_wind,
            R.string.alert_uv, R.string.alert_air};
    private static final int[] RULES = {R.string.alert_frost_rule, R.string.alert_heat_rule,
            R.string.alert_wind_rule, R.string.alert_uv_rule, R.string.alert_air_rule};

    private ActivityAlertsBinding binding;
    private Context appCtx;
    private UnitFormat uf;
    /** Switch que pidió el permiso de notificaciones, para revertirlo si se niega. */
    private CompoundButton pendingSwitch;
    private Runnable pendingAction;
    private ActivityResultLauncher<String> notificationPermission;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        setTheme(ThemeUtils.resolveThemeRes(this));
        super.onCreate(savedInstanceState);
        binding = ActivityAlertsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        final int basePadding = binding.scroll.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(binding.scroll, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), basePadding + bars.bottom);
            return insets;
        });

        appCtx = getApplicationContext();
        uf = Prefs.units(appCtx);
        notificationPermission = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted && pendingAction != null) {
                pendingAction.run();
            } else if (pendingSwitch != null) {
                pendingSwitch.setChecked(false);
                Toast.makeText(appCtx, R.string.rain_alert_blocked, Toast.LENGTH_LONG).show();
            }
            pendingSwitch = null;
            pendingAction = null;
        });

        for (WeatherAlerts.Type type : WeatherAlerts.Type.values()) {
            addAlertRow(type);
        }
        setupSummary();
    }

    /** Activa algo que notifica: si falta el permiso lo pide; si se niega, apaga el switch. */
    private void enableWithPermission(CompoundButton button, Runnable action) {
        if (RainNotifier.canNotify(appCtx)) {
            action.run();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pendingSwitch = button;
            pendingAction = action;
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        } else {
            button.setChecked(false);
            Toast.makeText(appCtx, R.string.rain_alert_blocked, Toast.LENGTH_LONG).show();
        }
    }

    // ------------------------------------------------------------------ alertas

    private void addAlertRow(final WeatherAlerts.Type type) {
        float density = getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, Math.round(6 * density), 0, Math.round(6 * density));

        final MaterialSwitch toggle = new MaterialSwitch(this);
        toggle.setText(NAMES[type.ordinal()]);
        toggle.setTextSize(14);
        toggle.setTypeface(toggle.getTypeface(), android.graphics.Typeface.BOLD);
        toggle.setMinHeight(Math.round(48 * density));
        toggle.setChecked(Prefs.isAlertEnabled(appCtx, type) && RainNotifier.canNotify(appCtx));
        toggle.setTag("alert_" + type.name());
        row.addView(toggle);

        final TextView rule = new TextView(this);
        rule.setTextColor(com.google.android.material.color.MaterialColors.getColor(
                binding.getRoot(), com.google.android.material.R.attr.colorOnSurfaceVariant));
        rule.setTextSize(13.5f);
        rule.setGravity(Gravity.CENTER_VERTICAL);
        rule.setMinHeight(Math.round(32 * density));
        rule.setBackgroundResource(resolveSelectable());
        rule.setTag("rule_" + type.name());
        updateRule(rule, type);
        rule.setOnClickListener(v -> editThreshold(type, rule));
        row.addView(rule);

        toggle.setOnCheckedChangeListener((button, checked) -> {
            if (!checked) {
                Prefs.setAlertEnabled(appCtx, type, false);
                return;
            }
            enableWithPermission(button, () -> Prefs.setAlertEnabled(appCtx, type, true));
        });
        binding.listAlerts.addView(row);
    }

    private int resolveSelectable() {
        android.util.TypedValue value = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true);
        return value.resourceId;
    }

    /** Umbral en la unidad elegida, para mostrar y editar. */
    private double displayValue(WeatherAlerts.Type type, double metric) {
        switch (type) {
            case FROST:
            case HEAT:
                return Units.temp(metric, uf.temp);
            case WIND:
                return Units.wind(metric, uf.wind);
            default:
                return metric;
        }
    }

    private double toMetric(WeatherAlerts.Type type, double value) {
        switch (type) {
            case FROST:
            case HEAT:
                return Units.tempToCelsius(value, uf.temp);
            case WIND:
                return Units.windToKmh(value, uf.wind);
            default:
                return value;
        }
    }

    private String unitLabel(WeatherAlerts.Type type) {
        switch (type) {
            case FROST:
            case HEAT:
                return uf.temp.label;
            case WIND:
                return uf.wind.label;
            default:
                return "";
        }
    }

    private void updateRule(TextView rule, WeatherAlerts.Type type) {
        double value = displayValue(type, Prefs.getAlertThreshold(appCtx, type));
        String shown = (Fmt.round(value) + " " + unitLabel(type)).trim();
        rule.setText(getString(RULES[type.ordinal()], shown));
    }

    private void editThreshold(final WeatherAlerts.Type type, final TextView rule) {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        input.setText(Fmt.round(displayValue(type, Prefs.getAlertThreshold(appCtx, type))));
        input.setSelectAllOnFocus(true);
        int pad = Math.round(24 * getResources().getDisplayMetrics().density);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(pad, pad / 3, pad, 0);
        box.addView(input, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        new MaterialAlertDialogBuilder(this)
                .setTitle(NAMES[type.ordinal()])
                .setMessage(getString(R.string.alert_threshold_prompt, unitLabel(type)).trim())
                .setView(box)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    try {
                        double v = Double.parseDouble(input.getText().toString().trim().replace(',', '.'));
                        Prefs.setAlertThreshold(appCtx, type, toMetric(type, v));
                        updateRule(rule, type);
                    } catch (NumberFormatException e) {
                        Toast.makeText(appCtx, R.string.alert_threshold_invalid, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // ------------------------------------------------------------------ resumen diario

    private void setupSummary() {
        binding.switchSummary.setChecked(Prefs.isSummaryEnabled(appCtx) && RainNotifier.canNotify(appCtx));
        updateSummaryTime();
        binding.switchSummary.setOnCheckedChangeListener((button, checked) -> {
            if (!checked) {
                Prefs.setSummaryEnabled(appCtx, false);
                DailySummaryWorker.schedule(appCtx);
                return;
            }
            enableWithPermission(button, () -> {
                Prefs.setSummaryEnabled(appCtx, true);
                DailySummaryWorker.schedule(appCtx);
            });
        });
        binding.rowSummaryTime.setOnClickListener(v -> {
            int minutes = Prefs.getSummaryMinutes(appCtx);
            MaterialTimePicker picker = new MaterialTimePicker.Builder()
                    .setTimeFormat(TimeFormat.CLOCK_24H)
                    .setHour(minutes / 60)
                    .setMinute(minutes % 60)
                    .setTitleText(R.string.summary_time)
                    .build();
            picker.addOnPositiveButtonClickListener(x -> {
                Prefs.setSummaryMinutes(appCtx, picker.getHour() * 60 + picker.getMinute());
                updateSummaryTime();
                DailySummaryWorker.schedule(appCtx);
            });
            picker.show(getSupportFragmentManager(), "summary_time");
        });
    }

    private void updateSummaryTime() {
        int minutes = Prefs.getSummaryMinutes(appCtx);
        binding.tvSummaryTime.setText(String.format(Locale.getDefault(), "%02d:%02d", minutes / 60, minutes % 60));
    }
}
