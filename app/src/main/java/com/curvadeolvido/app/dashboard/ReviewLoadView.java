package com.curvadeolvido.app.dashboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

/** Compact chart of the currently scheduled review load for the next 14 days. */
public final class ReviewLoadView extends View {
    private final DashboardModel model;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public ReviewLoadView(Context context, DashboardModel model) {
        super(context);
        this.model = model;
        setMinimumHeight(Math.round(dp(205)));
        setContentDescription(buildDescription());
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        float left = dp(18);
        float right = width - dp(14);
        float top = dp(48);
        float bottom = height - dp(36);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(240, 245, 252));
        canvas.drawRoundRect(0, 0, width, height, dp(16), dp(16), paint);

        paint.setColor(Color.rgb(24, 47, 82));
        paint.setTextSize(sp(15));
        paint.setFakeBoldText(true);
        canvas.drawText("Carga ya programada · 14 días", left, dp(25), paint);
        paint.setFakeBoldText(false);

        int max = 1;
        for (int count : model.scheduledLoad) {
            max = Math.max(max, count);
        }
        float gap = dp(3);
        float slot = (right - left) / DashboardModel.LOAD_DAYS;
        float barWidth = Math.max(dp(4), slot - gap);

        paint.setStrokeWidth(dp(1));
        paint.setColor(Color.rgb(205, 215, 229));
        canvas.drawLine(left, bottom, right, bottom, paint);

        for (int index = 0; index < DashboardModel.LOAD_DAYS; index++) {
            int count = model.scheduledLoad[index];
            float x = left + index * slot + (slot - barWidth) / 2f;
            float barHeight = (bottom - top) * count / max;
            float y = bottom - barHeight;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(index == 0 ? Color.rgb(225, 92, 119) : Color.rgb(78, 129, 224));
            canvas.drawRoundRect(x, y, x + barWidth, bottom, dp(3), dp(3), paint);

            if (count > 0) {
                paint.setColor(Color.rgb(40, 52, 70));
                paint.setTextSize(sp(9));
                String value = Integer.toString(count);
                canvas.drawText(
                        value,
                        x + barWidth / 2f - paint.measureText(value) / 2f,
                        Math.max(top - dp(3), y - dp(3)),
                        paint);
            }
        }

        drawLabel(canvas, left, bottom + dp(18), "Hoy");
        drawCenteredLabel(canvas, left + 3 * slot, bottom + dp(18), "+3");
        drawCenteredLabel(canvas, left + 7 * slot, bottom + dp(18), "+7");
        drawCenteredLabel(canvas, left + 13 * slot, bottom + dp(18), "+13");
    }

    private void drawLabel(Canvas canvas, float x, float y, String label) {
        paint.setColor(Color.rgb(76, 88, 108));
        paint.setTextSize(sp(9));
        canvas.drawText(label, x, y, paint);
    }

    private void drawCenteredLabel(Canvas canvas, float x, float y, String label) {
        paint.setColor(Color.rgb(76, 88, 108));
        paint.setTextSize(sp(9));
        canvas.drawText(label, x - paint.measureText(label) / 2f, y, paint);
    }

    private String buildDescription() {
        StringBuilder description =
                new StringBuilder("Carga de repasos programados para catorce días. ");
        for (int index = 0; index < model.scheduledLoad.length; index++) {
            int count = model.scheduledLoad[index];
            if (count == 0) {
                continue;
            }
            description.append(index == 0 ? "Hoy" : "Día " + index)
                    .append(": ")
                    .append(count)
                    .append(". ");
        }
        return description.toString();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}
