package de.christina.sonnenwecker;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Benachrichtigung „Guten Morgen“ mit Weck-Bildschirm und „Ich bin wach“. */
public final class Ringing {
    public static final String CH_WAKE = "wecken";
    public static final String CH_SERVICE = "dienst";
    public static final int NOTIF_ID = 7;

    private Ringing() { }

    public static void ensureChannels(Context ctx) {
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        NotificationChannel wake = new NotificationChannel(CH_WAKE, "Wecken", NotificationManager.IMPORTANCE_HIGH);
        wake.setDescription("Weck-Bildschirm und „Ich bin wach“");
        wake.setSound(null, null);          // Ton kommt vom Lautsprecher
        wake.enableVibration(false);        // Vibration steuert die App selbst (Backup)
        wake.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(wake);
        NotificationChannel svc = new NotificationChannel(CH_SERVICE, "Begrüßung vorbereiten", NotificationManager.IMPORTANCE_LOW);
        svc.setShowBadge(false);
        nm.createNotificationChannel(svc);
    }

    public static Notification build(Context ctx, long target, boolean vibrating) {
        ensureChannels(ctx);
        Prefs p = new Prefs(ctx);
        Intent full = new Intent(ctx, AlarmActivity.class)
                .putExtra(Scheduler.EXTRA_TARGET, target)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        PendingIntent fullPi = PendingIntent.getActivity(ctx, 20, full,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String backupAt = new SimpleDateFormat("HH:mm", Locale.GERMANY).format(new Date(target + Prefs.BACKUP_DELAY_MS));
        String text;
        if (vibrating) text = "Backup-Vibration läuft. Tippe auf „Ich bin wach“.";
        else if (p.backupEnabled()) text = "Tippe auf „Ich bin wach“, sonst vibriert das Handy um " + backupAt + ".";
        else text = "Tippe auf „Ich bin wach“.";
        Notification.Action wake = new Notification.Action.Builder(
                Icon.createWithResource(ctx, R.drawable.i_check), "Ich bin wach",
                Scheduler.dismissIntent(ctx, target)).build();
        return new Notification.Builder(ctx, CH_WAKE)
                .setSmallIcon(R.drawable.i_sunrise)
                .setContentTitle("Guten Morgen, " + p.name())
                .setContentText(text)
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setShowWhen(false)
                .setFullScreenIntent(fullPi, true)
                .setContentIntent(fullPi)
                .addAction(wake)
                .build();
    }

    /** Zur Weckzeit: Weck-Bildschirm zeigen. */
    public static void show(Context ctx, long target) {
        ctx.getSystemService(NotificationManager.class).notify(NOTIF_ID, build(ctx, target, false));
    }

    /** „Ich bin wach“: Backup abbrechen, Vibration stoppen, Benachrichtigung entfernen. Radio läuft weiter. */
    public static void confirm(Context ctx, long target) {
        Prefs p = new Prefs(ctx);
        if (target > 0) p.setLong("confirmedTarget", target);
        Scheduler.cancelBackup(ctx);
        ctx.stopService(new Intent(ctx, AlarmService.class));
        ctx.getSystemService(NotificationManager.class).cancel(NOTIF_ID);
        p.log("Ich bin wach ✓");
    }
}
