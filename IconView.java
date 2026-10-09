package com.kuro.companionctl;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Simple vector icons drawn with Canvas (no image resources, no emoji). */
final class IconView extends View {
    static final int SLIDERS = 0, TOGGLES = 1, GEAR = 2;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final int type;
    private int color;

    IconView(Context c, int type, int color) {
        super(c);
        this.type = type;
        this.color = color;
        setClickable(true);
    }

    void setColor(int c) {
        color = c;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth() - getPaddingLeft() - getPaddingRight();
        float h = getHeight() - getPaddingTop() - getPaddingBottom();
        float s = Math.min(w, h);
        if (s <= 0) return;
        c.save();
        c.translate(getPaddingLeft() + (w - s) / 2f, getPaddingTop() + (h - s) / 2f);
        p.setColor(color);
        p.setStrokeWidth(s * 0.1f);
        p.setStrokeCap(Paint.Cap.ROUND);
        switch (type) {
            case SLIDERS: {
                float[] ys = {0.26f, 0.5f, 0.74f};
                float[] knob = {0.64f, 0.34f, 0.7f};
                for (int i = 0; i < 3; i++) {
                    p.setStyle(Paint.Style.STROKE);
                    c.drawLine(0.14f * s, ys[i] * s, 0.86f * s, ys[i] * s, p);
                    p.setStyle(Paint.Style.FILL);
                    c.drawCircle(knob[i] * s, ys[i] * s, s * 0.11f, p);
                }
                break;
            }
            case TOGGLES: {
                p.setStyle(Paint.Style.STROKE);
                r.set(0.1f * s, 0.16f * s, 0.9f * s, 0.46f * s);
                c.drawRoundRect(r, s * 0.15f, s * 0.15f, p);
                r.set(0.1f * s, 0.54f * s, 0.9f * s, 0.84f * s);
                c.drawRoundRect(r, s * 0.15f, s * 0.15f, p);
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(0.72f * s, 0.31f * s, s * 0.075f, p);
                c.drawCircle(0.28f * s, 0.69f * s, s * 0.075f, p);
                break;
            }
            default: {   // GEAR
                p.setStyle(Paint.Style.STROKE);
                float cx = s / 2f, cy = s / 2f;
                c.drawCircle(cx, cy, s * 0.2f, p);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * 2 * i / 8;
                    float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
                    c.drawLine(cx + ca * s * 0.32f, cy + sa * s * 0.32f, cx + ca * s * 0.45f, cy + sa * s * 0.45f, p);
                }
                break;
            }
        }
        c.restore();
    }
}
