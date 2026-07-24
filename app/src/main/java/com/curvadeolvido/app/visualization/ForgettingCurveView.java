package com.curvadeolvido.app.visualization;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;
import com.curvadeolvido.app.domain.FsrsMemoryEngine;
import com.curvadeolvido.app.domain.StudyTopic;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Dynamic FSRS forgetting curve with review events and an adaptive time axis. */
public final class ForgettingCurveView extends View {
    private static final int BLUE = Color.rgb(55, 115, 220);
    private static final int GREEN = Color.rgb(45, 157, 114);
    private static final int ORANGE = Color.rgb(224, 137, 47);
    private static final int RED = Color.rgb(207, 68, 78);
    private static final int INK = Color.rgb(23, 43, 70);
    private static final int GRID = Color.rgb(197, 207, 222);

    private final StudyTopic topic;
    private final FsrsMemoryEngine engine;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<MarkerHitbox> markerHitboxes = new ArrayList<>();
    private float reveal = 1f;

    private final Runnable tick =
            new Runnable() {
                @Override
                public void run() {
                    invalidate();
                    handler.postDelayed(this, 60_000L);
                }
            };

    public ForgettingCurveView(Context context, StudyTopic topic, FsrsMemoryEngine engine) {
        super(context);
        this.topic = topic;
        this.engine = engine;
        setMinimumHeight(Math.round(dp(480)));
        setFocusable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(550L);
        animator.addUpdateListener(
                value -> {
                    reveal = (float) value.getAnimatedValue();
                    invalidate();
                });
        animator.start();
        handler.post(tick);
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now = System.currentTimeMillis();
        ForgettingCurveModel model = ForgettingCurveModel.build(topic, engine, now);
        markerHitboxes.clear();

        float width = getWidth();
        float height = Math.max(getHeight(), dp(480));
        float left = dp(48);
        float right = width - dp(16);
        float top = dp(82);
        float bottom = height - dp(150);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(232, 242, 255));
        canvas.drawRoundRect(0, 0, width, height, dp(20), dp(20), paint);

        paint.setColor(Color.rgb(16, 58, 120));
        paint.setTextSize(sp(22));
        paint.setFakeBoldText(true);
        canvas.drawText("Curva de recuerdo", dp(18), dp(32), paint);
        paint.setFakeBoldText(false);
        paint.setTextSize(sp(12));
        paint.setColor(Color.rgb(67, 82, 105));
        canvas.drawText(
                "Probabilidad estimada · escala adaptativa en " + model.window.scaleLabel,
                dp(18),
                dp(54),
                paint);

        drawAxes(canvas, model, left, right, top, bottom);
        drawThreshold(canvas, model, left, right, top, bottom);
        drawSegments(canvas, model, left, right, top, bottom);
        drawResets(canvas, model, left, right, top, bottom);
        drawMarkers(canvas, model, left, right, top, bottom);
        drawCurrentHead(canvas, model, left, right, top, bottom);
        drawLegend(canvas, height);

