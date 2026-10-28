package de.christina.sonnenwecker;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import java.io.File;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Führt die Begrüßung im Hintergrund aus. (Den Sonnenaufgang macht die Hue-Bridge selbst.) */
public class WakeService extends Service {
    public static final String ACTION_TEST_WAKE = "de.christina.sonnenwecker.TEST_WAKE";
    private static final String CHANNEL = "sonnenwecker";

    private final AtomicInteger running = new AtomicInteger();

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startInForeground();
        String action = intent != null ? intent.getAction() : null;
        long target = intent != null ? intent.getLongExtra(Scheduler.EXTRA_TARGET, 0) : 0;
        running.incrementAndGet();
        new Thread(() -> work(action, target), "sonnenwecker-work").start();
        return START_NOT_STICKY;
    }

    private void startInForeground() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Sonnenwecker", NotificationManager.IMPORTANCE_LOW));
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Sonnenwecker")
                .setContentText("Guten Morgen wird vorbereitet …")
                .build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(1, n);
        }
    }

    private void work(String action, long target) {
        Prefs p = new Prefs(this);
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sonnenwecker:work");
        wl.acquire(10 * 60_000L);
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiManager.WifiLock wifiLock = wm.createWifiLock(
                Build.VERSION.SDK_INT >= 29 ? WifiManager.WIFI_MODE_FULL_LOW_LATENCY : WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                "sonnenwecker:wifi");
        wifiLock.acquire();
        try {
            if (Scheduler.ACTION_WAKE.equals(action)) {
                p.setLong("lastWakeTarget", target);
                greet(p, target, false);
            } else if (ACTION_TEST_WAKE.equals(action)) {
                greet(p, System.currentTimeMillis(), true);
            }
        } catch (Exception e) {
            p.log("Fehler: " + e.getMessage());
        } finally {
            try {
                if (Scheduler.ACTION_WAKE.equals(action)) {
                    // Nächsten Wecker planen und die Hue-Automation darauf umstellen
                    p.log(Scheduler.reschedule(this).replace('\n', ' '));
                    BridgeSync.sync(this, false);
                }
            } finally {
                wifiLock.release();
                if (wl.isHeld()) wl.release();
                if (running.decrementAndGet() == 0) {
                    stopForeground(STOP_FOREGROUND_REMOVE);
                    stopSelf();
                }
            }
        }
    }

    private void greet(Prefs p, long wakeTime, boolean test) throws Exception {
        // 1. Wetter holen und Text bauen
        String forecast = null;
        for (int attempt = 0; attempt < 3 && forecast == null; attempt++) {
            try {
                forecast = Weather.fetchForecast(p.lat(), p.lon());
            } catch (Exception e) {
                Thread.sleep(3000);
            }
        }
        String text = Weather.greeting(p.name(), forecast, wakeTime, p.middayDays());
        p.log("Begrüßung: " + text);

        // 2. Sprachdatei erzeugen
        File wav = new File(getCacheDir(), "gruss.wav");
        Tts.synthesize(this, text, wav);
        long wavMs = Tts.wavDurationMs(wav);

        // 3. Lautsprecher finden
        String host = p.castHost();
        int port = p.castPort();
        if (!p.castName().isEmpty()) {
            List<CastDiscovery.Device> found = CastDiscovery.discover(this, 8000, p.castName());
            for (CastDiscovery.Device d : found) {
                if (p.castName().equals(d.name)) {
                    host = d.host;
                    port = d.port;
                    p.edit().putString("castHost", host).putInt("castPort", port).apply();
                }
            }
        }
        if (host.isEmpty()) throw new Exception("Kein Lautsprecher eingerichtet");

        // 4. Verbinden, Lautstärke setzen, abspielen
        Exception last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try (CastClient cast = new CastClient(); MiniHttpServer server = new MiniHttpServer(wav, "audio/wav")) {
                cast.connect(host, port);
                cast.launchMediaReceiver();
                cast.setVolume(p.volumePercent() / 100.0);
                InetAddress me = cast.localAddress();
                String ip = me instanceof Inet6Address ? "[" + me.getHostAddress().replaceAll("%.*", "") + "]" : me.getHostAddress();
                String url = "http://" + ip + ":" + server.port() + "/gruss-" + System.currentTimeMillis() + ".wav";

                if (!test) {
                    long wait = wakeTime - System.currentTimeMillis();
                    if (wait > 0) Thread.sleep(wait);
                }
                cast.load(url, "audio/wav", "BUFFERED", "Guten Morgen");
                if (!cast.waitFinished(wavMs + 20000)) p.log("Ende der Begrüßung nicht erkannt – starte Radio trotzdem");
                cast.load(p.radioUrl(), "audio/mpeg", "LIVE", p.radioTitle());
                if (cast.waitPlaying(20000)) {
                    p.log("Radio läuft");
                } else {
                    p.log("Radio hat nicht bestätigt, dass es spielt – Sender-Adresse prüfen");
                }
                return;
            } catch (Exception e) {
                last = e;
                p.log("Lautsprecher-Versuch " + (attempt + 1) + " fehlgeschlagen: " + e.getMessage());
                Thread.sleep(3000);
            }
        }
        throw new Exception("Lautsprecher nicht erreichbar: " + (last != null ? last.getMessage() : "?"));
    }
}
