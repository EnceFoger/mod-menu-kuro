package com.kuro.companionctl;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Remote control for the Kuro Companion module. Every change is written to kuro_companion.cfg through root
 * (su); the module re-reads that file about every 2 seconds. Slider limits are edited from the gear icon
 * and saved as the max_* lines of the same file.
 */
public class MainActivity extends Activity {
    private static final String PKG = "com.klab.bleach";
    private static final String CFG_EXT = "/storage/emulated/0/Android/data/" + PKG + "/files/kuro_companion.cfg";
    private static final String CFG_INT = "/data/user/0/" + PKG + "/files/kuro_companion.cfg";

    private static final float DEFAULT_MAX = 5.0f;
    private static final float LIMIT_MIN = 1.0f;     // a configured maximum must be at least this
    private static final float LIMIT_MAX = 100.0f;   // ...and at most this (Unity caps timeScale at 100)

    private static final int NAV_BTN = 52, NAV_PAD = 8, NAV_GAP = 10;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private boolean loading = true;
    private boolean rootOn = false;

    private SwitchView rootSwitch;
    private TextView sectionTitle;
    private final ScrollView[] pages = new ScrollView[2];
    private final LinearLayout[] lists = new LinearLayout[2];
    private int tab = 0;
    private View indicator, navBar, header;
    private final IconView[] navIcons = new IconView[2];
    private FeatureCard cAttack, cMove, cRange, cUnity, cGame, cDamage, cCd, cGod, cSkip, cOverlay, cMode;
    private final FeatureCard[] sliderCards = new FeatureCard[6];
    private final String[] sliderKeys = {"attack_speed", "move_speed", "attack_range", "unity_speed", "damage_rate", "game_speed"};

    // ---------------------------------------------------------------- UI construction

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("kuro_ui", MODE_PRIVATE);

