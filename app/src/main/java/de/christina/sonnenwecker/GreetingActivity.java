package de.christina.sonnenwecker;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;

/** Begrüßung eines Weckers bearbeiten: Text mit Bausteinen, Mittagswetter-Tage, Stimme, Vorschau zum Anhören. */
public class GreetingActivity extends Activity implements VoicePlayer.Listener {
    public static final String EXTRA_ALARM = "alarm";
    private static final int SNACK_MS = 6000;

    private Ui ui;
    private Prefs prefs;
    private AlarmStore store;
    private int alarmId;
    private long previewTime;

    private TokenEditText field;
    private LinearLayout middayBox, snackbar;
    private TextView previewCaption, previewText, listenLabel, voiceText;
    private LinearLayout voiceRow;
    private ImageView listenIcon;

    private String undoText;
    private boolean restoring;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hideSnack = this::hideSnackbar;

    private VoicePlayer player;

    private String forecast;
    private boolean forecastLoading = true;

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
        long now = System.currentTimeMillis();
        long next = alarm.nextAfter(now);
        previewTime = next == Long.MAX_VALUE ? now : next;

        FrameLayout frame = new FrameLayout(this);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = ui.col();
        root.setPadding(0, 0, 0, ui.dp(88)); // Platz für den Hinweis „Rückgängig“
        scroll.addView(root);
        frame.addView(scroll);

        root.addView(header(alarm));
        LinearLayout body = ui.col();
        body.setPadding(ui.dp(16), 0, ui.dp(16), 0);
        root.addView(body);
        buildText(body, alarm);
        buildMidday(body, alarm);
        buildVoice(body);
        buildPreview(body);
        TextView foot = ui.text("Änderungen werden sofort gespeichert.", 13, ui.p.muted);
        foot.setGravity(Gravity.CENTER);
        body.addView(foot, ui.top(20));

        snackbar = buildSnackbar();
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        sl.setMargins(ui.dp(16), 0, ui.dp(16), ui.dp(24));
        frame.addView(snackbar, sl);
        setContentView(frame);

        field.setOnChanged(this::onTextChanged);
        updateMiddayVisibility();
        updatePreview();
        loadForecast();
        player = new VoicePlayer(this, this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateVoice(); // nach der Stimmenauswahl
    }

    // ---------- Aufbau ----------

    private View header(Alarm a) {
        LinearLayout h = ui.row();
        h.setMinimumHeight(ui.dp(64));
        h.setPadding(ui.dp(4), 0, ui.dp(16), 0);
        ImageButton back = ui.iconButton(R.drawable.i_back, "Zurück", ui.p.text2);
        back.setOnClickListener(v -> finish());
        h.addView(back);
        LinearLayout t = ui.col();
        TextView title = ui.medium("Begrüßung", 22, ui.p.text);
        Ui.heading(title);
        t.addView(title);
        t.addView(ui.text("Wecker " + a.time() + " · " + a.daysText(System.currentTimeMillis()), 13, ui.p.muted));
        LinearLayout.LayoutParams l = ui.weight();
        l.leftMargin = ui.dp(4);
        h.addView(t, l);
        return h;
    }

