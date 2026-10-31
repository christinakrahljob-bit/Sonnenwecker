package de.christina.sonnenwecker;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Hörproben auf dem Handy – mit jeder Stimme, die ein Wecker haben kann (siehe {@link VoiceSpec}).
 * Es spielt immer nur eine Probe; eine neue beendet die vorige. Alle Aufrufe auf dem Hauptthread.
 */
final class VoicePlayer {
    interface Listener {
        /** Eine Probe beginnt (auch während sie noch vorbereitet wird) oder endet. */
        void onState(String spec, boolean playing);

        void onError(String spec, String message);
    }

    interface EngineReady {
        void ready(TextToSpeech tts, boolean ok);
    }

    private static final class Engine {
        TextToSpeech tts;
        boolean done, ok;
        final List<EngineReady> waiting = new ArrayList<>();
    }

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, Engine> engines = new HashMap<>();
    private Listener listener;
    /** Stimme der laufenden Probe; null = keine. */
    private String playing;
    /** Zählt jede Probe hoch – Rückmeldungen älterer Proben werden ignoriert. */
    private int generation;
    private MediaPlayer media;
    private File mediaFile;
    private boolean released;

    VoicePlayer(Context ctx, Listener listener) {
        this.app = ctx.getApplicationContext();
        this.listener = listener;
    }

    /** Stimme der laufenden Probe; null = keine. */
    String current() {
        return playing;
    }

    boolean isPlaying(String spec) {
        return spec != null && spec.equals(playing);
    }

    /** Gestartete Sprachausgabe-App; pkg leer = die in Android eingestellte. Antwort auf dem Hauptthread. */
    void engine(String pkg, EngineReady cb) {
        if (released) return;
        String key = pkg == null ? "" : pkg;
        Engine e = engines.get(key);
        if (e == null) {
            Engine fresh = new Engine();
            engines.put(key, fresh);
            TextToSpeech.OnInitListener init = status -> main.post(() -> {
                if (released) return;
                fresh.done = true;
                fresh.ok = status == TextToSpeech.SUCCESS;
                if (fresh.ok) fresh.tts.setOnUtteranceProgressListener(progress);
                List<EngineReady> w = new ArrayList<>(fresh.waiting);
                fresh.waiting.clear();
                for (EngineReady r : w) r.ready(fresh.tts, fresh.ok);
            });
            fresh.tts = key.isEmpty() ? new TextToSpeech(app, init) : new TextToSpeech(app, init, key);
            e = fresh;
        }
        if (e.done) cb.ready(e.tts, e.ok);
        else e.waiting.add(cb);
    }

