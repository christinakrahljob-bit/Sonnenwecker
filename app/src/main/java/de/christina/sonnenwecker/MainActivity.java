package de.christina.sonnenwecker;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Startbildschirm: Wecker-Liste wie in der Uhr-App. */
public class MainActivity extends Activity {
    private Ui ui;
    private Prefs prefs;
    private AlarmStore store;
    private LinearLayout nextCard, list;
    private int expandedId = -1;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ui = new Ui(this);
        ui.applyWindow();
        prefs = new Prefs(this);
        store = new AlarmStore(this);
        Ringing.ensureChannels(this);
        if (state != null) expandedId = state.getInt("expanded", -1);

        FrameLayout frame = new FrameLayout(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        LinearLayout root = ui.col();
        root.setPadding(0, 0, 0, ui.dp(112));
        scroll.addView(root);
        frame.addView(scroll);

        LinearLayout header = ui.header("Sonnenwecker", false);
        ImageButton settings = ui.iconButton(R.drawable.i_settings, "Einstellungen", ui.p.text2);
        settings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        header.addView(settings);
        root.addView(header);

        LinearLayout body = ui.col();
        body.setPadding(ui.dp(16), 0, ui.dp(16), 0);
        root.addView(body);

        nextCard = ui.card(ui.p.surface, 24);
        nextCard.setPadding(ui.dp(18), ui.dp(14), ui.dp(18), ui.dp(16));
        body.addView(nextCard, ui.fill());

        body.addView(ui.section("Wecker"), ui.top(8));
        list = ui.col();
        body.addView(list, ui.fill());

        ImageButton fab = new ImageButton(this);
        fab.setImageResource(R.drawable.i_add);
        fab.setImageTintList(android.content.res.ColorStateList.valueOf(ui.p.onAccent));
        fab.setBackground(ui.ripple(ui.shape(ui.p.accent, 20), 20));
        fab.setElevation(ui.dp(6));
        fab.setContentDescription("Neuer Wecker");
        fab.setOnClickListener(v -> newAlarm());
        FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(ui.dp(64), ui.dp(64), Gravity.BOTTOM | Gravity.END);
        fl.rightMargin = ui.dp(20);
        fl.bottomMargin = ui.dp(28);
        frame.addView(fab, fl);

        setContentView(frame);

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("expanded", expandedId);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Scheduler.reschedule(this);
        renderNext();
        renderList();
        syncInBackground(true);
    }

    // ---------- Nächstes Wecken ----------

    private void renderNext() {
        nextCard.removeAllViews();
        long now = System.currentTimeMillis();
        AlarmStore.Next next = store.next(now);

        LinearLayout top = ui.row();
        TextView label = ui.medium("Nächstes Wecken", 14, ui.p.muted);
        Ui.heading(label);
        top.addView(label, ui.weight());
        top.addView(statusChip());
        nextCard.addView(top, ui.fill());

        if (next == null) {
            nextCard.addView(ui.medium("Kein Wecker aktiv", 22, ui.p.text), ui.top(4));
            nextCard.addView(ui.text("Tippe auf +, um einen Wecker anzulegen.", 13, ui.p.muted), ui.top(2));
            return;
        }
        String hm = new SimpleDateFormat("HH:mm", Locale.GERMANY).format(new Date(next.time));
        TextView when = ui.medium(Scheduler.dayText(next.time, now) + " · " + hm, 22, ui.p.text);
        nextCard.addView(when, ui.top(4));
        String detail;
        if (prefs.fadeMsKnown() > 0 && prefs.hueOk()) {
            String start = new SimpleDateFormat("HH:mm", Locale.GERMANY).format(new Date(next.time - prefs.fadeMsKnown()));
            detail = "Sonnenaufgang ab " + start + " · Begrüßung und Radio um " + hm;
        } else {
            detail = "Begrüßung und Radio um " + hm;
        }
        nextCard.addView(ui.text(detail, 13, ui.p.muted), ui.top(2));
    }

    private View statusChip() {
        boolean ok = StatusActivity.quickOk(this);
        LinearLayout chip = ui.row();
        chip.setPadding(ui.dp(12), 0, ui.dp(12), 0);
        chip.setMinimumHeight(ui.dp(32));
        int bg = ok ? ui.p.okBg : ui.p.warnBg;
        int fg = ok ? ui.p.ok : ui.p.warn;
        chip.setBackground(ui.ripple(ui.shape(bg, 16), 16));
        chip.addView(ui.icon(ok ? R.drawable.i_check : R.drawable.i_alert, fg, 16));
        TextView t = ui.medium(ok ? "Alles bereit" : "Bitte prüfen", 13, fg);
        LinearLayout.LayoutParams l = ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        l.leftMargin = ui.dp(6);
        chip.addView(t, l);
        chip.setClickable(true);
        chip.setFocusable(true);
        chip.setContentDescription((ok ? "Alles bereit" : "Bitte prüfen") + ", Status anzeigen");
        chip.setOnClickListener(v -> startActivity(new Intent(this, StatusActivity.class)));
        return chip;
    }

