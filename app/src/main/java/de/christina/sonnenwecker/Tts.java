package de.christina.sonnenwecker;

import android.content.Context;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Wandelt den Begrüßungstext mit der Android-Sprachausgabe in eine WAV-Datei um. */
public final class Tts {
    private Tts() { }

    public static void synthesize(Context ctx, String text, File out) throws Exception {
        CountDownLatch init = new CountDownLatch(1);
        int[] status = {TextToSpeech.ERROR};
        TextToSpeech[] tts = new TextToSpeech[1];
        tts[0] = new TextToSpeech(ctx.getApplicationContext(), s -> {
            status[0] = s;
            init.countDown();
        });
        try {
            if (!init.await(15, TimeUnit.SECONDS) || status[0] != TextToSpeech.SUCCESS) {
                throw new Exception("Sprachausgabe konnte nicht gestartet werden");
            }
            int lang = tts[0].setLanguage(Locale.GERMANY);
            if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
                throw new Exception("Deutsche Stimme fehlt – bitte in den Android-Einstellungen unter Sprachausgabe installieren");
            }
            CountDownLatch done = new CountDownLatch(1);
            boolean[] ok = {false};
            tts[0].setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) { }
                @Override public void onDone(String id) { ok[0] = true; done.countDown(); }
                @Override public void onError(String id) { done.countDown(); }
            });
            if (out.exists()) out.delete();
            int r = tts[0].synthesizeToFile(text, new Bundle(), out, "gruss");
            if (r != TextToSpeech.SUCCESS) throw new Exception("Sprachausgabe hat abgelehnt");
            if (!done.await(30, TimeUnit.SECONDS) || !ok[0] || out.length() < 100) {
                throw new Exception("Sprachausgabe hat keine Datei erzeugt");
            }
        } finally {
            tts[0].shutdown();
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
