package de.christina.sonnenwecker;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Stimme eines Weckers wählen: Premium-Stimmen von Google Cloud oder Stimmen der Sprachausgabe-Apps auf dem Handy. */
public class VoiceActivity extends Activity implements VoicePlayer.Listener {
    public static final String EXTRA_ALARM = "alarm";
    static final String SAMPLE_WEATHER = "Heute wird es bis zu 12 Grad und bewölkt.";
    static final String SAMPLE_MIDDAY =
            "Zwischen 12 und 14 Uhr wird es etwa 11 Grad und sonnig. Die Regenwahrscheinlichkeit liegt bei 10 Prozent.";
    private static final String CLOUD_CONSOLE = "https://console.cloud.google.com/";
    private static final String SHERPA_URL = "https://f-droid.org/packages/org.woheller69.ttsengine/";

    private Ui ui;
    private Prefs prefs;
    private AlarmStore store;
    private int alarmId;
    private long previewTime;
    private String selected;
    private VoicePlayer player;

    private TextView current;
    private LinearLayout cloudBox, phoneBox;
    private int cloudLoad;

    private static final class VoiceRow {
        final String spec, title;
        final RadioButton radio;
        final ImageButton play;

        VoiceRow(String spec, String title, RadioButton radio, ImageButton play) {
            this.spec = spec;
            this.title = title;
            this.radio = radio;
            this.play = play;
        }
    }

    private final Map<String, VoiceRow> rows = new HashMap<>();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ui = new Ui(this);
        ui.applyWindow();
        prefs = new Prefs(this);
        store = new AlarmStore(this);
        alarmId = getIntent().getIntExtra(EXTRA_ALARM, 0);
        Alarm alarm = store.get(alarmId);
        if (alarm == null) {
            finish();
            return;
        }
        selected = alarm.voice;
        long now = System.currentTimeMillis();
        long next = alarm.nextAfter(now);
        previewTime = next == Long.MAX_VALUE ? now : next;
        player = new VoicePlayer(this, this);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = ui.col();
        root.setPadding(0, 0, 0, ui.dp(24));
        scroll.addView(root);

        root.addView(header(alarm));
        LinearLayout body = ui.col();
        body.setPadding(ui.dp(16), 0, ui.dp(16), 0);
        root.addView(body);

        current = ui.text("", 15, ui.p.text2);
        current.setPadding(ui.dp(8), ui.dp(4), ui.dp(8), 0);
        current.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        body.addView(current);
        updateCurrent(alarm.voice, alarm.voiceLabel);
        TextView intro = ui.text("Mit dem Abspielknopf hörst du eine Probe auf dem Handy. Morgens spricht der "
                + "Lautsprecher mit derselben Stimme.", 13, ui.p.muted);
        intro.setPadding(ui.dp(8), ui.dp(6), ui.dp(8), 0);
        body.addView(intro);

        body.addView(ui.section("Premium-Stimmen (Google Cloud)"), ui.top(8));
        cloudBox = ui.card(ui.p.surface, 20);
        cloudBox.setPadding(0, ui.dp(8), 0, ui.dp(8));
        body.addView(cloudBox, ui.fill());

        body.addView(ui.section("Stimmen auf dem Handy"), ui.top(8));
        phoneBox = ui.card(ui.p.surface, 20);
        phoneBox.setPadding(0, ui.dp(8), 0, ui.dp(8));
        body.addView(phoneBox, ui.fill());

        body.addView(ui.section("Mehr Stimmen"), ui.top(8));
        body.addView(moreVoices(), ui.fill());

        TextView foot = ui.text("Die Stimme gilt für diesen Wecker. Neue Wecker übernehmen die zuletzt gewählte. "
                + "Ist sie morgens nicht verfügbar, spricht die Standardstimme des Handys.", 13, ui.p.muted);
        foot.setGravity(Gravity.CENTER);
        foot.setPadding(ui.dp(8), 0, ui.dp(8), 0);
        body.addView(foot, ui.top(20));
        setContentView(scroll);

