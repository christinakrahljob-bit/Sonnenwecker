package de.christina.sonnenwecker;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Status und Einrichtung: zeigt, ob alles fürs Wecken bereit ist – und was noch fehlt. */
public class StatusActivity extends Activity {
    private Ui ui;
    private Prefs prefs;
    private LinearLayout summary, listCard;
    private final List<Check> checks = new ArrayList<>();

    private static final class Check {
        String title, detail, action;
        Boolean ok; // null = wird geprüft
        Runnable run;
    }

    /** Schnelle Prüfung ohne Netzwerk – für den Hinweis auf dem Startbildschirm. */
    static boolean quickOk(Context ctx) {
        Prefs p = new Prefs(ctx);
        if (p.hueUser().isEmpty() || !p.hueOk()) return false;
        if (p.castName().isEmpty()) return false;
        if (!p.statusOk()) return false;
        return permissionsOk(ctx);
    }

    static boolean permissionsOk(Context ctx) {
        AlarmManager am = ctx.getSystemService(AlarmManager.class);
        if (!Scheduler.canExact(am)) return false;
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (!nm.areNotificationsEnabled()) return false;
        if (Build.VERSION.SDK_INT >= 34 && !nm.canUseFullScreenIntent()) return false;
        PowerManager pm = ctx.getSystemService(PowerManager.class);
        return pm.isIgnoringBatteryOptimizations(ctx.getPackageName());
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ui = new Ui(this);
        ui.applyWindow();
        prefs = new Prefs(this);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = ui.col();
        scroll.addView(root);
        root.addView(ui.header("Status und Einrichtung", true));
        LinearLayout body = ui.col();
        body.setPadding(ui.dp(16), ui.dp(4), ui.dp(16), ui.dp(32));
        root.addView(body);
        summary = ui.card(ui.p.surface, 24);
        summary.setPadding(ui.dp(20), ui.dp(18), ui.dp(20), ui.dp(18));
        body.addView(summary, ui.fill());
        listCard = ui.card(ui.p.surface, 20);
        listCard.setPadding(0, ui.dp(4), 0, ui.dp(4));
        body.addView(listCard, ui.top(16));
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        runChecks();
    }