        int memory = (int) Math.round(model.currentRetrievability * 100.0);
        setContentDescription(
                "Curva de recuerdo de "
                        + topic.name
                        + ". Recuerdo estimado "
                        + memory
                        + " por ciento. "
                        + model.markers.size()
                        + " eventos visibles.");
    }

    private void drawAxes(
            Canvas canvas,
            ForgettingCurveModel model,
            float left,
            float right,
            float top,
            float bottom) {
        int[] percentages = {0, 25, 50, 75, 100};
        paint.setPathEffect(null);
        paint.setStrokeWidth(dp(1));
        for (int percent : percentages) {
            float y = yForMemory(top, bottom, percent / 100.0);
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(GRID);
            canvas.drawLine(left, y, right, y, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(54, 66, 84));
            paint.setTextSize(sp(10));
            canvas.drawText(percent + "%", dp(4), y + dp(4), paint);
        }

        long firstTick =
                (model.window.startAt / model.window.tickMillis) * model.window.tickMillis;
        if (firstTick < model.window.startAt) {
            firstTick += model.window.tickMillis;
        }
        int rendered = 0;
        for (long tick = firstTick;
                tick <= model.window.endAt && rendered < 8;
                tick += model.window.tickMillis) {
            float x = xForTime(left, right, tick, model.window);
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(GRID);
            canvas.drawLine(x, top, x, bottom, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(54, 66, 84));
            paint.setTextSize(sp(9));
            String label = formatTick(tick, model.window.spanMillis());
            canvas.drawText(label, x - paint.measureText(label) / 2f, bottom + dp(18), paint);
            rendered++;
        }
    }

    private void drawThreshold(
            Canvas canvas,
            ForgettingCurveModel model,
            float left,
            float right,
            float top,
            float bottom) {
        float y = yForMemory(top, bottom, model.targetRetention);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.5f));
        paint.setColor(GREEN);
        paint.setPathEffect(new DashPathEffect(new float[] {dp(7), dp(5)}, 0));
        canvas.drawLine(left, y, right, y, paint);
        paint.setPathEffect(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(sp(10));
        paint.setColor(GREEN);
        canvas.drawText(
                "Umbral " + Math.round(model.targetRetention * 100) + "%",
                right - dp(72),
                y - dp(5),
                paint);
    }

    private void drawSegments(
            Canvas canvas,
            ForgettingCurveModel model,
            float left,
            float right,
            float top,
            float bottom) {
        for (ForgettingCurveModel.Segment segment : model.segments) {
            Path path = new Path();
            long duration = Math.max(1L, segment.endAt - segment.startAt);
            int samples =
                    Math.max(
                            12,
                            Math.min(
                                    160,
                                    (int) (duration / ForgettingCurveModel.HOUR)));
            for (int index = 0; index <= samples; index++) {
                long timestamp = segment.startAt + duration * index / samples;
                double memory = model.retrievabilityAt(segment, timestamp);
                float x = xForTime(left, right, timestamp, model.window);
                float y = yForMemory(top, bottom, memory);
                if (index == 0) {
                    path.moveTo(x, y);
                } else {
                    path.lineTo(x, y);
                }
            }
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(3.5f));
            paint.setColor(BLUE);
            paint.setPathEffect(
                    segment.projected
                            ? new DashPathEffect(new float[] {dp(9), dp(6)}, 0)
                            : null);
            canvas.drawPath(path, paint);
        }
        paint.setPathEffect(null);
    }

    private void drawResets(
            Canvas canvas,
            ForgettingCurveModel model,
            float left,
            float right,
            float top,
            float bottom) {
        paint.setStrokeWidth(dp(1.5f));
        paint.setColor(Color.rgb(120, 142, 174));
        paint.setPathEffect(new DashPathEffect(new float[] {dp(3), dp(4)}, 0));
        for (ForgettingCurveModel.Reset reset : model.resets) {
            float x = xForTime(left, right, reset.reviewedAt, model.window);
            float yBefore = yForMemory(top, bottom, reset.before);
            float yAfter = yForMemory(top, bottom, reset.after);
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawLine(x, yBefore, x, yAfter, paint);
        }
        paint.setPathEffect(null);
    }

    private void drawMarkers(
            Canvas canvas,
            ForgettingCurveModel model,
            float left,
            float right,
            float top,
            float bottom) {
        for (ForgettingCurveModel.Marker marker : model.markers) {
            float x = xForTime(left, right, marker.timestamp, model.window);
            float y = yForMemory(top, bottom, marker.retrievability);
            if (marker.kind == ForgettingCurveModel.MarkerKind.RETRIEVAL_FAILURE) {
                y += dp(9);
            }
            drawMarker(canvas, marker.kind, x, y);
            markerHitboxes.add(
                    new MarkerHitbox(
                            marker,
                            new RectF(x - dp(18), y - dp(18), x + dp(18), y + dp(18))));
        }
    }

    private void drawMarker(
            Canvas canvas, ForgettingCurveModel.MarkerKind kind, float x, float y) {
        paint.setPathEffect(null);
        paint.setStrokeWidth(dp(2.5f));
        switch (kind) {
            case COMPLETED_ON_TIME -> {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(GREEN);
                canvas.drawCircle(x, y, dp(6), paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(1.8f));
                paint.setColor(Color.WHITE);
                Path check = new Path();
                check.moveTo(x - dp(3), y);
                check.lineTo(x - dp(0.5f), y + dp(2.5f));
                check.lineTo(x + dp(4), y - dp(3));
                canvas.drawPath(check, paint);
            }
            case COMPLETED_LATE -> {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(ORANGE);
                Path triangle = new Path();
                triangle.moveTo(x, y - dp(7));
                triangle.lineTo(x - dp(7), y + dp(6));
                triangle.lineTo(x + dp(7), y + dp(6));
                triangle.close();
                canvas.drawPath(triangle, paint);
            }
            case MISSED -> {
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(RED);
                canvas.drawLine(x - dp(6), y - dp(6), x + dp(6), y + dp(6), paint);
                canvas.drawLine(x + dp(6), y - dp(6), x - dp(6), y + dp(6), paint);
            }
            case RETRIEVAL_FAILURE -> {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(RED);
                canvas.drawCircle(x, y, dp(6), paint);
            }
            case UPCOMING -> {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.rgb(232, 242, 255));
                canvas.drawCircle(x, y, dp(7), paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(BLUE);
                canvas.drawCircle(x, y, dp(7), paint);
            }
        }
    }

    private void drawCurrentHead(
            Canvas canvas,
            ForgettingCurveModel model,
            float left,
            float right,
            float top,
            float bottom) {
        float x = xForTime(left, right, model.measuredAt, model.window);
        float y = yForMemory(top, bottom, model.currentRetrievability);
        drawHead(canvas, x, y, (float) (model.currentRetrievability * 100.0 * reveal));

        int memory = (int) Math.round(model.currentRetrievability * 100.0);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(INK);
        paint.setTextSize(sp(14));
        canvas.drawText(
                "Ahora: recuerdo " + memory + "% · olvido " + (100 - memory) + "%",
                dp(18),
                getHeight() - dp(116),
                paint);
    }

    private void drawLegend(Canvas canvas, float height) {
        float y1 = height - dp(84);
        float y2 = height - dp(55);
        drawLegendItem(
                canvas,
                ForgettingCurveModel.MarkerKind.COMPLETED_ON_TIME,
                dp(25),
                y1,
                "A tiempo");
        drawLegendItem(
                canvas,
                ForgettingCurveModel.MarkerKind.COMPLETED_LATE,
                dp(125),
                y1,
                "Tarde");
        drawLegendItem(
                canvas,
                ForgettingCurveModel.MarkerKind.MISSED,
                dp(225),
                y1,
                "Omitido");
        drawLegendItem(
                canvas,
                ForgettingCurveModel.MarkerKind.RETRIEVAL_FAILURE,
                dp(25),
                y2,
                "No recordó");
        drawLegendItem(
                canvas,
                ForgettingCurveModel.MarkerKind.UPCOMING,
                dp(150),
                y2,
                "Próximo");
    }

    private void drawLegendItem(
            Canvas canvas,
            ForgettingCurveModel.MarkerKind kind,
            float x,
            float y,
            String label) {
        drawMarker(canvas, kind, x, y - dp(3));
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(54, 66, 84));
        paint.setTextSize(sp(10));
        canvas.drawText(label, x + dp(12), y, paint);
    }

    private void drawHead(Canvas canvas, float x, float y, float memory) {
        Path head = headPath(x, y);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        canvas.drawPath(head, paint);
        canvas.save();
        canvas.clipPath(head);
        float fillTop = y + dp(32) - dp(64) * memory / 100f;
        paint.setColor(Color.rgb(91, 202, 169));
        canvas.drawRect(x - dp(27), fillTop, x + dp(27), y + dp(34), paint);
        canvas.restore();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2));
        paint.setColor(Color.rgb(58, 68, 82));
        canvas.drawPath(head, paint);
    }

    public static Path headPath(float x, float y) {
        Path path = new Path();
        path.moveTo(x, y - 32);
        path.cubicTo(x - 24, y - 32, x - 27, y - 14, x - 26, y + 2);
        path.cubicTo(x - 25, y + 16, x - 16, y + 22, x - 12, y + 26);
        path.lineTo(x - 12, y + 34);
        path.quadTo(x, y + 39, x + 12, y + 34);
        path.lineTo(x + 12, y + 26);
        path.cubicTo(x + 16, y + 22, x + 25, y + 16, x + 26, y + 2);
        path.cubicTo(x + 27, y - 14, x + 24, y - 32, x, y - 32);
        path.close();
        return path;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_UP) {
            return true;
        }
        for (MarkerHitbox item : markerHitboxes) {
            if (item.bounds.contains(event.getX(), event.getY())) {
                String date =
                        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                                .format(new Date(item.marker.timestamp));
                Toast.makeText(
                                getContext(),
                                item.marker.label
                                        + "\n"
                                        + date
                                        + " · recuerdo "
                                        + Math.round(item.marker.retrievability * 100)
                                        + "%",
                                Toast.LENGTH_SHORT)
                        .show();
                performClick();
                return true;
            }
        }
        return performClick();
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private float xForTime(
            float left,
            float right,
            long timestamp,
            ForgettingCurveModel.Window window) {
        double fraction =
                (timestamp - window.startAt) / (double) Math.max(1L, window.spanMillis());
        return left + (right - left) * (float) Math.max(0.0, Math.min(1.0, fraction));
    }

    private static float yForMemory(float top, float bottom, double memory) {
        return bottom - (bottom - top) * (float) Math.max(0.0, Math.min(1.0, memory));
    }

    private String formatTick(long timestamp, long span) {
        SimpleDateFormat formatter;
        if (span <= 2 * ForgettingCurveModel.DAY) {
            formatter = new SimpleDateFormat("HH:mm", Locale.getDefault());
        } else if (span <= 120 * ForgettingCurveModel.DAY) {
            formatter = new SimpleDateFormat("d MMM", Locale.getDefault());
        } else {
            formatter = new SimpleDateFormat("MMM yy", Locale.getDefault());
        }
        return formatter.format(new Date(timestamp));
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }

    private static final class MarkerHitbox {
        final ForgettingCurveModel.Marker marker;
        final RectF bounds;

        MarkerHitbox(ForgettingCurveModel.Marker marker, RectF bounds) {
            this.marker = marker;
            this.bounds = bounds;
        }
    }
}
