package com.kuro.companionctl;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;

/**
 * License screen. The key is checked online by the license server, which returns a signed, short-lived token.
 * The token is stored in the app and copied (through root) into the game's folders, where the Kuro Companion
 * module verifies its signature offline. Until a token exists the module stays completely idle.
 */
final class LicenseGate {
    interface Callback { void onLicensed(); }

    private static final String PACKAGE = "com.klab.bleach";
    private static final String TOKEN_EXT = "/storage/emulated/0/Android/data/" + PACKAGE + "/files/kuro_license.dat";
    private static final String TOKEN_INT = "/data/user/0/" + PACKAGE + "/files/kuro_license.dat";
    private static final String PREFS = "license";
    private static final long RENEW_BELOW_SEC = 24 * 3600;

    private LicenseGate() { }

    private static final class Outcome {
        boolean ok;
        boolean network;     // true = could not reach the server (not a verdict on the key)
        String token;
        String error;
    }

    // ------------------------------------------------------------------ entry points

    /** Runs the licence check at startup; onLicensed is called on the UI thread once the app may be used. */
    static void run(final Activity a, final Callback cb) {
        final SharedPreferences p = a.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (LicenseConfig.URL.isEmpty() || LicenseConfig.ANON_KEY.isEmpty()) {
            showDialog(a, p, cb, LicenseToken.message("not_configured"), true);
            return;
        }
        final String key = p.getString("key", "");
        final String token = p.getString("token", "");
        if (key.isEmpty()) {
            showDialog(a, p, cb, null, false);
            return;
        }
        new Thread(new Runnable() {
            @Override public void run() {
                final Outcome o = activate(a, key);
                if (o.ok) {
                    save(p, key, o.token);
                    deliver(o.token);
                    ui(a, new Runnable() { @Override public void run() { cb.onLicensed(); } });
                } else if (o.network && LicenseToken.isFresh(token, System.currentTimeMillis() / 1000, 0)) {
                    ui(a, new Runnable() { @Override public void run() { cb.onLicensed(); } });   // offline grace
                } else {
                    if (!o.network) p.edit().remove("key").remove("token").apply();            // key was rejected
                    final String msg = LicenseToken.message(o.error);
                    ui(a, new Runnable() { @Override public void run() { showDialog(a, p, cb, msg, false); } });
                }
            }
        }).start();
    }

    /** Silently renews the token in the background when it is close to expiring (call from onResume). */
    static void refresh(final Activity a) {
        if (LicenseConfig.URL.isEmpty()) return;
        final SharedPreferences p = a.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        final String key = p.getString("key", "");
        if (key.isEmpty() || LicenseToken.isFresh(p.getString("token", ""), System.currentTimeMillis() / 1000, RENEW_BELOW_SEC)) return;
        new Thread(new Runnable() {
            @Override public void run() {
                Outcome o = activate(a, key);
                if (o.ok) { save(p, key, o.token); deliver(o.token); }
            }
        }).start();
    }

    // ------------------------------------------------------------------ dialog

    private static void showDialog(final Activity a, final SharedPreferences p, final Callback cb,
                                   String initialMessage, boolean fatal) {
        final Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setCancelable(false);
        d.setCanceledOnTouchOutside(false);

        final LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(a, 24), Ui.dp(a, 24), Ui.dp(a, 24), Ui.dp(a, 20));
        box.setBackground(Ui.rounded(Ui.CARD, Ui.dp(a, 30)));

        TextView title = new TextView(a);
        title.setText("Aktivasi Lisensi");
        title.setTextColor(Ui.TEXT);
        title.setTextSize(20);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        box.addView(title);

        TextView sub = new TextView(a);
        sub.setText("Masukkan key lisensi Anda untuk memakai aplikasi dan mengaktifkan modul.");
        sub.setTextColor(Ui.MUTED);
        sub.setTextSize(13);
        sub.setPadding(0, Ui.dp(a, 6), 0, 0);
        box.addView(sub);

        final EditText input = new EditText(a);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setSingleLine(true);
        input.setHint("KURO-XXXXXXXXXXXXXXXX");
        input.setHintTextColor(Ui.MUTED);
        input.setTextColor(Ui.TEXT);
        input.setTextSize(16);
        input.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        input.setGravity(Gravity.CENTER);
        input.setPadding(Ui.dp(a, 14), Ui.dp(a, 14), Ui.dp(a, 14), Ui.dp(a, 14));
        final int fieldColor = 0xFFECE8DC;
        input.setBackground(Ui.rounded(fieldColor, Ui.dp(a, 18)));
        input.setEnabled(!fatal);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ip.setMargins(0, Ui.dp(a, 16), 0, Ui.dp(a, 8));
        box.addView(input, ip);

        final TextView status = new TextView(a);
        status.setTextSize(13);
        status.setTextColor(Ui.DANGER);
        status.setText(initialMessage == null ? "" : initialMessage);
        status.setPadding(0, 0, 0, Ui.dp(a, 12));
        box.addView(status);