    private void runChecks() {
        checks.clear();
        boolean paired = !prefs.hueUser().isEmpty();

        Check bridge = add("Hue Bridge gekoppelt", paired ? prefs.hueIp() : "Noch nicht gekoppelt", paired,
                "Einrichten", () -> startActivity(new Intent(this, SettingsActivity.class)));

        Check auto = add("Hue-Automation „" + BridgeSync.AUTOMATION_NAME + "“", "Wird geprüft …", null,
                "Einstellungen", () -> startActivity(new Intent(this, SettingsActivity.class)));

        Check speaker = add("Lautsprecher erreichbar",
                prefs.castName().isEmpty() ? "Noch kein Lautsprecher gewählt" : "Wird geprüft …",
                prefs.castName().isEmpty() ? Boolean.FALSE : null,
                "Auswählen", () -> startActivity(new Intent(this, SettingsActivity.class)));

        Check voice = add("Deutsche Stimme vorhanden", "Wird geprüft …", null, "Öffnen", () -> {
            try {
                startActivity(new Intent("com.android.settings.TTS_SETTINGS"));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        });

        PowerManager pm = getSystemService(PowerManager.class);
        boolean battery = pm.isIgnoringBatteryOptimizations(getPackageName());
        add("Akku-Optimierung aus", battery ? "Android stoppt die App nachts nicht" : "Android könnte die App nachts stoppen",
                battery, "Ausschalten", () -> startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + getPackageName()))));

        boolean exact = Scheduler.canExact(getSystemService(AlarmManager.class));
        add("Genaue Wecker erlaubt", exact ? "Wecken pünktlich auf die Minute" : "Wecken könnte sich verspäten",
                exact, "Erlauben", () -> {
                    if (Build.VERSION.SDK_INT >= 31) {
                        startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                Uri.parse("package:" + getPackageName())));
                    }
                });

        NotificationManager nm = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 34) {
            boolean full = nm.canUseFullScreenIntent();
            add("Weck-Bildschirm erlaubt", full ? "Erscheint auch auf dem Sperrbildschirm" : "Nur als Benachrichtigung",
                    full, "Erlauben", () -> startActivity(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                            Uri.parse("package:" + getPackageName()))));
        }
        boolean notif = nm.areNotificationsEnabled();
        add("Benachrichtigungen erlaubt", notif ? "Für „Ich bin wach“" : "Ohne sie fehlt „Ich bin wach“",
                notif, "Erlauben", () -> {
                    if (Build.VERSION.SDK_INT >= 33
                            && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 2);
                    } else {
                        startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
                    }
                });
        render();

        // Netzwerk-Prüfungen im Hintergrund
        new Thread(() -> {
            if (paired) BridgeSync.sync(this, true);
            boolean hueOk = paired && prefs.hueOk();
            String hueText = paired ? cleanHue(prefs.hueSync()) : "Erst die Bridge koppeln";
            boolean spk = !prefs.castName().isEmpty() && Speaker.reachable(this);
            runOnUiThread(() -> {
                auto.ok = hueOk;
                auto.detail = hueText;
                if (!prefs.castName().isEmpty()) {
                    speaker.ok = spk;
                    speaker.detail = spk ? prefs.castName() : prefs.castName() + " antwortet nicht";
                }
                render();
            });
        }).start();

        checkVoice(voice);
        bridge.ok = paired;
    }

    private void checkVoice(Check voice) {
        final TextToSpeech[] tts = new TextToSpeech[1];
        tts[0] = new TextToSpeech(getApplicationContext(), status -> {
            boolean ok = false;
            if (status == TextToSpeech.SUCCESS) {
                int r = tts[0].isLanguageAvailable(Locale.GERMANY);
                ok = r >= TextToSpeech.LANG_AVAILABLE;
            }
            tts[0].shutdown();
            boolean result = ok;
            runOnUiThread(() -> {
                voice.ok = result;
                voice.detail = result ? "Für die Begrüßung" : "Fehlt – in den Einstellungen installieren";
                render();
            });
        });
    }

    private static String cleanHue(String s) {
        String t = s.replace("Hue-Automation „" + BridgeSync.AUTOMATION_NAME + "“", "").replaceFirst("^[: ]+", "");
        return t.isEmpty() ? s : t;
    }

    private Check add(String title, String detail, Boolean ok, String action, Runnable run) {
        Check c = new Check();
        c.title = title;
        c.detail = detail;
        c.ok = ok;
        c.action = action;
        c.run = run;
        checks.add(c);
        return c;
    }

    private void render() {
        listCard.removeAllViews();
        int problems = 0, pending = 0;
        for (int i = 0; i < checks.size(); i++) {
            Check c = checks.get(i);
            if (c.ok == null) pending++;
            else if (!c.ok) problems++;
            if (i > 0) listCard.addView(ui.divider());
            listCard.addView(rowFor(c));
        }
        boolean allOk = problems == 0 && pending == 0;
        if (pending == 0) prefs.setBool("statusOk", problems == 0);

        summary.removeAllViews();
        LinearLayout r = ui.row();
        LinearLayout circle = ui.row();
        circle.setGravity(Gravity.CENTER);
        circle.setBackground(ui.oval(allOk ? ui.p.okBg : ui.p.warnBg, 52));
        circle.addView(ui.icon(allOk ? R.drawable.i_check : R.drawable.i_alert, allOk ? ui.p.ok : ui.p.warn, 28));
        r.addView(circle, ui.lp(ui.dp(52), ui.dp(52)));
        LinearLayout texts = ui.col();
        String title;
        if (pending > 0) title = "Wird geprüft …";
        else if (problems == 0) title = nextText();
        else title = problems == 1 ? "1 Punkt prüfen" : problems + " Punkte prüfen";
        TextView t = ui.medium(title, 18, ui.p.text);
        Ui.heading(t);
        texts.addView(t);
        String when = new SimpleDateFormat("HH:mm", Locale.GERMANY).format(new Date());
        texts.addView(ui.text("Zuletzt geprüft: heute, " + when, 13, ui.p.muted), ui.top(2));
        LinearLayout.LayoutParams tl = ui.weight();
        tl.leftMargin = ui.dp(16);
        r.addView(texts, tl);
        summary.addView(r, ui.fill());
    }

    private String nextText() {
        long now = System.currentTimeMillis();
        AlarmStore.Next n = new AlarmStore(this).next(now);
        if (n == null) return "Alles bereit";
        String hm = new SimpleDateFormat("HH:mm", Locale.GERMANY).format(new Date(n.time));
        return "Bereit für " + Scheduler.dayText(n.time, now).replace("Morgen, ", "morgen, ") + " · " + hm;
    }

    private View rowFor(Check c) {
        LinearLayout r = ui.row();
        r.setMinimumHeight(ui.dp(64));
        r.setPadding(ui.dp(20), ui.dp(10), ui.dp(16), ui.dp(10));
        ImageView icon;
        if (c.ok == null) {
            icon = ui.icon(R.drawable.i_alarm, ui.p.muted, 22);
        } else {
            icon = ui.icon(c.ok ? R.drawable.i_check : R.drawable.i_alert, c.ok ? ui.p.ok : ui.p.warn, 22);
        }
        r.addView(icon);
        LinearLayout texts = ui.col();
        texts.addView(ui.text(c.title, 15, ui.p.text));
        texts.addView(ui.text(c.detail, 13, ui.p.muted), ui.top(2));
        LinearLayout.LayoutParams tl = ui.weight();
        tl.leftMargin = ui.dp(14);
        r.addView(texts, tl);
        String state = c.ok == null ? "wird geprüft" : c.ok ? "in Ordnung" : "bitte prüfen";
        r.setContentDescription(c.title + ", " + state + ". " + c.detail);
        if (Boolean.FALSE.equals(c.ok) && c.run != null) {
            Button b = ui.tonal(c.action);
            b.setMinHeight(ui.dp(40));
            b.setMinimumHeight(ui.dp(40));
            b.setOnClickListener(v -> c.run.run());
            LinearLayout.LayoutParams bl = ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bl.leftMargin = ui.dp(8);
            r.addView(b, bl);
        }
        return r;
    }
}
