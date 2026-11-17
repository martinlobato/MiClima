package martinlobato.com.ar.clima.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;

/**
 * Disco de la luna con la parte iluminada según la fase. En el hemisferio sur se ve espejada
 * (el cuarto creciente tiene iluminado el lado izquierdo), así que se invierte con la latitud.
 */
public class MoonPhaseView extends View {

    private static final int LIT_COLOR = 0xFFE9E6DC;
    private static final int CRATER_COLOR = 0xFFCFCBBE;
    /** Cráteres: {x, y, radio} relativos al radio del disco. */
    private static final float[][] CRATERS = {
            {-0.35f, -0.30f, 0.20f}, {0.30f, -0.42f, 0.12f}, {0.38f, 0.18f, 0.22f},
            {-0.18f, 0.40f, 0.15f}, {0.05f, 0.02f, 0.10f}, {-0.55f, 0.12f, 0.09f},
    };

    private final Paint darkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint litPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint craterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path litPath = new Path();
    private final RectF oval = new RectF();

    private double cycle = 0.25;
    private boolean southern;

    public MoonPhaseView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        // Sombra: el contorno del tema a media opacidad, se distingue en claro y en oscuro.
        darkPaint.setColor(MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorOutline));
        darkPaint.setAlpha(110);
        litPaint.setColor(LIT_COLOR);
        craterPaint.setColor(CRATER_COLOR);
        rimPaint.setStyle(Paint.Style.STROKE);
        rimPaint.setStrokeWidth(getResources().getDisplayMetrics().density);
        rimPaint.setColor(MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorOutlineVariant));
    }

    /** {@code cycle} 0..1 (0 nueva, 0,5 llena) como lo da {@code Moon.Phase}. */
    public void setPhase(double cycle, double latitude) {
        this.cycle = cycle - Math.floor(cycle);
        this.southern = latitude < 0;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float size = Math.min(getWidth() - getPaddingLeft() - getPaddingRight(),
                getHeight() - getPaddingTop() - getPaddingBottom());
        float r = size / 2f - rimPaint.getStrokeWidth();
        float cx = getPaddingLeft() + (getWidth() - getPaddingLeft() - getPaddingRight()) / 2f;
        float cy = getPaddingTop() + (getHeight() - getPaddingTop() - getPaddingBottom()) / 2f;
        if (r <= 0) return;

        canvas.drawCircle(cx, cy, r, darkPaint);

        // Parte iluminada: medio disco del lado del sol + elipse del terminador.
        // k = 1 en luna nueva (nada iluminado), -1 en llena (todo).
        double k = Math.cos(2 * Math.PI * cycle);
        float ex = (float) Math.max(0.001, Math.abs(k) * r);
        litPath.reset();
        oval.set(cx - r, cy - r, cx + r, cy + r);
        litPath.arcTo(oval, -90, 180, true);
        oval.set(cx - ex, cy - r, cx + ex, cy + r);
        litPath.arcTo(oval, 90, k > 0 ? -180 : 180, false);
        litPath.close();

        boolean waning = cycle > 0.5;
        canvas.save();
        if (waning != southern) canvas.scale(-1, 1, cx, cy);
        canvas.drawPath(litPath, litPaint);
        canvas.clipPath(litPath);
        for (float[] c : CRATERS) {
            // Los cráteres no se espejan con la fase: deshacer la escala para ubicarlos.
            float x = (waning != southern) ? cx - c[0] * r : cx + c[0] * r;
            canvas.drawCircle(x, cy + c[1] * r, c[2] * r, craterPaint);
        }
        canvas.restore();

        canvas.drawCircle(cx, cy, r, rimPaint);
    }
}
