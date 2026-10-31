package de.christina.sonnenwecker;

import android.content.Context;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Wandelt den Begrüßungstext in eine WAV-Datei um – mit der Stimme, die beim Wecker gewählt ist. */
public final class Tts {
    private Tts() { }

    /**
     * Mit der gewählten Stimme (siehe {@link VoiceSpec}).
     * @param style Sprechweise für Gemini-Stimmen; sonst ohne Bedeutung
     */
    public static void toWav(Context ctx, String text, String spec, String style, File out) throws Exception {
        if (VoiceSpec.isOnline(spec)) {
            byte[] wav = onlineWav(ctx, spec, style, text);
            if (out.exists()) out.delete();
            try (FileOutputStream o = new FileOutputStream(out)) {
                o.write(wav);
            }
        } else if (VoiceSpec.isEngine(spec)) {
            synthesize(ctx, text, out, VoiceSpec.enginePackage(spec), VoiceSpec.engineVoice(spec));
        } else {
            synthesize(ctx, text, out, null, null);
        }
    }

    /** WAV-Daten einer Online-Stimme (Google Cloud oder Gemini). */
    public static byte[] onlineWav(Context ctx, String spec, String style, String text) throws Exception {
        Prefs p = new Prefs(ctx);
        if (VoiceSpec.isGemini(spec)) {
            if (p.geminiKey().isEmpty()) throw new Exception("Kein API-Schlüssel für Gemini hinterlegt");
            return GeminiTts.synthesize(p.geminiKey(), VoiceSpec.geminiVoice(spec), style, text);
        }
        if (p.cloudKey().isEmpty()) throw new Exception("Kein API-Schlüssel für Google Cloud hinterlegt");
        return CloudTts.synthesize(p.cloudKey(), VoiceSpec.cloudVoice(spec), text);
    }

    /** Mit der Standardstimme des Handys. */
    public static void synthesize(Context ctx, String text, File out) throws Exception {
        synthesize(ctx, text, out, null, null);
    }

    /**
     * @param enginePackage Sprachausgabe-App; null/leer = die in Android eingestellte
     * @param voiceName     Stimme dieser App; null/leer = deutsche Standardstimme. Fehlt sie, gibt es einen Fehler.
     */
    public static void synthesize(Context ctx, String text, File out, String enginePackage, String voiceName)
            throws Exception {
        CountDownLatch init = new CountDownLatch(1);
        int[] status = {TextToSpeech.ERROR};
        TextToSpeech.OnInitListener listener = s -> {
            status[0] = s;
            init.countDown();
        };
        Context app = ctx.getApplicationContext();
        TextToSpeech tts = enginePackage == null || enginePackage.isEmpty()
                ? new TextToSpeech(app, listener)
                : new TextToSpeech(app, listener, enginePackage);
        try {
            if (!init.await(20, TimeUnit.SECONDS) || status[0] != TextToSpeech.SUCCESS) {
                throw new Exception("Sprachausgabe konnte nicht gestartet werden");
            }
            if (voiceName != null && !voiceName.isEmpty()) {
                Voice chosen = null;
                Set<Voice> voices = tts.getVoices();
                if (voices != null) {
                    for (Voice v : voices) if (voiceName.equals(v.getName())) chosen = v;
                }
                if (chosen == null || tts.setVoice(chosen) != TextToSpeech.SUCCESS) {
                    throw new Exception("Stimme „" + voiceName + "“ ist nicht verfügbar");
                }
            } else {
                int lang = tts.setLanguage(Locale.GERMANY);
                if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
                    throw new Exception("Deutsche Stimme fehlt – bitte in den Android-Einstellungen unter Sprachausgabe installieren");
                }
            }
            CountDownLatch done = new CountDownLatch(1);
            boolean[] ok = {false};
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) { }
                @Override public void onDone(String id) { ok[0] = true; done.countDown(); }
                @Override public void onError(String id) { done.countDown(); }
            });
            if (out.exists()) out.delete();
            int r = tts.synthesizeToFile(text, new Bundle(), out, "gruss");
            if (r != TextToSpeech.SUCCESS) throw new Exception("Sprachausgabe hat abgelehnt");
            if (!done.await(60, TimeUnit.SECONDS) || !ok[0] || out.length() < 100) {
                throw new Exception("Sprachausgabe hat keine Datei erzeugt");
            }
        } finally {
            tts.shutdown();
        }
    }

    /** Dauer einer WAV-Datei in Millisekunden (aus dem Dateikopf). */
    public static long wavDurationMs(File f) {
        try (RandomAccessFile r = new RandomAccessFile(f, "r")) {
            r.seek(28);
            int byteRate = Integer.reverseBytes(r.readInt());
            if (byteRate <= 0) return 20000;
            return (f.length() - 44) * 1000L / byteRate;
        } catch (Exception e) {
            return 20000;
        }
    }
}
