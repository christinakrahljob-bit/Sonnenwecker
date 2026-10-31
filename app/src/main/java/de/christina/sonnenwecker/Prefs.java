package de.christina.sonnenwecker;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Alle Einstellungen der App an einem Ort. Änderungen werden sofort gespeichert. */
public final class Prefs {
    public static final String DEFAULT_RADIO = "https://liveradio.sr.de/sr/sr3/mp3/128/stream.mp3";
    public static final String DEFAULT_RADIO_TITLE = "SR3 Saarlandwelle";
    /** Backup-Vibration so lange nach der Weckzeit, falls nicht bestätigt. */
    public static final long BACKUP_DELAY_MS = 5 * 60_000L;

    private final SharedPreferences sp;
    private static volatile boolean migrated;

    /**
     * Die Einstellungen liegen im „geräteverschlüsselten“ Speicher, damit die Wecker auch nach einem nächtlichen
     * Neustart funktionieren, bevor das Handy entsperrt wurde.
     */
    public Prefs(Context ctx) {
        Context app = ctx.getApplicationContext() != null ? ctx.getApplicationContext() : ctx;
        Context dp = app.createDeviceProtectedStorageContext();
        migrate(app, dp);
        sp = dp.getSharedPreferences("sonnenwecker", Context.MODE_PRIVATE);
    }

    /** Einmalig: Einstellungen aus älteren Versionen in den geräteverschlüsselten Speicher verschieben. */
    private static void migrate(Context app, Context dp) {
        if (migrated) return;
        synchronized (Prefs.class) {
            if (migrated) return;
            android.os.UserManager um = app.getSystemService(android.os.UserManager.class);
            if (um == null || um.isUserUnlocked()) {
                dp.moveSharedPreferencesFrom(app, "sonnenwecker");
                migrated = true;
            }
        }
    }

    /** Speicherort für Dateien, der auch vor dem ersten Entsperren verfügbar ist. */
    public static java.io.File cacheDir(Context ctx) {
        return ctx.getApplicationContext().createDeviceProtectedStorageContext().getCacheDir();
    }

    public SharedPreferences.Editor edit() { return sp.edit(); }
    public void setLong(String key, long v) { sp.edit().putLong(key, v).apply(); }
    public void setDouble(String key, double v) { sp.edit().putLong(key, Double.doubleToLongBits(v)).apply(); }
    public void setString(String key, String v) { sp.edit().putString(key, v).apply(); }
    public void setInt(String key, int v) { sp.edit().putInt(key, v).apply(); }
    public void setBool(String key, boolean v) { sp.edit().putBoolean(key, v).apply(); }

    // --- Wecker ---
    public String alarmsJson() { return sp.getString("alarms", "[]"); }
    /** Nächste geplante Weckzeit (0 = keine). */
    public long nextTarget() { return sp.getLong("nextTarget", 0); }
    public int nextAlarmId() { return sp.getInt("nextAlarmId", 0); }
    /** Weckzeit, die gerade klingelt bzw. zuletzt geklingelt hat. */
    public long ringTarget() { return sp.getLong("ringTarget", 0); }
    /** Weckzeit, für die „Ich bin wach“ getippt wurde. */
    public long confirmedTarget() { return sp.getLong("confirmedTarget", 0); }
    public long greetStartedFor() { return sp.getLong("greetStartedFor", 0); }
    public boolean backupEnabled() { return sp.getBoolean("backupEnabled", true); }

    // --- Begrüßung ---
    public String name() { return sp.getString("name", "Christina"); }
    public String placeName() { return sp.getString("placeName", "Audun-le-Tiche"); }
    public double lat() { return Double.longBitsToDouble(sp.getLong("lat", Double.doubleToLongBits(49.4719))); }
    public double lon() { return Double.longBitsToDouble(sp.getLong("lon", Double.doubleToLongBits(5.9533))); }
    /** Wochentage (Calendar.DAY_OF_WEEK als Ziffern), an denen das Mittagswetter angesagt wird. 2=Mo, 4=Mi. */
    public String middayDays() { return sp.getString("middayDays", "24"); }
    /** Dieselben Tage als Bitmaske (Mo = Bit 0) – Vorgabe für neue Wecker und für Wecker aus älteren Versionen. */
    public int defaultMiddayMask() {
        int mask = 0;
        for (char c : middayDays().toCharArray()) {
            int dow = c - '0';
            if (dow >= 1 && dow <= 7) mask |= 1 << Alarm.dayIndex(dow);
        }
        return mask;
    }
    /** Wetter für den Weck-Bildschirm (vom letzten Wecken). */
    public String weatherToday() { return sp.getString("weatherToday", ""); }
    public String weatherMidday() { return sp.getString("weatherMidday", ""); }
    public String weatherMiddayRain() { return sp.getString("weatherMiddayRain", ""); }
    public long weatherFor() { return sp.getLong("weatherFor", 0); }

    // --- Lautsprecher und Radio ---
    public String radioUrl() { return sp.getString("radioUrl", DEFAULT_RADIO); }
    public String radioTitle() { return sp.getString("radioTitle", DEFAULT_RADIO_TITLE); }
    public int volumePercent() { return sp.getInt("volume", 35); }
    public String castName() { return sp.getString("castName", ""); }
    public String castHost() { return sp.getString("castHost", ""); }
    public int castPort() { return sp.getInt("castPort", 8009); }
    /** Was der Lautsprecher beim letzten Wecken macht – für den Weck-Bildschirm. */
    public String radioStatus() { return sp.getString("radioStatus", ""); }

    // --- Hue ---
    public String hueIp() { return sp.getString("hueIp", ""); }
    public String hueUser() { return sp.getString("hueUser", ""); }
    /** Auf diese Weckzeit soll die Hue-Automation gestellt sein; 0 = ausgeschaltet. */
    public long lightTarget() { return sp.getLong("lightTarget", 0); }
    /** Zuletzt erfolgreich auf der Bridge eingestellter Zustand ("on:<Zeit>", "off", "test" oder leer). */
    public String syncedKey() { return sp.getString("syncedKey", ""); }
    /** Bis dahin läuft ein Test – die Automation wird nicht verändert. */
    public long testUntil() { return sp.getLong("testUntil", 0); }
    /** Dauer des Sonnenaufgangs laut Hue-Automation; 0 = noch unbekannt. */
    public long fadeMsKnown() { return sp.getLong("fadeMs", 0); }
    /** Für Schutzzeiten: bekannte Dauer oder vorsichtshalber 30 Minuten. */
    public long fadeMs() { long f = fadeMsKnown(); return f > 0 ? f : 30 * 60_000L; }
    /** Ergebnis des letzten Abgleichs, zur Anzeige. */
    public String hueSync() { return sp.getString("hueSync", "Noch nicht mit der Hue-Automation abgeglichen."); }
    public boolean hueOk() { return sp.getBoolean("hueOk", false); }

    // --- Status ---
    public boolean statusOk() { return sp.getBoolean("statusOk", true); }

    // --- Protokoll ---
    public synchronized void log(String line) {
        String ts = new SimpleDateFormat("dd.MM. HH:mm:ss", Locale.GERMANY).format(new Date());
        String old = sp.getString("log", "");
        String all = ts + "  " + line + "\n" + old;
        String[] lines = all.split("\n");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(lines.length, 80); i++) sb.append(lines[i]).append('\n');
        sp.edit().putString("log", sb.toString()).apply();
    }

    public String log() { return sp.getString("log", ""); }
}
