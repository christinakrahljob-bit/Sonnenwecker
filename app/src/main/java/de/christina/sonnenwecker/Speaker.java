package de.christina.sonnenwecker;

import android.content.Context;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;

/** Hilfen rund um den Lautsprecher. Alles blockierend – nicht im UI-Thread aufrufen. */
final class Speaker {
    private Speaker() { }

    /** Aktuelle Adresse des gewählten Lautsprechers (sucht im WLAN nach, falls er umgezogen ist). */
    static String[] resolve(Context ctx) {
        Prefs p = new Prefs(ctx);
        String host = p.castHost();
        int port = p.castPort();
        if (!p.castName().isEmpty()) {
            List<CastDiscovery.Device> found = CastDiscovery.discover(ctx, 8000, p.castName());
            for (CastDiscovery.Device d : found) {
                if (p.castName().equals(d.name)) {
                    host = d.host;
                    port = d.port;
                    p.edit().putString("castHost", host).putInt("castPort", port).apply();
                }
            }
        }
        return new String[]{host, String.valueOf(port)};
    }

    /** Schnelle Prüfung, ob der Lautsprecher unter der gespeicherten Adresse antwortet. */
    static boolean reachable(Context ctx) {
        Prefs p = new Prefs(ctx);
        if (p.castHost().isEmpty()) return false;
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(p.castHost(), p.castPort()), 2500);
            return true;
        } catch (Exception e) {
            String[] r = resolve(ctx); // vielleicht neue Adresse im WLAN
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(r[0], Integer.parseInt(r[1])), 2500);
                return true;
            } catch (Exception e2) {
                return false;
            }
        }
    }

    /** „Radio aus“ */
    static void stopRadio(Context ctx) throws Exception {
        Prefs p = new Prefs(ctx);
        String host = p.castHost();
        int port = p.castPort();
        if (host.isEmpty()) throw new Exception("Kein Lautsprecher eingerichtet");
        Exception last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try (CastClient c = new CastClient()) {
                c.connect(host, port);
                c.stopMediaApp();
                p.setString("radioStatus", "aus");
                p.log("Radio aus");
                return;
            } catch (Exception e) {
                last = e;
                String[] r = resolve(ctx);
                host = r[0];
                port = Integer.parseInt(r[1]);
            }
        }
        throw last;
    }
}
