package de.christina.sonnenwecker;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Minimaler Google-Cast-Client (CASTV2-Protokoll), ohne Google-Play-Bibliotheken.
 * Spricht direkt mit dem Lautsprecher im WLAN und startet dort den Standard-Mediaplayer.
 */
public class CastClient implements Closeable {
    static final String NS_CONN = "urn:x-cast:com.google.cast.tp.connection";
    static final String NS_HEARTBEAT = "urn:x-cast:com.google.cast.tp.heartbeat";
    static final String NS_RECEIVER = "urn:x-cast:com.google.cast.receiver";
    static final String NS_MEDIA = "urn:x-cast:com.google.cast.media";
    static final String DEFAULT_MEDIA_RECEIVER = "CC1AD845";
    static final String SENDER = "sender-0";
    static final String RECEIVER = "receiver-0";

    public static final class Msg {
        public final String source, destination, namespace, payload;

        Msg(String source, String destination, String namespace, String payload) {
            this.source = source;
            this.destination = destination;
            this.namespace = namespace;
            this.payload = payload;
        }

        JSONObject json() {
            try {
                return new JSONObject(payload == null ? "{}" : payload);
            } catch (JSONException e) {
                return new JSONObject();
            }
        }
    }

    private SSLSocket socket;
    private DataOutputStream out;
    private DataInputStream in;
    private final LinkedBlockingQueue<Msg> inbox = new LinkedBlockingQueue<>();
    private volatile boolean closed;
    private int requestId = 1;
    private String transportId;

