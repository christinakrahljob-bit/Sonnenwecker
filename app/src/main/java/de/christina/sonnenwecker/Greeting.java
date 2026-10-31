package de.christina.sonnenwecker;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Begrüßungstext mit Bausteinen wie {Datum}: Standardtext, Bausteine einsetzen und die Regeln,
 * mit denen das Textfeld Bausteine nur als Ganzes löscht.
 */
public final class Greeting {
    /** Die Bausteine in der Reihenfolge, in der sie im Editor angeboten werden. */
    public static final String[] LABELS = {"Name", "Wochentag", "Datum", "Uhrzeit", "Wetter heute", "Mittagswetter"};
    public static final String WEATHER = "{Wetter heute}";
    public static final String MIDDAY = "{Mittagswetter}";
    /** Standardtext für neue Wecker und für „Standardtext wiederherstellen“. */
    public static final String STANDARD =
            "Guten Morgen, {Name}! Es ist {Wochentag}, der {Datum}, {Uhrzeit}. {Wetter heute} {Mittagswetter}";

    static final Pattern TOKEN = Pattern.compile("\\{(Name|Wochentag|Datum|Uhrzeit|Wetter heute|Mittagswetter)\\}");
    private static final String[] MONTHS = {"Januar", "Februar", "März", "April", "Mai", "Juni", "Juli",
            "August", "September", "Oktober", "November", "Dezember"};

    private Greeting() { }

    public static String token(String label) {
        return "{" + label + "}";
    }

    public static boolean usesWeather(String template) {
        return template != null && (template.contains(WEATHER) || template.contains(MIDDAY));
    }

    public static boolean usesMidday(String template) {
        return template != null && template.contains(MIDDAY);
    }

    /** „Montag“ */
    public static String weekday(long when) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(when);
        return Alarm.LONG[Alarm.dayIndex(c.get(Calendar.DAY_OF_WEEK))];
    }

    /** „2. November“ – die Sprachausgabe beugt die Ordnungszahl passend („der zweite November“). */
    public static String date(long when) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(when);
        return c.get(Calendar.DAY_OF_MONTH) + ". " + MONTHS[c.get(Calendar.MONTH)];
    }

    /** „6 Uhr 45“ bzw. „7 Uhr“ */
    public static String time(long when) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(when);
        int m = c.get(Calendar.MINUTE);
        return c.get(Calendar.HOUR_OF_DAY) + " Uhr" + (m != 0 ? " " + m : "");
    }

    /**
     * Setzt die Bausteine ein.
     * @param weather fertiger Satz für {Wetter heute}; null oder leer = weglassen
     * @param midday  fertiger Satz für {Mittagswetter}; null oder leer = weglassen
     */
    public static String render(String template, String name, long when, String weather, String midday) {
        if (template == null) return "";
        Matcher m = TOKEN.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String v;
            switch (m.group(1)) {
                case "Name": v = name; break;
                case "Wochentag": v = weekday(when); break;
                case "Datum": v = date(when); break;
                case "Uhrzeit": v = time(when); break;
                case "Wetter heute": v = weather; break;
                default: v = midday; break;
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(v == null ? "" : v));
        }
        m.appendTail(sb);
        return tidy(sb.toString());
    }

    /** Doppelte Leerzeichen und Leerzeichen vor Satzzeichen entfernen, z. B. wenn ein Baustein leer bleibt. */
    static String tidy(String s) {
        return s.replaceAll("\\s+", " ").replaceAll(" ([,.!?;:])", "$1").trim();
    }

    /** Positionen aller Bausteine im Text, je Baustein {Anfang, Ende}. */
    public static List<int[]> ranges(CharSequence text) {
        List<int[]> out = new ArrayList<>();
        if (text == null) return out;
        Matcher m = TOKEN.matcher(text);
        while (m.find()) out.add(new int[]{m.start(), m.end()});
        return out;
    }

    /**
     * Ersetzt eine Änderung die Zeichen [start, start+count) durch {@code after} neue Zeichen und trifft sie
     * einen Baustein nur teilweise (z. B. Rücktaste am Ende von „{Datum}“), soll der ganze Baustein verschwinden.
     * @return die danach noch zu löschenden Reste als {linksVon, linksBis, rechtsVon, rechtsBis}
     *         (Positionen im geänderten Text), oder null, wenn nichts zu tun ist
     */
    public static int[] partialTokenRemoval(CharSequence before, int start, int count, int after) {
        if (count <= 0) return null;
        int end = start + count, a = start, b = end;
        for (int[] r : ranges(before)) {
            if (r[1] <= start || r[0] >= end) continue;
            a = Math.min(a, r[0]);
            b = Math.max(b, r[1]);
        }
        if (a == start && b == end) return null;
        return new int[]{a, start, start + after, start + after + (b - end)};
    }

    /** Liegt die Position mitten in einem Baustein, wird sie an dessen Ende (bzw. mit start=true an den Anfang) gelegt. */
    public static int snap(CharSequence text, int pos, boolean toStart) {
        for (int[] r : ranges(text)) {
            if (pos > r[0] && pos < r[1]) return toStart ? r[0] : r[1];
        }
        return pos;
    }

    /** Was beim Einfügen eines Bausteins an der Position eingesetzt wird – mit Leerzeichen davor, wenn nötig. */
    public static String insertion(CharSequence text, int pos, String token) {
        if (pos <= 0 || pos > text.length()) return token;
        char before = text.charAt(pos - 1);
        if (Character.isWhitespace(before) || "(„\"'«‹".indexOf(before) >= 0) return token;
        return " " + token;
    }
}
