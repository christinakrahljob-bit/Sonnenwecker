package de.christina.sonnenwecker;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Einstellungsbildschirm: Bridge koppeln, Lampe und Lautsprecher wählen, testen. */
public class MainActivity extends Activity {
    private static final String[] DAY_LABELS = {"Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"};
    private static final int[] DAY_VALUES = {2, 3, 4, 5, 6, 7, 1}; // Calendar.DAY_OF_WEEK

    private Prefs prefs;
    private LinearLayout root;
    private TextView status, log, hueState;
    private EditText ipEdit, nameEdit, leadEdit, radioEdit, latEdit, lonEdit, volumeEdit;
    private Spinner lightSpinner, speakerSpinner;
    private final CheckBox[] dayBoxes = new CheckBox[7];
    private Button batteryButton;
    private List<HueClient.Light> lights = new ArrayList<>();
    private List<CastDiscovery.Device> speakers = new ArrayList<>();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = new Prefs(this);
        setTitle("Sonnenwecker");

        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad * 2);
        scroll.addView(root);
        setContentView(scroll);

        status = text("", 17, true);
        root.addView(status);
        batteryButton = button("Akku-Optimierung für die App ausschalten", v -> askBatteryExemption());
        root.addView(batteryButton);
        root.addView(hint("Wichtig: Stelle deinen Wecker ganz normal in der Uhr-App. Die App erkennt ihn automatisch."));

        // --- Hue ---
        root.addView(header("1. Hue-Lampe"));
        ipEdit = edit("IP-Adresse der Bridge", prefs.hueIp(), InputType.TYPE_CLASS_TEXT);
        root.addView(ipEdit);
        root.addView(button("Bridge automatisch suchen", v -> findBridge()));
        root.addView(hint("Zum Koppeln: runden Knopf oben auf der Bridge drücken, dann innerhalb von 30 Sekunden hier tippen."));
        root.addView(button("Mit Bridge koppeln", v -> pairBridge()));
        hueState = hint("");
        root.addView(hueState);
        root.addView(button("Lampen laden", v -> loadLights()));
        lightSpinner = new Spinner(this);
        root.addView(lightSpinner);
        root.addView(button("Sonnenaufgang testen (10 Minuten)", v -> {
            saveAll();
            startTest(WakeService.ACTION_TEST_SUNRISE);
        }));

        // --- Lautsprecher ---
        root.addView(header("2. Lautsprecher"));
        root.addView(button("Lautsprecher im WLAN suchen", v -> findSpeakers()));
        speakerSpinner = new Spinner(this);
        root.addView(speakerSpinner);
        volumeEdit = edit("Lautstärke in %", String.valueOf(prefs.volumePercent()), InputType.TYPE_CLASS_NUMBER);
        root.addView(label("Lautstärke in %"));
        root.addView(volumeEdit);
        root.addView(button("Begrüßung + Radio jetzt testen", v -> {
            saveAll();
            startTest(WakeService.ACTION_TEST_WAKE);
        }));

        // --- Einstellungen ---
        root.addView(header("3. Einstellungen"));
        root.addView(label("Name in der Begrüßung"));
        nameEdit = edit("Name", prefs.name(), InputType.TYPE_CLASS_TEXT);
        root.addView(nameEdit);
        root.addView(label("Sonnenaufgang beginnt so viele Minuten vor dem Wecker"));
        leadEdit = edit("Minuten", String.valueOf(prefs.leadMinutes()), InputType.TYPE_CLASS_NUMBER);
        root.addView(leadEdit);
        root.addView(label("Radio-Adresse (Stream)"));
        radioEdit = edit("https://…", prefs.radioUrl(), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        root.addView(radioEdit);
        root.addView(label("Ort fürs Wetter (Breitengrad / Längengrad)"));
        int dec = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED;
        latEdit = edit("Breitengrad", String.valueOf(prefs.lat()), dec);
        lonEdit = edit("Längengrad", String.valueOf(prefs.lon()), dec);
        root.addView(latEdit);
        root.addView(lonEdit);
        root.addView(label("Mittagswetter (12–14 Uhr) ansagen an:"));
        LinearLayout days = new LinearLayout(this);
        days.setOrientation(LinearLayout.HORIZONTAL);
        String md = prefs.middayDays();
        for (int i = 0; i < 7; i++) {
            CheckBox cb = new CheckBox(this);
            cb.setText(DAY_LABELS[i]);
            cb.setChecked(md.indexOf(Character.forDigit(DAY_VALUES[i], 10)) >= 0);
            dayBoxes[i] = cb;
            days.addView(cb, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
        HorizontalWrap.wrap(root, days);

        root.addView(button("Speichern", v -> {
            saveAll();
            toast("Gespeichert");
            refresh();
        }));

        root.addView(header("Protokoll"));
        log = text("", 12, false);
        log.setTypeface(Typeface.MONOSPACE);
        root.addView(log);

        showSavedSelections();
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        status.setText(Scheduler.reschedule(this));
        log.setText(prefs.log());
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        batteryButton.setVisibility(pm.isIgnoringBatteryOptimizations(getPackageName()) ? View.GONE : View.VISIBLE);
        hueState.setText(prefs.hueUser().isEmpty() ? "Noch nicht gekoppelt." : "Gekoppelt ✓");
    }

    private void showSavedSelections() {
        if (!prefs.lightName().isEmpty()) {
            lightSpinner.setAdapter(adapter(java.util.Collections.singletonList(prefs.lightName())));
        }
        if (!prefs.castName().isEmpty()) {
            speakerSpinner.setAdapter(adapter(java.util.Collections.singletonList(prefs.castName())));
        }
    }

    private void saveAll() {
        SharedPreferences.Editor e = prefs.edit();
        e.putString("hueIp", ipEdit.getText().toString().trim());
        e.putString("name", nameEdit.getText().toString().trim());
        e.putInt("lead", parseInt(leadEdit, 15, 1, 120));
        e.putInt("volume", parseInt(volumeEdit, 35, 1, 100));
        String radio = radioEdit.getText().toString().trim();
        e.putString("radioUrl", radio.isEmpty() ? Prefs.DEFAULT_RADIO : radio);
        try {
            e.putLong("lat", Double.doubleToLongBits(Double.parseDouble(latEdit.getText().toString().trim())));
            e.putLong("lon", Double.doubleToLongBits(Double.parseDouble(lonEdit.getText().toString().trim())));
        } catch (NumberFormatException ignored) {
            toast("Ort ungültig – alter Wert bleibt");
        }
        StringBuilder md = new StringBuilder();
        for (int i = 0; i < 7; i++) if (dayBoxes[i].isChecked()) md.append(DAY_VALUES[i]);
        e.putString("middayDays", md.toString());

        int li = lightSpinner.getSelectedItemPosition();
        if (!lights.isEmpty() && li >= 0 && li < lights.size()) {
            HueClient.Light l = lights.get(li);
            e.putString("lightId", l.id).putString("lightIdV1", l.idV1).putString("lightName", l.name);
        }
        int si = speakerSpinner.getSelectedItemPosition();
        if (!speakers.isEmpty() && si >= 0 && si < speakers.size()) {
            CastDiscovery.Device d = speakers.get(si);
            e.putString("castName", d.name).putString("castHost", d.host).putInt("castPort", d.port);
        }
        e.apply();
    }

    // ---------- Aktionen ----------

    private void findBridge() {
        background("Suche Bridge …", () -> {
            String ip = HueClient.discover();
            runOnUiThread(() -> {
                if (ip == null) {
                    toast("Keine Bridge gefunden – IP bitte aus der Hue-App abschreiben (Einstellungen → Bridges)");
                } else {
                    ipEdit.setText(ip);
                    saveAll();
                    toast("Bridge gefunden: " + ip);
                }
            });
        });
    }

    private void pairBridge() {
        saveAll();
        String ip = prefs.hueIp();
        if (ip.isEmpty()) {
            toast("Bitte zuerst die Bridge suchen oder die IP eintragen");
            return;
        }
        background("Kopple …", () -> {
            String user = HueClient.pair(ip);
            prefs.edit().putString("hueUser", user).apply();
            runOnUiThread(() -> {
                toast("Gekoppelt ✓");
                refresh();
                loadLights();
            });
        });
    }

    private void loadLights() {
        if (prefs.hueUser().isEmpty()) {
            toast("Bitte zuerst mit der Bridge koppeln");
            return;
        }
        background("Lade Lampen …", () -> {
            List<HueClient.Light> l = HueClient.lights(prefs.hueIp(), prefs.hueUser());
            runOnUiThread(() -> {
                lights = l;
                List<String> names = new ArrayList<>();
                int sel = 0;
                for (int i = 0; i < l.size(); i++) {
                    HueClient.Light x = l.get(i);
                    names.add(x.name + (x.sunrise ? "  ☀ Sonnenaufgang" : ""));
                    if (x.id.equals(prefs.lightId()) || (prefs.lightId().isEmpty() && x.name.toLowerCase().contains("iris"))) sel = i;
                }
                lightSpinner.setAdapter(adapter(names));
                if (!names.isEmpty()) lightSpinner.setSelection(sel);
                toast(l.size() + " Lampen gefunden – bitte die Iris auswählen und speichern");
            });
        });
    }

    private void findSpeakers() {
        background("Suche Lautsprecher (ca. 8 Sek.) …", () -> {
            List<CastDiscovery.Device> d = CastDiscovery.discover(this, 8000, null);
            runOnUiThread(() -> {
                speakers = d;
                List<String> names = new ArrayList<>();
                int sel = 0;
                for (int i = 0; i < d.size(); i++) {
                    names.add(d.get(i).name);
                    if (d.get(i).name.equals(prefs.castName()) || d.get(i).name.toLowerCase().contains("jbl")) sel = i;
                }
                speakerSpinner.setAdapter(adapter(names));
                if (!names.isEmpty()) speakerSpinner.setSelection(sel);
                toast(d.isEmpty() ? "Kein Lautsprecher gefunden – ist das Handy im selben WLAN?"
                        : d.size() + " gefunden – bitte den JBL auswählen und speichern");
            });
        });
    }

    private void startTest(String action) {
        startForegroundService(new android.content.Intent(this, WakeService.class).setAction(action));
        toast("Test läuft – Ergebnis erscheint unten im Protokoll");
        log.postDelayed(() -> log.setText(prefs.log()), 15000);
        log.postDelayed(() -> log.setText(prefs.log()), 45000);
    }

    private void askBatteryExemption() {
        Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName()));
        startActivity(i);
    }

    // ---------- Hilfsfunktionen ----------

    interface Job { void run() throws Exception; }

    private void background(String msg, Job job) {
        toast(msg);
        new Thread(() -> {
            try {
                job.run();
            } catch (Exception e) {
                runOnUiThread(() -> toast(e.getMessage() != null ? e.getMessage() : e.toString()));
            }
        }).start();
    }

    private ArrayAdapter<String> adapter(List<String> items) {
        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, items);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return a;
    }

    private int parseInt(EditText e, int def, int min, int max) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(e.getText().toString().trim())));
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private TextView text(String s, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(6), 0, dp(6));
        return t;
    }

    private TextView header(String s) {
        TextView t = text(s, 20, true);
        t.setPadding(0, dp(24), 0, dp(4));
        return t;
    }

    private TextView label(String s) {
        TextView t = text(s, 14, false);
        t.setPadding(0, dp(10), 0, 0);
        return t;
    }

    private TextView hint(String s) {
        TextView t = text(s, 13, false);
        t.setAlpha(0.7f);
        return t;
    }

    private EditText edit(String hint, String value, int type) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setInputType(type);
        e.setSingleLine(true);
        return e;
    }

    private Button button(String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    /** Sieben Wochentage passen nicht in eine Zeile – auf zwei Zeilen verteilen. */
    static final class HorizontalWrap {
        static void wrap(LinearLayout parent, LinearLayout row) {
            LinearLayout second = new LinearLayout(parent.getContext());
            second.setOrientation(LinearLayout.HORIZONTAL);
            List<View> moved = new ArrayList<>();
            for (int i = 4; i < row.getChildCount(); i++) moved.add(row.getChildAt(i));
            for (View v : moved) {
                row.removeView(v);
                second.addView(v, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            }
            // Platzhalter, damit beide Zeilen gleich breite Spalten haben
            View spacer = new View(parent.getContext());
            second.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));
            parent.addView(row);
            parent.addView(second);
        }
    }
}