    public void connect(String host, int port) throws Exception {
        SSLContext ctx = SSLContext.getInstance("TLS");
        // Cast-Geräte nutzen selbst signierte Zertifikate – im eigenen WLAN unkritisch.
        ctx.init(null, new TrustManager[]{new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] c, String a) { }
            public void checkServerTrusted(X509Certificate[] c, String a) { }
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        }}, new SecureRandom());
        socket = (SSLSocket) ctx.getSocketFactory().createSocket();
        socket.connect(new InetSocketAddress(host, port), 8000);
        socket.startHandshake();
        out = new DataOutputStream(socket.getOutputStream());
        in = new DataInputStream(socket.getInputStream());

        Thread reader = new Thread(this::readLoop, "cast-reader");
        reader.setDaemon(true);
        reader.start();

        send(RECEIVER, NS_CONN, "{\"type\":\"CONNECT\"}");

        Thread heartbeat = new Thread(() -> {
            while (!closed) {
                try {
                    Thread.sleep(5000);
                    if (!closed) send(RECEIVER, NS_HEARTBEAT, "{\"type\":\"PING\"}");
                } catch (Exception e) {
                    return;
                }
            }
        }, "cast-heartbeat");
        heartbeat.setDaemon(true);
        heartbeat.start();
    }

    /** Die eigene IP-Adresse im WLAN (damit der Lautsprecher die Begrüßung vom Handy laden kann). */
    public InetAddress localAddress() {
        return socket.getLocalAddress();
    }

    public void setVolume(double level) throws IOException {
        level = Math.max(0, Math.min(1, level));
        send(RECEIVER, NS_RECEIVER, "{\"type\":\"SET_VOLUME\",\"requestId\":" + (requestId++)
                + ",\"volume\":{\"level\":" + level + "}}");
        send(RECEIVER, NS_RECEIVER, "{\"type\":\"SET_VOLUME\",\"requestId\":" + (requestId++)
                + ",\"volume\":{\"muted\":false}}");
    }

    /** Startet den Standard-Mediaplayer auf dem Lautsprecher und verbindet sich mit ihm. */
    public void launchMediaReceiver() throws Exception {
        send(RECEIVER, NS_RECEIVER, "{\"type\":\"LAUNCH\",\"appId\":\"" + DEFAULT_MEDIA_RECEIVER
                + "\",\"requestId\":" + (requestId++) + "}");
        JSONObject status = waitFor(NS_RECEIVER, j -> findTransportId(j) != null, 20000);
        if (status == null) throw new IOException("Lautsprecher hat den Mediaplayer nicht gestartet");
        transportId = findTransportId(status);
        send(transportId, NS_CONN, "{\"type\":\"CONNECT\"}");
    }

    static String findTransportId(JSONObject j) {
        if (!"RECEIVER_STATUS".equals(j.optString("type"))) return null;
        JSONObject status = j.optJSONObject("status");
        if (status == null) return null;
        JSONArray apps = status.optJSONArray("applications");
        if (apps == null) return null;
        for (int i = 0; i < apps.length(); i++) {
            JSONObject a = apps.optJSONObject(i);
            if (a != null && DEFAULT_MEDIA_RECEIVER.equals(a.optString("appId"))) {
                String t = a.optString("transportId", "");
                if (!t.isEmpty()) return t;
            }
        }
        return null;
    }

    public void load(String url, String contentType, String streamType, String title) throws Exception {
        if (transportId == null) throw new IllegalStateException("launchMediaReceiver() zuerst aufrufen");
        JSONObject media = new JSONObject();
        media.put("contentId", url);
        media.put("contentType", contentType);
        media.put("streamType", streamType);
        JSONObject meta = new JSONObject();
        meta.put("metadataType", 0);
        meta.put("title", title);
        media.put("metadata", meta);
        JSONObject load = new JSONObject();
        load.put("type", "LOAD");
        load.put("requestId", requestId++);
        load.put("autoplay", true);
        load.put("currentTime", 0);
        load.put("media", media);
        inbox.clear();
        send(transportId, NS_MEDIA, load.toString());
    }

    /** Wartet, bis die aktuelle Wiedergabe läuft. */
    public boolean waitPlaying(long timeoutMs) throws InterruptedException {
        JSONObject j = waitFor(NS_MEDIA, m -> isLoadFailed(m) || "PLAYING".equals(playerState(m)), timeoutMs);
        return j != null && !isLoadFailed(j);
    }

    /** Wartet, bis die aktuelle (endliche) Wiedergabe zu Ende ist. */
    public boolean waitFinished(long timeoutMs) throws InterruptedException {
        final boolean[] sawPlaying = {false};
        JSONObject j = waitFor(NS_MEDIA, m -> {
            if (isLoadFailed(m)) return true;
            if (!"MEDIA_STATUS".equals(m.optString("type"))) return false;
            JSONArray st = m.optJSONArray("status");
            if (st == null || st.length() == 0) return sawPlaying[0];
            JSONObject s = st.optJSONObject(0);
            if (s == null) return false;
            String state = s.optString("playerState");
            if ("PLAYING".equals(state) || "BUFFERING".equals(state)) sawPlaying[0] = true;
            return "IDLE".equals(state) && !s.optString("idleReason", "").isEmpty();
        }, timeoutMs);
        return j != null;
    }

    static boolean isLoadFailed(JSONObject m) {
        String t = m.optString("type");
        return "LOAD_FAILED".equals(t) || "LOAD_CANCELLED".equals(t) || "INVALID_REQUEST".equals(t);
    }

    static String playerState(JSONObject m) {
        if (!"MEDIA_STATUS".equals(m.optString("type"))) return null;
        JSONArray st = m.optJSONArray("status");
        if (st == null || st.length() == 0) return null;
        JSONObject s = st.optJSONObject(0);
        return s == null ? null : s.optString("playerState", null);
    }

    private JSONObject waitFor(String ns, Predicate<JSONObject> match, long timeoutMs) throws InterruptedException {
        long end = System.currentTimeMillis() + timeoutMs;
        while (!closed) {
            long left = end - System.currentTimeMillis();
            if (left <= 0) return null;
            Msg m = inbox.poll(left, TimeUnit.MILLISECONDS);
            if (m == null) return null;
            if (!ns.equals(m.namespace)) continue;
            JSONObject j = m.json();
            if (match.test(j)) return j;
        }
        return null;
    }

    private void readLoop() {
        try {
            while (!closed) {
                int len = in.readInt();
                if (len <= 0 || len > 1 << 20) throw new IOException("Ungültige Nachrichtenlänge " + len);
                byte[] buf = new byte[len];
                in.readFully(buf);
                Msg m = decode(buf);
                if (NS_HEARTBEAT.equals(m.namespace)) {
                    if (m.payload != null && m.payload.contains("PING")) {
                        send(m.source, NS_HEARTBEAT, "{\"type\":\"PONG\"}");
                    }
                    continue;
                }
                if (NS_CONN.equals(m.namespace) && m.payload != null && m.payload.contains("CLOSE")) {
                    continue;
                }
                inbox.offer(m);
            }
        } catch (Exception e) {
            closed = true;
        }
    }

    private synchronized void send(String destination, String namespace, String payload) throws IOException {
        byte[] msg = encode(SENDER, destination, namespace, payload);
        out.writeInt(msg.length);
        out.write(msg);
        out.flush();
    }

    @Override
    public void close() {
        closed = true;
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
        }
    }

    // ---------- Protobuf-Kodierung der CastMessage (von Hand, um ohne Bibliotheken auszukommen) ----------

    static byte[] encode(String source, String destination, String namespace, String payload) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(0x08); // protocol_version = CASTV2_1_0
        b.write(0x00);
        writeString(b, 2, source);
        writeString(b, 3, destination);
        writeString(b, 4, namespace);
        b.write(0x28); // payload_type = STRING
        b.write(0x00);
        writeString(b, 6, payload);
        return b.toByteArray();
    }

    private static void writeString(ByteArrayOutputStream b, int field, String s) {
        byte[] d = s.getBytes(StandardCharsets.UTF_8);
        b.write((field << 3) | 2);
        writeVarint(b, d.length);
        b.write(d, 0, d.length);
    }

    private static void writeVarint(ByteArrayOutputStream b, long v) {
        while ((v & ~0x7FL) != 0) {
            b.write((int) ((v & 0x7F) | 0x80));
            v >>>= 7;
        }
        b.write((int) v);
    }

    static Msg decode(byte[] buf) throws IOException {
        int[] pos = {0};
        String source = null, destination = null, namespace = null, payload = null;
        while (pos[0] < buf.length) {
            long tag = readVarint(buf, pos);
            int field = (int) (tag >>> 3);
            int wire = (int) (tag & 7);
            if (wire == 0) {
                readVarint(buf, pos);
            } else if (wire == 2) {
                int len = (int) readVarint(buf, pos);
                if (len < 0 || pos[0] + len > buf.length) throw new IOException("Kaputte Nachricht");
                String s = new String(buf, pos[0], len, StandardCharsets.UTF_8);
                pos[0] += len;
                switch (field) {
                    case 2: source = s; break;
                    case 3: destination = s; break;
                    case 4: namespace = s; break;
                    case 6: payload = s; break;
                    default: break; // z. B. binäre Nutzdaten – ignorieren
                }
            } else if (wire == 1) {
                pos[0] += 8;
            } else if (wire == 5) {
                pos[0] += 4;
            } else {
                throw new IOException("Unbekannter Feldtyp " + wire);
            }
        }
        return new Msg(source, destination, namespace, payload);
    }

    private static long readVarint(byte[] buf, int[] pos) throws IOException {
        long result = 0;
        int shift = 0;
        while (true) {
            if (pos[0] >= buf.length) throw new IOException("Nachricht zu kurz");
            int b = buf[pos[0]++] & 0xFF;
            result |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return result;
            shift += 7;
            if (shift > 63) throw new IOException("Varint zu lang");
        }
    }
}
