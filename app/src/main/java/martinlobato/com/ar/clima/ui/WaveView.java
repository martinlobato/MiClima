package martinlobato.com.ar.clima.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import martinlobato.com.ar.clima.R;

/** Dibujo de olas cuya altura acompaña la altura de ola (0 a 4 m). */
public class WaveView extends View {

    private final Paint back = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint front = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private double height = Double.NaN;

    public WaveView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        int blue = ContextCompat.getColor(context, R.color.wx_rain);
        back.setColor(blue);
        back.setAlpha(80);
        front.setColor(blue);
        front.setAlpha(200);
    }

    public void setWaveHeight(double meters) {
        this.height = meters;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        double frac = Double.isNaN(height) ? 0.15 : Math.max(0.08, Math.min(1, height / 4.0));
        float amp = (float) (h * 0.42 * frac);
        drawWave(canvas, back, w, h, amp, h * 0.5f, 1.4, 0.6);
        drawWave(canvas, front, w, h, amp * 0.85f, h * 0.62f, 1.9, 0);
    }

    private void drawWave(Canvas canvas, Paint paint, float w, float h, float amp, float base,
                          double cycles, double phase) {
        path.reset();
        path.moveTo(0, h);
        for (int i = 0; i <= 60; i++) {
            float x = w * i / 60f;
            float y = (float) (base - amp * Math.sin(2 * Math.PI * (cycles * i / 60.0 + phase)));
            path.lineTo(x, y);
        }
        path.lineTo(w, h);
        path.close();
        canvas.drawPath(path, paint);
    }
}
