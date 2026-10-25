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
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/** Steuerung der Hue Bridge im eigenen WLAN. */
public final class HueClient {
    private HueClient() { }

    public static final class Light {
        public final String id, idV1, name;
        public final boolean sunrise;

        Light(String id, String idV1, String name, boolean sunrise) {
            this.id = id;
            this.idV1 = idV1;
            this.name = name;
            this.sunrise = sunrise;
        }
    }

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

    public static List<Light> lights(String ip, String user) throws Exception {
        String[] r = http("GET", "https://" + ip + "/clip/v2/resource/light", null, user);
        JSONArray data = new JSONObject(r[1]).getJSONArray("data");
        List<Light> out = new ArrayList<>();
        for (int i = 0; i < data.length(); i++) {
            JSONObject l = data.getJSONObject(i);
            String name = l.optJSONObject("metadata") != null ? l.getJSONObject("metadata").optString("name") : l.optString("id");
            boolean sunrise = false;
            JSONObject te = l.optJSONObject("timed_effects");
            if (te != null) {
                JSONArray vals = te.optJSONArray("status_values");
                if (vals == null) vals = te.optJSONArray("effect_values");
                if (vals != null) for (int k = 0; k < vals.length(); k++) if ("sunrise".equals(vals.optString(k))) sunrise = true;
            }
            out.add(new Light(l.getString("id"), l.optString("id_v1", ""), name, sunrise));
        }
        return out;
    }

    /**
     * Startet den Sonnenaufgang. Nutzt den echten Hue-Sonnenaufgangseffekt, wenn die Lampe ihn kann,
     * sonst ein langsames Hochdimmen von tiefem Orange zu warmem Weiß.
     * @return Beschreibung, was gemacht wurde
     */
    public static String startSunrise(String ip, String user, String id, String idV1, long durationMs) throws Exception {
        durationMs = Math.max(30000, durationMs);
        try {
            String body = "{\"timed_effects\":{\"effect\":\"sunrise\",\"duration\":" + durationMs + "}}";
            String[] r = http("PUT", "https://" + ip + "/clip/v2/resource/light/" + id, body, user);
            JSONArray errors = new JSONObject(r[1]).optJSONArray("errors");
            if ("200".equals(r[0]) && (errors == null || errors.length() == 0)) {
                return "Hue-Sonnenaufgang gestartet (" + durationMs / 60000 + " Min.)";
            }
        } catch (Exception e) {
            // weiter mit der Ersatzlösung
        }
        if (idV1 == null || idV1.isEmpty()) throw new Exception("Lampe kann keinen Sonnenaufgang und hat keine v1-ID");
        String base = "http://" + ip + "/api/" + user + idV1 + "/state";
        http("PUT", base, "{\"on\":true,\"bri\":1,\"xy\":[0.6,0.38],\"transitiontime\":0}", null);
        Thread.sleep(1500);
        long tt = Math.min(65535, durationMs / 100);
        http("PUT", base, "{\"on\":true,\"bri\":254,\"xy\":[0.46,0.41],\"transitiontime\":" + tt + "}", null);
        return "Ersatz-Sonnenaufgang gestartet (Hochdimmen, " + durationMs / 60000 + " Min.)";
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
