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
 * Barra de rango mín–máx de un día, ubicada dentro del rango global de todos los días
 * (así se compara de un vistazo qué días son más fríos o más cálidos).
 */
public class RangeBarView extends View {

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private float start;
    private float end = 1f;
    private int startColor;
    private int endColor;
    private boolean gradient;
    /** Posición 0..1 del punto de "ahora" en la barra, o NaN si no se marca. */
    private float marker = Float.NaN;
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerRing = new Paint(Paint.ANTI_ALIAS_FLAG);

    public RangeBarView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        trackPaint.setColor(MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorSurfaceContainerHigh));
        barPaint.setColor(MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary));
    }

    /** Fija el tramo a pintar; valores sin dato (NaN) o rango global vacío dejan solo la pista. */
    public void setRange(double min, double max, double globalMin, double globalMax) {
        double span = globalMax - globalMin;
        if (Double.isNaN(min) || Double.isNaN(max) || Double.isNaN(span) || span <= 0) {
            start = 0f;
            end = 0f;
        } else {
            start = clamp((float) ((min - globalMin) / span));
            end = clamp((float) ((max - globalMin) / span));
        }
        invalidate();
    }

    /** Degradé de la mínima a la máxima (colores de {@link TempColors}). */
    public void setColors(int fromColor, int toColor) {
        startColor = fromColor;
        endColor = toColor;
        gradient = true;
        invalidate();
    }

    /** Marca con un punto el valor actual dentro del rango global (fila "Hoy"). */
    public void setMarker(double value, double globalMin, double globalMax) {
        double span = globalMax - globalMin;
        marker = Double.isNaN(value) || !(span > 0) ? Float.NaN : clamp((float) ((value - globalMin) / span));
        markerPaint.setColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface));
        markerRing.setColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface));
        invalidate();
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // La barra tiene 6 dp de alto centrada; el resto del alto deja lugar al punto de "ahora".
        float w = getWidth();
        float h = Math.min(getHeight(), 6 * getResources().getDisplayMetrics().density);
        float top = (getHeight() - h) / 2f;
        float r = h / 2f;
        rect.set(0, top, w, top + h);
        canvas.drawRoundRect(rect, r, r, trackPaint);
        if (end <= 0f && start <= 0f) return;
        float left = start * w;
        float right = end * w;
        // Un día con mín ≈ máx igual se ve: ancho mínimo de un "punto".
        if (right - left < h) {
            float mid = (left + right) / 2f;
            left = Math.max(0, Math.min(w - h, mid - r));
            right = left + h;
        }
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        if (rtl) {
            rect.set(w - right, top, w - left, top + h);
        } else {
            rect.set(left, top, right, top + h);
        }
        if (gradient) {
            barPaint.setShader(new android.graphics.LinearGradient(rect.left, 0, rect.right, 0,
                    rtl ? endColor : startColor, rtl ? startColor : endColor,
                    android.graphics.Shader.TileMode.CLAMP));
        }
        canvas.drawRoundRect(rect, r, r, barPaint);
        barPaint.setShader(null);
        if (!Float.isNaN(marker)) {
            float cy = getHeight() / 2f;
            float ring = Math.min(getHeight() / 2f, h * 1.05f);
            float x = rtl ? w * (1 - marker) : w * marker;
            x = Math.max(ring, Math.min(w - ring, x));
            canvas.drawCircle(x, cy, ring, markerRing);
            canvas.drawCircle(x, cy, ring * 0.62f, markerPaint);
        }
    }
}