        Window w = getWindow();
        w.setBackgroundDrawable(new ColorDrawable(Ui.BG));
        w.setStatusBarColor(Ui.BG);
        w.setNavigationBarColor(Ui.BG);
        int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        w.getDecorView().setSystemUiVisibility(flags);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        root.addView(column, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        buildHeader(column);

        FrameLayout content = new FrameLayout(this);
        column.addView(content, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        buildPages(content);

        buildNav(root);
        setContentView(root);

        setCardsActive(false);
        playEntrance();

        // License gate first: nothing touches root or the game's files until the key is accepted.
        LicenseGate.run(this, new LicenseGate.Callback() {
            @Override public void onLicensed() {
                if (prefs.getBoolean("root_on", true)) requestRoot(true);   // rooted devices: switch on by default
            }
        });
    }

    private int dp(float v) { return Ui.dp(this, v); }

    private void buildHeader(LinearLayout column) {
        LinearLayout h = new LinearLayout(this);
        header = h;
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setPadding(dp(24), dp(22), dp(20), dp(12));
        column.addView(h, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        h.addView(titles, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView app = new TextView(this);
        app.setText("Kuro Control");
        app.setTextColor(Ui.TEXT);
        app.setTextSize(28);
        app.setTypeface(Typeface.DEFAULT_BOLD);
        titles.addView(app);

        sectionTitle = new TextView(this);
        sectionTitle.setText("Slider");
        sectionTitle.setTextColor(Ui.SAGE);
        sectionTitle.setTextSize(17);
        sectionTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        titles.addView(sectionTitle);

        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(16), dp(8), dp(10), dp(8));
        pill.setBackground(Ui.rounded(Ui.CARD, dp(26)));
        pill.setElevation(dp(2));
        h.addView(pill, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView rootLabel = new TextView(this);
        rootLabel.setText("Root");
        rootLabel.setTextColor(Ui.TEXT);
        rootLabel.setTextSize(16);
        rootLabel.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(12), 0);
        pill.addView(rootLabel, lp);

        rootSwitch = new SwitchView(this);
        pill.addView(rootSwitch, new LinearLayout.LayoutParams(dp(52), dp(30)));
        rootSwitch.setListener(new SwitchView.Listener() {
            @Override public void onChanged(boolean checked) {
                if (checked) {
                    requestRoot(false);
                } else {
                    rootOn = false;
                    prefs.edit().putBoolean("root_on", false).apply();
                    setCardsActive(false);
                }
            }
        });
    }

    private void buildPages(FrameLayout content) {
        for (int i = 0; i < 2; i++) {
            ScrollView sv = new ScrollView(this);
            sv.setVerticalScrollBarEnabled(false);
            sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
            LinearLayout list = new LinearLayout(this);
            list.setOrientation(LinearLayout.VERTICAL);
            list.setPadding(dp(20), dp(8), dp(20), dp(120));
            sv.addView(list, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
            content.addView(sv, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            pages[i] = sv;
            lists[i] = list;
        }
        pages[1].setVisibility(View.GONE);

        cAttack = addSlider("Attack Speed", 1.0f, 1.5f, 0);
        cMove = addSlider("Move Speed", 1.0f, 1.5f, 1);
        cRange = addSlider("Attack Range", 1.0f, 1.5f, 2);
        cUnity = addSlider("Unity Speed", 0.1f, 2.0f, 3);
        cGame = addSlider("Game Speed (battle)", 0.1f, 2.0f, 5);   // index 5 = "game_speed" in sliderKeys
        cDamage = addSlider("Damage Rate Up", 1.0f, 1.5f, 4);

        cCd = addToggle("Skill No Cooldown");
        cGod = addToggle("God Mode");
        cSkip = addToggle("Skip Story");
        cOverlay = addToggle("Overlay Poin Event");
        cMode = addToggle("Mode Add Kecepatan");
        cMode.setOn(true, false);

        final FeatureCard.Listener plain = new FeatureCard.Listener() {
            @Override public void onChanged() { if (!loading) save(); }
            @Override public void onOpenSettings() { }
        };
        cCd.setListener(plain);
        cGod.setListener(plain);
        cSkip.setListener(plain);
        cMode.setListener(plain);
        cOverlay.setListener(new FeatureCard.Listener() {
            @Override public void onChanged() {
                if (loading) return;
                if (cOverlay.isOn() && !Settings.canDrawOverlays(MainActivity.this)) {
                    cOverlay.setOn(false, true);
                    startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
                    toast("Izinkan tampil di atas aplikasi lain, lalu nyalakan lagi");
                    return;
                }
                save();
                setOverlayRunning(cOverlay.isOn());
            }
            @Override public void onOpenSettings() { }
        });
    }

    private FeatureCard addSlider(String title, float min, float def, final int index) {
        final FeatureCard c = new FeatureCard(this, title, true, min, DEFAULT_MAX, def);
        c.setListener(new FeatureCard.Listener() {
            @Override public void onChanged() { if (!loading) save(); }
            @Override public void onOpenSettings() { showMaxDialog(c); }
        });
        sliderCards[index] = c;
        addCard(0, c);
        return c;
    }

    private FeatureCard addToggle(String title) {
        FeatureCard c = new FeatureCard(this, title, false, 1.0f, DEFAULT_MAX, 1.0f);
        addCard(1, c);
        return c;
    }

    private void addCard(int page, FeatureCard c) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(14));
        lists[page].addView(c, lp);
    }

    private void buildNav(FrameLayout root) {
        FrameLayout nav = new FrameLayout(this);
        navBar = nav;
        nav.setBackground(Ui.rounded(Ui.CARD, dp(36)));
        nav.setElevation(dp(10));
        int btn = dp(NAV_BTN), pad = dp(NAV_PAD), gap = dp(NAV_GAP);

        indicator = new View(this);
        indicator.setBackground(Ui.rounded(Ui.DARK, dp(26)));
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(btn, btn);
        ip.setMargins(pad, pad, 0, 0);
        nav.addView(indicator, ip);

        for (int i = 0; i < 2; i++) {
            final int index = i;
            IconView icon = new IconView(this, i == 0 ? IconView.SLIDERS : IconView.TOGGLES, i == 0 ? Ui.WHITE : Ui.MUTED);
            icon.setPadding(dp(13), dp(13), dp(13), dp(13));
            Ui.pressable(icon);
            icon.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { selectTab(index); }
            });
            FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(btn, btn);
            p.setMargins(pad + i * (btn + gap), pad, 0, 0);
            nav.addView(icon, p);
            navIcons[i] = icon;
        }
        FrameLayout.LayoutParams np = new FrameLayout.LayoutParams(pad * 2 + btn * 2 + gap, pad * 2 + btn);
        np.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        np.setMargins(0, 0, 0, dp(22));
        root.addView(nav, np);
    }

