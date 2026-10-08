package com.kuro.companionctl;

import android.app.Activity;
import android.os.Bundle;
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
    private Feature attackSpeed, moveSpeed, attackRange, unitySpeed, damageRate, skillNoCd, godMode, skipStory;
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
            sw = new Switch(MainActivity.this);
            sw.setText(title);
            sw.setTextSize(18);
            sw.setPadding(0, dp(14), 0, dp(4));
            parent.addView(sw);
            if (slider) {
                valueText = new TextView(MainActivity.this);
                bar = new SeekBar(MainActivity.this);
                bar.setMax(steps());
                bar.setProgress(Math.max(0, Math.min(steps(), Math.round((defaultValue - min) * 10.0f))));
                parent.addView(valueText);
                parent.addView(bar);
                refreshText();
                bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) { refreshText(); }
                    @Override public void onStartTrackingTouch(SeekBar s) { }
                    @Override public void onStopTrackingTouch(SeekBar s) { save(); }
                });
            } else {
                bar = null;
                valueText = null;
            }
            sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override public void onCheckedChanged(CompoundButton b, boolean checked) { save(); }
            });
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
                valueText.setText(String.format(Locale.US, "Nilai: %.1fx  (%.1fx - %.1fx)", value(), min, max));
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(20), dp(40), dp(20), dp(24));
        scroll.addView(col);

        TextView title = new TextView(this);
        title.setText("Kuro Companion");
        title.setTextSize(24);
        col.addView(title);

        status = new TextView(this);
        status.setText("Memeriksa root...");
        status.setPadding(0, dp(8), 0, dp(8));
        col.addView(status);

        attackSpeed = new Feature(col, "Attack Speed", true, 1.0f, DEFAULT_MAX, 1.5f);
        moveSpeed   = new Feature(col, "Move Speed", true, 1.0f, DEFAULT_MAX, 1.5f);
        attackRange = new Feature(col, "Attack Range", true, 1.0f, DEFAULT_MAX, 1.5f);
        unitySpeed  = new Feature(col, "Unity Speed", true, 0.1f, DEFAULT_MAX, 2.0f);
        damageRate  = new Feature(col, "Damage Rate Up", true, 1.0f, DEFAULT_MAX, 1.5f);
        skillNoCd   = new Feature(col, "Skill No Cooldown (eksperimental)", false, 1.0f, DEFAULT_MAX, 1.0f);
        godMode     = new Feature(col, "God Mode (karakter pemain)", false, 1.0f, DEFAULT_MAX, 1.0f);
        skipStory    = new Feature(col, "Skip Story (dialog)", false, 1.0f, DEFAULT_MAX, 1.0f);

        modeAdd = new Switch(this);
        modeAdd.setText("Mode kecepatan: Add (mati = Multiply)");
        modeAdd.setChecked(true);
        modeAdd.setPadding(0, dp(20), 0, dp(4));
        modeAdd.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean checked) { save(); }
        });
        col.addView(modeAdd);

        TextView note = new TextView(this);
        note.setText("Perubahan terbaca game dalam sekitar 2 detik. Jika stage di file belum 3, "
                + "tutup dan buka game sekali. Batas slider diubah lewat baris max_* di kuro_companion.cfg "
                + "(contoh max_unity_speed=20.0, lalu buka ulang aplikasi ini). "
                + "Jangan menyalakan fitur yang sama (God Mode, Unity Speed, Damage Rate) di sini sekaligus di menu Kuro. "
                + "Hindari mode online (risiko banned).");
        note.setPadding(0, dp(20), 0, 0);
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
                            status.setText("Root ditolak atau tidak tersedia. Beri izin root untuk aplikasi ini di Magisk.");
                        } else {
                            status.setText("Root OK. Mengubah: " + CFG_EXT);
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
        skipStory.sw.setChecked(flag(m.get("skip_story")));
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
        sb.append("skip_story=").append(skipStory.sw.isChecked() ? "1" : "0").append('\n');
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
                        status.setText(w.code == 0 ? "Tersimpan. Game membacanya dalam ~2 detik."
                                                   : "Gagal menulis file (kode " + w.code + ").");
                    }
                });
            }
        });
    }
}
