package martinlobato.com.ar.clima.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;

/**
 * Tramo de la línea de temperatura de una ficha de la tira horaria: va del borde izquierdo
 * (promedio con la hora anterior) al centro (esta hora, con un punto) y al borde derecho
 * (promedio con la siguiente). Puestas una al lado de la otra forman una curva continua.
 */
public class TempLineView extends View {

    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private double prev = Double.NaN;
    private double cur = Double.NaN;
    private double next = Double.NaN;
    private double min;
    private double max;

    public TempLineView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(2.5f * density);
        line.setStrokeCap(Paint.Cap.ROUND);
        ring.setColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface));
    }

    /** Temperaturas (°C) de la hora anterior, esta y la siguiente, y el rango de la tira. */
    public void set(double prev, double cur, double next, double min, double max) {
        this.prev = prev;
        this.cur = cur;
        this.next = next;
        this.min = min;
        this.max = max;
        invalidate();
    }

    private float y(double t) {
        float pad = 5 * density;
        double span = max - min;
        double f = span > 0 ? (t - min) / span : 0.5;
        return (float) (getHeight() - pad - (getHeight() - 2 * pad) * f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (Double.isNaN(cur)) return;
        float w = getWidth();
        float cx = w / 2f;
        float cy = y(cur);
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        double before = rtl ? next : prev;
        double after = rtl ? prev : next;
        int color = TempColors.forCelsius(cur);
        if (!Double.isNaN(before)) {
            double mid = (before + cur) / 2;
            line.setColor(TempColors.forCelsius(mid));
            canvas.drawLine(0, y(mid), cx, cy, line);
        }
        if (!Double.isNaN(after)) {
            double mid = (after + cur) / 2;
            line.setColor(TempColors.forCelsius(mid));
            canvas.drawLine(cx, cy, w, y(mid), line);
        }
        canvas.drawCircle(cx, cy, 4.2f * density, ring);
        dot.setColor(color);
        canvas.drawCircle(cx, cy, 3f * density, dot);
    }
}
