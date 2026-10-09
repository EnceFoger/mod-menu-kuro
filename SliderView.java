package com.kuro.companionctl;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Horizontal slider, 0.1 steps, with an animated thumb. */
final class SliderView extends View {
    interface Listener {
        void onValue(float v);      // while dragging
        void onStop(float v);       // finger lifted
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private float min = 1f, max = 5f, value = 1.5f, shown = 1.5f, thumbScale = 1f;
    private int trackColor = Ui.TRACK, fillColor = Ui.SAGE, thumbColor = Ui.WHITE;
    private ValueAnimator moveAnim, scaleAnim;
    private Listener listener;
    private final float thumbR, trackH;

    SliderView(Context c) {
        super(c);
        thumbR = Ui.dp(c, 11);
        trackH = Ui.dp(c, 6);
    }

    void setListener(Listener l) { listener = l; }

    float getValue() { return value; }

    void setColors(int track, int fill, int thumb) {
        trackColor = track;
        fillColor = fill;
        thumbColor = thumb;
        invalidate();
    }

    private static float round1(float v) { return Math.round(v * 10f) / 10f; }

    private float clamp(float v) { return Math.max(min, Math.min(max, round1(v))); }

    void setRange(float newMin, float newMax) {
        min = newMin;
        max = Math.max(newMax, newMin);
        value = clamp(value);
        shown = value;
        invalidate();
    }

    void setValue(float v, boolean animate) {
        value = clamp(v);
        if (moveAnim != null) moveAnim.cancel();
        if (animate) {
            moveAnim = ValueAnimator.ofFloat(shown, value);
            moveAnim.setDuration(260);
            moveAnim.setInterpolator(new DecelerateInterpolator(1.6f));
            moveAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator a) {
                    shown = (Float) a.getAnimatedValue();
                    invalidate();
                }
            });
            moveAnim.start();
        } else {
            shown = value;
            invalidate();
        }
    }

    private void animateThumb(float target) {
        if (scaleAnim != null) scaleAnim.cancel();
        scaleAnim = ValueAnimator.ofFloat(thumbScale, target);
        scaleAnim.setDuration(160);
        scaleAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                thumbScale = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        scaleAnim.start();
    }

    private void updateFromX(float x) {
        float left = thumbR * 1.35f, right = getWidth() - thumbR * 1.35f;
        float f = right > left ? (x - left) / (right - left) : 0f;
        f = Math.max(0f, Math.min(1f, f));
        float v = clamp(min + f * (max - min));
        if (v != value) {
            value = v;
            if (moveAnim != null) moveAnim.cancel();
            shown = v;
            invalidate();
            if (listener != null) listener.onValue(v);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!isEnabled()) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                animateThumb(1.3f);
                updateFromX(e.getX());
                return true;
            case MotionEvent.ACTION_MOVE:
                updateFromX(e.getX());
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                animateThumb(1f);
                if (listener != null) listener.onStop(value);
                return true;
            default:
                return super.onTouchEvent(e);
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float left = thumbR * 1.35f, right = w - thumbR * 1.35f, cy = h / 2f;
        float f = max > min ? (shown - min) / (max - min) : 0f;
        f = Math.max(0f, Math.min(1f, f));
        float tx = left + f * (right - left);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(trackColor);
        rect.set(left, cy - trackH / 2f, right, cy + trackH / 2f);
        c.drawRoundRect(rect, trackH / 2f, trackH / 2f, paint);
        paint.setColor(fillColor);
        rect.set(left, cy - trackH / 2f, tx, cy + trackH / 2f);
        c.drawRoundRect(rect, trackH / 2f, trackH / 2f, paint);

        float r = thumbR * thumbScale;
        paint.setColor(thumbColor);
        c.drawCircle(tx, cy, r, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(trackH * 0.55f);
        paint.setColor(fillColor);
        c.drawCircle(tx, cy, r - trackH * 0.275f, paint);
    }
}
