package de.christina.sonnenwecker;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Die Stimmen von Google Gemini über die Gemini-API (Interactions). Braucht nur einen kostenlosen
 * API-Schlüssel aus Google AI Studio. Liefert WAV, damit der Lautsprecher es wie die anderen Stimmen abspielt.
 */
public final class GeminiTts {
    /** Für Tests austauschbar. */
    static String base = "https://generativelanguage.googleapis.com/v1beta";

    /**
     * Sprachmodelle in dieser Reihenfolge. Flash-Lite ist für das tägliche Vorlesen gedacht und hat das größere
     * Gratis-Kontingent; ist eines aufgebraucht oder nicht verfügbar, kommt das nächste dran.
     */
    static final String[] MODELS = {"gemini-3.8-flash-lite-tts", "gemini-3.8-flash-tts", "gemini-3.1-flash-tts-preview"};

    /** Die 30 Gemini-Stimmen: Name, Eigenschaft laut Google (übersetzt), weiblich? Alle sprechen Deutsch. */
    private static final Object[][] VOICES = {
            {"Achernar", "weich", true}, {"Aoede", "luftig", true}, {"Autonoe", "hell", true},
            {"Callirrhoe", "entspannt", true}, {"Despina", "geschmeidig", true}, {"Erinome", "klar", true},
            {"Gacrux", "reif", true}, {"Kore", "bestimmt", true}, {"Laomedeia", "munter", true},
            {"Leda", "jugendlich", true}, {"Pulcherrima", "direkt", true}, {"Sulafat", "warm", true},
            {"Vindemiatrix", "sanft", true}, {"Zephyr", "hell", true},
            {"Achird", "freundlich", false}, {"Algenib", "rau", false}, {"Algieba", "geschmeidig", false},
            {"Alnilam", "bestimmt", false}, {"Charon", "sachlich", false}, {"Enceladus", "hauchig", false},
            {"Fenrir", "aufgeweckt", false}, {"Iapetus", "klar", false}, {"Orus", "bestimmt", false},
            {"Puck", "munter", false}, {"Rasalgethi", "sachlich", false}, {"Sadachbia", "lebendig", false},
            {"Sadaltager", "kundig", false}, {"Schedar", "gleichmäßig", false}, {"Umbriel", "entspannt", false},
            {"Zubenelgenubi", "locker", false},
    };

    /** Vorgegebene Sprechweisen: Anzeige, Anweisung an Gemini. Leere Anweisung = ohne Vorgabe. */
    public static final String[][] STYLES = {
            {"Ohne Vorgabe", ""},
            {"Ruhig und warm", "calm, warm and gentle, like a soft good-morning greeting"},
            {"Fröhlich und munter", "cheerful, upbeat and friendly"},
            {"Sanft und leise", "soft, quiet and soothing, a little slower"},
    };

    private GeminiTts() { }

    public static final class GeminiVoice {
        public final String name, trait;
        public final boolean female;

        GeminiVoice(String name, String trait, boolean female) {
            this.name = name;
            this.trait = trait;
            this.female = female;
        }

        /** „Gemini · Kore (bestimmt)“ */
        public String label() {
            return "Gemini · " + name + " (" + trait + ")";
        }
    }

    /** Erst die weiblichen, dann die männlichen Stimmen, jeweils alphabetisch. */
    public static List<GeminiVoice> voices() {
        List<GeminiVoice> out = new ArrayList<>();
        for (Object[] v : VOICES) out.add(new GeminiVoice((String) v[0], (String) v[1], (Boolean) v[2]));
        return out;
    }

    /** Anzeige einer Sprechweise: Name der Vorgabe oder der eigene Text. Leer = „Ohne Vorgabe“. */
    public static String styleLabel(String instruction) {
        String s = instruction == null ? "" : instruction.trim();
        for (String[] st : STYLES) if (st[1].equals(s)) return st[0];
        return s;
    }

    public static boolean isPresetStyle(String instruction) {
        String s = instruction == null ? "" : instruction.trim();
        for (String[] st : STYLES) if (st[1].equals(s)) return true;
        return false;
    }

    /** Fehler der API; {@link #nextModel} = mit dem nächsten Modell weiterversuchen. */
    static final class ApiError extends Exception {
        private static final long serialVersionUID = 1L;
        final int code;
        final boolean nextModel, quota;

        ApiError(int code, String message, boolean nextModel, boolean quota) {
            super(message);
            this.code = code;
            this.nextModel = nextModel;
            this.quota = quota;
        }
    }

