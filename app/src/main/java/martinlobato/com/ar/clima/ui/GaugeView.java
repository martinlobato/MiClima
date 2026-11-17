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
 * Mini gráfico cuadrado para las celdas "LABEL / valor": anillo de porcentaje, arco con escala
 * de colores, brújula de viento, medidor de aguja, barra, termómetro o vaso de lluvia.
 * Cada modo recibe una fracción 0..1 ya calculada (NaN = sin dato: solo se dibuja el fondo).
 */
public class GaugeView extends View {

    public enum Mode { RING, SCALE, COMPASS, NEEDLE, BAR, THERMO, GLASS }

    /** Escala UV: verde, amarillo, naranja, rojo, violeta (cortes de la OMS sobre 0..11+). */
    public static final int[] UV_COLORS = {0xFF3BA55C, 0xFFF2C12E, 0xFFF08A24, 0xFFE0453A, 0xFF8E4FC9};
    public static final float[] UV_STOPS = {0f, 3f / 11, 6f / 11, 8f / 11, 11f / 11};
    /** Escala de viento o ráfagas: de calmo a fuerte. */
    public static final int[] WIND_COLORS = {0xFF3BA55C, 0xFFF2C12E, 0xFFF08A24, 0xFFE0453A};
    public static final float[] WIND_STOPS = {0f, 0.3f, 0.55f, 0.8f};

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final Path path = new Path();
    private final float density;
    private final int primary;
    private final int onSurface;
    private final int surface;

    private Mode mode = Mode.RING;
    private float fraction = Float.NaN;
    private int color;
    private int[] scaleColors;
    private float[] scaleStops;
    private int trend;

