package martinlobato.com.ar.clima;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.RemoteViews;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import martinlobato.com.ar.clima.data.Forecast;
import martinlobato.com.ar.clima.data.WeatherRepository;
import martinlobato.com.ar.clima.widget.WeatherWidgetProvider;

/** Contenido del widget armado desde la caché (sin red). */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "es-w411dp-h800dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class WidgetTest {

    private Context ctx;

    @Before
    public void setUp() throws Exception {
        ctx = ApplicationProvider.getApplicationContext();
        TestData.writeCache(ctx);
    }

    private View inflate(RemoteViews views) {
        FrameLayout parent = new FrameLayout(ctx);
        View view = views.apply(ctx, parent);
        float d = ctx.getResources().getDisplayMetrics().density;
        int w = View.MeasureSpec.makeMeasureSpec(Math.round(330 * d), View.MeasureSpec.EXACTLY);
        int h = View.MeasureSpec.makeMeasureSpec(Math.round(150 * d), View.MeasureSpec.EXACTLY);
        view.measure(w, h);
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
        return view;
    }

    @Test
    public void showsCurrentWeatherAndNextHours() {
        Forecast f = WeatherRepository.loadCached(ctx, WeatherRepository.KEY_CURRENT_LOCATION);
        View view = inflate(WeatherWidgetProvider.buildViews(ctx, f, System.currentTimeMillis()));

        assertEquals(View.VISIBLE, view.findViewById(R.id.widgetContent).getVisibility());
        assertEquals(View.GONE, view.findViewById(R.id.widgetEmpty).getVisibility());
        assertEquals("Santiago", text(view, R.id.widgetPlace));
        assertTrue(text(view, R.id.widgetTemp), text(view, R.id.widgetTemp).matches("-?\\d+°"));
        assertTrue(text(view, R.id.widgetHiLo), text(view, R.id.widgetHiLo).startsWith("Máx "));
        assertTrue(text(view, R.id.widgetHour1), text(view, R.id.widgetHour1).matches("\\d\\d:00"));
        assertTrue(text(view, R.id.widgetTemp5), text(view, R.id.widgetTemp5).matches("-?\\d+°"));
        TestData.saveScreenshot(view, "widget");
    }

    @Test
    public void withoutUsableForecastAsksToOpenTheApp() {
        View empty = inflate(WeatherWidgetProvider.buildViews(ctx, null, System.currentTimeMillis()));
        assertEquals(View.VISIBLE, empty.findViewById(R.id.widgetEmpty).getVisibility());
        assertEquals(View.GONE, empty.findViewById(R.id.widgetContent).getVisibility());

        // Una caché que ya no cubre el momento actual tampoco se muestra como si fuera vigente.
        Forecast f = WeatherRepository.loadCached(ctx, WeatherRepository.KEY_CURRENT_LOCATION);
        long farFuture = System.currentTimeMillis() + 40L * 24 * 60 * 60 * 1000;
        View stale = inflate(WeatherWidgetProvider.buildViews(ctx, f, farFuture));
        assertEquals(View.VISIBLE, stale.findViewById(R.id.widgetEmpty).getVisibility());
    }

    private static String text(View root, int id) {
        return ((TextView) root.findViewById(id)).getText().toString();
    }
}
