package com.kuro.companionctl;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Toggles for the Kuro Companion module. Writes kuro_companion.cfg through root (su);
 * the module re-reads that file about every 2 seconds, so changes apply without restarting the game.
 * Slider upper limits come from max_* lines in that file (default 5.0, valid 1.0 .. 100.0).
 */
public class MainActivity extends Activity {
    private static final String PKG = "com.klab.bleach";
    private static final String CFG_EXT = "/storage/emulated/0/Android/data/" + PKG + "/files/kuro_companion.cfg";
    private static final String CFG_INT = "/data/user/0/" + PKG + "/files/kuro_companion.cfg";

    private static final float DEFAULT_MAX = 5.0f;
    private static final float LIMIT_MIN = 1.0f;     // a configured maximum must be at least this
    private static final float LIMIT_MAX = 100.0f;   // ...and at most this (Unity caps timeScale at 100)

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private boolean loading = true;

    private TextView status;
    private TextView modeValue;
    private Feature attackSpeed, moveSpeed, attackRange, unitySpeed, damageRate, skillNoCd, godMode, autoNext;
    private Switch modeAdd;

    private class Feature {
        final Switch sw;
        final SeekBar bar;         // null for plain on/off features
        final TextView valueText;
        final float min;
        float max;

        Feature(LinearLayout parent, String title, boolean slider, float min, float max, float defaultValue) {
            this.min = min;
            this.max = max;
            sw = newSwitch();
            TextView value = null;
            SeekBar seek = null;
            if (slider) {
                value = label(14, R.color.sub);
                seek = newSeekBar();
            }
            valueText = value;
            bar = seek;

            parent.addView(newRow(title, valueText, sw));
            if (slider) {
                bar.setMax(steps());
                bar.setProgress(Math.max(0, Math.min(steps(), Math.round((defaultValue - min) * 10.0f))));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(32));
                lp.leftMargin = -dp(8);
                lp.rightMargin = -dp(8);
                lp.bottomMargin = dp(10);
                parent.addView(bar, lp);
                refreshText();
                bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) { refreshText(); }
                    @Override public void onStartTrackingTouch(SeekBar s) { }
                    @Override public void onStopTrackingTouch(SeekBar s) { save(); }
                });
            }
            sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override public void onCheckedChanged(CompoundButton b, boolean checked) {
                    syncLook();
                    save();
                }
            });
            syncLook();
            parent.addView(divider());
        }

        /** Slider and value are dimmed while the feature is off. */
        void syncLook() {
            if (bar == null) return;
            float a = sw.isChecked() ? 1.0f : 0.4f;
            bar.setAlpha(a);
            valueText.setAlpha(a);
        }

        int steps() { return Math.max(1, Math.round((max - min) * 10.0f)); }

        float value() { return Math.round((min + bar.getProgress() / 10.0f) * 10.0f) / 10.0f; }

        void setMaxValue(float newMax) {
            max = newMax;
            if (bar == null) return;
            bar.setMax(steps());
            if (bar.getProgress() > steps()) bar.setProgress(steps());
            refreshText();
        }

        void refreshText() {
            if (valueText != null)
                valueText.setText(String.format(Locale.US, "%.1fx", value()));
        }

        /** v is the value stored in the file; 1.0 (or anything below min) means "off". */
        void setFromFile(float v) {
            if (bar == null) return;
            boolean on = Math.abs(v - 1.0f) > 0.001f && v >= min;
            sw.setChecked(on);
            if (on) {
                float c = Math.max(min, Math.min(max, v));
                bar.setProgress(Math.round((c - min) * 10.0f));
            }
            refreshText();
        }
    }

    // ---------- UI helpers ----------

    private TextView label(int sp, int colorRes) {
        TextView t = new TextView(this);
        t.setTextSize(sp);
        t.setTextColor(getColor(colorRes));
        return t;
    }

    private Switch newSwitch() {
        Switch s = new Switch(this);
        s.setThumbDrawable(getDrawable(R.drawable.switch_thumb));
        s.setTrackDrawable(getDrawable(R.drawable.switch_track));
        return s;
    }

    private SeekBar newSeekBar() {
        SeekBar b = new SeekBar(this);
        b.setProgressDrawable(getDrawable(R.drawable.seek_progress));
        b.setThumb(getDrawable(R.drawable.seek_thumb));
        b.setThumbOffset(dp(8));
        b.setSplitTrack(false);
        b.setPadding(dp(8), 0, dp(8), 0);
        return b;
    }

    /** Flat settings row: name on the left, optional value, switch on the right. Tapping the row toggles. */
    private LinearLayout newRow(String title, TextView value, final Switch sw) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(56));
        TextView name = label(16, R.color.fg);
        name.setText(title);
        row.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
        if (value != null) {
            value.setPadding(dp(12), 0, dp(16), 0);
            row.addView(value);
        } else {
            name.setPadding(0, 0, dp(12), 0);
        }
        row.addView(sw);
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { sw.toggle(); }
        });
        return row;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(getColor(R.color.line));
        v.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(1) / 2)));
        return v;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(getColor(R.color.bg));
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(20), dp(24), dp(20), dp(24));
        scroll.addView(col);

        TextView title = label(22, R.color.fg);
        title.setText("Kuro Companion");
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        col.addView(title);

        status = label(13, R.color.sub);
        status.setText("Memeriksa root...");
        status.setPadding(0, dp(4), 0, dp(16));
        col.addView(status);
        col.addView(divider());

        attackSpeed = new Feature(col, "Attack Speed", true, 1.0f, DEFAULT_MAX, 1.5f);
        moveSpeed   = new Feature(col, "Move Speed", true, 1.0f, DEFAULT_MAX, 1.5f);
        attackRange = new Feature(col, "Attack Range", true, 1.0f, DEFAULT_MAX, 1.5f);
        unitySpeed  = new Feature(col, "Unity Speed", true, 0.1f, DEFAULT_MAX, 2.0f);
        damageRate  = new Feature(col, "Damage Rate Up", true, 1.0f, DEFAULT_MAX, 1.5f);
        skillNoCd   = new Feature(col, "Skill No Cooldown", false, 1.0f, DEFAULT_MAX, 1.0f);
        godMode     = new Feature(col, "God Mode", false, 1.0f, DEFAULT_MAX, 1.0f);
        autoNext    = new Feature(col, "Auto Next Quest", false, 1.0f, DEFAULT_MAX, 1.0f);

        modeAdd = newSwitch();
        modeValue = label(14, R.color.sub);
        modeValue.setText("Add");
        modeAdd.setChecked(true);
        modeAdd.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean checked) {
                modeValue.setText(checked ? "Add" : "Multiply");
                save();
            }
        });
        col.addView(newRow("Mode kecepatan", modeValue, modeAdd));
        col.addView(divider());

        TextView note = label(12, R.color.sub);
        note.setText("Perubahan diterapkan dalam sekitar 2 detik.\n"
                + "Jangan aktifkan fitur yang sama di sini dan di menu Kuro.\n"
                + "Hindari mode online (risiko banned).");
        note.setLineSpacing(0, 1.2f);
        note.setPadding(0, dp(16), 0, 0);
        col.addView(note);

        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadAsync();   // also picks up edits made to max_* in the file while the app was in the background
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    // ---------- root helpers ----------

    private static class Result { int code = -1; String out = ""; }

    private Result su(String cmd, String stdin) {
        Result r = new Result();
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            OutputStream os = p.getOutputStream();
            if (stdin != null) os.write(stdin.getBytes("UTF-8"));
            os.flush();
            os.close();
            r.out = readAll(p.getInputStream());
            readAll(p.getErrorStream());
            r.code = p.waitFor();
        } catch (Exception e) {
            r.out = String.valueOf(e);
        }
        return r;
    }

    private static String readAll(InputStream in) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line).append('\n');
        return sb.toString();
    }

    // ---------- load / save ----------

    private void loadAsync() {
        loading = true;
        io.execute(new Runnable() {
            @Override public void run() {
                final Result id = su("id", null);
                final boolean root = id.code == 0 && id.out.contains("uid=0");
                final Result cfg = root ? su("cat '" + CFG_EXT + "' 2>/dev/null || cat '" + CFG_INT + "' 2>/dev/null", null) : null;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (!root) {
                            status.setText("Root tidak tersedia. Beri izin root di Magisk.");
                        } else {
                            status.setText("Root aktif");
                            applyFile(cfg.out);
                        }
                        loading = false;
                    }
                });
            }
        });
    }

    /** A configured maximum must be a finite number in 1.0 .. 100.0; anything else falls back to 5.0. */
    static float parseMax(String s) {
        try {
            float f = Float.parseFloat(s.trim());
            if (!Float.isNaN(f) && !Float.isInfinite(f) && f >= LIMIT_MIN && f <= LIMIT_MAX) return f;
        } catch (Exception e) {
            // fall through to the default
        }
        return DEFAULT_MAX;
    }

    private static float num(String s) {
        try {
            float f = Float.parseFloat(s.trim());
            return (Float.isNaN(f) || Float.isInfinite(f)) ? 1.0f : f;
        } catch (Exception e) {
            return 1.0f;
        }
    }

    private static boolean flag(String s) { return "1".equals(s) || "true".equals(s) || "on".equals(s); }

    private void applyFile(String text) {
        Map<String, String> m = new HashMap<String, String>();
        for (String line : text.split("\n")) {
            int hash = line.indexOf('#');
            if (hash >= 0) line = line.substring(0, hash);
            int eq = line.indexOf('=');
            if (eq < 0) continue;
            m.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
        }
        // Limits first, so the sliders have the right range before values are applied.
        attackSpeed.setMaxValue(m.containsKey("max_attack_speed") ? parseMax(m.get("max_attack_speed")) : DEFAULT_MAX);
        moveSpeed.setMaxValue(m.containsKey("max_move_speed") ? parseMax(m.get("max_move_speed")) : DEFAULT_MAX);
        attackRange.setMaxValue(m.containsKey("max_attack_range") ? parseMax(m.get("max_attack_range")) : DEFAULT_MAX);
        unitySpeed.setMaxValue(m.containsKey("max_unity_speed") ? parseMax(m.get("max_unity_speed")) : DEFAULT_MAX);
        damageRate.setMaxValue(m.containsKey("max_damage_rate") ? parseMax(m.get("max_damage_rate")) : DEFAULT_MAX);

        attackSpeed.setFromFile(num(m.get("attack_speed")));
        moveSpeed.setFromFile(num(m.get("move_speed")));
        attackRange.setFromFile(num(m.get("attack_range")));
        unitySpeed.setFromFile(num(m.get("unity_speed")));
        damageRate.setFromFile(num(m.get("damage_rate")));
        skillNoCd.sw.setChecked(flag(m.get("skill_no_cd")));
        godMode.sw.setChecked(flag(m.get("god_mode")));
        autoNext.sw.setChecked(flag(m.get("auto_next_quest")));
        String mode = m.get("attack_speed_mode");
        if (mode != null) modeAdd.setChecked(!"mul".equals(mode));
    }

    private String buildConfig() {
        String mode = modeAdd.isChecked() ? "add" : "mul";
        StringBuilder sb = new StringBuilder();
        sb.append("stage=3\n");
        sb.append(String.format(Locale.US, "attack_speed=%.1f\n", attackSpeed.sw.isChecked() ? attackSpeed.value() : 1.0f));
        sb.append("attack_speed_mode=").append(mode).append('\n');
        sb.append(String.format(Locale.US, "move_speed=%.1f\n", moveSpeed.sw.isChecked() ? moveSpeed.value() : 1.0f));
        sb.append("move_speed_mode=").append(mode).append('\n');
        sb.append(String.format(Locale.US, "attack_range=%.1f\n", attackRange.sw.isChecked() ? attackRange.value() : 1.0f));
        sb.append(String.format(Locale.US, "unity_speed=%.1f\n", unitySpeed.sw.isChecked() ? unitySpeed.value() : 1.0f));
        sb.append(String.format(Locale.US, "damage_rate=%.1f\n", damageRate.sw.isChecked() ? damageRate.value() : 1.0f));
        sb.append("damage_rate_mode=add\n");
        sb.append("skill_no_cd=").append(skillNoCd.sw.isChecked() ? "1" : "0").append('\n');
        sb.append("god_mode=").append(godMode.sw.isChecked() ? "1" : "0").append('\n');
        sb.append("auto_next_quest=").append(autoNext.sw.isChecked() ? "1" : "0").append('\n');
        sb.append(String.format(Locale.US, "max_attack_speed=%.1f\n", attackSpeed.max));
        sb.append(String.format(Locale.US, "max_move_speed=%.1f\n", moveSpeed.max));
        sb.append(String.format(Locale.US, "max_attack_range=%.1f\n", attackRange.max));
        sb.append(String.format(Locale.US, "max_unity_speed=%.1f\n", unitySpeed.max));
        sb.append(String.format(Locale.US, "max_damage_rate=%.1f\n", damageRate.max));
        return sb.toString();
    }

    private void save() {
        if (loading) return;
        final String content = buildConfig();
        io.execute(new Runnable() {
            @Override public void run() {
                // Create the file if missing (readable by the game), then overwrite in place so the
                // owner and permissions of an existing file are kept.
                su("mkdir -p \"$(dirname '" + CFG_EXT + "')\"; [ -f '" + CFG_EXT + "' ] || { : > '" + CFG_EXT + "'; chmod 666 '" + CFG_EXT + "'; }", null);
                final Result w = su("cat > '" + CFG_EXT + "'", content);
                su("[ -f '" + CFG_INT + "' ] && cat > '" + CFG_INT + "'", content);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        status.setText(w.code == 0 ? "Tersimpan"
                                                   : "Gagal menyimpan (kode " + w.code + ")");
                    }
                });
            }
        });
    }
}
