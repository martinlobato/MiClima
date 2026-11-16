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
import martinlobato.com.ar.clima.util.Fmt;

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
    private SimpleDateFormat hourFmt;
    private SimpleDateFormat dayNameFmt;
    private SimpleDateFormat dayNumberFmt;

    public void submit(Forecast f, int nowIndex) {
        this.forecast = f;
        this.nowIndex = nowIndex;
        hourFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
        dayNameFmt = new SimpleDateFormat("EEE", Locale.getDefault());
        dayNumberFmt = new SimpleDateFormat("d", Locale.getDefault());
        hourFmt.setTimeZone(f.timeZone());
        dayNameFmt.setTimeZone(f.timeZone());
        dayNumberFmt.setTimeZone(f.timeZone());

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

    /** Posición del separador (o de la primera hora) correspondiente al índice horario dado. */
    public int positionOfHour(int hourIndex) {
        int separator = rows.indexOf(-1 - hourIndex);
        if (separator >= 0) return separator;
        return Math.max(0, rows.indexOf(hourIndex));
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
            return;
        }
        ItemHourBinding b = ((HourHolder) holder).binding;
        Context ctx = b.getRoot().getContext();
        Forecast.Hourly h = forecast.hourly;
        b.tvHour.setText(i == nowIndex ? ctx.getString(R.string.now) : hourFmt.format(date));
        b.ivIcon.setImageResource(WeatherCode.icon(h.code[i], h.isDay[i]));
        b.ivIcon.setContentDescription(ctx.getString(WeatherCode.description(h.code[i])));
        b.tvTemp.setText(Fmt.temp(h.temp[i]));
        b.tvRain.setText(Double.isNaN(h.precipProb[i]) ? ""
                : ctx.getString(R.string.fmt_percent, Fmt.round(h.precipProb[i])));
        // Sin lluvia probable el porcentaje se atenúa para que resalten las horas con lluvia.
        b.tvRain.setAlpha(h.precipProb[i] >= 20 ? 1f : 0.45f);
        b.tvWind.setText(Double.isNaN(h.wind[i]) ? ""
                : ctx.getString(R.string.fmt_kmh, Fmt.round(h.wind[i])));
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
