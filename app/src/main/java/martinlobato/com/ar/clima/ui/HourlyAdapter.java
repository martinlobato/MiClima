package martinlobato.com.ar.clima.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import martinlobato.com.ar.clima.R;
import martinlobato.com.ar.clima.data.Forecast;
import martinlobato.com.ar.clima.data.WeatherCode;
import martinlobato.com.ar.clima.databinding.ItemHourBinding;
import martinlobato.com.ar.clima.databinding.ItemHourDayBinding;
import martinlobato.com.ar.clima.util.UnitFormat;

/**
 * Tira horizontal con todas las horas del pronóstico (desde la hora actual hasta el día 16),
 * con una "ficha" separadora al comienzo de cada día nuevo.
 */
public class HourlyAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HOUR = 0;
    private static final int TYPE_DAY = 1;

    /** Cada fila es un índice horario (>= 0) o un separador de día (-1 - índice de la primera hora). */
    private final List<Integer> rows = new ArrayList<>();
    private Forecast forecast;
    private int nowIndex;
    /** Rango de la línea de temperatura del día de cada hora (índice horario). */
    private double[] dayMin = new double[0];
    private double[] dayMax = new double[0];
    private int[] dayOf = new int[0];
    private UnitFormat units = UnitFormat.metric();
    private OnDayClickListener dayClickListener;

    public interface OnDayClickListener {
        /** @param time instante (epoch millis) de la primera hora del día tocado */
        void onDayClick(long time);
    }

    public void setOnDayClickListener(OnDayClickListener listener) {
        this.dayClickListener = listener;
    }
    private SimpleDateFormat hourFmt;
    private SimpleDateFormat dayNameFmt;
    private SimpleDateFormat dayNumberFmt;

    public void submit(Forecast f, int nowIndex, UnitFormat units) {
        this.forecast = f;
        this.units = units;
        this.nowIndex = nowIndex;
        hourFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
        dayNameFmt = new SimpleDateFormat("EEE", Locale.getDefault());
        dayNumberFmt = new SimpleDateFormat("d", Locale.getDefault());
        hourFmt.setTimeZone(f.timeZone());
        dayNameFmt.setTimeZone(f.timeZone());
        dayNumberFmt.setTimeZone(f.timeZone());

        // Rango de la línea de temperatura: el de cada día (así se ve cómo sube y baja en el
        // día), con al menos 8° de margen para que un día parejo no parezca una montaña rusa.
        int n = f.hourly.size();
        dayMin = new double[n];
        dayMax = new double[n];
        dayOf = new int[n];
        Calendar dc = Calendar.getInstance(f.timeZone());
        int start = 0;
        for (int i = 0; i <= n; i++) {
            int day = -1;
            if (i < n) {
                dc.setTimeInMillis(f.hourly.time[i]);
                day = dc.get(Calendar.YEAR) * 1000 + dc.get(Calendar.DAY_OF_YEAR);
                dayOf[i] = day;
            }
            if (i == n || (i > start && day != dayOf[start])) {
                double lo = Double.NaN;
                double hi = Double.NaN;
                for (int k = start; k < i; k++) {
                    double t = f.hourly.temp[k];
                    if (Double.isNaN(t)) continue;
                    if (!(t >= lo)) lo = t;
                    if (!(t <= hi)) hi = t;
                }
                if (hi - lo < 8) {
                    double mid = (hi + lo) / 2;
                    lo = mid - 4;
                    hi = mid + 4;
                }
                for (int k = start; k < i; k++) {
                    dayMin[k] = lo;
                    dayMax[k] = hi;
                }
                start = i;
            }
        }

        rows.clear();
        Calendar cal = Calendar.getInstance(f.timeZone());
        int prevDay = -1;
        for (int i = nowIndex; i < f.hourly.size(); i++) {
            cal.setTimeInMillis(f.hourly.time[i]);
            int day = cal.get(Calendar.DAY_OF_YEAR);
            if (prevDay != -1 && day != prevDay) {
                rows.add(-1 - i);
            }
            prevDay = day;
            rows.add(i);
        }
        notifyDataSetChanged();
    }

    /** Instante (epoch millis) de la fila en {@code position}, para el rótulo del día visible. */
    public long timeAt(int position) {
        if (forecast == null || position < 0 || position >= rows.size()) return 0L;
        return forecast.hourly.time[hourIndex(rows.get(position))];
    }

    private static int hourIndex(int row) {
        return row >= 0 ? row : -1 - row;
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position) >= 0 ? TYPE_HOUR : TYPE_DAY;
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_DAY) {
            return new DayHolder(ItemHourDayBinding.inflate(inflater, parent, false));
        }
        return new HourHolder(ItemHourBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        int row = rows.get(position);
        int i = hourIndex(row);
        Date date = new Date(forecast.hourly.time[i]);
        if (holder instanceof DayHolder) {
            ItemHourDayBinding b = ((DayHolder) holder).binding;
            b.tvDayName.setText(dayNameFmt.format(date).replace(".", ""));
            b.tvDayNumber.setText(dayNumberFmt.format(date));
            final long time = forecast.hourly.time[i];
            b.getRoot().setOnClickListener(v -> {
                if (dayClickListener != null) dayClickListener.onDayClick(time);
            });
            return;
        }
        ItemHourBinding b = ((HourHolder) holder).binding;
        Context ctx = b.getRoot().getContext();
        Forecast.Hourly h = forecast.hourly;
        b.tvHour.setText(i == nowIndex ? ctx.getString(R.string.now) : hourFmt.format(date));
        b.ivIcon.setImageResource(WeatherCode.icon(h.code[i], h.isDay[i]));
        b.ivIcon.setContentDescription(ctx.getString(WeatherCode.description(h.code[i])));
        b.tvTemp.setText(units.temp(h.temp[i]));
        // La línea se corta en el cambio de día (ahí va la ficha separadora).
        boolean hasPrev = i > nowIndex && dayOf[i - 1] == dayOf[i];
        boolean hasNext = i + 1 < h.size() && dayOf[i + 1] == dayOf[i];
        b.tempLine.set(hasPrev ? h.temp[i - 1] : Double.NaN, h.temp[i],
                hasNext ? h.temp[i + 1] : Double.NaN, dayMin[i], dayMax[i]);
        b.tvRain.setText(Double.isNaN(h.precipProb[i]) ? "" : UnitFormat.percent(h.precipProb[i]));
        // Sin lluvia probable el porcentaje se atenúa; con lluvia muy probable, en negrita.
        double p = h.precipProb[i];
        b.tvRain.setAlpha(p >= 20 ? 1f : 0.4f);
        b.tvRain.setTypeface(null, p >= 60 ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        b.tvWind.setText(Double.isNaN(h.wind[i]) ? "" : units.wind(h.wind[i]));
    }

    static final class HourHolder extends RecyclerView.ViewHolder {
        final ItemHourBinding binding;

        HourHolder(ItemHourBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    static final class DayHolder extends RecyclerView.ViewHolder {
        final ItemHourDayBinding binding;

        DayHolder(ItemHourDayBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