        renderCloud();
        loadPhoneVoices();
    }

    private View header(Alarm a) {
        LinearLayout h = ui.row();
        h.setMinimumHeight(ui.dp(64));
        h.setPadding(ui.dp(4), 0, ui.dp(16), 0);
        ImageButton back = ui.iconButton(R.drawable.i_back, "Zurück", ui.p.text2);
        back.setOnClickListener(v -> finish());
        h.addView(back);
        LinearLayout t = ui.col();
        TextView title = ui.medium("Stimme", 22, ui.p.text);
        Ui.heading(title);
        t.addView(title);
        t.addView(ui.text("Wecker " + a.time() + " · " + a.daysText(System.currentTimeMillis()), 13, ui.p.muted));
        LinearLayout.LayoutParams l = ui.weight();
        l.leftMargin = ui.dp(4);
        h.addView(t, l);
        return h;
    }

    private void updateCurrent(String spec, String label) {
        current.setText("Gewählt: " + VoiceSpec.label(spec, label));
    }

    // ---------- Google Cloud ----------

    private void renderCloud() {
        cloudLoad++;
        if (VoiceSpec.isCloud(player.current())) player.stop();
        cloudBox.removeAllViews();
        removeRows(true);
        String key = prefs.cloudKey();
        if (key.isEmpty()) {
            LinearLayout pad = padded();
            pad.addView(ui.text("Sehr natürlich klingende Stimmen von Google. Dafür brauchst du ein eigenes "
                    + "Google-Cloud-Konto mit API-Schlüssel. Die Begrüßungen bleiben weit unter der Menge, "
                    + "die Google jeden Monat kostenlos spricht.", 15, ui.p.text2));
            if (VoiceSpec.isCloud(selected)) {
                pad.addView(ui.text("Dieser Wecker ist auf eine Google-Cloud-Stimme eingestellt. Ohne Schlüssel "
                        + "spricht morgens die Standardstimme des Handys.", 14, ui.p.warn), ui.top(10));
            }
            Button enter = ui.tonal("API-Schlüssel eingeben");
            enter.setOnClickListener(v -> keyDialog());
            pad.addView(enter, ui.top(14));
            LinearLayout how = ui.iconText(R.drawable.i_cloud, "So richtest du es ein", ui.p.onTonal);
            how.setOnClickListener(v -> setupDialog());
            pad.addView(how, wrap(6));
            cloudBox.addView(pad);
            return;
        }
        LinearLayout pad = padded();
        pad.addView(ui.text("Stimmen werden geladen …", 15, ui.p.muted));
        cloudBox.addView(pad);
        int token = cloudLoad;
        new Thread(() -> {
            List<CloudTts.CloudVoice> voices = null;
            String error = null;
            try {
                voices = CloudTts.listVoices(key);
            } catch (Exception e) {
                error = e.getMessage();
            }
            List<CloudTts.CloudVoice> v = voices;
            String err = error;
            runOnUiThread(() -> {
                if (token != cloudLoad || isFinishing() || isDestroyed()) return;
                if (v != null) showCloudVoices(v);
                else showCloudError(err);
            });
        }, "sonnenwecker-cloudstimmen").start();
    }

    private void showCloudVoices(List<CloudTts.CloudVoice> voices) {
        cloudBox.removeAllViews();
        removeRows(true);
        if (voices.isEmpty()) {
            LinearLayout pad = padded();
            pad.addView(ui.text("Google meldet gerade keine deutschen Premium-Stimmen.", 15, ui.p.text2));
            cloudBox.addView(pad);
        } else {
            String group = null;
            for (CloudTts.CloudVoice v : voices) {
                String g = "FEMALE".equals(v.gender) ? "Weibliche Stimmen"
                        : "MALE".equals(v.gender) ? "Männliche Stimmen" : "Weitere Stimmen";
                if (!g.equals(group)) {
                    group = g;
                    cloudBox.addView(groupLabel(g));
                }
                String title = v.title().replace(" (weiblich)", "").replace(" (männlich)", "");
                addRow(cloudBox, VoiceSpec.cloud(v.name), v.label(), title, "");
            }
            TextView note = ui.text("Hörproben zählen mit zur kostenlosen Menge.", 13, ui.p.muted);
            note.setPadding(ui.dp(20), ui.dp(8), ui.dp(20), 0);
            cloudBox.addView(note);
        }
        LinearLayout change = ui.iconText(R.drawable.i_settings, "API-Schlüssel ändern", ui.p.onTonal);
        change.setOnClickListener(v -> keyDialog());
        LinearLayout.LayoutParams cl = wrap(4);
        cl.leftMargin = ui.dp(12);
        cloudBox.addView(change, cl);
    }

    private void showCloudError(String message) {
        cloudBox.removeAllViews();
        removeRows(true);
        LinearLayout pad = padded();
        pad.addView(ui.text("Die Premium-Stimmen ließen sich nicht laden: " + message, 15, ui.p.warn));
        LinearLayout retry = ui.iconText(R.drawable.i_restore, "Erneut versuchen", ui.p.onTonal);
        retry.setOnClickListener(v -> renderCloud());
        pad.addView(retry, wrap(8));
        LinearLayout change = ui.iconText(R.drawable.i_settings, "API-Schlüssel ändern", ui.p.onTonal);
        change.setOnClickListener(v -> keyDialog());
        pad.addView(change, wrap(0));
        cloudBox.addView(pad);
    }

    private void keyDialog() {
        LinearLayout box = ui.col();
        box.setPadding(ui.dp(24), ui.dp(8), ui.dp(24), 0);
        box.addView(ui.text("Du findest ihn in Google Cloud unter „APIs und Dienste → Anmeldedaten“. "
                + "Er wird nur auf diesem Handy gespeichert.", 14, ui.p.text2));
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        e.setHint("API-Schlüssel (beginnt mit AIza)");
        e.setText(prefs.cloudKey());
        e.setSelection(e.getText().length());
        e.setContentDescription("API-Schlüssel");
        box.addView(e, ui.top(12));
        TextView status = ui.text("", 14, ui.p.muted);
        status.setVisibility(View.GONE);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        box.addView(status, ui.top(8));

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle("API-Schlüssel")
                .setView(box)
                .setPositiveButton("Prüfen und speichern", null)
                .setNegativeButton("Abbrechen", null);
        if (!prefs.cloudKey().isEmpty()) b.setNeutralButton("Entfernen", (d, w) -> removeKey());
        AlertDialog dialog = b.show();
        Button ok = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        ok.setOnClickListener(v -> {
            String key = e.getText().toString().trim();
            status.setVisibility(View.VISIBLE);
            if (key.isEmpty()) {
                status.setTextColor(ui.p.warn);
                status.setText("Bitte den Schlüssel einfügen.");
                return;
            }
            ok.setEnabled(false);
            status.setTextColor(ui.p.muted);
            status.setText("Wird geprüft …");
            new Thread(() -> {
                String error = null;
                int count = 0;
                try {
                    List<CloudTts.CloudVoice> voices = CloudTts.listVoices(key);
                    count = voices.size();
                    // Einmal kurz sprechen lassen: prüft auch Abrechnung und Einschränkungen des Schlüssels
                    if (count > 0) CloudTts.synthesize(key, voices.get(0).name, "Test");
                } catch (Exception ex) {
                    error = ex.getMessage();
                }
                String err = error;
                int n = count;
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (err != null) {
                        ok.setEnabled(true);
                        status.setTextColor(ui.p.warn);
                        status.setText(err);
                        return;
                    }
                    prefs.setString("cloudKey", key);
                    prefs.log("Google-Cloud-Schlüssel gespeichert (" + n + " deutsche Premium-Stimmen)");
                    dialog.dismiss();
                    Toast.makeText(this, "Schlüssel gespeichert ✓", Toast.LENGTH_SHORT).show();
                    renderCloud();
                });
            }, "sonnenwecker-schluessel").start();
        });
    }

    private void removeKey() {
        prefs.setString("cloudKey", "");
        prefs.log("Google-Cloud-Schlüssel entfernt");
        if (VoiceSpec.isCloud(selected)) {
            Toast.makeText(this, "Ohne Schlüssel spricht dieser Wecker mit der Standardstimme.", Toast.LENGTH_LONG).show();
        }
        renderCloud();
    }

    private void setupDialog() {
        LinearLayout box = ui.col();
        box.setPadding(ui.dp(24), ui.dp(8), ui.dp(24), 0);
        box.addView(ui.text("1. Öffne Google Cloud und melde dich mit deinem Google-Konto an.\n\n"
                + "2. Lege ein neues Projekt an, z. B. „Sonnenwecker“.\n\n"
                + "3. Richte für das Projekt die Abrechnung ein. Google verlangt dafür eine Zahlungsmethode – "
                + "auch wenn du nur die kostenlose Menge nutzt.\n\n"
                + "4. Suche die „Cloud Text-to-Speech API“ und aktiviere sie.\n\n"
                + "5. Erstelle unter „APIs und Dienste → Anmeldedaten“ einen API-Schlüssel. Beschränke ihn am "
                + "besten auf die Cloud Text-to-Speech API.\n\n"
                + "6. Kopiere den Schlüssel und füge ihn hier unter „API-Schlüssel eingeben“ ein.\n\n"
                + "Tipp: Unter „Abrechnung → Budgets und Benachrichtigungen“ kannst du dir eine Warnung "
                + "schicken lassen, falls doch einmal Kosten entstehen (z. B. ab 1 €).", 15, ui.p.text2));
        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        new AlertDialog.Builder(this)
                .setTitle("Google Cloud einrichten")
                .setView(sv)
                .setPositiveButton("Google Cloud öffnen", (d, w) -> open(CLOUD_CONSOLE))
                .setNegativeButton("Schließen", null)
                .show();
    }

    // ---------- Stimmen auf dem Handy ----------

    private void loadPhoneVoices() {
        phoneBox.removeAllViews();
        addRow(phoneBox, VoiceSpec.DEFAULT, VoiceSpec.DEFAULT_LABEL, VoiceSpec.DEFAULT_LABEL,
                "Wie in den Android-Einstellungen eingestellt");
        LinearLayout list = ui.col();
        phoneBox.addView(list, ui.fill());
        TextView searching = ui.text("Weitere Stimmen werden gesucht …", 14, ui.p.muted);
        searching.setPadding(ui.dp(20), ui.dp(8), ui.dp(20), ui.dp(8));
        list.addView(searching);
        player.engine("", (tts, ok) -> {
            list.removeAllViews();
            List<TextToSpeech.EngineInfo> engines = new ArrayList<>();
            if (ok) {
                try {
                    engines.addAll(tts.getEngines());
                } catch (Exception ignored) {
                }
            }
            if (engines.isEmpty()) {
                TextView none = ui.text("Keine Sprachausgabe-App gefunden.", 14, ui.p.muted);
                none.setPadding(ui.dp(20), ui.dp(8), ui.dp(20), ui.dp(8));
                list.addView(none);
                return;
            }
            for (TextToSpeech.EngineInfo info : engines) {
                LinearLayout group = ui.col();
                list.addView(group, ui.fill());
                group.addView(groupLabel(engineLabel(info)));
                TextView wait = ui.text("Wird geladen …", 14, ui.p.muted);
                wait.setPadding(ui.dp(20), ui.dp(4), ui.dp(20), ui.dp(8));
                group.addView(wait);
                player.engine(info.name, (t, engineOk) -> fillEngine(group, wait, info, t, engineOk));
            }
        });
    }

    private void fillEngine(LinearLayout group, TextView wait, TextToSpeech.EngineInfo info, TextToSpeech tts,
                            boolean ok) {
        group.removeView(wait);
        if (!ok) {
            group.addView(note("Lässt sich gerade nicht starten."));
            return;
        }
        Set<Voice> all = null;
        try {
            all = tts.getVoices();
        } catch (Exception ignored) {
        }
        List<Voice> german = new ArrayList<>();
        int missing = 0;
        if (all != null) {
            for (Voice v : all) {
                if (v.getLocale() == null || !VoiceSpec.isGerman(v.getLocale().getLanguage())) continue;
                Set<String> f = v.getFeatures();
                if (f != null && f.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)) {
                    missing++;
                    continue;
                }
                german.add(v);
            }
        }
        // „de-DE-language“ ist bei Google nur ein Verweis auf eine der anderen Stimmen
        boolean hasNamed = false;
        for (Voice v : german) if (!v.getName().endsWith("-language")) hasNamed = true;
        if (hasNamed) german.removeIf(v -> v.getName().endsWith("-language"));
        Collections.sort(german, (a, b) -> {
            int c = Boolean.compare(a.isNetworkConnectionRequired(), b.isNetworkConnectionRequired());
            if (c != 0) return c;
            c = region(a).compareTo(region(b));
            if (c != 0) return c;
            return a.getName().compareTo(b.getName());
        });
        if (german.isEmpty()) {
            group.addView(note(missing > 0
                    ? "Deutsche Stimmen sind noch nicht heruntergeladen – unter „Sprachausgabe-Einstellungen öffnen“ (unten)."
                    : "Keine deutsche Stimme installiert."));
            return;
        }
        for (Voice v : german) {
            String title = VoiceSpec.phoneTitle(v.getName());
            String region = region(v);
            List<String> parts = new ArrayList<>();
            if (!region.isEmpty()) parts.add(region);
            String q = VoiceSpec.quality(v.getQuality());
            if (!q.isEmpty()) parts.add(q);
            if (v.isNetworkConnectionRequired()) parts.add("braucht Internet");
            String subtitle = String.join(" · ", parts);
            String label = engineLabel(info) + " · " + title + (region.isEmpty() ? "" : " (" + region + ")")
                    + (v.isNetworkConnectionRequired() ? " · Internet" : "");
            addRow(group, VoiceSpec.engine(info.name, v.getName()), label, title, subtitle);
        }
    }

    private static String engineLabel(TextToSpeech.EngineInfo info) {
        return info.label != null && !info.label.isEmpty() ? info.label : info.name;
    }

    private static String region(Voice v) {
        try {
            return VoiceSpec.region(v.getLocale().getCountry());
        } catch (Exception e) {
            return "";
        }
    }

    private View moreVoices() {
        LinearLayout card = ui.card(ui.p.surface, 20);
        card.setPadding(ui.dp(20), ui.dp(16), ui.dp(20), ui.dp(12));
        card.addView(ui.text("Stimmen anderer Sprachausgabe-Apps erscheinen hier von selbst. Fehlende "
                + "Google-Stimmen lädst du in den Android-Einstellungen unter Sprachausgabe herunter.", 15, ui.p.text2));
        LinearLayout tts = ui.iconText(R.drawable.i_settings, "Sprachausgabe-Einstellungen öffnen", ui.p.onTonal);
        tts.setOnClickListener(v -> {
            try {
                startActivity(new Intent("com.android.settings.TTS_SETTINGS"));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        });
        card.addView(tts, wrap(8));
        card.addView(ui.text("Natürlicher und ganz ohne Internet: die kostenlose App SherpaTTS aus F-Droid "
                + "mit Stimmen wie „Thorsten“. Die deutsche Stimme lädst du dort einmal herunter.", 15, ui.p.text2),
                ui.top(12));
        LinearLayout sherpa = ui.iconText(R.drawable.i_chevron, "SherpaTTS bei F-Droid ansehen", ui.p.onTonal);
        sherpa.setOnClickListener(v -> open(SHERPA_URL));
        card.addView(sherpa, wrap(8));
        return card;
    }

    // ---------- Zeilen ----------

    private void addRow(LinearLayout parent, String spec, String label, String title, String subtitle) {
        LinearLayout r = ui.row();
        r.setMinimumHeight(ui.dp(56));
        r.setPadding(ui.dp(12), ui.dp(4), ui.dp(8), ui.dp(4));
        r.setBackground(ui.ripple(null, 0));
        RadioButton radio = new RadioButton(this);
        radio.setButtonTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{ui.p.onTonal, ui.p.outline}));
        radio.setClickable(false);
        radio.setFocusable(false);
        radio.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        radio.setChecked(spec.equals(selected));
        r.addView(radio);
        LinearLayout texts = ui.col();
        texts.addView(ui.text(title, 16, ui.p.text));
        if (!subtitle.isEmpty()) texts.addView(ui.text(subtitle, 13, ui.p.muted), ui.top(2));
        LinearLayout.LayoutParams tl = ui.weight();
        tl.leftMargin = ui.dp(4);
        r.addView(texts, tl);
        ImageButton play = ui.iconButton(R.drawable.i_play, "Hörprobe " + title, ui.p.onTonal);
        play.setOnClickListener(v -> togglePlay(spec));
        r.addView(play);
        r.setClickable(true);
        r.setFocusable(true);
        r.setContentDescription(subtitle.isEmpty() ? title : title + ", " + subtitle);
        r.setOnClickListener(v -> select(spec, label));
        r.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(RadioButton.class.getName());
                info.setCheckable(true);
                info.setChecked(spec.equals(selected));
            }
        });
        parent.addView(r, ui.fill());
        rows.put(spec, new VoiceRow(spec, title, radio, play));
    }

    private void removeRows(boolean cloud) {
        rows.keySet().removeIf(s -> VoiceSpec.isCloud(s) == cloud);
    }

    private TextView groupLabel(String s) {
        TextView t = ui.medium(s, 13, ui.p.muted);
        t.setPadding(ui.dp(20), ui.dp(12), ui.dp(20), ui.dp(2));
        Ui.heading(t);
        return t;
    }

    private TextView note(String s) {
        TextView t = ui.text(s, 14, ui.p.muted);
        t.setPadding(ui.dp(20), ui.dp(4), ui.dp(20), ui.dp(8));
        return t;
    }

    private LinearLayout padded() {
        LinearLayout pad = ui.col();
        pad.setPadding(ui.dp(20), ui.dp(8), ui.dp(20), ui.dp(8));
        return pad;
    }

    private LinearLayout.LayoutParams wrap(int topDp) {
        LinearLayout.LayoutParams l = ui.lp(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        l.topMargin = ui.dp(topDp);
        return l;
    }

    // ---------- Auswahl und Hörprobe ----------

    private void select(String spec, String label) {
        Alarm a = store.get(alarmId);
        if (a == null) return;
        a.voice = spec;
        a.voiceLabel = spec.isEmpty() ? "" : label;
        store.update(a);
        prefs.edit().putString("lastVoice", a.voice).putString("lastVoiceLabel", a.voiceLabel).apply();
        selected = spec;
        for (VoiceRow r : rows.values()) r.radio.setChecked(r.spec.equals(selected));
        updateCurrent(a.voice, a.voiceLabel);
    }

    private void togglePlay(String spec) {
        if (player.isPlaying(spec)) player.stop();
        else player.play(spec, sampleText());
    }

    /** Die Begrüßung dieses Weckers mit Beispielwetter. */
    private String sampleText() {
        Alarm a = store.get(alarmId);
        String template = a != null ? a.greeting : Greeting.STANDARD;
        int mask = a != null ? a.middayDays : prefs.defaultMiddayMask();
        String s = render(template, mask);
        return s.isEmpty() ? render(Greeting.STANDARD, mask) : s;
    }

    private String render(String template, int mask) {
        boolean midday = Greeting.usesMidday(template) && Alarm.maskOn(mask, previewTime);
        return Greeting.render(template, prefs.name(), previewTime, SAMPLE_WEATHER, midday ? SAMPLE_MIDDAY : "");
    }

    @Override
    public void onState(String spec, boolean playing) {
        VoiceRow r = rows.get(spec);
        if (r == null) return;
        r.play.setImageResource(playing ? R.drawable.i_stop : R.drawable.i_play);
        r.play.setContentDescription(playing ? "Hörprobe stoppen" : "Hörprobe " + r.title);
    }

    @Override
    public void onError(String spec, String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void open(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, url, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) player.stop();
    }

    @Override
    protected void onDestroy() {
        if (player != null) player.release();
        super.onDestroy();
    }
}
