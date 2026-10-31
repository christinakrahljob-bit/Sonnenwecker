package de.christina.sonnenwecker;

import android.app.Notification;
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
import java.util.concurrent.atomic.AtomicInteger;

/** Begrüßung und Radio auf dem Lautsprecher. (Den Sonnenaufgang macht die Hue-Bridge selbst.) */
public class WakeService extends Service {
    public static final String ACTION_WAKE = "de.christina.sonnenwecker.GREET";
    public static final String ACTION_TEST_WAKE = "de.christina.sonnenwecker.TEST_WAKE";

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
        int alarmId = intent != null ? intent.getIntExtra(Scheduler.EXTRA_ALARM, 0) : 0;
        running.incrementAndGet();
        new Thread(() -> work(action, target, alarmId), "sonnenwecker-work").start();
        return START_NOT_STICKY;
    }

    private void startInForeground() {
        Ringing.ensureChannels(this);
        Notification n = new Notification.Builder(this, Ringing.CH_SERVICE)
                .setSmallIcon(R.drawable.i_sunrise)
                .setContentTitle("Sonnenwecker")
                .setContentText("Guten Morgen wird vorbereitet …")
                .build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(1, n);
        }
    }

    private void work(String action, long target, int alarmId) {
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
            if (ACTION_WAKE.equals(action)) {
                if (p.greetStartedFor() == target) return; // läuft schon
                p.setLong("greetStartedFor", target);
                greet(p, target, alarmId, false);
            } else if (ACTION_TEST_WAKE.equals(action)) {
                greet(p, System.currentTimeMillis(), alarmId, true);
            }
        } catch (Exception e) {
            p.log("Fehler: " + e.getMessage());
        } finally {
            try {
                if (ACTION_WAKE.equals(action)) {
                    // Nächsten Wecker planen und die Hue-Automation darauf umstellen
                    p.log(Scheduler.reschedule(this));
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

    /**
     * @param alarmId der Wecker, dessen Begrüßung gesprochen wird; 0 beim Test = der nächste Wecker
     */
    private void greet(Prefs p, long wakeTime, int alarmId, boolean test) throws Exception {
        // 0. Welcher Begrüßungstext? Der des Weckers – ohne Wecker der Standardtext
        AlarmStore store = new AlarmStore(this);
        Alarm alarm = alarmId > 0 ? store.get(alarmId) : null;
        if (alarm == null && test) {
            AlarmStore.Next next = store.next(System.currentTimeMillis());
            if (next != null) alarm = next.alarm;
        }
        String template = alarm != null ? alarm.greeting : Greeting.STANDARD;
        int middayMask = alarm != null ? alarm.middayDays : p.defaultMiddayMask();
        boolean midday = Greeting.usesMidday(template) && Alarm.maskOn(middayMask, wakeTime);

        // 1. Wetter holen (auch für den Weck-Bildschirm), Text bauen
        String forecast = null;
        for (int attempt = 0; attempt < 3 && forecast == null; attempt++) {
            try {
                forecast = Weather.fetchForecast(p.lat(), p.lon());
            } catch (Exception e) {
                Thread.sleep(3000);
            }
        }
        Weather.Day day = Weather.evaluate(forecast, wakeTime, midday);
        String[] lines = Weather.display(day);
        p.edit().putString("weatherToday", lines[0]).putString("weatherMidday", lines[1])
                .putString("weatherMiddayRain", lines[2]).putLong("weatherFor", wakeTime)
                .putString("radioStatus", "startet").apply();
        String text = Greeting.render(template, p.name(), wakeTime,
                Weather.todaySentence(day), Weather.middaySentence(day));
        p.log(text.isEmpty() ? "Begrüßung: (leer – nur Radio)" : "Begrüßung: " + text);

        // 2. Sprachdatei erzeugen – mit der Stimme des Weckers, sonst mit der Standardstimme.
        //    Klappt beides nicht, läuft wenigstens das Radio.
        File wav = new File(Prefs.cacheDir(this), "gruss.wav");
        boolean haveGreeting = false;
        if (!text.isEmpty()) {
            String voice = alarm != null ? alarm.voice : VoiceSpec.DEFAULT;
            String error = null;
            int attempts = VoiceSpec.isCloud(voice) ? 2 : 1; // Internet kann morgens kurz brauchen
            for (int attempt = 0; attempt < attempts && !haveGreeting; attempt++) {
                try {
                    if (attempt > 0) Thread.sleep(3000);
                    Tts.toWav(this, text, voice, wav);
                    haveGreeting = true;
                } catch (Exception e) {
                    error = e.getMessage();
                }
            }
            if (!haveGreeting && !voice.isEmpty()) {
                p.log("Stimme „" + VoiceSpec.label(voice, alarm.voiceLabel) + "“ nicht verfügbar (" + error
                        + ") – nehme die Standardstimme");
                try {
                    Tts.synthesize(this, text, wav);
                    haveGreeting = true;
                } catch (Exception e) {
                    error = e.getMessage();
                }
            }
            if (!haveGreeting) p.log("Begrüßung übersprungen: " + error);
        }
        long wavMs = haveGreeting ? Tts.wavDurationMs(wav) : 0;

        // 3. Lautsprecher finden
        String[] hp = Speaker.resolve(this);
        String host = hp[0];
        int port = Integer.parseInt(hp[1]);
        if (host.isEmpty()) {
            p.setString("radioStatus", "Kein Lautsprecher eingerichtet");
            throw new Exception("Kein Lautsprecher eingerichtet");
        }

        // 4. Verbinden, Lautstärke setzen, abspielen
        Exception last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try (CastClient cast = new CastClient();
                 MiniHttpServer server = haveGreeting ? new MiniHttpServer(wav, "audio/wav") : null) {
                cast.connect(host, port);
                cast.launchMediaReceiver();
                cast.setVolume(p.volumePercent() / 100.0);

                if (!test) {
                    long wait = wakeTime - System.currentTimeMillis();
                    if (wait > 0) Thread.sleep(wait);
                }
                if (server != null) {
                    InetAddress me = cast.localAddress();
                    String ip = me instanceof Inet6Address ? "[" + me.getHostAddress().replaceAll("%.*", "") + "]" : me.getHostAddress();
                    String url = "http://" + ip + ":" + server.port() + "/gruss-" + System.currentTimeMillis() + ".wav";
                    cast.load(url, "audio/wav", "BUFFERED", "Guten Morgen");
                    if (!cast.waitFinished(wavMs + 20000)) p.log("Ende der Begrüßung nicht erkannt – starte Radio trotzdem");
                }
                cast.load(p.radioUrl(), "audio/mpeg", "LIVE", p.radioTitle());
                if (cast.waitPlaying(20000)) {
                    p.setString("radioStatus", "läuft");
                    p.log("Radio läuft");
                } else {
                    p.setString("radioStatus", "startet nicht – Sender prüfen");
                    p.log("Radio hat nicht bestätigt, dass es spielt – Sender-Adresse prüfen");
                }
                return;
            } catch (Exception e) {
                last = e;
                p.log("Lautsprecher-Versuch " + (attempt + 1) + " fehlgeschlagen: " + e.getMessage());
                Thread.sleep(3000);
            }
        }
        p.setString("radioStatus", "Lautsprecher nicht erreichbar");
        throw new Exception("Lautsprecher nicht erreichbar: " + (last != null ? last.getMessage() : "?"));
    }
}
