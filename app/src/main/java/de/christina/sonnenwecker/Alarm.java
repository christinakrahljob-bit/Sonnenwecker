package de.christina.sonnenwecker;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;

/**
 * Ein Wecker wie in der Uhr-App: Uhrzeit, Wochentage (keine = einmalig), an/aus und optionale Bezeichnung.
 * Wochentage als Bitmaske: Bit 0 = Montag … Bit 6 = Sonntag.
 */
public final class Alarm {
    public static final String[] SHORT = {"Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"};
    public static final String[] LONG = {"Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag"};

    public int id;
    public int hour;
    public int minute;
    public int days;
    public boolean enabled;
    public String label = "";

    public Alarm(int id, int hour, int minute) {
        this.id = id;
        this.hour = hour;
        this.minute = minute;
        this.enabled = true;
    }

    public boolean isOneTime() {
        return days == 0;
    }

    public boolean hasDay(int index) {
        return (days & (1 << index)) != 0;
    }

    public void setDay(int index, boolean on) {
        if (on) days |= (1 << index);
        else days &= ~(1 << index);
    }

    /** Montag = 0 … Sonntag = 6 für einen Calendar.DAY_OF_WEEK-Wert. */
    static int dayIndex(int calendarDay) {
        return (calendarDay + 5) % 7;
    }

    /** Nächster Zeitpunkt nach {@code now}, an dem dieser Wecker klingelt (unabhängig von an/aus). */
    public long nextAfter(long now) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        for (int i = 0; i < 8; i++) {
            if (c.getTimeInMillis() > now && (isOneTime() || hasDay(dayIndex(c.get(Calendar.DAY_OF_WEEK))))) {
                return c.getTimeInMillis();
            }
            c.add(Calendar.DAY_OF_MONTH, 1);
            c.set(Calendar.HOUR_OF_DAY, hour); // falls eine Zeitumstellung dazwischen liegt
            c.set(Calendar.MINUTE, minute);
        }
        return Long.MAX_VALUE;
    }

    public String time() {
        return two(hour) + ":" + two(minute);
    }

    /** Kurze Beschreibung der Tage wie in der Uhr-App: „Mo–Fr“, „Sa, So“, „Täglich“, „Morgen“ … */
    public String daysText(long now) {
        if (isOneTime()) {
            if (!enabled) return "Nicht geplant";
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(now);
            int today = c.get(Calendar.DAY_OF_YEAR);
            c.setTimeInMillis(nextAfter(now));
            return c.get(Calendar.DAY_OF_YEAR) == today ? "Heute" : "Morgen";
        }
        if (days == 0x7F) return "Täglich";
        if (days == 0x1F) return "Mo–Fr";
        if (days == 0x60) return "Sa, So";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            if (hasDay(i)) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(SHORT[i]);
            }
        }
        return sb.toString();
    }

    /** Ausführliche Beschreibung für Screenreader. */
    public String daysSpoken() {
        if (isOneTime()) return "einmalig";
        if (days == 0x7F) return "täglich";
        if (days == 0x1F) return "Montag bis Freitag";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            if (hasDay(i)) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(LONG[i]);
            }
        }
        return sb.toString();
    }

    static String two(int v) {
        return v < 10 ? "0" + v : String.valueOf(v);
    }

    JSONObject toJson() throws JSONException {
        return new JSONObject()
                .put("id", id).put("h", hour).put("m", minute)
                .put("days", days).put("on", enabled).put("label", label);
    }

    static Alarm fromJson(JSONObject o) {
        Alarm a = new Alarm(o.optInt("id"), o.optInt("h"), o.optInt("m"));
        a.days = o.optInt("days", 0);
        a.enabled = o.optBoolean("on", true);
        a.label = o.optString("label", "");
        return a;
    }
}
