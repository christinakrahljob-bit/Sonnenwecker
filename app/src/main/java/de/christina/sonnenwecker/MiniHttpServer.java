package de.christina.sonnenwecker;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Winziger Webserver, der genau eine Datei (die Begrüßung) im WLAN für den Lautsprecher bereitstellt. */
public class MiniHttpServer implements Closeable {
    private static final Pattern RANGE = Pattern.compile("bytes=(\\d*)-(\\d*)");

    private final ServerSocket server;
    private final File file;
    private final String mime;
    private volatile boolean closed;

    public MiniHttpServer(File file, String mime) throws IOException {
        this.file = file;
        this.mime = mime;
        this.server = new ServerSocket(0);
        Thread t = new Thread(this::acceptLoop, "mini-http");
        t.setDaemon(true);
        t.start();
    }

    public int port() {
        return server.getLocalPort();
    }

    private void acceptLoop() {
        while (!closed) {
            try {
                Socket s = server.accept();
                Thread t = new Thread(() -> handle(s), "mini-http-client");
                t.setDaemon(true);
                t.start();
            } catch (IOException e) {
                if (closed) return;
            }
        }
    }

    private void handle(Socket s) {
        try (Socket sock = s) {
            sock.setSoTimeout(15000);
            BufferedReader r = new BufferedReader(new InputStreamReader(sock.getInputStream(), StandardCharsets.ISO_8859_1));
            String requestLine = r.readLine();
            if (requestLine == null) return;
            String range = null;
            String line;
            while ((line = r.readLine()) != null && !line.isEmpty()) {
                int c = line.indexOf(':');
                if (c > 0 && line.substring(0, c).trim().equalsIgnoreCase("range")) {
                    range = line.substring(c + 1).trim();
                }
            }
            boolean head = requestLine.startsWith("HEAD ");
            long total = file.length();
            long start = 0, end = total - 1;
            boolean partial = false;
            if (range != null) {
                Matcher m = RANGE.matcher(range);
                if (m.matches()) {
                    String a = m.group(1), b = m.group(2);
                    if (!a.isEmpty()) {
                        start = Long.parseLong(a);
                        if (!b.isEmpty()) end = Math.min(Long.parseLong(b), total - 1);
                    } else if (!b.isEmpty()) {
                        start = Math.max(0, total - Long.parseLong(b));
                    }
                    partial = true;
                }
            }
            OutputStream o = sock.getOutputStream();
            if (start > end || start >= total) {
                o.write(("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */" + total
                        + "\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
                return;
            }
            long len = end - start + 1;
            StringBuilder h = new StringBuilder();
            h.append(partial ? "HTTP/1.1 206 Partial Content\r\n" : "HTTP/1.1 200 OK\r\n");
            h.append("Content-Type: ").append(mime).append("\r\n");
            h.append("Content-Length: ").append(len).append("\r\n");
            h.append("Accept-Ranges: bytes\r\n");
            if (partial) {
                h.append(String.format(Locale.US, "Content-Range: bytes %d-%d/%d\r\n", start, end, total));
            }
            h.append("Access-Control-Allow-Origin: *\r\n");
            h.append("Connection: close\r\n\r\n");
            o.write(h.toString().getBytes(StandardCharsets.ISO_8859_1));
            if (!head) {
                try (RandomAccessFile f = new RandomAccessFile(file, "r")) {
                    f.seek(start);
                    byte[] buf = new byte[16384];
                    long left = len;
                    while (left > 0) {
                        int n = f.read(buf, 0, (int) Math.min(buf.length, left));
                        if (n < 0) break;
                        o.write(buf, 0, n);
                        left -= n;
                    }
                }
            }
            o.flush();
        } catch (IOException ignored) {
            // Lautsprecher hat die Verbindung abgebrochen – kein Problem.
        }
    }

    @Override
    public void close() {
        closed = true;
        try {
            server.close();
        } catch (IOException ignored) {
        }
    }
}