    // ---------------------------------------------------------------- animation

    private void playEntrance() {
        header.setAlpha(0f);
        header.setTranslationY(-dp(24));
        header.animate().alpha(1f).translationY(0f).setStartDelay(0).setDuration(480)
                .setInterpolator(new DecelerateInterpolator(1.8f)).start();
        navBar.setTranslationY(dp(110));
        navBar.animate().translationY(0f).setStartDelay(260).setDuration(620)
                .setInterpolator(new OvershootInterpolator(1.1f)).start();
        animateCards(lists[0]);
    }

    private void animateCards(LinearLayout list) {
        for (int i = 0; i < list.getChildCount(); i++) {
            View v = list.getChildAt(i);
            v.setTranslationY(dp(40));
            float target = v.isEnabled() ? 1f : 0.45f;
            v.setAlpha(0f);
            v.animate().alpha(target).translationY(0f).setStartDelay(90L + 70L * i).setDuration(460)
                    .setInterpolator(new DecelerateInterpolator(1.7f)).start();
        }
    }

    private void selectTab(int idx) {
        if (idx == tab) return;
        final View out = pages[tab];
        final View in = pages[idx];
        int dir = idx > tab ? 1 : -1;
        tab = idx;
        out.animate().cancel();
        in.animate().cancel();

        in.setVisibility(View.VISIBLE);
        in.setAlpha(0f);
        in.setTranslationX(dir * dp(70));
        in.animate().alpha(1f).translationX(0f).setStartDelay(70).setDuration(380)
                .setInterpolator(new DecelerateInterpolator(1.8f)).start();
        out.animate().alpha(0f).translationX(-dir * dp(70)).setStartDelay(0).setDuration(220)
                .setInterpolator(new AccelerateInterpolator()).withEndAction(new Runnable() {
                    @Override public void run() {
                        if (out != pages[tab]) {
                            out.setVisibility(View.GONE);
                            out.setTranslationX(0f);
                        }
                    }
                }).start();
        animateCards(lists[idx]);

        int btn = dp(NAV_BTN), gap = dp(NAV_GAP);
        indicator.animate().translationX(idx * (btn + gap)).setStartDelay(0).setDuration(420)
                .setInterpolator(new OvershootInterpolator(1.15f)).start();
        animateNavColors(idx);
        swapTitle(idx == 0 ? "Slider" : "Toggle");
    }