    /** Spricht den Text und gibt eine WAV-Datei (als Bytes) zurück. Probiert die Modelle der Reihe nach. */
    public static byte[] synthesize(String apiKey, String voice, String style, String text) throws Exception {
        ApiError best = null;
        for (String model : MODELS) {
            try {
                return synthesize(apiKey, model, voice, style, text);
            } catch (ApiError e) {
                // Aussagekräftigsten Fehler merken: Kontingent vor „Modell nicht gefunden“
                if (best == null || (best.code == 404 && e.code != 404) || (e.quota && !best.quota)) best = e;
                if (!e.nextModel) throw e;
            }
        }
        throw best;
    }

    static byte[] synthesize(String apiKey, String model, String voice, String style, String text) throws Exception {
        String[] r = http(base + "/interactions", requestBody(model, voice, style, text), apiKey);
        int code = Integer.parseInt(r[0]);
        if (code != 200) throw error(code, r[1]);
        return audioFrom(r[1]);
    }

    static String requestBody(String model, String voice, String style, String text) throws Exception {
        JSONObject content = new JSONObject().put("type", "text").put("text", text);
        if (style != null && !style.trim().isEmpty()) {
            content.put("annotations", new JSONArray().put(
                    new JSONObject().put("type", "speech_metadata").put("style", style.trim())));
        }
        return new JSONObject()
                .put("model", model)
                .put("input", new JSONArray().put(
                        new JSONObject().put("type", "user_input").put("content", new JSONArray().put(content))))
                .put("response_format", new JSONObject().put("type", "audio"))
                .put("generation_config", new JSONObject().put("speech_config",
                        new JSONArray().put(new JSONObject().put("voice", voice))))
                .put("store", false)
                .toString();
    }

    private static final Pattern RATE = Pattern.compile("rate=(\\d+)");

    /** Das Audio aus der Antwort – wie im offiziellen SDK das letzte Audio der Modell-Antwort. Immer als WAV. */
    static byte[] audioFrom(String json) throws Exception {
        JSONObject r = new JSONObject(json);
        JSONObject audio = r.optJSONObject("output_audio");
        if (audio == null || audio.optString("data", "").isEmpty()) {
            audio = null;
            JSONArray steps = r.optJSONArray("steps");
            if (steps != null) {
                for (int i = steps.length() - 1; i >= 0 && audio == null; i--) {
                    JSONObject step = steps.optJSONObject(i);
                    if (step == null) continue;
                    String type = step.optString("type", "");
                    if (type.equals("user_input")) break;
                    if (!type.equals("model_output")) continue;
                    JSONArray content = step.optJSONArray("content");
                    if (content == null) continue;
                    for (int j = content.length() - 1; j >= 0; j--) {
                        JSONObject item = content.optJSONObject(j);
                        if (item != null && item.optString("type", "").equals("audio")
                                && !item.optString("data", "").isEmpty()) {
                            audio = item;
                            break;
                        }
                    }
                }
            }
        }
        if (audio == null) {
            String status = r.optString("status", "");
            throw new Exception("Gemini hat keine Audiodaten geliefert" + (status.isEmpty() ? "" : " (" + status + ")"));
        }
        byte[] bytes = decode(audio.optString("data", ""));
        if (isWav(bytes)) return bytes;
        String mime = audio.optString("mime_type", "").toLowerCase(Locale.ROOT);
        if (!mime.isEmpty() && !mime.startsWith("audio/l16") && !mime.contains("pcm")) {
            throw new Exception("Gemini hat ein unerwartetes Audioformat geliefert: " + mime);
        }
        int rate = audio.optInt("sample_rate", 0);
        Matcher m = RATE.matcher(mime);
        if (rate <= 0 && m.find()) rate = Integer.parseInt(m.group(1));
        if (rate <= 0) rate = 24000;
        int channels = Math.max(1, audio.optInt("channels", 1));
        return wav(bytes, rate, channels);
    }

    private static byte[] decode(String data) {
        try {
            return Base64.getDecoder().decode(data);
        } catch (IllegalArgumentException e) {
            return Base64.getUrlDecoder().decode(data);
        }
    }

    static boolean isWav(byte[] b) {
        return b.length > 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'A' && b[10] == 'V' && b[11] == 'E';
    }

