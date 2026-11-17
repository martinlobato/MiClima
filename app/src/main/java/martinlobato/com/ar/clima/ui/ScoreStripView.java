package martinlobato.com.ar.clima.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;

/**
 * Franja de horas coloreada según el puntaje de una actividad (rojo = malo, verde = muy bueno),
 * con el mejor tramo enmarcado y la hora rotulada cada 6 casillas.
 */
public class ScoreStripView extends View {

    private static final int BAD = 0xFFE0453A;
    private static final int MEH = 0xFFF2C12E;
    private static final int GOOD = 0xFF3BA55C;

    private final Paint cellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint framePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float density;

    private int[] scores = new int[0];
    private String[] labels = new String[0];
    private int from = -1;
    private int to = -1;

    public ScoreStripView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        framePaint.setStyle(Paint.Style.STROKE);
        framePaint.setStrokeWidth(2 * density);
        framePaint.setColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface));
        textPaint.setColor(MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorOnSurfaceVariant));
        textPaint.setTextSize(11 * getResources().getDisplayMetrics().scaledDensity);
    }

    /** Puntaje 0..100 por hora (-1 = sin dato), rótulos (null = ninguno) y mejor tramo [from, to). */
    public void setData(int[] scores, String[] labels, int from, int to) {
        this.scores = scores;
        this.labels = labels;
        this.from = from;
        this.to = to;
        invalidate();
    }

    static int colorFor(int score) {
        if (score < 0) return 0x33888888;
        if (score <= 50) return TempColors.blend(BAD, MEH, score / 50f);
        return TempColors.blend(MEH, GOOD, Math.min(1f, (score - 50) / 40f));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int n = scores.length;
        if (n == 0) return;
        float textH = textPaint.getTextSize() + 6 * density;
        float left = getPaddingLeft() + density;
        float right = getWidth() - getPaddingRight() - density;
        float top = getPaddingTop() + density;
        float bottom = getHeight() - getPaddingBottom() - textH;
        float slot = (right - left) / n;
        float gap = Math.min(2 * density, slot * 0.15f);
        float r = 3 * density;
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        for (int k = 0; k < n; k++) {
            int i = rtl ? n - 1 - k : k;
            float x = left + slot * k;
            rect.set(x + gap / 2, top, x + slot - gap / 2, bottom);
            cellPaint.setColor(colorFor(scores[i]));
            canvas.drawRoundRect(rect, r, r, cellPaint);
            if (i < labels.length && labels[i] != null) {
                textPaint.setTextAlign(k == 0 ? Paint.Align.LEFT : Paint.Align.CENTER);
                canvas.drawText(labels[i], k == 0 ? x : x + slot / 2,
                        getHeight() - getPaddingBottom() - 2 * density, textPaint);
            }
        }
        if (from >= 0 && to > from && to <= n) {
            float a = rtl ? left + slot * (n - to) : left + slot * from;
            float b = rtl ? left + slot * (n - from) : left + slot * to;
            rect.set(a - density, top - density, b + density, bottom + density);
            canvas.drawRoundRect(rect, r + density, r + density, framePaint);
        }
    }
}
