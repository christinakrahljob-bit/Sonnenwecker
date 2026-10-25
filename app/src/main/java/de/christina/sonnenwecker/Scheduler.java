package de.christina.sonnenwecker;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Liest den nächsten Handywecker aus und plant Sonnenaufgang und Begrüßung passend dazu. */
public final class Scheduler {
    public static final String ACTION_SUNRISE = "de.christina.sonnenwecker.SUNRISE";
    public static final String ACTION_WAKE = "de.christina.sonnenwecker.WAKE";
    public static final String EXTRA_TARGET = "target";
    /** So viel früher wird die Begrüßung vorbereitet (Wetter holen, Sprachdatei, Lautsprecher verbinden). */
    public static final long PREP_MS = 60_000;
    /** Wecker innerhalb dieser Zeit nach dem letzten Wecken gelten als Schlummern und werden ignoriert. */
    private static final long SNOOZE_WINDOW_MS = 60 * 60_000;

    private Scheduler() { }

    public static synchronized String reschedule(Context ctx) {
        Prefs p = new Prefs(ctx);
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        PendingIntent sunrisePi = pending(ctx, ACTION_SUNRISE, 0);
        PendingIntent wakePi = pending(ctx, ACTION_WAKE, 0);
        am.cancel(sunrisePi);
        am.cancel(wakePi);

        AlarmManager.AlarmClockInfo next = am.getNextAlarmClock();
        long now = System.currentTimeMillis();
        if (next == null || next.getTriggerTime() <= now + 5000) {
            p.setLong("nextTarget", 0);
            return "Kein Wecker gestellt.";
        }
        long target = next.getTriggerTime();
        long last = p.lastWakeTarget();
        if (target == last) {
            p.setLong("nextTarget", 0);
            return "Begrüßung für diesen Wecker ist bereits gelaufen.";
        }
        if (last > 0 && target > last && target - last < SNOOZE_WINDOW_MS) {
            p.setLong("nextTarget", 0);
            return "Schlummern erkannt – keine erneute Begrüßung.";
        }

        long sunriseAt = Math.max(now + 2000, target - p.leadMinutes() * 60_000L);
        long wakeAt = Math.max(now + 2000, target - PREP_MS);
        p.setLong("nextTarget", target);

        boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        if (p.sunriseDoneFor() != target) set(am, exact, sunriseAt, pending(ctx, ACTION_SUNRISE, target));
        set(am, exact, wakeAt, pending(ctx, ACTION_WAKE, target));

        SimpleDateFormat f = new SimpleDateFormat("EE dd.MM. HH:mm", Locale.GERMANY);
        SimpleDateFormat hm = new SimpleDateFormat("HH:mm", Locale.GERMANY);
        String creator = "";
        if (next.getShowIntent() != null) creator = " (" + next.getShowIntent().getCreatorPackage() + ")";
        String s = "Nächster Wecker: " + f.format(new Date(target)) + creator
                + "\nSonnenaufgang ab " + hm.format(new Date(sunriseAt))
                + ", Begrüßung um " + hm.format(new Date(target));
        if (!exact) s += "\n⚠ Genaue Wecker sind nicht erlaubt – Zeiten können sich verschieben.";
        return s;
    }

    private static void set(AlarmManager am, boolean exact, long at, PendingIntent pi) {
        if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
    }

    private static PendingIntent pending(Context ctx, String action, long target) {
        Intent i = new Intent(ctx, TriggerReceiver.class).setAction(action).putExtra(EXTRA_TARGET, target);
        int code = ACTION_SUNRISE.equals(action) ? 1 : 2;
        return PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
