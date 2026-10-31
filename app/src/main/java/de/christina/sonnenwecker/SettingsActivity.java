package de.christina.sonnenwecker;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Einstellungen für den Ablauf beim Wecken. Änderungen werden sofort gespeichert. */
public class SettingsActivity extends Activity {
    private static final int[] FADES = {10, 15, 20, 30};

    private Ui ui;
    private Prefs prefs;
    private LinearLayout bridgeRow, automationRow, placeRow, speakerRow, stationRow;
    private ImageView bridgeIcon, automationIcon;
    private RadioGroup fadeGroup;
    private boolean updatingFade;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ui = new Ui(this);
        ui.applyWindow();
        prefs = new Prefs(this);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = ui.col();
        scroll.addView(root);
        root.addView(ui.header("Einstellungen", true));
        LinearLayout body = ui.col();
        body.setPadding(ui.dp(16), 0, ui.dp(16), ui.dp(32));
        root.addView(body);

        buildSunrise(body);
        buildGreeting(body);
        buildSpeaker(body);
        buildBackup(body);
        buildHelp(body);

        TextView foot = ui.text("Änderungen werden sofort gespeichert.", 13, ui.p.muted);
        foot.setGravity(android.view.Gravity.CENTER);
        body.addView(foot, ui.top(20));
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshHue();
        new Thread(() -> {
            BridgeSync.sync(this, true);
            runOnUiThread(this::refreshHue);
        }).start();
    }

    // ---------- Sonnenaufgang ----------

    private void buildSunrise(LinearLayout body) {
        body.addView(ui.section("Sonnenaufgang"));
        LinearLayout card = ui.card(ui.p.surface, 20);
        card.setPadding(0, ui.dp(4), 0, ui.dp(4));

        bridgeIcon = ui.icon(R.drawable.i_check, ui.p.ok, 22);
        bridgeRow = ui.listRow("Hue Bridge", "", bridgeIcon);
        ui.clickable(bridgeRow, this::pairDialog);
        card.addView(bridgeRow);

        card.addView(ui.divider());
        automationIcon = ui.icon(R.drawable.i_check, ui.p.ok, 22);
        automationRow = ui.listRow("Hue-Automation „" + BridgeSync.AUTOMATION_NAME + "“", "", automationIcon);
        ui.clickable(automationRow, () -> {
            Toast.makeText(this, "Prüfe die Hue-Automation …", Toast.LENGTH_SHORT).show();
            new Thread(() -> {
                BridgeSync.sync(this, true);
                runOnUiThread(this::refreshHue);
            }).start();
        });
        card.addView(automationRow);

        card.addView(ui.divider());
        LinearLayout fade = ui.col();
        fade.setPadding(ui.dp(20), ui.dp(12), ui.dp(20), ui.dp(16));
        TextView fl = ui.text("Dauer bis es hell ist", 16, ui.p.text);
        fade.addView(fl);
        fadeGroup = new RadioGroup(this);
        fadeGroup.setOrientation(RadioGroup.HORIZONTAL);
        for (int i = 0; i < FADES.length; i++) {
            RadioButton r = ui.radioChip(FADES[i] + " Min.");
            r.setId(View.generateViewId());
            r.setTag(FADES[i]);
            r.setContentDescription(FADES[i] + " Minuten");
            RadioGroup.LayoutParams l = new RadioGroup.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            if (i > 0) l.leftMargin = ui.dp(8);
            fadeGroup.addView(r, l);
        }
        fadeGroup.setOnCheckedChangeListener((g, id) -> {
            if (updatingFade || id == -1) return;
            int minutes = (Integer) g.findViewById(id).getTag();
            new Thread(() -> {
                String msg = BridgeSync.setFade(this, minutes);
                runOnUiThread(() -> {
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                    refreshHue();
                });
            }).start();
        });
        fade.addView(fadeGroup, ui.top(10));
        card.addView(fade);

        LinearLayout tb = ui.col();
        tb.setPadding(ui.dp(20), ui.dp(4), ui.dp(20), ui.dp(16));
        android.widget.Button test = ui.tonal("Sonnenaufgang testen");
        test.setOnClickListener(v -> {
            Toast.makeText(this, "Stelle die Hue-Automation für den Test …", Toast.LENGTH_SHORT).show();
            new Thread(() -> {
                String msg = BridgeSync.test(this);
                runOnUiThread(() -> {
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                    refreshHue();
                });
            }).start();
        });
        tb.addView(test, ui.fill());
        TextView th = ui.text("Der Sonnenaufgang beginnt in 1–2 Minuten. Danach stellt die App die Automation automatisch auf deinen Wecker zurück.", 13, ui.p.muted);
        tb.addView(th, ui.top(8));
        card.addView(tb);
        body.addView(card, ui.fill());
    }

    private void refreshHue() {
        boolean paired = !prefs.hueUser().isEmpty();
        Ui.setSubtitle(bridgeRow, paired ? "Verbunden · " + prefs.hueIp() : "Nicht gekoppelt – tippen zum Koppeln");
        setStatusIcon(bridgeIcon, paired);
        bridgeRow.setContentDescription("Hue Bridge, " + (paired ? "verbunden" : "nicht gekoppelt, zum Koppeln tippen"));

        String s = prefs.hueSync();
        String shortS = s.replace("Hue-Automation „" + BridgeSync.AUTOMATION_NAME + "“", "").replaceFirst("^[: ]+", "");
        Ui.setSubtitle(automationRow, shortS.isEmpty() ? s : shortS);
        setStatusIcon(automationIcon, prefs.hueOk());

        long fade = prefs.fadeMsKnown();
        updatingFade = true;
        fadeGroup.clearCheck();
        for (int i = 0; i < fadeGroup.getChildCount(); i++) {
            View c = fadeGroup.getChildAt(i);
            if (fade > 0 && ((Integer) c.getTag()) * 60_000L == fade) fadeGroup.check(c.getId());
            c.setEnabled(paired);
        }
        updatingFade = false;
    }

    private void setStatusIcon(ImageView v, boolean ok) {
        v.setImageResource(ok ? R.drawable.i_check : R.drawable.i_alert);
        v.setImageTintList(android.content.res.ColorStateList.valueOf(ok ? ui.p.ok : ui.p.warn));
    }

    private void pairDialog() {
        LinearLayout box = ui.col();
        box.setPadding(ui.dp(24), ui.dp(8), ui.dp(24), 0);
        TextView steps = ui.text("1. Prüfe die IP-Adresse der Bridge (wird automatisch gesucht).\n"
                + "2. Drücke den runden Knopf oben auf der Bridge.\n"
                + "3. Tippe innerhalb von 30 Sekunden auf „Koppeln“.", 15, ui.p.text2);
        box.addView(steps);
        EditText ip = new EditText(this);
        ip.setSingleLine(true);
        ip.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        ip.setHint("IP-Adresse, z. B. 192.168.1.20");
        ip.setText(prefs.hueIp());
        ip.setContentDescription("IP-Adresse der Bridge");
        box.addView(ip, ui.top(12));
        if (prefs.hueIp().isEmpty()) {
            new Thread(() -> {
                try {
                    String found = HueClient.discover();
                    if (found != null) runOnUiThread(() -> {
                        if (ip.getText().length() == 0) ip.setText(found);
                    });
                } catch (Exception ignored) {
                }
            }).start();
        }
        new AlertDialog.Builder(this)
                .setTitle("Mit Hue Bridge koppeln")
                .setView(box)
                .setPositiveButton("Koppeln", (d, w) -> {
                    String address = ip.getText().toString().trim();
                    if (address.isEmpty()) {
                        Toast.makeText(this, "Bitte die IP-Adresse eintragen", Toast.LENGTH_LONG).show();
                        return;
                    }
                    prefs.setString("hueIp", address);
                    new Thread(() -> {
                        try {
                            String user = HueClient.pair(address);
                            prefs.edit().putString("hueUser", user).putString("syncedKey", "").apply();
                            prefs.log("Mit Hue Bridge gekoppelt ✓");
                            BridgeSync.sync(this, true);
                            runOnUiThread(() -> {
                                Toast.makeText(this, "Gekoppelt ✓", Toast.LENGTH_SHORT).show();
                                refreshHue();
                            });
                        } catch (Exception e) {
                            runOnUiThread(() -> Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show());
                        }
                    }).start();
                })
                .setNegativeButton("Abbrechen", null)
                .show();
    }

    // ---------- Begrüßung ----------

    private void buildGreeting(LinearLayout body) {
        body.addView(ui.section("Begrüßung"), ui.top(8));
        LinearLayout card = ui.card(ui.p.surface, 20);
        card.setPadding(0, ui.dp(4), 0, ui.dp(4));

        TextView perAlarm = ui.text("Den Text der Begrüßung stellst du bei jedem Wecker ein.", 13, ui.p.muted);
        perAlarm.setPadding(ui.dp(20), ui.dp(12), ui.dp(20), ui.dp(4));
        card.addView(perAlarm);

        LinearLayout nameBox = ui.col();
        nameBox.setPadding(ui.dp(20), ui.dp(8), ui.dp(20), ui.dp(16));
        TextView nl = ui.text("Name", 16, ui.p.text);
        nameBox.addView(nl);
        EditText name = new EditText(this);
        name.setSingleLine(true);
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        name.setText(prefs.name());
        name.setTextColor(ui.p.text);
        name.setTextSize(16);
        name.setBackground(ui.outlined(ui.p.field, ui.p.outline, 1, 12));
        name.setPadding(ui.dp(14), 0, ui.dp(14), 0);
        name.setMinHeight(ui.dp(48));
        name.setId(View.generateViewId());
        nl.setLabelFor(name.getId());
        name.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(Editable s) {
                String v = s.toString().trim();
                if (!v.isEmpty()) prefs.setString("name", v);
            }
        });
        nameBox.addView(name, ui.top(8));
        TextView nameHint = ui.text("Für den Baustein „Name“ und den Weck-Bildschirm", 13, ui.p.muted);
        nameBox.addView(nameHint, ui.top(8));
        card.addView(nameBox);

        card.addView(ui.divider());
        placeRow = ui.listRow("Ort fürs Wetter", prefs.placeName(), ui.icon(R.drawable.i_chevron, ui.p.muted, 20));
        ui.clickable(placeRow, this::placeDialog);
        card.addView(placeRow);

        LinearLayout tb = ui.col();
        tb.setPadding(ui.dp(20), ui.dp(4), ui.dp(20), ui.dp(16));
        android.widget.Button test = ui.tonal("Begrüßung testen");
        test.setOnClickListener(v -> {
            startForegroundService(new Intent(this, WakeService.class).setAction(WakeService.ACTION_TEST_WAKE));
            Toast.makeText(this, "Gleich spricht der Lautsprecher …", Toast.LENGTH_SHORT).show();
        });
        tb.addView(test, ui.fill());
        tb.addView(ui.text("Über den Lautsprecher, mit dem Text des nächsten Weckers", 13, ui.p.muted), ui.top(8));
        card.addView(tb);
        body.addView(card, ui.fill());
    }

    private void placeDialog() {
        LinearLayout box = ui.col();
        box.setPadding(ui.dp(24), ui.dp(8), ui.dp(24), 0);
        EditText q = new EditText(this);
        q.setSingleLine(true);
        q.setHint("Ort, z. B. Audun-le-Tiche");
        q.setText(prefs.placeName());
        q.setContentDescription("Ort suchen");
        box.addView(q);
        new AlertDialog.Builder(this)
                .setTitle("Ort fürs Wetter")
                .setView(box)
                .setPositiveButton("Suchen", (d, w) -> {
                    String query = q.getText().toString().trim();
                    if (query.isEmpty()) return;
                    new Thread(() -> {
                        try {
                            List<Weather.Place> places = Weather.searchPlaces(query);
                            runOnUiThread(() -> choosePlace(places));
                        } catch (Exception e) {
                            runOnUiThread(() -> Toast.makeText(this, "Suche fehlgeschlagen: " + e.getMessage(), Toast.LENGTH_LONG).show());
                        }
                    }).start();
                })
                .setNegativeButton("Abbrechen", null)
                .show();
    }

    private void choosePlace(List<Weather.Place> places) {
        if (places.isEmpty()) {
            Toast.makeText(this, "Keinen Ort gefunden", Toast.LENGTH_LONG).show();
            return;
        }
        String[] names = new String[places.size()];
        for (int i = 0; i < names.length; i++) {
            Weather.Place pl = places.get(i);
            names[i] = pl.name + (pl.detail.isEmpty() ? "" : " (" + pl.detail + ")");
        }
        new AlertDialog.Builder(this)
                .setTitle("Welcher Ort?")
                .setItems(names, (d, which) -> {
                    Weather.Place pl = places.get(which);
                    prefs.setString("placeName", pl.name);
                    prefs.setDouble("lat", pl.lat);
                    prefs.setDouble("lon", pl.lon);
                    Ui.setSubtitle(placeRow, pl.name);
                })
                .setNegativeButton("Abbrechen", null)
                .show();
    }

    // ---------- Lautsprecher und Radio ----------

    private void buildSpeaker(LinearLayout body) {
        body.addView(ui.section("Lautsprecher und Radio"), ui.top(8));
        LinearLayout card = ui.card(ui.p.surface, 20);
        card.setPadding(0, ui.dp(4), 0, ui.dp(4));

        speakerRow = ui.listRow("Lautsprecher", speakerText(), ui.icon(R.drawable.i_chevron, ui.p.muted, 20));
        ui.clickable(speakerRow, this::speakerDialog);
        card.addView(speakerRow);

        card.addView(ui.divider());
        LinearLayout vol = ui.col();
        vol.setPadding(ui.dp(20), ui.dp(12), ui.dp(20), ui.dp(12));
        LinearLayout vr = ui.row();
        TextView vl = ui.text("Lautstärke", 16, ui.p.text);
        vr.addView(vl, ui.weight());
        TextView vv = ui.medium(prefs.volumePercent() + " %", 15, ui.p.onTonal);
        vr.addView(vv);
        vol.addView(vr, ui.fill());
        SeekBar sb = new SeekBar(this);
        sb.setMax(19);
        sb.setProgress(Math.max(0, prefs.volumePercent() / 5 - 1));
        sb.setProgressTintList(android.content.res.ColorStateList.valueOf(ui.p.accent));
        sb.setThumbTintList(android.content.res.ColorStateList.valueOf(ui.p.accent));
        sb.setContentDescription("Lautstärke");
        if (android.os.Build.VERSION.SDK_INT >= 30) sb.setStateDescription(prefs.volumePercent() + " Prozent");
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                int v = (progress + 1) * 5;
                vv.setText(v + " %");
                if (android.os.Build.VERSION.SDK_INT >= 30) s.setStateDescription(v + " Prozent");
                prefs.setInt("volume", v);
            }
            public void onStartTrackingTouch(SeekBar s) { }
            public void onStopTrackingTouch(SeekBar s) { }
        });
        sb.setMinimumHeight(ui.dp(40));
        vol.addView(sb, ui.top(6));
        card.addView(vol);

        card.addView(ui.divider());
        stationRow = ui.listRow("Sender", prefs.radioTitle(), ui.icon(R.drawable.i_chevron, ui.p.muted, 20));
        ui.clickable(stationRow, this::stationDialog);
        card.addView(stationRow);
        body.addView(card, ui.fill());
    }

    private String speakerText() {
        return prefs.castName().isEmpty() ? "Noch nicht gewählt – tippen zum Suchen" : prefs.castName();
    }

    private void speakerDialog() {
        AlertDialog searching = new AlertDialog.Builder(this)
                .setTitle("Lautsprecher")
                .setMessage("Suche Lautsprecher im WLAN (ca. 8 Sekunden) …")
                .setCancelable(true)
                .show();
        new Thread(() -> {
            List<CastDiscovery.Device> found = CastDiscovery.discover(this, 8000, null);
            runOnUiThread(() -> {
                if (!searching.isShowing()) return;
                searching.dismiss();
                if (found.isEmpty()) {
                    Toast.makeText(this, "Kein Lautsprecher gefunden – ist das Handy im selben WLAN?", Toast.LENGTH_LONG).show();
                    return;
                }
                String[] names = new String[found.size()];
                int checked = -1;
                for (int i = 0; i < names.length; i++) {
                    names[i] = found.get(i).name;
                    if (names[i].equals(prefs.castName())) checked = i;
                }
                new AlertDialog.Builder(this)
                        .setTitle("Welcher Lautsprecher?")
                        .setSingleChoiceItems(names, checked, (d, which) -> {
                            CastDiscovery.Device dev = found.get(which);
                            prefs.edit().putString("castName", dev.name).putString("castHost", dev.host)
                                    .putInt("castPort", dev.port).apply();
                            Ui.setSubtitle(speakerRow, dev.name);
                            d.dismiss();
                        })
                        .setNegativeButton("Abbrechen", null)
                        .show();
            });
        }).start();
    }

    private void stationDialog() {
        String[] options = {Prefs.DEFAULT_RADIO_TITLE, "Eigene Adresse …"};
        new AlertDialog.Builder(this)
                .setTitle("Sender")
                .setItems(options, (d, which) -> {
                    if (which == 0) {
                        prefs.edit().putString("radioTitle", Prefs.DEFAULT_RADIO_TITLE)
                                .putString("radioUrl", Prefs.DEFAULT_RADIO).apply();
                        Ui.setSubtitle(stationRow, Prefs.DEFAULT_RADIO_TITLE);
                    } else {
                        customStation();
                    }
                })
                .show();
    }

    private void customStation() {
        LinearLayout box = ui.col();
        box.setPadding(ui.dp(24), ui.dp(8), ui.dp(24), 0);
        EditText title = new EditText(this);
        title.setSingleLine(true);
        title.setHint("Name, z. B. SR1");
        title.setContentDescription("Name des Senders");
        box.addView(title);
        EditText url = new EditText(this);
        url.setSingleLine(true);
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        url.setHint("Stream-Adresse (https://…)");
        url.setContentDescription("Stream-Adresse");
        box.addView(url, ui.top(8));
        new AlertDialog.Builder(this)
                .setTitle("Eigener Sender")
                .setView(box)
                .setPositiveButton("Übernehmen", (d, w) -> {
                    String t = title.getText().toString().trim();
                    String u = url.getText().toString().trim();
                    if (u.isEmpty()) return;
                    if (t.isEmpty()) t = "Eigener Sender";
                    prefs.edit().putString("radioTitle", t).putString("radioUrl", u).apply();
                    Ui.setSubtitle(stationRow, t);
                })
                .setNegativeButton("Abbrechen", null)
                .show();
    }

    // ---------- Sicherheitsnetz ----------

    private void buildBackup(LinearLayout body) {
        body.addView(ui.section("Sicherheitsnetz"), ui.top(8));
        LinearLayout card = ui.card(ui.p.surface, 20);
        card.setPadding(0, ui.dp(4), 0, ui.dp(4));
        Switch sw = ui.toggle();
        sw.setChecked(prefs.backupEnabled());
        sw.setContentDescription("Vibration als Backup");
        sw.setOnCheckedChangeListener((b, on) -> {
            prefs.setBool("backupEnabled", on);
            if (!on) Scheduler.cancelBackup(this);
        });
        LinearLayout row = ui.listRow("Vibration als Backup",
                "5 Minuten nach der Weckzeit, falls du nicht „Ich bin wach“ tippst", sw);
        row.setOnClickListener(v -> sw.toggle());
        card.addView(row);
        body.addView(card, ui.fill());
    }

    // ---------- Hilfe ----------

    private void buildHelp(LinearLayout body) {
        body.addView(ui.section("Hilfe"), ui.top(8));
        LinearLayout card = ui.card(ui.p.surface, 20);
        card.setPadding(0, ui.dp(4), 0, ui.dp(4));
        LinearLayout st = ui.listRow("Status und Einrichtung", null, ui.icon(R.drawable.i_chevron, ui.p.muted, 20));
        st.setMinimumHeight(ui.dp(56));
        ui.clickable(st, () -> startActivity(new Intent(this, StatusActivity.class)));
        card.addView(st);
        card.addView(ui.divider());
        LinearLayout lg = ui.listRow("Protokoll", null, ui.icon(R.drawable.i_chevron, ui.p.muted, 20));
        lg.setMinimumHeight(ui.dp(56));
        ui.clickable(lg, () -> startActivity(new Intent(this, LogActivity.class)));
        card.addView(lg);
        body.addView(card, ui.fill());
    }

    static String hm(long t) {
        return new SimpleDateFormat("HH:mm", Locale.GERMANY).format(new Date(t));
    }
}