    public GaugeView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        primary = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary);
        onSurface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface);
        surface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface);
        color = primary;
        track.setColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant));
        track.setStrokeCap(Paint.Cap.ROUND);
        fill.setStrokeCap(Paint.Cap.ROUND);
        ink.setColor(onSurface);
        ink.setStrokeCap(Paint.Cap.ROUND);
        dot.setColor(surface);
    }

    /** Modo de un solo color (anillo, barra, termómetro, vaso, aguja). */
    public GaugeView set(Mode mode, double fraction, int color) {
        this.mode = mode;
        this.fraction = Double.isNaN(fraction) ? Float.NaN : (float) Math.max(0, Math.min(1, fraction));
        this.color = color;
        this.scaleColors = null;
        setVisibility(VISIBLE);
        invalidate();
        return this;
    }

    /** Arco con escala de colores y un punto en el valor. */
    public GaugeView setScale(double fraction, int[] colors, float[] stops) {
        set(Mode.SCALE, fraction, primary);
        this.scaleColors = colors;
        this.scaleStops = stops;
        return this;
    }

    /** Brújula: {@code fromDegrees} es de dónde viene el viento (como lo da la API). */
    public GaugeView setCompass(double fromDegrees) {
        return set(Mode.COMPASS, Double.isNaN(fromDegrees) ? Double.NaN : (fromDegrees % 360) / 360.0, primary);
    }

    /** Tendencia que se marca con una flechita en el medidor de aguja: -1 baja, 0, 1 sube. */
    public GaugeView setTrend(int trend) {
        this.trend = trend;
        invalidate();
        return this;
    }

    /** Color de la escala en la fracción {@code f}. */
    public static int colorAt(int[] colors, float[] stops, float f) {
        int idx = 0;
        for (int i = 0; i < stops.length; i++) {
            if (f >= stops[i]) idx = i;
        }
        return colors[idx];
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth() - getPaddingLeft() - getPaddingRight();
        float h = getHeight() - getPaddingTop() - getPaddingBottom();
        float size = Math.min(w, h);
        float cx = getPaddingLeft() + w / 2f;
        float cy = getPaddingTop() + h / 2f;
        float stroke = Math.max(3 * density, size * 0.11f);
        float r = size / 2f - stroke / 2f;
        boolean has = !Float.isNaN(fraction);
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(stroke);
        fill.setStyle(Paint.Style.STROKE);
        fill.setStrokeWidth(stroke);
        fill.setColor(color);

        switch (mode) {
            case RING:
                canvas.drawCircle(cx, cy, r, track);
                if (has && fraction > 0) {
                    oval.set(cx - r, cy - r, cx + r, cy + r);
                    canvas.drawArc(oval, -90, 360 * fraction, false, fill);
                }
                break;
            case SCALE: {
                // Arco de 240° abierto abajo, pintado por tramos, con un punto en el valor.
                oval.set(cx - r, cy - r, cx + r, cy + r);
                float start = 150;
                float sweep = 240;
                fill.setStrokeCap(Paint.Cap.BUTT);
                for (int i = 0; i < scaleStops.length; i++) {
                    float a = scaleStops[i];
                    float b = i + 1 < scaleStops.length ? scaleStops[i + 1] : 1f;
                    fill.setColor(scaleColors[i]);
                    fill.setAlpha(has ? 255 : 90);
                    canvas.drawArc(oval, start + sweep * a, sweep * (b - a), false, fill);
                }
                fill.setStrokeCap(Paint.Cap.ROUND);
                fill.setAlpha(255);
                if (has) {
                    double ang = Math.toRadians(start + sweep * fraction);
                    float px = (float) (cx + r * Math.cos(ang));
                    float py = (float) (cy + r * Math.sin(ang));
                    dot.setColor(surface);
                    canvas.drawCircle(px, py, stroke * 0.95f, dot);
                    dot.setColor(colorAt(scaleColors, scaleStops, fraction));
                    canvas.drawCircle(px, py, stroke * 0.6f, dot);
                }
                break;
            }
            case NEEDLE: {
                oval.set(cx - r, cy - r, cx + r, cy + r);
                canvas.drawArc(oval, 150, 240, false, track);
                if (has) {
                    canvas.drawArc(oval, 150, 240 * fraction, false, fill);
                    double ang = Math.toRadians(150 + 240 * fraction);
                    ink.setStyle(Paint.Style.STROKE);
                    ink.setStrokeWidth(2 * density);
                    float len = r * 0.72f;
                    canvas.drawLine(cx, cy, (float) (cx + len * Math.cos(ang)),
                            (float) (cy + len * Math.sin(ang)), ink);
                    ink.setStyle(Paint.Style.FILL);
                    canvas.drawCircle(cx, cy, 2.6f * density, ink);
                }
                if (trend != 0) {
                    // Flechita abajo al centro: hacia arriba sube, hacia abajo baja.
                    float ty = cy + r * 0.62f;
                    float s = 3.2f * density;
                    path.reset();
                    if (trend > 0) {
                        path.moveTo(cx, ty - s);
                        path.lineTo(cx + s, ty + s * 0.6f);
                        path.lineTo(cx - s, ty + s * 0.6f);
                    } else {
                        path.moveTo(cx, ty + s);
                        path.lineTo(cx + s, ty - s * 0.6f);
                        path.lineTo(cx - s, ty - s * 0.6f);
                    }
                    path.close();
                    ink.setStyle(Paint.Style.FILL);
                    canvas.drawPath(path, ink);
                }
                break;
            }
            case COMPASS: {
                track.setStrokeWidth(1.5f * density);
                float cr = size / 2f - density;
                canvas.drawCircle(cx, cy, cr, track);
                // Marcas cardinales; la del norte, más marcada.
                ink.setStyle(Paint.Style.STROKE);
                for (int i = 0; i < 4; i++) {
                    double a = Math.toRadians(i * 90 - 90);
                    ink.setStrokeWidth((i == 0 ? 2.2f : 1.2f) * density);
                    ink.setAlpha(i == 0 ? 255 : 130);
                    float r1 = cr - (i == 0 ? 5 : 3.5f) * density;
                    canvas.drawLine((float) (cx + r1 * Math.cos(a)), (float) (cy + r1 * Math.sin(a)),
                            (float) (cx + cr * Math.cos(a)), (float) (cy + cr * Math.sin(a)), ink);
                }
                ink.setAlpha(255);
                if (has) {
                    // La flecha apunta hacia donde va el viento (desde + 180°).
                    float to = fraction * 360 + 180;
                    canvas.save();
                    canvas.rotate(to, cx, cy);
                    float L = cr * 0.72f;
                    path.reset();
                    path.moveTo(cx, cy - L);
                    path.lineTo(cx + L * 0.42f, cy + L * 0.55f);
                    path.lineTo(cx, cy + L * 0.28f);
                    path.lineTo(cx - L * 0.42f, cy + L * 0.55f);
                    path.close();
                    dot.setColor(color);
                    canvas.drawPath(path, dot);
                    canvas.restore();
                }
                break;
            }
            case BAR: {
                float bw = size * 0.26f;
                float top = cy - size / 2f + bw / 2f;
                float bottom = cy + size / 2f - bw / 2f;
                track.setStrokeWidth(bw);
                fill.setStrokeWidth(bw);
                canvas.drawLine(cx, top, cx, bottom, track);
                if (has) canvas.drawLine(cx, bottom, cx, bottom - (bottom - top) * fraction, fill);
                break;
            }
            case THERMO: {
                float bulb = size * 0.2f;
                float tube = size * 0.17f;
                float top = cy - size / 2f + tube / 2f;
                float by = cy + size / 2f - bulb;
                track.setStrokeWidth(tube);
                canvas.drawLine(cx, top, cx, by, track);
                track.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx, by, bulb, track);
                if (has) {
                    fill.setStrokeWidth(tube * 0.55f);
                    canvas.drawLine(cx, by, cx, by - (by - top) * fraction, fill);
                    dot.setColor(color);
                    canvas.drawCircle(cx, by, bulb * 0.62f, dot);
                }
                break;
            }
            case GLASS: {
                // Vaso de lados apenas inclinados que se llena de agua.
                float gw = size * 0.62f;
                float top = cy - size / 2f + density;
                float bottom = cy + size / 2f - density;
                float inset = gw * 0.12f;
                path.reset();
                path.moveTo(cx - gw / 2, top);
                path.lineTo(cx - gw / 2 + inset, bottom);
                path.lineTo(cx + gw / 2 - inset, bottom);
                path.lineTo(cx + gw / 2, top);
                if (has && fraction > 0) {
                    float level = bottom - (bottom - top) * fraction;
                    float t = (level - top) / (bottom - top);
                    Path water = new Path();
                    water.moveTo(cx - gw / 2 + inset * t, level);
                    water.lineTo(cx - gw / 2 + inset, bottom);
                    water.lineTo(cx + gw / 2 - inset, bottom);
                    water.lineTo(cx + gw / 2 - inset * t, level);
                    water.close();
                    dot.setColor(color);
                    canvas.drawPath(water, dot);
                }
                track.setStrokeWidth(1.8f * density);
                track.setStrokeJoin(Paint.Join.ROUND);
                canvas.drawPath(path, track);
                break;
            }
        }
        track.setStyle(Paint.Style.STROKE);
    }
}