        LinearLayout buttons = new LinearLayout(a);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        box.addView(buttons, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        final TextView exit = button(a, "Keluar", Ui.TEXT, 0x00000000);
        final TextView go = button(a, "Aktifkan", Ui.WHITE, Ui.SAGE);
        buttons.addView(exit, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        if (!fatal) {
            LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            gp.setMargins(Ui.dp(a, 10), 0, 0, 0);
            buttons.addView(go, gp);
        }

        exit.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { d.dismiss(); a.finish(); }
        });
        go.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                final String key = LicenseToken.normalizeKey(input.getText().toString());
                if (!LicenseToken.looksLikeKey(key)) {
                    status.setTextColor(Ui.DANGER);
                    status.setText(LicenseToken.message("invalid_input"));
                    Ui.shake(box, Ui.dp(a, 10));
                    return;
                }
                go.setClickable(false);
                go.setAlpha(0.5f);
                status.setTextColor(Ui.MUTED);
                status.setText("Memeriksa key...");
                new Thread(new Runnable() {
                    @Override public void run() {
                        final Outcome o = activate(a, key);
                        if (o.ok) {
                            save(p, key, o.token);
                            deliver(o.token);
                        }
                        ui(a, new Runnable() {
                            @Override public void run() {
                                if (o.ok) {
                                    d.dismiss();
                                    cb.onLicensed();
                                } else {
                                    status.setTextColor(Ui.DANGER);
                                    status.setText(LicenseToken.message(o.error));
                                    Ui.shake(box, Ui.dp(a, 10));
                                    go.setClickable(true);
                                    go.setAlpha(1f);
                                }
                            }
                        });
                    }
                }).start();
            }
        });

        d.setContentView(box, new LinearLayout.LayoutParams(Ui.dp(a, 320), LinearLayout.LayoutParams.WRAP_CONTENT));
        Window win = d.getWindow();
        win.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        win.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        box.setScaleX(0.85f);
        box.setScaleY(0.85f);
        box.setAlpha(0f);
        d.show();
        box.animate().scaleX(1f).scaleY(1f).alpha(1f).setStartDelay(0).setDuration(300)
                .setInterpolator(new OvershootInterpolator(1.2f)).start();
    }

    private static TextView button(Context c, String label, int textColor, int bgColor) {
        TextView b = new TextView(c);
        b.setText(label);
        b.setTextColor(textColor);
        b.setTextSize(16);
        b.setGravity(Gravity.CENTER);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setPadding(Ui.dp(c, 16), Ui.dp(c, 14), Ui.dp(c, 16), Ui.dp(c, 14));
        b.setBackground(Ui.rounded(bgColor, Ui.dp(c, 22)));
        b.setClickable(true);
        Ui.pressable(b);
        return b;
    }

    // ------------------------------------------------------------------ storage and delivery

    private static void save(SharedPreferences p, String key, String token) {
        p.edit().putString("key", key).putString("token", token).apply();
    }

    private static void ui(Activity a, Runnable r) { a.runOnUiThread(r); }

    private static String deviceId(Activity a) {
        String id = LicenseToken.sanitizeDeviceId(Settings.Secure.getString(a.getContentResolver(), Settings.Secure.ANDROID_ID));
        if (id != null) return id;
        SharedPreferences p = a.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String saved = LicenseToken.sanitizeDeviceId(p.getString("device", null));
        if (saved != null) return saved;
        String fresh = UUID.randomUUID().toString();
        p.edit().putString("device", fresh).apply();
        return fresh;
    }

    /** Copies the token next to the game's other files so the module can read it. Needs root. */
    private static void deliver(String token) {
        // The token only contains [A-Za-z0-9|+/=\n-], so it is safe to pass on stdin and in no shell command line.
        su("mkdir -p \"$(dirname '" + TOKEN_EXT + "')\"; [ -f '" + TOKEN_EXT + "' ] || { : > '" + TOKEN_EXT + "'; chmod 644 '" + TOKEN_EXT + "'; }", null);
        su("cat > '" + TOKEN_EXT + "'", token);
        su("[ -d \"$(dirname '" + TOKEN_INT + "')\" ] && { cat > '" + TOKEN_INT + "' && chmod 644 '" + TOKEN_INT + "'; }", token);
    }

    private static int su(String cmd, String stdin) {
        try {
            Process proc = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            OutputStream os = proc.getOutputStream();
            if (stdin != null) os.write(stdin.getBytes("UTF-8"));
            os.flush();
            os.close();
            drain(proc.getInputStream());
            drain(proc.getErrorStream());
            return proc.waitFor();
        } catch (Exception e) {
            return -1;
        }
    }

    private static String drain(InputStream in) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line).append('\n');
        return sb.toString();
    }

    // ------------------------------------------------------------------ server call

    private static Outcome activate(Activity a, String key) {
        Outcome o = new Outcome();
        String body = LicenseToken.activateBody(key, deviceId(a));
        if (body == null) { o.error = "invalid_input"; return o; }
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(LicenseConfig.URL + "/functions/v1/license").openConnection();
            c.setRequestMethod("POST");
            c.setConnectTimeout(8000);
            c.setReadTimeout(10000);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("apikey", LicenseConfig.ANON_KEY);
            c.setRequestProperty("Authorization", "Bearer " + LicenseConfig.ANON_KEY);
            OutputStream os = c.getOutputStream();
            os.write(body.getBytes("UTF-8"));
            os.close();
            int code = c.getResponseCode();
            if (code == 401 || code == 404) { o.error = "bad_config"; return o; }
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            JSONObject j = new JSONObject(drain(in));
            if (j.optBoolean("ok", false)) {
                String token = j.optString("token", "");
                if (LicenseToken.expiry(token) <= 0) { o.error = "server"; return o; }
                o.ok = true;
                o.token = token;
            } else {
                o.error = j.optString("error", "server");
            }
        } catch (java.io.IOException e) {
            o.network = true;
            o.error = "network";
        } catch (Exception e) {
            o.error = "server";
        } finally {
            if (c != null) c.disconnect();
        }
        return o;
    }
}
