package com.kuro.companionctl;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Floating, touch-through overlay that shows the event points the Kuro Companion module recorded for the last
 * finished quest. It only reads kuro_companion_points.txt (through root) and displays it; it never writes to
 * the game.
 */
public class OverlayService extends Service {
    private static final String PKG = "com.klab.bleach";
    private static final String PTS_EXT = "/storage/emulated/0/Android/data/" + PKG + "/files/kuro_companion_points.txt";
    private static final String PTS_INT = "/data/user/0/" + PKG + "/files/kuro_companion_points.txt";
    private static final String CHANNEL = "kuro_overlay";
    private static final long POLL_MS = 2000;
    private static final long FLASH_MS = 6000;

    private WindowManager wm;
    private TextView view;
    private Handler ui;
    private volatile boolean running;
    private long lastSeq = Long.MIN_VALUE;
    private long flashUntil = 0;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startAsForeground();
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY; }
        if (ui == null) ui = new Handler(Looper.getMainLooper());
        if (view == null) createOverlay();
        if (!running) { running = true; startPolling(); }
        return START_STICKY;
    }

    private void startAsForeground() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Kuro overlay", NotificationManager.IMPORTANCE_LOW));
            b = new Notification.Builder(this, CHANNEL);
        } else {
            b = new Notification.Builder(this);
        }
        b.setContentTitle("Kuro Companion").setContentText("Overlay poin event aktif")
                .setSmallIcon(android.R.drawable.ic_dialog_info);
        Notification n = b.build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, 0x40000000);   // FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        else startForeground(1, n);
    }

    private void createOverlay() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        view = new TextView(this);
        view.setTextColor(Color.WHITE);
        view.setTextSize(12);
        view.setBackgroundColor(Color.argb(170, 0, 0, 0));
        int pad = Math.round(8 * getResources().getDisplayMetrics().density);
        view.setPadding(pad, pad, pad, pad);
        view.setText("EVENT POINT\nMenunggu quest event selesai...");

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                                            : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE      // touches pass through to the game
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = 16;
        lp.y = 120;
        wm.addView(view, lp);
    }

    private void startPolling() {
        Thread t = new Thread(new Runnable() {
            @Override public void run() {
                while (running) {
                    final String[] shown = render(readPoints());
                    final int color = Color.parseColor(shown[1]);
                    ui.post(new Runnable() {
                        @Override public void run() {
                            if (view != null) { view.setText(shown[0]); view.setTextColor(color); }
                        }
                    });
                    try { Thread.sleep(POLL_MS); } catch (InterruptedException e) { return; }
                }
            }
        });
        t.setDaemon(true);
        t.start();
    }

    /** null = root denied/unavailable, "" = no file yet. */
    private String readPoints() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c",
                    "cat '" + PTS_EXT + "' 2>/dev/null || cat '" + PTS_INT + "' 2>/dev/null"});
            p.getOutputStream().close();
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            p.waitFor();
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /** Returns {text, "#RRGGBB"}; green for a few seconds after the points changed. */
    private String[] render(String raw) {
        if (raw == null) return new String[]{"EVENT POINT\nRoot ditolak", "#FF8080"};
        Map<String, String> m = new HashMap<String, String>();
        for (String line : raw.split("\n")) {
            int eq = line.indexOf('=');
            if (eq > 0) m.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
        }
        if (!m.containsKey("seq")) return new String[]{"EVENT POINT\nMenunggu quest event selesai...", "#FFFFFF"};
        long seq = num(m.get("seq")), gained = num(m.get("gained")), total = num(m.get("total"));
        long sessionPoints = num(m.get("session_points")), sessionQuests = num(m.get("session_quests"));
        long now = System.currentTimeMillis();
        if (lastSeq != Long.MIN_VALUE && seq != lastSeq) flashUntil = now + FLASH_MS;
        lastSeq = seq;
        NumberFormat nf = NumberFormat.getInstance(Locale.US);
        String text = "EVENT POINT\nTerakhir: +" + nf.format(gained) + "\nTotal: " + nf.format(total)
                + "\nSesi: +" + nf.format(sessionPoints) + " (" + sessionQuests + " quest)";
        return new String[]{text, now < flashUntil ? "#7CFC00" : "#FFFFFF"};
    }

    private static long num(String s) {
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return 0; }
    }

    @Override
    public void onDestroy() {
        running = false;
        if (wm != null && view != null) {
            try { wm.removeView(view); } catch (Exception e) { /* already gone */ }
        }
        view = null;
        super.onDestroy();
    }
}