    // ---------- Wecker-Liste ----------

    private void renderList() {
        list.removeAllViews();
        List<Alarm> alarms = store.all();
        if (alarms.isEmpty()) {
            TextView empty = ui.text("Noch kein Wecker. Tippe unten rechts auf +.", 15, ui.p.muted);
            empty.setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8));
            list.addView(empty);
            return;
        }
        long now = System.currentTimeMillis();
        for (Alarm a : alarms) {
            View card = a.id == expandedId ? expandedCard(a, now) : collapsedCard(a, now);
            LinearLayout.LayoutParams l = ui.fill();
            l.bottomMargin = ui.dp(12);
            list.addView(card, l);
        }
    }

    private View collapsedCard(Alarm a, long now) {
        LinearLayout card = ui.card(ui.p.surface, 24);
        card.setPadding(ui.dp(20), ui.dp(8), ui.dp(12), ui.dp(4));
        card.setBackground(ui.ripple(ui.shape(ui.p.surface, 24), 24));
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> expand(a.id));

        LinearLayout r1 = ui.row();
        TextView time = ui.light(a.time(), 56, a.enabled ? ui.p.text : ui.p.outline);
        time.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        r1.addView(time, ui.weight());
        r1.addView(toggleFor(a));
        card.addView(r1, ui.fill());

        LinearLayout r2 = ui.row();
        String days = a.daysText(now) + (a.label.isEmpty() ? "" : " · " + a.label);
        TextView d = ui.text(days, 15, a.enabled ? ui.p.text2 : ui.p.outline);
        d.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        r2.addView(d, ui.weight());
        ImageButton more = ui.iconButton(R.drawable.i_expand, "Wecker " + a.time() + " bearbeiten", ui.p.text2);
        more.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(44), ui.dp(44)));
        more.setOnClickListener(v -> expand(a.id));
        r2.addView(more);
        card.addView(r2, ui.fill());

        card.setContentDescription("Wecker " + a.time() + ", " + a.daysSpoken()
                + (a.label.isEmpty() ? "" : ", " + a.label) + (a.enabled ? ", an" : ", aus"));
        return card;
    }

    private View expandedCard(Alarm a, long now) {
        LinearLayout card = ui.card(ui.p.surfaceHigh, 24);
        card.setPadding(ui.dp(20), ui.dp(8), ui.dp(12), ui.dp(8));

        // Uhrzeit + Schalter
        LinearLayout r1 = ui.row();
        TextView time = ui.light(a.time(), 56, ui.p.onTonal);
        time.setBackground(ui.ripple(null, 12));
        time.setClickable(true);
        time.setFocusable(true);
        time.setContentDescription("Uhrzeit ändern, aktuell " + a.time());
        time.setOnClickListener(v -> pickTime(a));
        r1.addView(time, ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        r1.addView(new View(this), ui.weight());
        r1.addView(toggleFor(a));
        card.addView(r1, ui.fill());

        // Wochentage
        TextView rep = ui.text("Wiederholen", 14, ui.p.muted);
        card.addView(rep, ui.top(8));
        LinearLayout days = ui.row();
        for (int i = 0; i < 7; i++) {
            final int idx = i;
            CheckBox c = ui.dayCircle(Alarm.SHORT[i], Alarm.LONG[i]);
            c.setChecked(a.hasDay(i));
            c.setOnCheckedChangeListener((b, on) -> {
                Alarm cur = store.get(a.id);
                if (cur == null) return;
                cur.setDay(idx, on);
                cur.enabled = true;
                store.update(cur);
                changed(cur, false, false);
            });
            days.addView(c);
            if (i < 6) days.addView(new View(this), ui.weight());
        }
        LinearLayout.LayoutParams dl = ui.top(8);
        dl.rightMargin = ui.dp(8);
        card.addView(days, dl);

        // Bezeichnung
        LinearLayout label = ui.iconText(R.drawable.i_label,
                a.label.isEmpty() ? "Bezeichnung hinzufügen" : a.label, a.label.isEmpty() ? ui.p.text2 : ui.p.text);
        label.setOnClickListener(v -> editLabel(a));
        card.addView(label, ui.top(10));

        // Hinweis auf den Ablauf
        LinearLayout info = ui.card(ui.p.surface, 14);
        info.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(6));
        String ablauf = "Löst den Ablauf aus: Sonnenaufgang, Begrüßung, Radio"
                + (prefs.backupEnabled() ? " und Backup-Vibration." : ".");
        info.addView(ui.text(ablauf, 13, ui.p.text2));
        TextView link = ui.medium("Ablauf anpassen", 13, ui.p.onTonal);
        link.setPadding(0, ui.dp(10), ui.dp(12), ui.dp(10));
        link.setBackground(ui.ripple(null, 8));
        link.setClickable(true);
        link.setFocusable(true);
        link.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        info.addView(link);
        LinearLayout.LayoutParams il = ui.top(6);
        il.rightMargin = ui.dp(8);
        card.addView(info, il);

        // Löschen + Einklappen
        LinearLayout r4 = ui.row();
        LinearLayout del = ui.iconText(R.drawable.i_delete, "Löschen", ui.p.text2);
        del.setContentDescription("Wecker " + a.time() + " löschen");
        del.setOnClickListener(v -> {
            store.delete(a.id);
            expandedId = -1;
            Toast.makeText(this, "Wecker " + a.time() + " gelöscht", Toast.LENGTH_SHORT).show();
            afterChange();
            renderList();
        });
        r4.addView(del);
        r4.addView(new View(this), ui.weight());
        ImageButton less = ui.iconButton(R.drawable.i_collapse, "Einklappen", ui.p.text2);
        less.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(44), ui.dp(44)));
        less.setOnClickListener(v -> expand(-1));
        r4.addView(less);
        card.addView(r4, ui.top(4));
        return card;
    }

    private Switch toggleFor(Alarm a) {
        Switch s = ui.toggle();
        s.setChecked(a.enabled);
        s.setContentDescription("Wecker " + a.time() + ", " + a.daysSpoken());
        s.setOnCheckedChangeListener((b, on) -> {
            Alarm cur = store.get(a.id);
            if (cur == null) return;
            cur.enabled = on;
            store.update(cur);
            changed(cur, true, true);
        });
        return s;
    }

    private void expand(int id) {
        expandedId = id;
        renderList();
    }

    /** Nach jeder Änderung: neu planen, Hinweis „Wecker in …“, Anzeige aktualisieren. */
    private void changed(Alarm a, boolean rerender, boolean toast) {
        afterChange();
        if (toast && a.enabled) {
            long now = System.currentTimeMillis();
            Toast.makeText(this, Scheduler.untilText(a.nextAfter(now), now), Toast.LENGTH_SHORT).show();
        }
        // Bei geänderten Wochentagen die Liste nicht neu bauen, damit der TalkBack-Fokus bleibt
        if (rerender) renderList();
    }

    private void afterChange() {
        Scheduler.reschedule(this);
        renderNext();
        syncInBackground(false);
    }

    private void pickTime(Alarm a) {
        new TimePickerDialog(this, (view, h, m) -> {
            Alarm cur = store.get(a.id);
            if (cur == null) return;
            cur.hour = h;
            cur.minute = m;
            cur.enabled = true;
            store.update(cur);
            changed(cur, true, true);
        }, a.hour, a.minute, true).show();
    }

    private void newAlarm() {
        Calendar c = Calendar.getInstance();
        new TimePickerDialog(this, (view, h, m) -> {
            Alarm a = store.add(h, m);
            expandedId = a.id;
            changed(a, true, true);
        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show();
    }

    private void editLabel(Alarm a) {
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        e.setSingleLine(true);
        e.setText(a.label);
        e.setHint("z. B. Arbeit");
        e.setSelection(e.getText().length());
        FrameLayout box = new FrameLayout(this);
        box.setPadding(ui.dp(24), ui.dp(8), ui.dp(24), 0);
        box.addView(e);
        new AlertDialog.Builder(this)
                .setTitle("Bezeichnung")
                .setView(box)
                .setPositiveButton("Fertig", (d, w) -> {
                    Alarm cur = store.get(a.id);
                    if (cur == null) return;
                    cur.label = e.getText().toString().trim();
                    store.update(cur);
                    renderList();
                })
                .setNegativeButton("Abbrechen", null)
                .show();
    }

    private void syncInBackground(boolean force) {
        new Thread(() -> {
            BridgeSync.sync(this, force);
            runOnUiThread(() -> {
                if (!isFinishing()) renderNext();
            });
        }, "sonnenwecker-sync").start();
    }
}
