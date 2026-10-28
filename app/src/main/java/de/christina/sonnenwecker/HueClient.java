package de.christina.sonnenwecker;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/** Verbindung zur Hue Bridge im eigenen WLAN. */
public final class HueClient {
    private HueClient() { }

    /** Sucht die Bridge über den Philips-Dienst. Gibt die IP zurück oder null. */
    public static String discover() throws Exception {
        String[] r = http("GET", "https://discovery.meethue.com/", null, null);
        JSONArray a = new JSONArray(r[1]);
        if (a.length() == 0) return null;
        return a.getJSONObject(0).optString("internalipaddress", null);
    }

    /** Koppelt die App mit der Bridge. Vorher muss der runde Knopf auf der Bridge gedrückt worden sein. */
    public static String pair(String ip) throws Exception {
        String[] r = http("POST", "http://" + ip + "/api", "{\"devicetype\":\"sonnenwecker#handy\"}", null);
        JSONObject first = new JSONArray(r[1]).getJSONObject(0);
        if (first.has("success")) return first.getJSONObject("success").getString("username");
        JSONObject err = first.optJSONObject("error");
        if (err != null && err.optInt("type") == 101) {
            throw new Exception("Bitte zuerst den runden Knopf auf der Bridge drücken, dann innerhalb von 30 Sekunden erneut tippen.");
        }
        throw new Exception("Bridge meldet: " + r[1]);
    }

    /** Alle Automationen der Bridge (so, wie die Hue-App sie anlegt). */
    public static JSONArray behaviorInstances(String ip, String user) throws Exception {
        String[] r = http("GET", "https://" + ip + "/clip/v2/resource/behavior_instance", null, user);
        if (!"200".equals(r[0])) throw new Exception("Bridge antwortet mit HTTP " + r[0] + ": " + shorten(r[1]));
        return new JSONObject(r[1]).getJSONArray("data");
    }

    /** Ändert eine Automation. Wirft eine Ausnahme mit der Antwort der Bridge, wenn sie ablehnt. */
    public static void putBehavior(String ip, String user, String id, JSONObject body) throws Exception {
        String[] r = http("PUT", "https://" + ip + "/clip/v2/resource/behavior_instance/" + id, body.toString(), user);
        JSONArray errors = null;
        try {
            errors = new JSONObject(r[1]).optJSONArray("errors");
        } catch (Exception ignored) {
        }
        if (!"200".equals(r[0]) || (errors != null && errors.length() > 0)) {
            throw new Exception("Bridge lehnt ab (HTTP " + r[0] + "): " + shorten(r[1]));
        }
    }

    static String shorten(String s) {
        if (s == null) return "";
        s = s.replace('\n', ' ');
        return s.length() > 200 ? s.substring(0, 200) + "…" : s;
    }

    /** @return {HTTP-Status, Antworttext} */
    static String[] http(String method, String url, String body, String hueKey) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        if (c instanceof HttpsURLConnection && !url.contains("meethue.com")) {
            // Die Bridge hat ein eigenes Zertifikat – im eigenen WLAN unkritisch.
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[]{new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] ch, String a) { }
                public void checkServerTrusted(X509Certificate[] ch, String a) { }
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            }}, new SecureRandom());
            ((HttpsURLConnection) c).setSSLSocketFactory(ctx.getSocketFactory());
            ((HttpsURLConnection) c).setHostnameVerifier((h, s) -> true);
        }
        c.setConnectTimeout(8000);
        c.setReadTimeout(10000);
        c.setRequestMethod(method);
        if (hueKey != null) c.setRequestProperty("hue-application-key", hueKey);
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            try (OutputStream o = c.getOutputStream()) {
                o.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        if (in != null) {
            try (InputStream s = in) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = s.read(buf)) > 0) b.write(buf, 0, n);
            }
        }
        c.disconnect();
        return new String[]{String.valueOf(code), b.toString("UTF-8")};
    }
}
