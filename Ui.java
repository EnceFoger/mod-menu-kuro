package com.kuro.companionctl;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;

/** Shared palette and small helpers for the app's programmatic UI. */
final class Ui {
    static final int BG = 0xFFEAE6DB;
    static final int CARD = 0xFFFAF8F3;
    static final int DARK = 0xFF2B2C2E;
    static final int SAGE = 0xFF6F8F78;
    static final int SAGE_LIGHT = 0xFF9CBFA6;
    static final int TEXT = 0xFF1E1E1F;
    static final int MUTED = 0xFF8C897E;
    static final int TRACK = 0xFFD8D4C7;
    static final int WHITE = 0xFFFFFFFF;
    static final int DANGER = 0xFFD9827F;

    private Ui() { }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable rounded(int color, float radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusPx);
        return g;
    }

    /** Linear blend of two ARGB colors, t clamped to 0..1. */
    static int blend(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int out = 0;
        for (int shift = 24; shift >= 0; shift -= 8) {
            int ca = (a >>> shift) & 0xFF, cb = (b >>> shift) & 0xFF;
            out |= (Math.round(ca + (cb - ca) * t) & 0xFF) << shift;
        }
        return out;
    }

    /** Squeeze-on-press feedback that does not consume the touch (clicks still work). */
    static void pressable(View v) {
        v.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View view, MotionEvent e) {
                int a = e.getActionMasked();
                if (a == MotionEvent.ACTION_DOWN) {
                    view.animate().scaleX(0.92f).scaleY(0.92f).setStartDelay(0).setDuration(90).start();
                } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                    view.animate().scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(220)
                            .setInterpolator(new OvershootInterpolator(2f)).start();
                }
                return false;
            }
        });
    }

    /** Short horizontal shake, used for invalid input. */
    static void shake(final View v, final float amplitudePx) {
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(380);
        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator anim) {
                float t = (Float) anim.getAnimatedValue();
                v.setTranslationX((float) Math.sin(t * Math.PI * 4) * amplitudePx * (1f - t));
            }
        });
        a.start();
    }
}
