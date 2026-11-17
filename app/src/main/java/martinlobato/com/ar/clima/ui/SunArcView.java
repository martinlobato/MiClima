package martinlobato.com.ar.clima.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;

/**
 * Recorrido del sol en el día: una onda que cruza el horizonte en la salida y la puesta, con el
 * tramo ya recorrido resaltado y el sol en la hora actual. El eje horizontal son las 24 h del día
 * local del lugar (de medianoche a medianoche).
 */
public class SunArcView extends View {

    static final int SUN_COLOR = 0xFFF5C542;
    private static final long DAY_MS = 86_400_000L;
    private static final int STEPS = 96;

    private final Paint curvePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint donePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint horizonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sunPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint haloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float density;

    private long dayStart;
    private long sunrise;
    private long sunset;
    private long now;
    private boolean hasData;

    public SunArcView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        int outline = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutline);
        int outlineVariant = MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorOutlineVariant);

        curvePaint.setStyle(Paint.Style.STROKE);
        curvePaint.setStrokeWidth(2 * density);
        curvePaint.setStrokeCap(Paint.Cap.ROUND);
        curvePaint.setColor(outline);
        curvePaint.setAlpha(140);

        donePaint.setStyle(Paint.Style.STROKE);
        donePaint.setStrokeWidth(3 * density);
        donePaint.setStrokeCap(Paint.Cap.ROUND);
        donePaint.setColor(SUN_COLOR);

        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(SUN_COLOR);
        fillPaint.setAlpha(46);

        horizonPaint.setStyle(Paint.Style.STROKE);
        horizonPaint.setStrokeWidth(1 * density);
        horizonPaint.setColor(outlineVariant);

        sunPaint.setColor(SUN_COLOR);
        haloPaint.setColor(SUN_COLOR);
        haloPaint.setAlpha(70);
        nightPaint.setColor(outline);
    }

    /**
     * Datos del día: inicio del día local, salida y puesta (0 si no hay) y el instante actual.
     */
    public void setDay(long dayStart, long sunrise, long sunset, long now) {
        this.dayStart = dayStart;
        this.sunrise = sunrise;
        this.sunset = sunset;
        this.now = now;
        this.hasData = dayStart > 0;
        invalidate();
    }

    private boolean validSun() {
        return sunrise > dayStart && sunset > sunrise && sunset - sunrise < DAY_MS;
    }

    /** Mediodía solar (punto más alto de la onda). */
    private long noon() {
        return validSun() ? (sunrise + sunset) / 2 : dayStart + DAY_MS / 2;
    }

    /** Altura normalizada -1..1 de la onda en el instante {@code t}. */
    private double wave(long t) {
        return Math.cos(2 * Math.PI * (t - noon()) / DAY_MS);
    }

    /** Altura de la onda en la que está el horizonte (la salida y la puesta caen sobre ella). */
    private double horizon() {
        return validSun() ? wave(sunrise) : 0;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!hasData) return;
        float pad = 12 * density;
        float left = getPaddingLeft() + pad / 2;
        float right = getWidth() - getPaddingRight() - pad / 2;
        float top = getPaddingTop() + pad;
        float bottom = getHeight() - getPaddingBottom() - pad;
        float w = right - left;
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        float horizonY = y(horizon(), top, bottom);

        canvas.drawLine(left - pad / 2, horizonY, right + pad / 2, horizonY, horizonPaint);

        // Onda completa, tenue.
        path.reset();
        for (int i = 0; i <= STEPS; i++) {
            long t = dayStart + DAY_MS * i / STEPS;
            float x = x(t, left, w, rtl);
            float y = y(wave(t), top, bottom);
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        canvas.drawPath(path, curvePaint);

        // Tramo de día ya recorrido, con el área bajo la curva.
        // Hoy: el tramo ya recorrido. Otro día: todo el tramo con luz.
        boolean today = now >= dayStart && now < dayStart + DAY_MS;
        if (validSun() && (!today || now > sunrise)) {
            long end = today ? Math.min(now, sunset) : sunset;
            path.reset();
            path.moveTo(x(sunrise, left, w, rtl), horizonY);
            int steps = Math.max(2, (int) (STEPS * (end - sunrise) / DAY_MS));
            for (int i = 0; i <= steps; i++) {
                long t = sunrise + (end - sunrise) * i / steps;
                path.lineTo(x(t, left, w, rtl), y(wave(t), top, bottom));
            }
            Path line = new Path(path);
            path.lineTo(x(end, left, w, rtl), horizonY);
            path.close();
            canvas.drawPath(path, fillPaint);
            canvas.drawPath(line, donePaint);
        }

        // Sol (o un punto apagado de noche) en la hora actual.
        if (now >= dayStart && now < dayStart + DAY_MS) {
            float sx = x(now, left, w, rtl);
            float sy = y(wave(now), top, bottom);
            boolean day = validSun() ? now >= sunrise && now <= sunset : wave(now) > 0;
            if (day) {
                canvas.drawCircle(sx, sy, 13 * density, haloPaint);
                canvas.drawCircle(sx, sy, 9 * density, sunPaint);
            } else {
                canvas.drawCircle(sx, sy, 5 * density, nightPaint);
            }
        }
    }

    private float x(long t, float left, float w, boolean rtl) {
        float frac = (float) (t - dayStart) / DAY_MS;
        return rtl ? left + w * (1 - frac) : left + w * frac;
    }

    private static float y(double v, float top, float bottom) {
        return (float) (top + (1 - v) / 2 * (bottom - top));
    }
}
