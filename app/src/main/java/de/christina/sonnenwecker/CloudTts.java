package de.christina.sonnenwecker;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Premium-Stimmen von Google Cloud Text-to-Speech („Chirp 3: HD“). Braucht einen eigenen API-Schlüssel.
 * Liefert WAV (LINEAR16 mit WAV-Kopf), damit der Lautsprecher es genauso abspielt wie die Handy-Stimme.
 */
public final class CloudTts {
    /** Für Tests austauschbar. */
    static String base = "https://texttospeech.googleapis.com/v1";

    private CloudTts() { }

    public static final class CloudVoice {
        public final String name, gender;

        CloudVoice(String name, String gender) {
            this.name = name;
            this.gender = gender;
        }

        /** „Aoede (weiblich)“ */
        public String title() {
            int i = name.lastIndexOf('-');
            String shortName = i >= 0 ? name.substring(i + 1) : name;
            String g = "FEMALE".equals(gender) ? "weiblich" : "MALE".equals(gender) ? "männlich" : "";
            return g.isEmpty() ? shortName : shortName + " (" + g + ")";
        }

        public String label() {
            return "Google Cloud · " + title();
        }
    }

    /** Deutsche Premium-Stimmen; gibt es keine „Chirp 3: HD“-Stimmen, alle deutschen. */
    public static List<CloudVoice> listVoices(String apiKey) throws Exception {
        String[] r = http("GET", base + "/voices?languageCode=de-DE", null, apiKey);
        check(r);
        JSONArray all = new JSONObject(r[1]).optJSONArray("voices");
        List<CloudVoice> hd = new ArrayList<>(), other = new ArrayList<>();
        if (all != null) {
            for (int i = 0; i < all.length(); i++) {
                JSONObject v = all.getJSONObject(i);
                String name = v.optString("name", "");
                if (!name.startsWith("de-DE")) continue;
                CloudVoice cv = new CloudVoice(name, v.optString("ssmlGender", ""));
                if (name.contains("Chirp3-HD")) hd.add(cv);
                else other.add(cv);
            }
        }
        List<CloudVoice> out = hd.isEmpty() ? other : hd;
        Collections.sort(out, (a, b) -> {
            int g = a.gender.compareTo(b.gender);
            return g != 0 ? g : a.name.compareTo(b.name);
        });
        return out;
    }

    /** Spricht den Text mit der Stimme und gibt eine WAV-Datei (als Bytes) zurück. */
    public static byte[] synthesize(String apiKey, String voiceName, String text) throws Exception {
        JSONObject body = new JSONObject()
                .put("input", new JSONObject().put("text", text))
                .put("voice", new JSONObject().put("languageCode", "de-DE").put("name", voiceName))
                .put("audioConfig", new JSONObject().put("audioEncoding", "LINEAR16"));
        String[] r = http("POST", base + "/text:synthesize", body.toString(), apiKey);
        check(r);
        String audio = new JSONObject(r[1]).optString("audioContent", "");
        if (audio.isEmpty()) throw new Exception("Google Cloud hat keine Audiodaten geliefert.");
        return Base64.getDecoder().decode(audio);
    }

    private static void check(String[] r) throws Exception {
        if (!"200".equals(r[0])) throw new Exception(friendly(r[0], r[1]));
    }

    /** Verständliche Fehlermeldung für die häufigsten Einrichtungsfehler. */
    static String friendly(String code, String body) {
        String msg = "", reason = "";
        try {
            JSONObject e = new JSONObject(body).getJSONObject("error");
            msg = e.optString("message", "");
            JSONArray details = e.optJSONArray("details");
            if (details != null) {
                for (int i = 0; i < details.length(); i++) {
                    String r = details.getJSONObject(i).optString("reason", "");
                    if (!r.isEmpty()) reason = r;
                }
            }
        } catch (Exception ignored) {
        }
        String m = msg.toLowerCase(Locale.ROOT);
        if ("API_KEY_INVALID".equals(reason) || m.contains("api key not valid")) {
            return "Der API-Schlüssel ist ungültig.";
        }
        if ("SERVICE_DISABLED".equals(reason) || m.contains("has not been used") || m.contains("is disabled")) {
            return "Die „Cloud Text-to-Speech API“ ist im Google-Cloud-Projekt noch nicht aktiviert.";
        }
        if ("BILLING_DISABLED".equals(reason) || m.contains("billing")) {
            return "Für das Google-Cloud-Projekt ist noch keine Abrechnung eingerichtet.";
        }
        if ("API_KEY_SERVICE_BLOCKED".equals(reason) || "API_KEY_ANDROID_APP_BLOCKED".equals(reason)) {
            return "Der API-Schlüssel ist für die Text-to-Speech API gesperrt – bitte die Einschränkungen des Schlüssels prüfen.";
        }
        if ("429".equals(code) || "RATE_LIMIT_EXCEEDED".equals(reason)) {
            return "Google Cloud meldet zu viele Anfragen – bitte kurz warten.";
        }
        return "Google Cloud meldet (HTTP " + code + "): " + (msg.isEmpty() ? shorten(body) : msg);
    }

    private static String shorten(String s) {
        if (s == null) return "";
        s = s.replace('\n', ' ');
        return s.length() > 200 ? s.substring(0, 200) + "…" : s;
    }

    /** @return {HTTP-Status, Antworttext} */
    private static String[] http(String method, String url, String body, String apiKey) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(20000);
        c.setRequestMethod(method);
        c.setRequestProperty("X-Goog-Api-Key", apiKey);
        c.setRequestProperty("Accept", "application/json");
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (OutputStream o = c.getOutputStream()) {
                o.write(body.getBytes(StandardCharsets.UTF_8));
            }
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
