package com.kuro.companionctl;

import android.app.Activity;
import android.os.Bundle;
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
 */
public class MainActivity extends Activity {
    private static final String PKG = "com.klab.bleach";
    private static final String CFG_EXT = "/storage/emulated/0/Android/data/" + PKG + "/files/kuro_companion.cfg";
    private static final String CFG_INT = "/data/user/0/" + PKG + "/files/kuro_companion.cfg";

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private boolean loading = true;

    private TextView status;
    private Feature attackSpeed, moveSpeed, attackRange, skillNoCd;
    private Switch modeAdd;

    private class Feature {
        final Switch sw;
        final SeekBar bar;       // null for features without a multiplier
        final TextView valueText;

        Feature(LinearLayout parent, String title, boolean multiplier) {
            sw = new Switch(MainActivity.this);
            sw.setText(title);
            sw.setTextSize(18);
            sw.setPadding(0, dp(14), 0, dp(4));
            parent.addView(sw);
            if (multiplier) {
                valueText = new TextView(MainActivity.this);
                bar = new SeekBar(MainActivity.this);
                bar.setMax(40);          // 1.0x .. 5.0x in 0.1 steps
                bar.setProgress(5);      // default 1.5x
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

        float value() { return 1.0f + bar.getProgress() / 10.0f; }

        void refreshText() {
            if (valueText != null) valueText.setText(String.format(Locale.US, "Nilai: %.1fx", value()));
        }

        void setFromFile(float v) {
            if (bar == null) return;
            if (v > 1.0f) {
                sw.setChecked(true);
                int p = Math.round((v - 1.0f) * 10.0f);
                bar.setProgress(Math.max(0, Math.min(40, p)));
            } else {
                sw.setChecked(false);
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

        attackSpeed = new Feature(col, "Attack Speed", true);
        moveSpeed   = new Feature(col, "Move Speed", true);
        attackRange = new Feature(col, "Attack Range", true);
        skillNoCd   = new Feature(col, "Skill No Cooldown (eksperimental)", false);

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
                + "tutup dan buka game sekali. Hindari mode online (risiko banned).");
        note.setPadding(0, dp(20), 0, 0);
        col.addView(note);

        setContentView(scroll);
        loadAsync();
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

    private void applyFile(String text) {
        Map<String, String> m = new HashMap<String, String>();
        for (String line : text.split("\n")) {
            int hash = line.indexOf('#');
            if (hash >= 0) line = line.substring(0, hash);
            int eq = line.indexOf('=');
            if (eq < 0) continue;
            m.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
        }
        attackSpeed.setFromFile(num(m.get("attack_speed")));
        moveSpeed.setFromFile(num(m.get("move_speed")));
        attackRange.setFromFile(num(m.get("attack_range")));
        String cd = m.get("skill_no_cd");
        skillNoCd.sw.setChecked("1".equals(cd) || "true".equals(cd) || "on".equals(cd));
        String mode = m.get("attack_speed_mode");
        if (mode != null) modeAdd.setChecked(!"mul".equals(mode));
    }

    private static float num(String s) {
        try { return s == null ? 1.0f : Float.parseFloat(s); } catch (NumberFormatException e) { return 1.0f; }
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
        sb.append("skill_no_cd=").append(skillNoCd.sw.isChecked() ? "1" : "0").append('\n');
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
