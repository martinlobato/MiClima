package martinlobato.com.ar.clima.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;

import martinlobato.com.ar.clima.util.Fmt;

/** Barra con las 6 categorías del AQI y un marcador en el valor actual. */
public class AqiBarView extends View {

    /** Colores de categoría (buena → peligrosa); fijos porque tienen significado propio. */
    static final int[] LEVEL_COLORS = {
            0xFF43A047, 0xFFFDD835, 0xFFFB8C00, 0xFFE53935, 0xFF8E24AA, 0xFF7B1F1F};

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private double aqi = Double.NaN;

    public AqiBarView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        ringPaint.setColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface));
    }

    public void setAqi(double aqi) {
        this.aqi = aqi;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float density = getResources().getDisplayMetrics().density;
        float w = getWidth();
        float h = getHeight();
        float barHeight = 8 * density;
        float top = (h - barHeight) / 2f;
        float gap = 2 * density;
        float segment = (w - gap * 5) / 6f;
        for (int i = 0; i < 6; i++) {
            float left = i * (segment + gap);
            paint.setColor(LEVEL_COLORS[i]);
            rect.set(left, top, left + segment, top + barHeight);
            canvas.drawRoundRect(rect, barHeight / 2f, barHeight / 2f, paint);
        }
        if (Double.isNaN(aqi)) return;
        float radius = h / 2f;
        float x = (float) (Fmt.aqiFraction(aqi) * w);
        x = Math.max(radius, Math.min(w - radius, x));
        canvas.drawCircle(x, h / 2f, radius, ringPaint);
        paint.setColor(LEVEL_COLORS[Fmt.aqiLevel(aqi)]);
        canvas.drawCircle(x, h / 2f, radius - 2.5f * density, paint);
    }
}
