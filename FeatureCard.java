package com.kuro.companionctl;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/**
 * One feature: title, switch and (for slider features) a settings icon, a slider and a value chip.
 * The card turns dark when the feature is on.
 */
final class FeatureCard extends LinearLayout {
    interface Listener {
        void onChanged();          // the switch flipped or a slider drag ended (user action)
        void onOpenSettings();     // the gear icon was tapped
    }

    private final String titleText;
    private final TextView title;
    private final SwitchView sw;
    private final IconView gear;       // null without slider
    private final SliderView slider;   // null without slider
    private final TextView chip;       // null without slider
    private final GradientDrawable bg, chipBg;
    private final float min;
    private float max;
    private float progress;            // 0 = light card (off), 1 = dark card (on)
    private ValueAnimator colorAnim;
    private Listener listener;

    FeatureCard(Context c, String titleText, boolean hasSlider, float min, float max, float defaultValue) {
        super(c);
        this.titleText = titleText;
        this.min = min;
        this.max = max;
        setOrientation(VERTICAL);
        int pad = Ui.dp(c, 20);
        setPadding(pad, Ui.dp(c, hasSlider ? 18 : 22), pad, Ui.dp(c, hasSlider ? 16 : 22));
        bg = Ui.rounded(Ui.CARD, Ui.dp(c, 28));
        setBackground(bg);
        setElevation(Ui.dp(c, 2));

        LinearLayout row = new LinearLayout(c);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        title = new TextView(c);
        title.setText(titleText);
        title.setTextSize(17);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        row.addView(title, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        if (hasSlider) {
            gear = new IconView(c, IconView.GEAR, Ui.MUTED);
            gear.setPadding(Ui.dp(c, 5), Ui.dp(c, 5), Ui.dp(c, 5), Ui.dp(c, 5));
            Ui.pressable(gear);
            gear.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (isEnabled() && listener != null) listener.onOpenSettings();
                }
            });
            LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(Ui.dp(c, 34), Ui.dp(c, 34));
            gp.setMargins(0, 0, Ui.dp(c, 10), 0);
            row.addView(gear, gp);
        } else {
            gear = null;
        }

        sw = new SwitchView(c);
        row.addView(sw, new LinearLayout.LayoutParams(Ui.dp(c, 52), Ui.dp(c, 30)));

        if (hasSlider) {
            LinearLayout row2 = new LinearLayout(c);
            row2.setOrientation(HORIZONTAL);
            row2.setGravity(Gravity.CENTER_VERTICAL);
            LayoutParams r2 = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            r2.setMargins(0, Ui.dp(c, 10), 0, 0);
            addView(row2, r2);

            slider = new SliderView(c);
            slider.setRange(min, max);
            slider.setValue(defaultValue, false);
            row2.addView(slider, new LinearLayout.LayoutParams(0, Ui.dp(c, 40), 1f));

            chipBg = Ui.rounded(0xFFECE8DC, Ui.dp(c, 16));
            chip = new TextView(c);
            chip.setTextSize(14);
            chip.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            chip.setGravity(Gravity.CENTER);
            chip.setMinWidth(Ui.dp(c, 62));
            chip.setPadding(Ui.dp(c, 12), Ui.dp(c, 7), Ui.dp(c, 12), Ui.dp(c, 7));
            chip.setBackground(chipBg);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            cp.setMargins(Ui.dp(c, 10), 0, 0, 0);
            row2.addView(chip, cp);

            slider.setListener(new SliderView.Listener() {
                @Override public void onValue(float v) { refreshChip(true); }
                @Override public void onStop(float v) { if (listener != null) listener.onChanged(); }
            });
            refreshChip(false);
        } else {
            slider = null;
            chip = null;
            chipBg = null;
        }

        sw.setListener(new SwitchView.Listener() {
            @Override public void onChanged(boolean checked) {
                animateProgress(checked ? 1f : 0f);
                if (listener != null) listener.onChanged();
            }
        });
        applyProgress(0f);
    }

    void setListener(Listener l) { listener = l; }

    String getTitle() { return titleText; }

    boolean isOn() { return sw.isChecked(); }

    /** Silent: no listener call. */
    void setOn(boolean on, boolean animate) {
        sw.setChecked(on, animate);
        if (animate) animateProgress(on ? 1f : 0f);
        else applyProgress(on ? 1f : 0f);
    }

    float getValue() { return slider == null ? 1f : slider.getValue(); }

    void setValue(float v) {
        if (slider == null) return;
        slider.setValue(v, true);
        refreshChip(false);
    }

    float getMaxValue() { return max; }

    float getMinValue() { return min; }

    void setMaxValue(float newMax) {
        if (slider == null) return;
        max = newMax;
        slider.setRange(min, newMax);
        refreshChip(true);
    }

    /** Dims and blocks the card while root is off. */
    void setActive(boolean active) {
        setEnabled(active);
        sw.setEnabled(active);
        if (gear != null) gear.setEnabled(active);
        if (slider != null) slider.setEnabled(active);
        animate().alpha(active ? 1f : 0.45f).setStartDelay(0).setDuration(260).start();
    }

    private void refreshChip(boolean pop) {
        if (chip == null) return;
        chip.setText(String.format(Locale.US, "%.1fx", slider.getValue()));
        if (pop) {
            chip.setScaleX(1.12f);
            chip.setScaleY(1.12f);
            chip.animate().scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(160).start();
        }
    }

    private void animateProgress(float target) {
        if (colorAnim != null) colorAnim.cancel();
        colorAnim = ValueAnimator.ofFloat(progress, target);
        colorAnim.setDuration(340);
        colorAnim.setInterpolator(new DecelerateInterpolator(1.5f));
        colorAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) { applyProgress((Float) a.getAnimatedValue()); }
        });
        colorAnim.start();
    }

    private void applyProgress(float p) {
        progress = p;
        bg.setColor(Ui.blend(Ui.CARD, Ui.DARK, p));
        title.setTextColor(Ui.blend(Ui.TEXT, Ui.WHITE, p));
        sw.setColors(Ui.TRACK, Ui.blend(Ui.SAGE, Ui.SAGE_LIGHT, p));
        if (slider != null) {
            gear.setColor(Ui.blend(Ui.MUTED, 0xFFC9D3CB, p));
            chip.setTextColor(Ui.blend(Ui.TEXT, Ui.WHITE, p));
            chipBg.setColor(Ui.blend(0xFFECE8DC, Ui.SAGE, p));
            slider.setColors(Ui.blend(Ui.TRACK, 0xFF4B4D51, p), Ui.blend(0xFFB7B3A6, Ui.SAGE_LIGHT, p), Ui.WHITE);
        }
    }
}
