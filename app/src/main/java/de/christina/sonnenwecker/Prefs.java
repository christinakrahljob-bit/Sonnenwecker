package de.christina.sonnenwecker;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Alle Einstellungen der App an einem Ort. */
public final class Prefs {
    public static final String DEFAULT_RADIO = "https://liveradio.sr.de/sr/sr3/mp3/128/stream.mp3";

    private final SharedPreferences sp;

    public Prefs(Context ctx) {
        sp = ctx.getApplicationContext().getSharedPreferences("sonnenwecker", Context.MODE_PRIVATE);
    }

    // --- Allgemein ---
    public String name() { return sp.getString("name", "Christina"); }
    public int leadMinutes() { return sp.getInt("lead", 15); }
    public String radioUrl() { return sp.getString("radioUrl", DEFAULT_RADIO); }
    public String radioTitle() { return sp.getString("radioTitle", "SR3"); }
    public double lat() { return Double.longBitsToDouble(sp.getLong("lat", Double.doubleToLongBits(49.4719))); }
    public double lon() { return Double.longBitsToDouble(sp.getLong("lon", Double.doubleToLongBits(5.9533))); }
    public int volumePercent() { return sp.getInt("volume", 35); }
    /** Wochentage (Calendar.DAY_OF_WEEK als Ziffern), an denen das Mittagswetter angesagt wird. 2=Mo, 4=Mi. */
    public String middayDays() { return sp.getString("middayDays", "24"); }

    // --- Hue ---
    public String hueIp() { return sp.getString("hueIp", ""); }
    public String hueUser() { return sp.getString("hueUser", ""); }
    public String lightId() { return sp.getString("lightId", ""); }
    public String lightIdV1() { return sp.getString("lightIdV1", ""); }
    public String lightName() { return sp.getString("lightName", ""); }

    // --- Lautsprecher ---
    public String castName() { return sp.getString("castName", ""); }
    public String castHost() { return sp.getString("castHost", ""); }
    public int castPort() { return sp.getInt("castPort", 8009); }

    // --- Planung ---
    public long nextTarget() { return sp.getLong("nextTarget", 0); }
    public long lastWakeTarget() { return sp.getLong("lastWakeTarget", 0); }
    public long sunriseDoneFor() { return sp.getLong("sunriseDoneFor", 0); }
    /** Zuletzt gesehener Wecker der Uhr-App (falls eine fremde Erinnerung ihn gerade verdeckt). */
    public long clockTarget() { return sp.getLong("clockTarget", 0); }

    // --- Hue-Automation ---
    /** Auf diese Weckzeit soll die Hue-Automation gestellt sein; 0 = ausgeschaltet. */
    public long lightTarget() { return sp.getLong("lightTarget", 0); }
    /** Zuletzt erfolgreich auf der Bridge eingestellter Zustand ("on:<Zeit>", "off", "test" oder leer). */
    public String syncedKey() { return sp.getString("syncedKey", ""); }
    /** Bis dahin läuft ein Test – die Automation wird nicht verändert. */
    public long testUntil() { return sp.getLong("testUntil", 0); }
    /** Dauer des Sonnenaufgangs laut Hue-Automation (zuletzt gelesen). */
    public long fadeMs() { return sp.getLong("fadeMs", 30 * 60_000L); }
    /** Ergebnis des letzten Abgleichs, zur Anzeige. */
    public String hueSync() { return sp.getString("hueSync", "Noch nicht mit der Hue-Automation abgeglichen."); }

    public SharedPreferences.Editor edit() { return sp.edit(); }

    public void setLong(String key, long v) { sp.edit().putLong(key, v).apply(); }
    public void setDouble(String key, double v) { sp.edit().putLong(key, Double.doubleToLongBits(v)).apply(); }

    // --- Protokoll (für die Anzeige in der App) ---
    public synchronized void log(String line) {
        String ts = new SimpleDateFormat("dd.MM. HH:mm:ss", Locale.GERMANY).format(new Date());
        String old = sp.getString("log", "");
        String all = ts + "  " + line + "\n" + old;
        String[] lines = all.split("\n");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(lines.length, 25); i++) sb.append(lines[i]).append('\n');
        sp.edit().putString("log", sb.toString()).apply();
    }

    public String log() { return sp.getString("log", ""); }
}
