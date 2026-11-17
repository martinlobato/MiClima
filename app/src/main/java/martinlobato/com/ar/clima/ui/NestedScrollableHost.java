package martinlobato.com.ar.clima.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.viewpager2.widget.ViewPager2;

/**
 * Envuelve una vista que se desplaza en horizontal (la tira de horas, los chips) dentro del
 * ViewPager2 de lugares: mientras esa vista pueda seguir desplazándose, el gesto es suyo; al
 * llegar al borde, el deslizamiento pasa a cambiar de lugar. Adaptación a Java del ejemplo
 * oficial de ViewPager2.
 */
public class NestedScrollableHost extends FrameLayout {

    private final int touchSlop;
    private float initialX;
    private float initialY;

    public NestedScrollableHost(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    @Nullable
    private ViewPager2 parentPager() {
        ViewParent v = getParent();
        while (v != null && !(v instanceof ViewPager2)) {
            v = v.getParent();
        }
        return (ViewPager2) v;
    }

    @Nullable
    private View child() {
        return getChildCount() > 0 ? getChildAt(0) : null;
    }

    private boolean canChildScroll(float delta) {
        View child = child();
        int direction = -(int) Math.signum(delta);
        return child != null && child.canScrollHorizontally(direction);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        handle(e);
        return super.onInterceptTouchEvent(e);
    }

    private void handle(MotionEvent e) {
        ViewPager2 pager = parentPager();
        if (pager == null || pager.getOrientation() != ViewPager2.ORIENTATION_HORIZONTAL) return;
        if (!canChildScroll(-1) && !canChildScroll(1)) return;
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            initialX = e.getX();
            initialY = e.getY();
            getParent().requestDisallowInterceptTouchEvent(true);
        } else if (e.getAction() == MotionEvent.ACTION_MOVE) {
            float dx = e.getX() - initialX;
            float dy = e.getY() - initialY;
            float scaledDx = Math.abs(dx) * 0.5f;
            float scaledDy = Math.abs(dy);
            if (scaledDx > touchSlop || scaledDy > touchSlop) {
                if (scaledDy > scaledDx) {
                    // Gesto vertical: que lo maneje el scroll de la pantalla.
                    getParent().requestDisallowInterceptTouchEvent(false);
                } else {
                    getParent().requestDisallowInterceptTouchEvent(canChildScroll(dx));
                }
            }
        }
    }
}
