package martinlobato.com.ar.clima.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;

/**
 * Perfil de cordillera con la isoterma 0 °C a su altura: por encima de la línea la montaña se
 * pinta nevada. La escala vertical va de 0 a 6500 m (o más si la isoterma está más alta).
 */
public class SnowLineView extends View {

    /** Perfil de la cordillera: pares {x 0..1, altura en metros}. */
    private static final float[][] PROFILE = {
            {0f, 700}, {0.08f, 1100}, {0.16f, 2100}, {0.22f, 1800}, {0.30f, 3300}, {0.36f, 2900},
            {0.45f, 4700}, {0.52f, 4100}, {0.60f, 5900}, {0.67f, 4800}, {0.74f, 5300}, {0.82f, 3900},
            {0.90f, 4400}, {1f, 3000},
    };

    private final Paint rockPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint snowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mountain = new Path();
    private final float density;

    private double freezing = Double.NaN;
    private String label = "";

    public SnowLineView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        rockPaint.setColor(androidx.core.content.ContextCompat.getColor(context,
                martinlobato.com.ar.clima.R.color.snow_rock));
        snowPaint.setColor(androidx.core.content.ContextCompat.getColor(context,
                martinlobato.com.ar.clima.R.color.snow_cap));
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(2 * density);
        linePaint.setColor(0xFF3FA9E0);
        linePaint.setPathEffect(new DashPathEffect(new float[]{6 * density, 4 * density}, 0));
        textPaint.setColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface));
        textPaint.setTextSize(12 * getResources().getDisplayMetrics().scaledDensity);
        textPaint.setFakeBoldText(true);
        labelBg.setColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface));
        labelBg.setAlpha(220);
    }

    /** Altura de la isoterma en metros y el texto a rotular (ya en la unidad elegida). */
    public void setFreezingLevel(double meters, String label) {
        this.freezing = meters;
        this.label = label;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float left = getPaddingLeft();
        float right = getWidth() - getPaddingRight();
        float top = getPaddingTop() + 4 * density;
        float bottom = getHeight() - getPaddingBottom();
        double maxAlt = Math.max(6500, Double.isNaN(freezing) ? 0 : freezing + 600);
        float w = right - left;

        mountain.reset();
        mountain.moveTo(left, bottom);
        for (float[] p : PROFILE) {
            mountain.lineTo(left + w * p[0], y(p[1], top, bottom, maxAlt));
        }
        mountain.lineTo(right, bottom);
        mountain.close();
        canvas.drawPath(mountain, rockPaint);

        if (Double.isNaN(freezing)) return;
        float fy = y(freezing, top, bottom, maxAlt);
        // Nieve: la parte de la montaña por encima de la isoterma.
        canvas.save();
        canvas.clipRect(left, top - 4 * density, right, fy);
        canvas.drawPath(mountain, snowPaint);
        canvas.restore();
        canvas.drawLine(left, fy, right, fy, linePaint);

        float tw = textPaint.measureText(label);
        float th = textPaint.getTextSize();
        float tx = left + 6 * density;
        float ty = Math.max(top + th, fy - 6 * density);
        canvas.drawRoundRect(tx - 4 * density, ty - th, tx + tw + 4 * density, ty + 4 * density,
                4 * density, 4 * density, labelBg);
        canvas.drawText(label, tx, ty, textPaint);
    }

    private static float y(double alt, float top, float bottom, double maxAlt) {
        return (float) (bottom - (bottom - top) * Math.max(0, Math.min(1, alt / maxAlt)));
    }
}
