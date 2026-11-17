package martinlobato.com.ar.clima.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.graphics.drawable.DrawableCompat;

import com.google.android.material.color.MaterialColors;

import martinlobato.com.ar.clima.R;

/**
 * Indicador de página: un punto por lugar y, si la primera página es la ubicación actual, un
 * pin en su lugar (como la app de clima de Samsung). El punto activo va resaltado.
 */
public class PageDotsView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Drawable pin;
    private final int activeColor;
    private final int inactiveColor;
    private final float dot;
    private final float gap;
    private int count;
    private int current;
    private boolean firstIsLocation;

    public PageDotsView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;
        dot = 8 * density;
        gap = 10 * density;
        activeColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface);
        inactiveColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutline);
        Drawable d = AppCompatResources.getDrawable(context, R.drawable.ic_place);
        pin = d == null ? null : DrawableCompat.wrap(d.mutate());
    }

    public void setPages(int count, boolean firstIsLocation) {
        this.count = count;
        this.firstIsLocation = firstIsLocation;
        current = Math.max(0, Math.min(current, count - 1));
        invalidate();
    }

    public void setCurrent(int current) {
        this.current = current;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (count <= 1) return;
        float pinSize = dot * 1.9f;
        float total = 0;
        for (int i = 0; i < count; i++) {
            total += (i == 0 && firstIsLocation ? pinSize : dot) + (i < count - 1 ? gap : 0);
        }
        float x = (getWidth() - total) / 2f;
        float cy = getHeight() / 2f;
        for (int i = 0; i < count; i++) {
            int color = i == current ? activeColor : inactiveColor;
            if (i == 0 && firstIsLocation && pin != null) {
                DrawableCompat.setTint(pin, color);
                pin.setBounds(Math.round(x), Math.round(cy - pinSize / 2), Math.round(x + pinSize),
                        Math.round(cy + pinSize / 2));
                pin.draw(canvas);
                x += pinSize + gap;
            } else {
                paint.setColor(color);
                float r = i == current ? dot / 2f * 1.15f : dot / 2f;
                canvas.drawCircle(x + dot / 2f, cy, r, paint);
                x += dot + gap;
            }
        }
    }
}