    /** 16-Bit-PCM (little endian) mit WAV-Kopf versehen. */
    static byte[] wav(byte[] pcm, int rate, int channels) {
        ByteBuffer h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        h.put(new byte[]{'R', 'I', 'F', 'F'}).putInt(36 + pcm.length).put(new byte[]{'W', 'A', 'V', 'E'});
        h.put(new byte[]{'f', 'm', 't', ' '}).putInt(16).putShort((short) 1).putShort((short) channels)
                .putInt(rate).putInt(rate * channels * 2).putShort((short) (channels * 2)).putShort((short) 16);
        h.put(new byte[]{'d', 'a', 't', 'a'}).putInt(pcm.length);
        byte[] out = new byte[44 + pcm.length];
        System.arraycopy(h.array(), 0, out, 0, 44);
        System.arraycopy(pcm, 0, out, 44, pcm.length);
        return out;
    }

    /** Verständliche Meldung und ob ein anderes Modell helfen könnte. */
    static ApiError error(int code, String body) {
        String msg = "", status = "", reason = "", quotaId = "";
        try {
            JSONObject e = new JSONObject(body).getJSONObject("error");
            msg = e.optString("message", "");
            status = e.optString("status", "");
            JSONArray details = e.optJSONArray("details");
            if (details != null) {
                for (int i = 0; i < details.length(); i++) {
                    JSONObject d = details.optJSONObject(i);
                    if (d == null) continue;
                    if (!d.optString("reason", "").isEmpty()) reason = d.optString("reason", "");
                    JSONArray violations = d.optJSONArray("violations");
                    if (violations != null && violations.length() > 0) {
                        JSONObject v = violations.optJSONObject(0);
                        if (v != null) quotaId = v.optString("quotaId", "");
                    }
                }
            }
        } catch (Exception ignored) {
        }
        String m = msg.toLowerCase(Locale.ROOT);
        if ("API_KEY_INVALID".equals(reason) || m.contains("api key not valid") || m.contains("api key expired")) {
            return new ApiError(code, "Der API-Schlüssel ist ungültig.", false, false);
        }
        if (m.contains("location is not supported")) {
            return new ApiError(code, "Gemini ist an deinem Standort leider nicht verfügbar.", false, false);
        }
        if (code == 429 || "RESOURCE_EXHAUSTED".equals(status)) {
            boolean perMinute = quotaId.toLowerCase(Locale.ROOT).contains("minute");
            String text = perMinute
                    ? "Gerade waren es zu viele Anfragen an Gemini – bitte eine Minute warten."
                    : "Das kostenlose Tageskontingent von Gemini ist aufgebraucht. Ab " + resetTime() + " geht es wieder.";
            return new ApiError(code, text, true, true);
        }
        if ("SERVICE_DISABLED".equals(reason) || m.contains("has not been used in project")) {
            return new ApiError(code, "Die Gemini-API ist für diesen Schlüssel nicht freigeschaltet.", false, false);
        }
        boolean next = code == 404 || code >= 500 || (code == 400 || code == 403) && m.contains("model");
        String text = code == 404 ? "Dieses Gemini-Sprachmodell ist nicht verfügbar."
                : "Gemini meldet (HTTP " + code + "): " + (msg.isEmpty() ? shorten(body) : msg);
        return new ApiError(code, text, next, false);
    }

    /** Wann das Tageskontingent zurückgesetzt wird (Mitternacht in Kalifornien), in Ortszeit: „9 Uhr“. */
    static String resetTime() {
        ZoneId pacific = ZoneId.of("America/Los_Angeles");
        ZonedDateTime reset = LocalDate.now(pacific).plusDays(1).atStartOfDay(pacific)
                .withZoneSameInstant(ZoneId.systemDefault());
        int min = reset.getMinute();
        return reset.getHour() + (min == 0 ? " Uhr" : ":" + (min < 10 ? "0" : "") + min + " Uhr");
    }

    private static String shorten(String s) {
        if (s == null) return "";
        s = s.replace('\n', ' ');
        return s.length() > 200 ? s.substring(0, 200) + "…" : s;
    }

    /** @return {HTTP-Status, Antworttext} */
    private static String[] http(String url, String body, String apiKey) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(30000);
        c.setRequestMethod("POST");
        c.setRequestProperty("x-goog-api-key", apiKey);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        c.setDoOutput(true);
        try (OutputStream o = c.getOutputStream()) {
            o.write(body.getBytes(StandardCharsets.UTF_8));
        }
        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        if (in != null) {
            try (InputStream s = in) {
                byte[] buf = new byte[16384];
                int n;
                while ((n = s.read(buf)) > 0) b.write(buf, 0, n);
            }
        }
        c.disconnect();
        return new String[]{String.valueOf(code), b.toString("UTF-8")};
    }
}