    /** Spielt den Text mit der Stimme; läuft schon eine Probe, endet sie. */
    void play(String spec, String text) {
        stop();
        if (released) return;
        String s = spec == null ? "" : spec;
        int gen = ++generation;
        playing = s;
        if (listener != null) listener.onState(s, true);
        if (VoiceSpec.isCloud(s)) {
            playCloud(gen, s, text);
            return;
        }
        String voiceName = VoiceSpec.engineVoice(s);
        engine(VoiceSpec.enginePackage(s), (tts, ok) -> {
            if (gen != generation) return;
            if (!ok) {
                failed(gen, "Diese Sprachausgabe-App lässt sich gerade nicht starten.");
                return;
            }
            if (!voiceName.isEmpty()) {
                Voice v = find(tts, voiceName);
                if (v == null || tts.setVoice(v) != TextToSpeech.SUCCESS) {
                    failed(gen, "Diese Stimme ist auf dem Handy nicht mehr verfügbar.");
                    return;
                }
            } else {
                int r = tts.setLanguage(Locale.GERMANY);
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                    failed(gen, "Die deutsche Stimme fehlt – bitte unter „Status und Einrichtung“ prüfen.");
                    return;
                }
            }
            if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, String.valueOf(gen)) != TextToSpeech.SUCCESS) {
                failed(gen, "Die Stimme konnte den Text nicht sprechen.");
            }
        });
    }

    static Voice find(TextToSpeech tts, String name) {
        Set<Voice> voices;
        try {
            voices = tts.getVoices();
        } catch (Exception e) {
            return null;
        }
        if (voices == null) return null;
        for (Voice v : voices) if (name.equals(v.getName())) return v;
        return null;
    }

    private void playCloud(int gen, String spec, String text) {
        String key = new Prefs(app).cloudKey();
        if (key.isEmpty()) {
            failed(gen, "Für Google Cloud fehlt der API-Schlüssel.");
            return;
        }
        new Thread(() -> {
            try {
                byte[] wav = CloudTts.synthesize(key, VoiceSpec.cloudVoice(spec), text);
                File f = new File(app.getCacheDir(), "hoerprobe-" + gen + ".wav");
                try (FileOutputStream o = new FileOutputStream(f)) {
                    o.write(wav);
                }
                main.post(() -> startMedia(gen, f));
            } catch (Exception e) {
                main.post(() -> failed(gen, e.getMessage()));
            }
        }, "sonnenwecker-hoerprobe").start();
    }

    private void startMedia(int gen, File f) {
        if (gen != generation || released) {
            f.delete();
            return;
        }
        MediaPlayer m = new MediaPlayer();
        media = m;
        mediaFile = f;
        try {
            m.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
            m.setDataSource(f.getAbsolutePath());
            m.setOnCompletionListener(mp -> finished(gen));
            m.setOnErrorListener((mp, what, extra) -> {
                failed(gen, "Die Hörprobe ließ sich nicht abspielen.");
                return true;
            });
            m.prepare();
            m.start();
        } catch (Exception e) {
            failed(gen, "Die Hörprobe ließ sich nicht abspielen.");
        }
    }

    private final UtteranceProgressListener progress = new UtteranceProgressListener() {
        @Override public void onStart(String id) { }

        @Override public void onDone(String id) {
            main.post(() -> finished(parse(id)));
        }

        @Override public void onError(String id) {
            main.post(() -> failed(parse(id), "Die Stimme konnte den Text nicht sprechen."));
        }

        @Override public void onError(String id, int code) {
            main.post(() -> failed(parse(id), errorText(code)));
        }

        @Override public void onStop(String id, boolean interrupted) {
            main.post(() -> finished(parse(id)));
        }
    };

    private static int parse(String id) {
        try {
            return Integer.parseInt(id);
        } catch (Exception e) {
            return -1;
        }
    }

    private static String errorText(int code) {
        switch (code) {
            case TextToSpeech.ERROR_NETWORK:
            case TextToSpeech.ERROR_NETWORK_TIMEOUT:
                return "Diese Stimme braucht Internet – gerade klappt die Verbindung nicht.";
            case TextToSpeech.ERROR_NOT_INSTALLED_YET:
                return "Diese Stimme ist noch nicht fertig heruntergeladen.";
            default:
                return "Die Stimme konnte den Text nicht sprechen.";
        }
    }

    private void finished(int gen) {
        if (gen != generation || playing == null) return;
        String was = playing;
        playing = null;
        releaseMedia();
        if (listener != null) listener.onState(was, false);
    }

    private void failed(int gen, String message) {
        if (gen != generation || playing == null) return;
        String was = playing;
        playing = null;
        generation++;
        releaseMedia();
        if (listener != null) {
            listener.onState(was, false);
            listener.onError(was, message == null || message.isEmpty() ? "Unbekannter Fehler" : message);
        }
    }

    /** Beendet die laufende Probe. */
    void stop() {
        generation++;
        String was = playing;
        playing = null;
        for (Engine e : engines.values()) {
            if (e.done && e.ok) {
                try {
                    e.tts.stop();
                } catch (Exception ignored) {
                }
            }
        }
        releaseMedia();
        if (was != null && listener != null) listener.onState(was, false);
    }

    private void releaseMedia() {
        if (media != null) {
            try {
                media.release();
            } catch (Exception ignored) {
            }
            media = null;
        }
        if (mediaFile != null) {
            mediaFile.delete();
            mediaFile = null;
        }
    }

    /** Am Ende der Activity: alles beenden und die Sprachausgabe-Apps freigeben. */
    void release() {
        stop();
        released = true;
        listener = null;
        for (Engine e : engines.values()) {
            try {
                e.tts.shutdown();
            } catch (Exception ignored) {
            }
        }
        engines.clear();
    }
}
