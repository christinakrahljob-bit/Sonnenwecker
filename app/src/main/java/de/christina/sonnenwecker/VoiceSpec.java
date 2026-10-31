package de.christina.sonnenwecker;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Kennung einer Stimme, wie sie beim Wecker gespeichert wird:
 * leer = Standardstimme des Handys,
 * „tts:&lt;App-Paket&gt;|&lt;Stimmenname&gt;“ = eine Stimme einer Sprachausgabe-App auf dem Handy,
 * „cloud:&lt;Stimmenname&gt;“ = eine Premium-Stimme von Google Cloud,
 * „gemini:&lt;Stimmenname&gt;“ = eine Stimme von Google Gemini.
 */
public final class VoiceSpec {
    public static final String DEFAULT = "";
    public static final String DEFAULT_LABEL = "Standardstimme des Handys";

    private VoiceSpec() { }

    public static String engine(String enginePackage, String voiceName) {
        return "tts:" + enginePackage + "|" + voiceName;
    }

    public static String cloud(String voiceName) {
        return "cloud:" + voiceName;
    }

    public static String gemini(String voiceName) {
        return "gemini:" + voiceName;
    }

    public static boolean isGemini(String spec) {
        return spec != null && spec.startsWith("gemini:");
    }

    public static String geminiVoice(String spec) {
        return isGemini(spec) ? spec.substring(7) : "";
    }

    /** Braucht morgens Internet (Google Cloud oder Gemini)? */
    public static boolean isOnline(String spec) {
        return isCloud(spec) || isGemini(spec);
    }

    public static boolean isEngine(String spec) {
        return spec != null && spec.startsWith("tts:");
    }

    public static boolean isCloud(String spec) {
        return spec != null && spec.startsWith("cloud:");
    }

    public static String enginePackage(String spec) {
        if (!isEngine(spec)) return "";
        String rest = spec.substring(4);
        int i = rest.indexOf('|');
        return i < 0 ? rest : rest.substring(0, i);
    }

    public static String engineVoice(String spec) {
        if (!isEngine(spec)) return "";
        String rest = spec.substring(4);
        int i = rest.indexOf('|');
        return i < 0 ? "" : rest.substring(i + 1);
    }

    public static String cloudVoice(String spec) {
        return isCloud(spec) ? spec.substring(6) : "";
    }

    private static final Pattern GOOGLE_VOICE = Pattern.compile("[a-z]{2,3}-[a-z]{2}-x-([a-z0-9]+)-(local|network)");
    private static final Pattern LOCALE_VOICE =
            Pattern.compile("([a-z]{2,3})(?:[-_]([a-z]{2,3}))?(?:[-_](.+))?", Pattern.CASE_INSENSITIVE);

    /** Lesbarer Name einer Handy-Stimme: „de-de-x-deb-local“ → „Stimme DEB“, „de“ → „Deutsch“. */
    public static String phoneTitle(String name) {
        if (name == null || name.isEmpty()) return "Stimme";
        Matcher g = GOOGLE_VOICE.matcher(name.toLowerCase(Locale.ROOT));
        if (g.matches()) return "Stimme " + g.group(1).toUpperCase(Locale.ROOT);
        Matcher l = LOCALE_VOICE.matcher(name);
        if (l.matches() && isGerman(l.group(1))) {
            String rest = l.group(3);
            if (rest == null || rest.equalsIgnoreCase("language")) return "Deutsch";
            return "Stimme " + rest.replace('_', ' ');
        }
        return name;
    }

    /** Sprachcode Deutsch („de“, „deu“, „ger“)? */
    public static boolean isGerman(String language) {
        if (language == null) return false;
        String l = language.toLowerCase(Locale.ROOT);
        return l.equals("de") || l.equals("deu") || l.equals("ger");
    }

    /** Land einer Stimme, falls nicht Deutschland: „AT“ → „Österreich“. */
    public static String region(String country) {
        if (country == null) return "";
        switch (country.toUpperCase(Locale.ROOT)) {
            case "AT": case "AUT": return "Österreich";
            case "CH": case "CHE": return "Schweiz";
            case "BE": case "BEL": return "Belgien";
            case "LU": case "LUX": return "Luxemburg";
            case "LI": case "LIE": return "Liechtenstein";
            case "IT": case "ITA": return "Italien";
            default: return "";
        }
    }

    /** Qualitätsangabe der Sprachausgabe-App (Voice.QUALITY_*); bei „normal“ leer, weil viele Apps nur das melden. */
    public static String quality(int q) {
        if (q >= 500) return "sehr hohe Qualität";
        if (q >= 400) return "hohe Qualität";
        if (q >= 300) return "";
        return "einfache Qualität";
    }

    /** Anzeigename; ohne gespeicherten Namen die Kennung selbst. */
    public static String label(String spec, String label) {
        if (spec == null || spec.isEmpty()) return DEFAULT_LABEL;
        return label == null || label.isEmpty() ? spec : label;
    }
}
