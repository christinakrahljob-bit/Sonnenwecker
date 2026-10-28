package de.christina.sonnenwecker;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

/** Backup: vibriert, bis „Ich bin wach“ getippt wird (höchstens 30 Minuten). */
public class AlarmService extends Service {
    private static final long MAX_MS = 30 * 60_000L;
    private Vibrator vibrator;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        long target = intent != null ? intent.getLongExtra(Scheduler.EXTRA_TARGET, 0) : 0;
        Prefs p = new Prefs(this);
        Notification n = Ringing.build(this, target, true);
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(Ringing.NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED);
            } else {
                startForeground(Ringing.NOTIF_ID, n);
            }
        } catch (Exception e) {
            p.log("Backup-Dienst konnte nicht starten: " + e.getMessage());
        }
        vibrate();
        p.log("Backup-Vibration gestartet");
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(this::stopSelf, MAX_MS);
        return START_NOT_STICKY;
    }

    private void vibrate() {
        if (Build.VERSION.SDK_INT >= 31) {
            vibrator = getSystemService(VibratorManager.class).getDefaultVibrator();
        } else {
            vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        }
        if (vibrator == null || !vibrator.hasVibrator()) return;
        long[] pattern = {0, 900, 700};
        VibrationEffect effect = VibrationEffect.createWaveform(pattern, 0);
        if (Build.VERSION.SDK_INT >= 33) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM));
        } else {
            vibrator.vibrate(effect, new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build());
        }
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (vibrator != null) vibrator.cancel();
        super.onDestroy();
    }
}
