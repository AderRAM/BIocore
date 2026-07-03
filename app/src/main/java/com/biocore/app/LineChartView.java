package com.biocore.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import java.util.List;
import java.util.Locale;

public class LineChartView extends View {

    private List<Float> valores;
    private int lineColor = Color.WHITE;
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private static final float PADDING_LEFT = 50f;
    private static final float PADDING_BOTTOM = 40f;
    private static final float PADDING_TOP = 20f;
    private static final float PADDING_RIGHT = 20f;

    public LineChartView(Context context) {
        super(context);
        init();
    }

    public LineChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        gridPaint.setColor(Color.parseColor("#333333"));
        gridPaint.setStrokeWidth(1f);

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(3f);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);

        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setStrokeWidth(6f);

        textPaint.setColor(Color.parseColor("#888888"));
        textPaint.setTextSize(28f);
        textPaint.setTextAlign(Paint.Align.LEFT);
    }

    public void setValores(List<Float> valores, int color) {
        this.valores = valores;
        this.lineColor = color;
        linePaint.setColor(color);
        dotPaint.setColor(color);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (valores == null || valores.isEmpty()) {
            textPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("Sem dados", getWidth() / 2f, getHeight() / 2f, textPaint);
            textPaint.setTextAlign(Paint.Align.LEFT);
            return;
        }

        float chartW = getWidth() - PADDING_LEFT - PADDING_RIGHT;
        float chartH = getHeight() - PADDING_TOP - PADDING_BOTTOM;
        if (chartW <= 0 || chartH <= 0) return;

        float min = Float.MAX_VALUE, max = Float.MIN_VALUE;
        for (float v : valores) {
            if (v < min) min = v;
            if (v > max) max = v;
        }
        float range = max - min;
        if (range == 0) range = 1;

        int n = valores.size();
        float stepX = chartW / Math.max(n - 1, 1);

        for (int i = 0; i <= 4; i++) {
            float y = PADDING_TOP + chartH * i / 4f;
            canvas.drawLine(PADDING_LEFT, y, getWidth() - PADDING_RIGHT, y, gridPaint);
        }

        textPaint.setTextAlign(Paint.Align.RIGHT);
        for (int i = 0; i <= 4; i++) {
            float y = PADDING_TOP + chartH * i / 4f;
            float val = max - range * i / 4f;
            canvas.drawText(String.format(Locale.US, "%.0f", val), PADDING_LEFT - 8f, y + 10f, textPaint);
        }

        Path path = new Path();
        for (int i = 0; i < n; i++) {
            float x = PADDING_LEFT + i * stepX;
            float y = PADDING_TOP + chartH * (1f - (valores.get(i) - min) / range);
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        canvas.drawPath(path, linePaint);

        for (int i = 0; i < n; i++) {
            float x = PADDING_LEFT + i * stepX;
            float y = PADDING_TOP + chartH * (1f - (valores.get(i) - min) / range);
            canvas.drawCircle(x, y, 6f, dotPaint);
        }

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(24f);
        String[] labels = {"agora", "-" + (n / 2), "-" + (n - 1)};
        float[] xs = {PADDING_LEFT + (n - 1) * stepX, PADDING_LEFT + (n / 2) * stepX, PADDING_LEFT};
        for (int i = 0; i < 3; i++) {
            canvas.drawText(labels[i], xs[i], getHeight() - 8f, textPaint);
        }
    }
}
