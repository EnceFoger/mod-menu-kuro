package com.kuro.companionctl;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.OvershootInterpolator;

/** Pill switch with an animated thumb and track colour. */
final class SwitchView extends View {
    interface Listener { void onChanged(boolean checked); }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private boolean checked;
    private float pos;                       // 0 = off, 1 = on (overshoots briefly while animating)
    private int offColor = Ui.TRACK, onColor = Ui.SAGE;
    private ValueAnimator anim;
    private Listener listener;

    SwitchView(Context c) {
        super(c);
        setClickable(true);
        setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!isEnabled()) return;
                setChecked(!checked, true);
                if (listener != null) listener.onChanged(checked);
            }
        });
        Ui.pressable(this);
    }

    void setListener(Listener l) { listener = l; }

    boolean isChecked() { return checked; }

    void setColors(int off, int on) {
        offColor = off;
        onColor = on;
        invalidate();
    }

    /** Silent: does not notify the listener. */
    void setChecked(boolean value, boolean animate) {
        checked = value;
        float target = value ? 1f : 0f;
        if (anim != null) anim.cancel();
        if (animate) {
            anim = ValueAnimator.ofFloat(pos, target);
            anim.setDuration(300);
            anim.setInterpolator(new OvershootInterpolator(1.4f));
            anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator a) {
                    pos = (Float) a.getAnimatedValue();
                    invalidate();
                }
            });
            anim.start();
        } else {
            pos = target;
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float pad = h * 0.13f, radius = (h - 2 * pad) / 2f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Ui.blend(offColor, onColor, pos));
        rect.set(0, 0, w, h);
        c.drawRoundRect(rect, h / 2f, h / 2f, paint);
        float cx = pad + radius + pos * (w - 2 * pad - 2 * radius);
        paint.setColor(Ui.WHITE);
        c.drawCircle(cx, h / 2f, radius, paint);
    }
}