    private void animateNavColors(final int selected) {
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(300);
        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator anim) {
                float t = (Float) anim.getAnimatedValue();
                for (int i = 0; i < 2; i++) {
                    boolean on = i == selected;
                    navIcons[i].setColor(on ? Ui.blend(Ui.MUTED, Ui.WHITE, t) : Ui.blend(Ui.WHITE, Ui.MUTED, t));
                }
            }
        });
        a.start();
    }

    private void swapTitle(final String text) {
        sectionTitle.animate().cancel();
        sectionTitle.animate().alpha(0f).translationY(-dp(8)).setStartDelay(0).setDuration(120).withEndAction(new Runnable() {
            @Override public void run() {
                sectionTitle.setText(text);
                sectionTitle.setTranslationY(dp(8));
                sectionTitle.animate().alpha(1f).translationY(0f).setStartDelay(0).setDuration(240)
                        .setInterpolator(new DecelerateInterpolator(1.5f)).start();
            }
        }).start();
    }

    // ---------------------------------------------------------------- root

    private void setCardsActive(boolean active) {
        for (FeatureCard c : allCards()) c.setActive(active);
    }

    private FeatureCard[] allCards() {
        return new FeatureCard[]{cAttack, cMove, cRange, cUnity, cGame, cDamage, cCd, cGod, cSkip, cOverlay, cMode};
    }

    private void requestRoot(final boolean automatic) {
        io.execute(new Runnable() {
            @Override public void run() {
                final Result r = su("id", null);
                final boolean ok = r.code == 0 && r.out.contains("uid=0");
                final boolean noSu = r.noSu;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        rootOn = ok;
                        rootSwitch.setChecked(ok, true);
                        setCardsActive(ok);
                        if (ok) {
                            prefs.edit().putBoolean("root_on", true).apply();
                            loadAsync();
                        } else if (!automatic) {
                            toast(noSu ? "Device belum di-root" : "Izin root ditolak");
                        }
                    }
                });
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        LicenseGate.refresh(this);   // renews the licence token in the background when it is close to expiring
        if (rootOn) loadAsync();   // picks up edits made to the file while the app was in the background
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private void setOverlayRunning(boolean on) {
        Intent i = new Intent(this, OverlayService.class);
        if (on) {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        } else {
            stopService(i);
        }
    }

    private static class Result { int code = -1; String out = ""; boolean noSu; }

    private Result su(String cmd, String stdin) {
        Result r = new Result();
        Process p;
        try {
            p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
        } catch (IOException e) {
            r.noSu = true;          // the su binary does not exist: device is not rooted
            r.out = String.valueOf(e);
            return r;
        }
        try {
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

    private static String readAll(InputStream in) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line).append('\n');
        return sb.toString();
    }

    // ---------------------------------------------------------------- max-value dialog

    private void showMaxDialog(final FeatureCard card) {
        final Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(24), dp(24), dp(20));
        box.setBackground(Ui.rounded(Ui.CARD, dp(30)));

        TextView t = new TextView(this);
        t.setText("Maks. " + card.getTitle());
        t.setTextColor(Ui.TEXT);
        t.setTextSize(19);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        box.addView(t);

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(String.format(Locale.US, "%.1f", card.getMaxValue()));
        input.setSelectAllOnFocus(true);
        input.setHint("1.0 - 100.0");
        input.setTextColor(Ui.TEXT);
        input.setHintTextColor(Ui.MUTED);
        input.setTextSize(22);
        input.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        input.setGravity(Gravity.CENTER);
        input.setPadding(dp(16), dp(14), dp(16), dp(14));
        final int fieldColor = 0xFFECE8DC;
        input.setBackground(Ui.rounded(fieldColor, dp(18)));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ip.setMargins(0, dp(18), 0, dp(20));
        box.addView(input, ip);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        box.addView(buttons, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView cancel = dialogButton("Batal", Ui.TEXT, 0x00000000);
        TextView save = dialogButton("Simpan", Ui.WHITE, Ui.SAGE);
        buttons.addView(cancel, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        sp.setMargins(dp(10), 0, 0, 0);
        buttons.addView(save, sp);

        cancel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { d.dismiss(); }
        });
        save.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Float parsed = parseDialogValue(input.getText().toString());
                if (parsed == null) {
                    Ui.shake(box, dp(10));
                    input.setBackground(Ui.rounded(Ui.DANGER, dp(18)));
                    input.postDelayed(new Runnable() {
                        @Override public void run() { input.setBackground(Ui.rounded(fieldColor, dp(18))); }
                    }, 450);
                    return;
                }
                card.setMaxValue(parsed);
                d.dismiss();
                if (!loading) save();
            }
        });

        d.setContentView(box, new LinearLayout.LayoutParams(dp(310), LinearLayout.LayoutParams.WRAP_CONTENT));
        Window win = d.getWindow();
        win.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        win.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        box.setScaleX(0.82f);
        box.setScaleY(0.82f);
        box.setAlpha(0f);
        d.show();
        box.animate().scaleX(1f).scaleY(1f).alpha(1f).setStartDelay(0).setDuration(320)
                .setInterpolator(new OvershootInterpolator(1.3f)).start();
    }

    private TextView dialogButton(String label, int textColor, int bgColor) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setTextColor(textColor);
        b.setTextSize(16);
        b.setGravity(Gravity.CENTER);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setPadding(dp(16), dp(14), dp(16), dp(14));
        b.setBackground(Ui.rounded(bgColor, dp(22)));
        b.setClickable(true);
        Ui.pressable(b);
        return b;
    }

    /** A valid limit is a finite number in 1.0 .. 100.0 (comma accepted); anything else is rejected. */
    static Float parseDialogValue(String raw) {
        try {
            float f = Float.parseFloat(raw.trim().replace(',', '.'));
            if (Float.isNaN(f) || Float.isInfinite(f) || f < LIMIT_MIN || f > LIMIT_MAX) return null;
            return Math.round(f * 10f) / 10f;
        } catch (Exception e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- load / save the cfg file

    private void loadAsync() {
        loading = true;
        io.execute(new Runnable() {
            @Override public void run() {
                final Result cfg = su("cat '" + CFG_EXT + "' 2>/dev/null || cat '" + CFG_INT + "' 2>/dev/null", null);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (cfg.code == 0 || !cfg.out.isEmpty()) applyFile(cfg.out);
                        loading = false;
                        if (cOverlay.isOn() && Settings.canDrawOverlays(MainActivity.this)) setOverlayRunning(true);
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
        for (int i = 0; i < sliderCards.length; i++) {
            FeatureCard c = sliderCards[i];
            String key = sliderKeys[i];
            c.setMaxValue(m.containsKey("max_" + key) ? parseMax(m.get("max_" + key)) : DEFAULT_MAX);
            float v = num(m.get(key));
            boolean on = Math.abs(v - 1.0f) > 0.001f && v >= c.getMinValue();
            if (on) c.setValue(Math.max(c.getMinValue(), Math.min(c.getMaxValue(), v)));
            else if (prefs.contains("val_" + key)) c.setValue(prefs.getFloat("val_" + key, c.getValue()));
            c.setOn(on, true);
        }
        cCd.setOn(flag(m.get("skill_no_cd")), true);
        cGod.setOn(flag(m.get("god_mode")), true);
        cSkip.setOn(flag(m.get("skip_story")), true);
        cOverlay.setOn(flag(m.get("event_points")), true);
        String mode = m.get("attack_speed_mode");
        if (mode != null) cMode.setOn(!"mul".equals(mode), true);
    }

    private String buildConfig() {
        String mode = cMode.isOn() ? "add" : "mul";
        StringBuilder sb = new StringBuilder();
        sb.append("stage=3\n");
        for (int i = 0; i < sliderCards.length; i++) {
            FeatureCard c = sliderCards[i];
            String key = sliderKeys[i];
            sb.append(String.format(Locale.US, "%s=%.1f\n", key, c.isOn() ? c.getValue() : 1.0f));
            if (key.equals("attack_speed") || key.equals("move_speed")) sb.append(key).append("_mode=").append(mode).append('\n');
            if (key.equals("damage_rate")) sb.append("damage_rate_mode=add\n");
        }
        sb.append("skill_no_cd=").append(cCd.isOn() ? "1" : "0").append('\n');
        sb.append("god_mode=").append(cGod.isOn() ? "1" : "0").append('\n');
        sb.append("event_points=").append(cOverlay.isOn() ? "1" : "0").append('\n');
        sb.append("skip_story=").append(cSkip.isOn() ? "1" : "0").append('\n');
        for (int i = 0; i < sliderCards.length; i++)
            sb.append(String.format(Locale.US, "max_%s=%.1f\n", sliderKeys[i], sliderCards[i].getMaxValue()));
        return sb.toString();
    }

    private void save() {
        if (loading || !rootOn) return;
        final String content = buildConfig();
        SharedPreferences.Editor ed = prefs.edit();
        for (int i = 0; i < sliderCards.length; i++) ed.putFloat("val_" + sliderKeys[i], sliderCards[i].getValue());
        ed.apply();
        io.execute(new Runnable() {
            @Override public void run() {
                // Create the file if missing (readable by the game), then overwrite in place so the
                // owner and permissions of an existing file are kept.
                su("mkdir -p \"$(dirname '" + CFG_EXT + "')\"; [ -f '" + CFG_EXT + "' ] || { : > '" + CFG_EXT + "'; chmod 666 '" + CFG_EXT + "'; }", null);
                final Result w = su("cat > '" + CFG_EXT + "'", content);
                su("[ -f '" + CFG_INT + "' ] && cat > '" + CFG_INT + "'", content);
                if (w.code != 0) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() { toast("Gagal menulis file"); }
                    });
                }
            }
        });
    }
}
