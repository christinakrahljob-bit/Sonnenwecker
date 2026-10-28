package de.christina.sonnenwecker;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Plant den nächsten Wecker:
 * – als echten Android-Wecker (zuverlässig, mit Wecker-Symbol in der Statusleiste),
 * – eine Minute vorher die Begrüßung (Wetter holen, Sprachdatei, Lautsprecher verbinden),
 * – und legt fest, auf welche Weckzeit die Hue-Automation gestellt werden soll.
 */
public final class Scheduler {
    public static final String ACTION_RING = "de.christina.sonnenwecker.RING";
    public static final String ACTION_PREP = "de.christina.sonnenwecker.PREP";
    public static final String ACTION_BACKUP = "de.christina.sonnenwecker.BACKUP";
    public static final String ACTION_DISMISS = "de.christina.sonnenwecker.DISMISS";
    /** Hue-Automation (erneut) abgleichen: nach einem Test, nach einem Fehler oder für weit entfernte Wecker. */
    public static final String ACTION_SYNC = "de.christina.sonnenwecker.SYNC";
    public static final String EXTRA_TARGET = "target";
    public static final String EXTRA_ALARM = "alarm";
    /** So viel früher wird die Begrüßung vorbereitet. */
    public static final long PREP_MS = 60_000;
    /**
     * Die Hue-Automation wiederholt sich am Wochentag des Weckers. Wecker, die weiter als 6 Tage entfernt sind,
     * werden deshalb erst später eingetragen.
     */
    private static final long ARM_WINDOW_MS = 6L * 24 * 60 * 60_000L;

    static final int CODE_RING = 10, CODE_PREP = 11, CODE_BACKUP = 12, CODE_DISMISS = 13,
            CODE_RETRY = 4, CODE_LATER = 5;

    private Scheduler() { }

    /** Plant alles neu. Schnell und ohne Netzwerk – darf im UI-Thread laufen. Gibt eine Kurzbeschreibung zurück. */
    public static synchronized String reschedule(Context ctx) {
        Prefs p = new Prefs(ctx);
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        cancelLegacy(ctx, am);
        am.cancel(pending(ctx, ACTION_RING, 0, 0, CODE_RING));
        am.cancel(pending(ctx, ACTION_PREP, 0, 0, CODE_PREP));

        long now = System.currentTimeMillis();
        boolean exact = canExact(am);
        AlarmStore.Next next = new AlarmStore(ctx).next(now);
        long armLater = 0;
        String status;

        if (next == null) {
            p.edit().putLong("nextTarget", 0).putInt("nextAlarmId", 0).putLong("lightTarget", 0).apply();
            status = "Kein Wecker aktiv.";
        } else {
            long target = next.time;
            p.edit().putLong("nextTarget", target).putInt("nextAlarmId", next.alarm.id).apply();

            PendingIntent ring = pending(ctx, ACTION_RING, target, next.alarm.id, CODE_RING);
            if (exact) {
                Intent show = new Intent(ctx, MainActivity.class);
                PendingIntent showPi = PendingIntent.getActivity(ctx, 0, show,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                am.setAlarmClock(new AlarmManager.AlarmClockInfo(target, showPi), ring);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target, ring);
            }
            if (target - now > 5000) {
                set(am, exact, Math.max(now + 2000, target - PREP_MS),
                        pending(ctx, ACTION_PREP, target, next.alarm.id, CODE_PREP));
            }

            if (target - now > ARM_WINDOW_MS) {
                p.setLong("lightTarget", 0);
                armLater = target - ARM_WINDOW_MS + 60_000L;
            } else {
                p.setLong("lightTarget", target);
            }
            status = "Nächster Wecker: " + new SimpleDateFormat("EE dd.MM. HH:mm", Locale.GERMANY).format(new Date(target));
        }

        // Späterer Abgleich der Hue-Automation: nach einem laufenden Sonnenaufgang/Test
        // oder wenn ein weit entfernter Wecker eingetragen werden muss.
        long later = BridgeSync.protectedUntil(p, now);
        if (armLater > now && (later <= now || armLater < later)) later = armLater;
        if (later > now) set(am, exact, later, pending(ctx, ACTION_SYNC, 0, 0, CODE_LATER));
        else am.cancel(pending(ctx, ACTION_SYNC, 0, 0, CODE_LATER));
        return status;
    }

    /** Backup-Vibration planen (zur Weckzeit + 5 Minuten). */
    public static void scheduleBackup(Context ctx, long target) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        set(am, canExact(am), target + Prefs.BACKUP_DELAY_MS, pending(ctx, ACTION_BACKUP, target, 0, CODE_BACKUP));
    }

    public static void cancelBackup(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pending(ctx, ACTION_BACKUP, 0, 0, CODE_BACKUP));
    }

    /** Neuer Versuch, die Hue-Automation abzugleichen (z. B. weil die Bridge gerade nicht erreichbar war). */
    public static void scheduleRetry(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 30 * 60_000L,
                pending(ctx, ACTION_SYNC, 0, 0, CODE_RETRY));
    }

    /** „Ich bin wach“ aus der Benachrichtigung. */
    public static PendingIntent dismissIntent(Context ctx, long target) {
        return pending(ctx, ACTION_DISMISS, target, 0, CODE_DISMISS);
    }

    public static boolean canExact(AlarmManager am) {
        return Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
    }

    /** „in 9 Std. 12 Min.“ – wie die Uhr-App nach dem Einschalten eines Weckers. */
    public static String untilText(long target, long now) {
        long min = Math.max(1, (target - now + 59_999) / 60_000);
        long d = min / (24 * 60), h = (min / 60) % 24, m = min % 60;
        StringBuilder sb = new StringBuilder("Wecker in ");
        if (d > 0) sb.append(d).append(d == 1 ? " Tag " : " Tagen ");
        if (h > 0) sb.append(h).append(" Std. ");
        if (d == 0 && (m > 0 || h == 0)) sb.append(m).append(" Min.");
        return sb.toString().trim();
    }

    /** „Mo, 2. Nov.“ bzw. „Heute“/„Morgen“. */
    public static String dayText(long target, long now) {
        Calendar t = Calendar.getInstance();
        t.setTimeInMillis(target);
        Calendar n = Calendar.getInstance();
        n.setTimeInMillis(now);
        String date = new SimpleDateFormat("EE, d. MMM", Locale.GERMANY).format(new Date(target));
        if (t.get(Calendar.YEAR) == n.get(Calendar.YEAR)) {
            int diff = t.get(Calendar.DAY_OF_YEAR) - n.get(Calendar.DAY_OF_YEAR);
            if (diff == 0) return "Heute";
            if (diff == 1) return "Morgen, " + date;
        }
        return date;
    }

    private static void cancelLegacy(Context ctx, AlarmManager am) {
        // Wecker aus älteren Versionen (Sonnenaufgang direkt, Begrüßung, Nachsehen) entfernen
        String[] old = {"de.christina.sonnenwecker.SUNRISE", "de.christina.sonnenwecker.WAKE", "de.christina.sonnenwecker.RECHECK"};
        int[] codes = {1, 2, 3};
        for (int i = 0; i < old.length; i++) am.cancel(pending(ctx, old[i], 0, 0, codes[i]));
    }

    private static void set(AlarmManager am, boolean exact, long at, PendingIntent pi) {
        if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
    }

    static PendingIntent pending(Context ctx, String action, long target, int alarmId, int code) {
        Intent i = new Intent(ctx, TriggerReceiver.class).setAction(action)
                .putExtra(EXTRA_TARGET, target).putExtra(EXTRA_ALARM, alarmId);
        return PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