    private void buildText(LinearLayout body, Alarm a) {
        TextView section = ui.section("Text");
        body.addView(section);
        LinearLayout card = ui.card(ui.p.surface, 20);
        card.setPadding(ui.dp(16), ui.dp(16), ui.dp(16), ui.dp(16));

        field = new TokenEditText(this, ui.p.tonal, ui.p.onTonal, getResources().getDisplayMetrics().density);
        field.setId(View.generateViewId());
        section.setLabelFor(field.getId());
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        field.setSingleLine(false);
        field.setMinLines(4);
        field.setGravity(Gravity.TOP | Gravity.START);
        field.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        field.setTextColor(ui.p.text);
        field.setHintTextColor(ui.p.muted);
        field.setHint("Leer = keine Begrüßung, nur Radio");
        field.setLineSpacing(ui.dp(8), 1f);
        field.setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12));
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, ui.outlined(ui.p.field, ui.p.accent, 2, 12));
        bg.addState(new int[]{}, ui.outlined(ui.p.field, ui.p.outline, 1, 12));
        field.setBackground(bg);
        field.setTokenText(a.greeting);
        card.addView(field, ui.fill());

        card.addView(ui.text("Bausteine fügst du über die Knöpfe unten an der Cursor-Stelle ein. "
                + "Gelöscht wird wie normaler Text.", 13, ui.p.muted), ui.top(10));

        LinearLayout restore = ui.iconText(R.drawable.i_restore, "Standardtext wiederherstellen", ui.p.onTonal);
        restore.setOnClickListener(v -> restoreStandard());
        LinearLayout.LayoutParams rl = ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rl.topMargin = ui.dp(6);
        card.addView(restore, rl);

        TextView insert = ui.medium("Baustein einfügen", 14, ui.p.muted);
        Ui.heading(insert);
        card.addView(insert, ui.top(10));
        LinearLayout row = null;
        for (int i = 0; i < Greeting.LABELS.length; i++) {
            if (i % 2 == 0) {
                row = ui.row();
                card.addView(row, ui.top(8));
            }
            String label = Greeting.LABELS[i];
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
            if (i % 2 == 1) l.leftMargin = ui.dp(8);
            row.addView(tokenButton(label, example(label)), l);
        }
        body.addView(card, ui.fill());
    }

    private String example(String label) {
        switch (label) {
            case "Name": return prefs.name();
            case "Wochentag": return Greeting.weekday(previewTime);
            case "Datum": return Greeting.date(previewTime);
            case "Uhrzeit": return Greeting.time(previewTime);
            case "Wetter heute": return "Höchstwert und Wetterlage";
            default: return "12–14 Uhr, an gewählten Tagen";
        }
    }

    private View tokenButton(String label, String example) {
        LinearLayout b = ui.row();
        b.setMinimumHeight(ui.dp(56));
        b.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(8));
        b.setBackground(ui.ripple(ui.outlined(0x00000000, ui.p.outline, 1, 14), 14));
        b.addView(ui.icon(R.drawable.i_add, ui.p.onTonal, 18));
        LinearLayout t = ui.col();
        t.addView(ui.medium(label, 15, ui.p.text));
        t.addView(ui.text(example, 12, ui.p.muted));
        LinearLayout.LayoutParams l = ui.weight();
        l.leftMargin = ui.dp(10);
        b.addView(t, l);
        b.setClickable(true);
        b.setFocusable(true);
        b.setContentDescription("Baustein " + label + " einfügen");
        b.setOnClickListener(v -> field.insertToken(Greeting.token(label)));
        return b;
    }

    private void buildMidday(LinearLayout body, Alarm a) {
        middayBox = ui.col();
        middayBox.addView(ui.section("Mittagswetter"), ui.top(8));
        LinearLayout card = ui.card(ui.p.surface, 20);
        card.setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(16));
        card.addView(ui.text("Ansagen an", 16, ui.p.text));
        LinearLayout chips = ui.row();
        for (int i = 0; i < 7; i++) {
            final int idx = i;
            CheckBox c = ui.checkChip(Alarm.SHORT[i], "Mittagswetter am " + Alarm.LONG[i]);
            c.setChecked(a.hasMidday(i));
            c.setOnCheckedChangeListener((btn, on) -> {
                Alarm cur = store.get(alarmId);
                if (cur == null) return;
                cur.setMidday(idx, on);
                store.update(cur);
                updatePreview();
            });
            LinearLayout.LayoutParams l = ui.weight();
            if (i > 0) l.leftMargin = ui.dp(6);
            chips.addView(c, l);
        }
        card.addView(chips, ui.top(10));
        card.addView(ui.text("An anderen Tagen lässt der Lautsprecher diesen Baustein weg.", 13, ui.p.muted), ui.top(10));
        middayBox.addView(card, ui.fill());
        body.addView(middayBox, ui.fill());
    }

    private void buildVoice(LinearLayout body) {
        body.addView(ui.section("Stimme"), ui.top(8));
        voiceRow = ui.row();
        voiceRow.setMinimumHeight(ui.dp(56));
        voiceRow.setPadding(ui.dp(16), ui.dp(8), ui.dp(12), ui.dp(8));
        voiceRow.setBackground(ui.ripple(ui.shape(ui.p.surface, 20), 20));
        voiceRow.addView(ui.icon(R.drawable.i_speaker, ui.p.accent, 20));
        voiceText = ui.text("", 16, ui.p.text);
        LinearLayout.LayoutParams l = ui.weight();
        l.leftMargin = ui.dp(12);
        voiceRow.addView(voiceText, l);
        voiceRow.addView(ui.icon(R.drawable.i_chevron, ui.p.muted, 20));
        voiceRow.setClickable(true);
        voiceRow.setFocusable(true);
        voiceRow.setOnClickListener(v -> startActivity(new Intent(this, VoiceActivity.class)
                .putExtra(VoiceActivity.EXTRA_ALARM, alarmId)));
        body.addView(voiceRow, ui.fill());
        updateVoice();
    }

    private void updateVoice() {
        if (voiceText == null) return;
        Alarm a = store.get(alarmId);
        String label = a != null ? VoiceSpec.label(a.voice, a.voiceLabel) : VoiceSpec.DEFAULT_LABEL;
        if (a != null && VoiceSpec.isGemini(a.voice) && !a.voiceStyle.trim().isEmpty()) {
            label += " · " + Ui.ellipsize(GeminiTts.styleLabel(a.voiceStyle), 40);
        }
        voiceText.setText(label);
        voiceRow.setContentDescription("Stimme: " + label + ". Ändern");
    }

    private void buildPreview(LinearLayout body) {
        body.addView(ui.section("Vorschau"), ui.top(8));
        LinearLayout card = ui.card(ui.p.surface, 20);
        card.setPadding(ui.dp(16), ui.dp(16), ui.dp(16), ui.dp(16));
        previewCaption = ui.text("", 13, ui.p.muted);
        card.addView(previewCaption);
        previewText = ui.text("", 17, ui.p.text);
        previewText.setLineSpacing(0, 1.2f);
        card.addView(previewText, ui.top(12));

        LinearLayout listen = ui.row();
        listen.setGravity(Gravity.CENTER);
        listen.setMinimumHeight(ui.dp(48));
        listen.setBackground(ui.ripple(ui.shape(ui.p.tonal, 24), 24));
        listenIcon = ui.icon(R.drawable.i_play, ui.p.onTonal, 20);
        listen.addView(listenIcon);
        listenLabel = ui.medium("Auf dem Handy anhören", 15, ui.p.onTonal);
        LinearLayout.LayoutParams ll = ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ll.leftMargin = ui.dp(10);
        listen.addView(listenLabel, ll);
        listen.setClickable(true);
        listen.setFocusable(true);
        listen.setOnClickListener(v -> toggleListen());
        card.addView(listen, ui.top(12));
        body.addView(card, ui.fill());
    }

    private LinearLayout buildSnackbar() {
        LinearLayout s = ui.row();
        s.setMinimumHeight(ui.dp(48));
        s.setPadding(ui.dp(16), ui.dp(4), ui.dp(4), ui.dp(4));
        s.setBackground(ui.shape(ui.p.snackBg, 8));
        s.setElevation(ui.dp(6));
        s.addView(ui.text("Standardtext wiederhergestellt", 14, ui.p.snackText), ui.weight());
        TextView undo = ui.medium("Rückgängig", 14, ui.p.snackAction);
        undo.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10));
        undo.setMinHeight(ui.dp(44));
        undo.setGravity(Gravity.CENTER);
        undo.setBackground(ui.ripple(null, 20));
        undo.setClickable(true);
        undo.setFocusable(true);
        undo.setOnClickListener(v -> undoRestore());
        s.addView(undo);
        s.setVisibility(View.GONE);
        s.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        return s;
    }

    // ---------- Verhalten ----------

    private void onTextChanged() {
        String text = field.getText().toString();
        Alarm a = store.get(alarmId);
        if (a != null && !text.equals(a.greeting)) {
            a.greeting = text;
            store.update(a);
        }
        if (!restoring) hideSnackbar(); // nach weiterem Tippen gibt es nichts mehr rückgängig zu machen
        updateMiddayVisibility();
        updatePreview();
    }

    private void restoreStandard() {
        String current = field.getText().toString();
        if (current.equals(Greeting.STANDARD)) {
            Toast.makeText(this, "Das ist bereits der Standardtext.", Toast.LENGTH_SHORT).show();
            return;
        }
        restoring = true;
        try {
            field.setTokenText(Greeting.STANDARD);
        } finally {
            restoring = false;
        }
        undoText = current;
        snackbar.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideSnack);
        handler.postDelayed(hideSnack, snackTimeout());
    }

    private void undoRestore() {
        String t = undoText;
        hideSnackbar();
        if (t != null) field.setTokenText(t);
    }

    private void hideSnackbar() {
        handler.removeCallbacks(hideSnack);
        undoText = null;
        if (snackbar != null) snackbar.setVisibility(View.GONE);
    }

    /** Mit TalkBack und Co. bleibt der Hinweis länger stehen. */
    private int snackTimeout() {
        if (Build.VERSION.SDK_INT >= 29) {
            AccessibilityManager am = getSystemService(AccessibilityManager.class);
            if (am != null) {
                return am.getRecommendedTimeoutMillis(SNACK_MS,
                        AccessibilityManager.FLAG_CONTENT_TEXT | AccessibilityManager.FLAG_CONTENT_CONTROLS);
            }
        }
        return SNACK_MS;
    }

    private void updateMiddayVisibility() {
        if (middayBox == null) return;
        middayBox.setVisibility(Greeting.usesMidday(field.getText().toString()) ? View.VISIBLE : View.GONE);
    }

    /** Der Text, wie er am Vorschau-Tag gesprochen würde. Für die Anzeige mit Hinweis, solange das Wetter lädt. */
    private String spoken(boolean forDisplay) {
        Alarm a = store.get(alarmId);
        String template = field.getText().toString();
        int mask = a != null ? a.middayDays : prefs.defaultMiddayMask();
        boolean midday = Greeting.usesMidday(template) && Alarm.maskOn(mask, previewTime);
        String weather, middayText;
        if (forecastLoading) {
            weather = forDisplay ? "[Wetter wird geladen …]" : "";
            middayText = forDisplay && midday ? "[Mittagswetter wird geladen …]" : "";
        } else {
            Weather.Day d = Weather.evaluate(forecast, previewTime, midday);
            weather = Weather.todaySentence(d);
            middayText = Weather.middaySentence(d);
        }
        return Greeting.render(template, prefs.name(), previewTime, weather, middayText);
    }

    private void updatePreview() {
        if (previewText == null || previewCaption == null) return;
        String s = spoken(true);
        previewText.setText(s.isEmpty() ? "Keine Begrüßung – nur Radio." : "„" + s + "“");
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(previewTime);
        String hm = c.get(Calendar.HOUR_OF_DAY) + ":" + Alarm.two(c.get(Calendar.MINUTE));
        StringBuilder cap = new StringBuilder("So klingt es am ").append(Greeting.weekday(previewTime)).append(", ")
                .append(Greeting.date(previewTime)).append(", um ").append(hm);
        if (Greeting.usesWeather(field.getText().toString())) {
            if (forecastLoading) cap.append(" – die Wettervorhersage wird geladen …");
            else if (forecast == null) cap.append(" – die Wettervorhersage ist gerade nicht abrufbar.");
            else cap.append(" – mit der aktuellen Wettervorhersage.");
        } else {
            cap.append('.');
        }
        previewCaption.setText(cap);
    }

    private void loadForecast() {
        int days = daysFromToday(previewTime) + 1;
        new Thread(() -> {
            String json = null;
            for (int attempt = 0; attempt < 2 && json == null; attempt++) {
                try {
                    json = Weather.fetchForecast(prefs.lat(), prefs.lon(), Math.max(2, days));
                } catch (Exception ignored) {
                }
            }
            String result = json;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                forecast = result;
                forecastLoading = false;
                updatePreview();
            });
        }, "sonnenwecker-vorschau").start();
    }

    private static int daysFromToday(long when) {
        Calendar a = Calendar.getInstance();
        Calendar b = Calendar.getInstance();
        b.setTimeInMillis(when);
        int d = 0;
        while (d < 16 && (a.get(Calendar.YEAR) != b.get(Calendar.YEAR)
                || a.get(Calendar.DAY_OF_YEAR) != b.get(Calendar.DAY_OF_YEAR))) {
            a.add(Calendar.DAY_OF_MONTH, 1);
            d++;
        }
        return d;
    }

    // ---------- Anhören auf dem Handy ----------

    private void toggleListen() {
        if (player.current() != null) {
            player.stop();
            return;
        }
        String text = spoken(false);
        if (text.isEmpty()) {
            Toast.makeText(this, "Der Text ist leer – morgens läuft dann nur das Radio.", Toast.LENGTH_LONG).show();
            return;
        }
        Alarm a = store.get(alarmId);
        player.play(a != null ? a.voice : VoiceSpec.DEFAULT, a != null ? a.voiceStyle : "", text);
    }

    @Override
    public void onState(String spec, boolean playing) {
        setSpeaking(playing);
    }

    @Override
    public void onError(String spec, String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void setSpeaking(boolean on) {
        if (listenIcon == null) return;
        listenIcon.setImageResource(on ? R.drawable.i_stop : R.drawable.i_play);
        listenLabel.setText(on ? "Anhören stoppen" : "Auf dem Handy anhören");
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) player.stop();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(hideSnack);
        if (player != null) player.release();
        super.onDestroy();
    }
}
