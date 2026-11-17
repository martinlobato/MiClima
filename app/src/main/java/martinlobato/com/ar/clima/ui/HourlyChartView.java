package martinlobato.com.ar.clima.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.MaterialColors;

import martinlobato.com.ar.clima.util.ChartScale;

/**
 * Gráfico horario de un día dibujado con Canvas (sin librerías): línea con área o barras,
 * una serie secundaria opcional en línea punteada, marca de "ahora" y selección al tocar
 * o arrastrar. Los valores sin dato (NaN) cortan la línea.
 */
public class HourlyChartView extends View {

    public interface OnSelectionListener {
        /** {@code index} es la hora seleccionada, o -1 si no hay selección. */
        void onSelected(int index);
    }

    public interface AxisFormatter {
        String format(double value);
    }

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint secondaryPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotCorePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final float density;
    private final int touchSlop;

    private double[] values = new double[0];
    @Nullable
    private double[] secondary;
    private String[] xLabels = new String[0];
    private boolean bars;
    private ChartScale scale = ChartScale.fixed(0, 1, 1);
    private AxisFormatter axisFormatter = String::valueOf;
    /** Modo comparación: varias líneas de colores en lugar de la serie principal. */
    @Nullable
    private double[][] lines;
    private int[] lineColors = new int[0];
    private final Paint comparePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int nowIndex = -1;
    private int selected = -1;
    @Nullable
    private OnSelectionListener listener;

    private float downX;
    private float downY;
    private boolean dragging;

