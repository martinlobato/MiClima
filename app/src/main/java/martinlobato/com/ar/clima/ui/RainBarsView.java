package martinlobato.com.ar.clima.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.color.MaterialColors;

import martinlobato.com.ar.clima.R;

/**
 * Barras de probabilidad de lluvia de las próximas horas (una por hora), más intensas cuanto más
 * milímetros se esperan, con la hora rotulada cada 3 barras.
 */
public class RainBarsView extends View {

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float density;
    private final int rainColor;

    private double[] prob = new double[0];
    private double[] mm = new double[0];
    private String[] labels = new String[0];

    public RainBarsView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        rainColor = ContextCompat.getColor(context, R.color.wx_rain);
        trackPaint.setColor(MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorSurfaceContainerHigh));
        textPaint.setColor(MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorOnSurfaceVariant));
        textPaint.setTextSize(11 * getResources().getDisplayMetrics().scaledDensity);
        textPaint.setTextAlign(Paint.Align.CENTER);
        gridPaint.setColor(MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorOutlineVariant));
        gridPaint.setStrokeWidth(density);
        gridPaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{4 * density, 4 * density}, 0));
    }

    /** Probabilidad (0..100), milímetros y rótulo de hora de cada barra (rótulo null = sin rótulo). */
    public void setData(double[] prob, double[] mm, String[] labels) {
        this.prob = prob;
        this.mm = mm;
        this.labels = labels;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int n = prob.length;
        if (n == 0) return;
        float textH = textPaint.getTextSize() + 6 * density;
        float left = getPaddingLeft();
        float right = getWidth() - getPaddingRight();
        float top = getPaddingTop() + 2 * density;
        float bottom = getHeight() - getPaddingBottom() - textH;
        float slot = (right - left) / n;
        float bw = Math.max(3 * density, slot * 0.62f);
        float r = Math.min(bw / 2, 4 * density);
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;

        // Línea de 50 %.
        float mid = top + (bottom - top) / 2;
        canvas.drawLine(left, mid, right, mid, gridPaint);

        for (int k = 0; k < n; k++) {
            int i = rtl ? n - 1 - k : k;
            float cx = left + slot * (k + 0.5f);
            rect.set(cx - bw / 2, top, cx + bw / 2, bottom);
            canvas.drawRoundRect(rect, r, r, trackPaint);
            double p = prob[i];
            if (!Double.isNaN(p) && p >= 10) {
                float h = (float) Math.max(bw, (bottom - top) * Math.min(100, p) / 100);
                rect.set(cx - bw / 2, bottom - h, cx + bw / 2, bottom);
                // Opacidad según los mm: llovizna clarita, lluvia fuerte bien azul.
                double amount = i < mm.length ? mm[i] : Double.NaN;
                int alpha = Double.isNaN(amount) ? 170 : (int) (110 + Math.min(1, amount / 3) * 145);
                barPaint.setColor(rainColor);
                barPaint.setAlpha(p < 20 ? 90 : alpha);
                canvas.drawRoundRect(rect, r, r, barPaint);
            }
            if (i < labels.length && labels[i] != null) {
                // El primero ("Ahora") va alineado al borde para que no se corte.
                boolean edge = k == 0;
                textPaint.setTextAlign(edge ? (rtl ? Paint.Align.RIGHT : Paint.Align.LEFT) : Paint.Align.CENTER);
                float tx = edge ? (rtl ? right : left) : cx;
                canvas.drawText(labels[i], tx, getHeight() - getPaddingBottom() - 2 * density, textPaint);
            }
        }
    }
}
