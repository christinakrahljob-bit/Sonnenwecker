package de.christina.sonnenwecker;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Weck-Bildschirm: Guten Morgen, Wetter, Radio, „Ich bin wach“. Immer dunkel, damit er nicht blendet. */
public class AlarmActivity extends Activity {
    private Ui ui;
    private Prefs prefs;
    private long target;
    private TextView weatherToday, weatherMidday, weatherRain, radioTitle, radioDetail, backupText;
    private LinearLayout middayRow, backupRow;
    private View radioOff;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            update();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        ui = new Ui(this, true);
        ui.applyWindow();
        prefs = new Prefs(this);
        target = readTarget(getIntent());
        build();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        target = readTarget(intent);
        build();
    }

    private long readTarget(Intent i) {
        long t = i != null ? i.getLongExtra(Scheduler.EXTRA_TARGET, 0) : 0;
        return t != 0 ? t : prefs.ringTarget();
    }

    private void build() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = ui.col();
        root.setPadding(ui.dp(20), ui.dp(40), ui.dp(20), ui.dp(32));
        scroll.addView(root, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Kopf: Sonne, Datum, Uhrzeit, Gruß
        LinearLayout head = ui.col();
        head.setGravity(Gravity.CENTER_HORIZONTAL);
        head.addView(ui.icon(R.drawable.i_logo, ui.p.accent, 48));
        long t = target != 0 ? target : System.currentTimeMillis();
        TextView date = ui.text(new SimpleDateFormat("EEEE, d. MMMM", Locale.GERMANY).format(new Date(t)), 16, ui.p.text2);
        head.addView(date, wrapTop(8));
        TextView time = ui.light(new SimpleDateFormat("HH:mm", Locale.GERMANY).format(new Date(t)), 96, ui.p.text);
        time.setIncludeFontPadding(false);
        head.addView(time, wrapTop(0));
        TextView hello = ui.medium("Guten Morgen, " + prefs.name(), 26, ui.p.text);
        hello.setGravity(Gravity.CENTER);
        Ui.heading(hello);
        head.addView(hello, wrapTop(10));
        root.addView(head, ui.fill());

        // Wetter
        LinearLayout weather = ui.card(ui.p.surface, 24);
        weather.setPadding(ui.dp(18), ui.dp(16), ui.dp(18), ui.dp(16));
        LinearLayout w1 = ui.row();
        w1.addView(ui.icon(R.drawable.i_cloud, ui.p.cloud, 28));
        weatherToday = ui.text("", 17, ui.p.text);
        LinearLayout.LayoutParams wl = ui.weight();
        wl.leftMargin = ui.dp(12);
        w1.addView(weatherToday, wl);
        weather.addView(w1, ui.fill());
        middayRow = ui.col();
        middayRow.addView(ui.divider(), ui.top(10));
        LinearLayout w2 = ui.row();
        w2.setGravity(Gravity.TOP);
        w2.addView(ui.icon(R.drawable.i_sun, ui.p.accent, 28));
        LinearLayout w2t = ui.col();
        weatherMidday = ui.text("", 15, ui.p.text);
        weatherRain = ui.text("", 13, ui.p.muted);
        w2t.addView(weatherMidday);
        w2t.addView(weatherRain, ui.top(2));
        LinearLayout.LayoutParams w2l = ui.weight();
        w2l.leftMargin = ui.dp(12);
        w2.addView(w2t, w2l);
        middayRow.addView(w2, ui.top(10));
        weather.addView(middayRow, ui.fill());
        root.addView(weather, ui.top(20));

        // Radio
        LinearLayout radio = ui.card(ui.p.surface, 24);
        radio.setPadding(ui.dp(18), ui.dp(14), ui.dp(14), ui.dp(14));
        LinearLayout rr = ui.row();
        rr.addView(ui.icon(R.drawable.i_speaker, ui.p.accent, 24));
        LinearLayout rt = ui.col();
        radioTitle = ui.text("", 16, ui.p.text);
        radioDetail = ui.text("", 13, ui.p.muted);
        rt.addView(radioTitle);
        rt.addView(radioDetail);
        LinearLayout.LayoutParams rtl = ui.weight();
        rtl.leftMargin = ui.dp(12);
        rr.addView(rt, rtl);
        Button off = ui.outlinedButton("Radio aus");
        off.setOnClickListener(v -> stopRadio());
        radioOff = off;
        rr.addView(off);
        radio.addView(rr, ui.fill());
        root.addView(radio, ui.top(16));

        View spacer = new View(this);
        root.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1));

        // Backup-Hinweis
        backupRow = ui.row();
        backupRow.setGravity(Gravity.CENTER);
        backupRow.addView(ui.icon(R.drawable.i_vibrate, ui.p.text2, 20));
        backupText = ui.text("", 14, ui.p.text2);
        LinearLayout.LayoutParams bl = ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bl.leftMargin = ui.dp(10);
        backupRow.addView(backupText, bl);
        backupRow.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_NONE);
        root.addView(backupRow, ui.top(20));

        // Ich bin wach
        Button awake = ui.filled("Ich bin wach");
        awake.setTextSize(19);
        awake.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
        awake.setBackground(ui.ripple(ui.shape(ui.p.accent, 32), 32));
        awake.setMinHeight(ui.dp(64));
        awake.setMinimumHeight(ui.dp(64));
        awake.setOnClickListener(v -> {
            Ringing.confirm(this, target);
            finish();
        });
        root.addView(awake, ui.top(16));

        setContentView(scroll);
        update();
    }

    private LinearLayout.LayoutParams wrapTop(int dpTop) {
        LinearLayout.LayoutParams l = ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        l.topMargin = ui.dp(dpTop);
        return l;
    }

    private void update() {
        // Wetter (schreibt die Begrüßung kurz vor der Weckzeit)
        if (target != 0 && prefs.weatherFor() == target) {
            weatherToday.setText(prefs.weatherToday());
            boolean md = !prefs.weatherMidday().isEmpty();
            middayRow.setVisibility(md ? View.VISIBLE : View.GONE);
            weatherMidday.setText(prefs.weatherMidday());
            weatherRain.setText(prefs.weatherMiddayRain());
            weatherRain.setVisibility(prefs.weatherMiddayRain().isEmpty() ? View.GONE : View.VISIBLE);
        } else {
            weatherToday.setText("Wetter wird geladen …");
            middayRow.setVisibility(View.GONE);
        }

        // Radio
        String rs = prefs.radioStatus();
        String station = prefs.radioTitle();
        String where = prefs.castName().isEmpty() ? "" : "Lautsprecher " + prefs.castName() + " · " + prefs.volumePercent() + " %";
        switch (rs) {
            case "läuft":
                radioTitle.setText(station + " läuft");
                radioOff.setVisibility(View.VISIBLE);
                break;
            case "aus":
                radioTitle.setText("Radio aus");
                radioOff.setVisibility(View.GONE);
                break;
            case "startet":
            case "":
                radioTitle.setText(station + " startet …");
                radioOff.setVisibility(View.VISIBLE);
                break;
            default:
                radioTitle.setText(rs);
                radioOff.setVisibility(View.GONE);
                break;
        }
        radioDetail.setText(where);

        // Backup-Countdown
        long now = System.currentTimeMillis();
        long at = target + Prefs.BACKUP_DELAY_MS;
        if (!prefs.backupEnabled() || target == 0) {
            backupRow.setVisibility(View.GONE);
        } else if (now < at) {
            backupRow.setVisibility(View.VISIBLE);
            long s = (at - now + 999) / 1000;
            backupText.setText("Vibration in " + (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60) + ", außer du bestätigst");
            backupRow.setContentDescription("Vibration in " + (s / 60) + " Minuten, außer du bestätigst");
        } else {
            backupRow.setVisibility(View.VISIBLE);
            backupText.setText("Backup-Vibration läuft");
        }
    }

    private void stopRadio() {
        radioOff.setEnabled(false);
        new Thread(() -> {
            String msg;
            try {
                Speaker.stopRadio(this);
                msg = "Radio aus";
            } catch (Exception e) {
                msg = "Radio ließ sich nicht ausschalten: " + e.getMessage();
            }
            String m = msg;
            runOnUiThread(() -> {
                radioOff.setEnabled(true);
                Toast.makeText(this, m, Toast.LENGTH_SHORT).show();
                update();
            });
        }).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }
}