    public HourlyChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        int primary = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary);
        int onSurface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface);
        int variant = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant);
        int outline = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant);
        int surface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface);

        gridPaint.setColor(outline);
        gridPaint.setStrokeWidth(density);
        labelPaint.setColor(variant);
        labelPaint.setTextSize(11 * getResources().getDisplayMetrics().scaledDensity);
        linePaint.setColor(primary);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(2.5f * density);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        fillPaint.setColor(ColorUtils.setAlphaComponent(primary, 36));
        secondaryPaint.setColor(variant);
        secondaryPaint.setStyle(Paint.Style.STROKE);
        secondaryPaint.setStrokeWidth(1.5f * density);
        secondaryPaint.setPathEffect(new DashPathEffect(new float[]{4 * density, 4 * density}, 0));
        barPaint.setColor(primary);
        markerPaint.setColor(onSurface);
        markerPaint.setStrokeWidth(density);
        dotPaint.setColor(primary);
        dotCorePaint.setColor(surface);
    }

    /**
     * @param values    serie principal (una por hora)
     * @param secondary serie secundaria punteada, o null
     * @param xLabels   rótulo de cada hora (se dibuja uno cada 3)
     * @param bars      true = barras, false = línea con área
     * @param scale     escala del eje Y
     * @param nowIndex  índice de la hora actual dentro de la serie, o -1
     */
    public void setData(double[] values, @Nullable double[] secondary, String[] xLabels,
                        boolean bars, ChartScale scale, AxisFormatter axisFormatter, int nowIndex) {
        this.values = values;
        this.secondary = secondary;
        this.xLabels = xLabels;
        this.bars = bars;
        this.scale = scale;
        this.axisFormatter = axisFormatter;
        this.nowIndex = nowIndex;
        this.selected = -1;
        this.lines = null;
        invalidate();
    }

    /**
     * Modo comparación: dibuja una línea por serie, cada una con su color. Las series deben
     * tener el mismo largo (una posición por hora); NaN corta la línea.
     */
    public void setComparison(double[][] lines, int[] colors, String[] xLabels, ChartScale scale,
                              AxisFormatter axisFormatter, int nowIndex) {
        setData(lines.length > 0 ? lines[0] : new double[0], null, xLabels, false, scale,
                axisFormatter, nowIndex);
        this.lines = lines;
        this.lineColors = colors;
        invalidate();
    }

    public void setOnSelectionListener(@Nullable OnSelectionListener listener) {
        this.listener = listener;
    }

    public int getSelectedIndex() {
        return selected;
    }

    // ------------------------------------------------------------------ geometría

    private float plotLeft() {
        float widest = 0;
        for (int i = 0; i < scale.tickCount(); i++) {
            widest = Math.max(widest, labelPaint.measureText(axisFormatter.format(scale.tick(i))));
        }
        return getPaddingLeft() + widest + 8 * density;
    }

    private float plotRight() {
        return getWidth() - getPaddingRight() - 4 * density;
    }

    private float plotTop() {
        return getPaddingTop() + 8 * density;
    }

    private float plotBottom() {
        return getHeight() - getPaddingBottom() - labelPaint.getTextSize() - 10 * density;
    }

    private float xOf(int index, float left, float right) {
        return left + (index + 0.5f) * (right - left) / Math.max(1, values.length);
    }

    private float yOf(double value, float top, float bottom) {
        double f = Math.max(0, Math.min(1, scale.fraction(value)));
        return (float) (bottom - f * (bottom - top));
    }

    // ------------------------------------------------------------------ dibujo

    @Override
    protected void onDraw(Canvas canvas) {
        int n = values.length;
        float left = plotLeft();
        float right = plotRight();
        float top = plotTop();
        float bottom = plotBottom();
        if (right <= left || bottom <= top) return;

        // Marcas horizontales con su valor.
        labelPaint.setTextAlign(Paint.Align.RIGHT);
        float textOffset = -(labelPaint.ascent() + labelPaint.descent()) / 2f;
        for (int i = 0; i < scale.tickCount(); i++) {
            double tick = scale.tick(i);
            float y = yOf(tick, top, bottom);
            canvas.drawLine(left, y, right, y, gridPaint);
            canvas.drawText(axisFormatter.format(tick), left - 8 * density, y + textOffset, labelPaint);
        }
        if (n == 0) return;

        // Horas en el eje X (una cada 3 para que no se pisen).
        labelPaint.setTextAlign(Paint.Align.CENTER);
        float xLabelY = getHeight() - getPaddingBottom() - 2 * density;
        for (int i = 0; i < n && i < xLabels.length; i += 3) {
            canvas.drawText(xLabels[i], xOf(i, left, right), xLabelY, labelPaint);
        }

        if (lines != null) {
            comparePaint.setStyle(Paint.Style.STROKE);
            comparePaint.setStrokeWidth(2.2f * density);
            comparePaint.setStrokeCap(Paint.Cap.ROUND);
            comparePaint.setStrokeJoin(Paint.Join.ROUND);
            for (int i = 0; i < lines.length; i++) {
                comparePaint.setColor(lineColors[i % lineColors.length]);
                drawLine(canvas, lines[i], comparePaint, left, right, top, bottom);
            }
        } else if (bars) {
            drawBars(canvas, left, right, top, bottom);
        } else {
            drawArea(canvas, left, right, top, bottom);
            if (secondary != null) {
                drawLine(canvas, secondary, secondaryPaint, left, right, top, bottom);
            }
            drawLine(canvas, values, linePaint, left, right, top, bottom);
        }

        if (nowIndex >= 0 && nowIndex < n && nowIndex != selected) {
            float x = xOf(nowIndex, left, right);
            markerPaint.setAlpha(90);
            canvas.drawLine(x, top, x, bottom, markerPaint);
        }
        if (selected >= 0 && selected < n) {
            float x = xOf(selected, left, right);
            markerPaint.setAlpha(255);
            canvas.drawLine(x, top, x, bottom, markerPaint);
            if (lines == null && !Double.isNaN(values[selected])) {
                float y = yOf(values[selected], top, bottom);
                canvas.drawCircle(x, y, 5.5f * density, dotPaint);
                canvas.drawCircle(x, y, 2.5f * density, dotCorePaint);
            }
        }
    }

    private void drawBars(Canvas canvas, float left, float right, float top, float bottom) {
        int n = values.length;
        float slot = (right - left) / n;
        float half = slot * 0.3f;
        float radius = Math.min(half, 4 * density);
        for (int i = 0; i < n; i++) {
            if (Double.isNaN(values[i])) continue;
            float x = xOf(i, left, right);
            float y = yOf(values[i], top, bottom);
            // Un valor en cero igual deja una marca mínima, para distinguirlo de "sin dato".
            boolean empty = bottom - y < 2 * density;
            barPaint.setAlpha(empty ? 70 : (selected == -1 || selected == i ? 255 : 150));
            rect.set(x - half, empty ? bottom - 2 * density : y, x + half, bottom);
            canvas.drawRoundRect(rect, radius, radius, barPaint);
        }
    }

    private void drawLine(Canvas canvas, double[] series, Paint paint,
                          float left, float right, float top, float bottom) {
        path.reset();
        boolean open = false;
        for (int i = 0; i < series.length && i < values.length; i++) {
            if (Double.isNaN(series[i])) {
                open = false;
                continue;
            }
            float x = xOf(i, left, right);
            float y = yOf(series[i], top, bottom);
            if (open) {
                path.lineTo(x, y);
            } else {
                path.moveTo(x, y);
                open = true;
            }
        }
        canvas.drawPath(path, paint);
    }

    /** Área tenue bajo la serie principal, un tramo por cada secuencia continua de datos. */
    private void drawArea(Canvas canvas, float left, float right, float top, float bottom) {
        int n = values.length;
        int i = 0;
        while (i < n) {
            if (Double.isNaN(values[i])) {
                i++;
                continue;
            }
            int start = i;
            path.reset();
            path.moveTo(xOf(i, left, right), bottom);
            while (i < n && !Double.isNaN(values[i])) {
                path.lineTo(xOf(i, left, right), yOf(values[i], top, bottom));
                i++;
            }
            if (i - start > 1) {
                path.lineTo(xOf(i - 1, left, right), bottom);
                path.close();
                canvas.drawPath(path, fillPaint);
            }
        }
    }

    // ------------------------------------------------------------------ selección

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (values.length == 0) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                dragging = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!dragging) {
                    float dx = Math.abs(event.getX() - downX);
                    float dy = Math.abs(event.getY() - downY);
                    // Arrastre horizontal = recorrer horas; vertical = dejar scrollear la pantalla.
                    if (dx > touchSlop && dx > dy) {
                        dragging = true;
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                }
                if (dragging) {
                    select(indexAt(event.getX()));
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (!dragging) {
                    int index = indexAt(event.getX());
                    // Tocar de nuevo la hora seleccionada la deselecciona.
                    select(index == selected ? -1 : index);
                    performClick();
                }
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            case MotionEvent.ACTION_CANCEL:
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private int indexAt(float x) {
        float left = plotLeft();
        float right = plotRight();
        if (right <= left) return -1;
        int index = (int) Math.floor((x - left) / (right - left) * values.length);
        return Math.max(0, Math.min(values.length - 1, index));
    }

    /** Selecciona una hora (o -1 para ninguna) y avisa al listener si cambió. */
    public void select(int index) {
        if (index >= values.length) index = -1;
        if (index == selected) return;
        selected = index;
        invalidate();
        if (listener != null) {
            listener.onSelected(selected);
        }
    }
}
