package de.christina.sonnenwecker;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Liest den nächsten Handywecker aus und plant Sonnenaufgang und Begrüßung passend dazu. */
public final class Scheduler {
    public static final String ACTION_SUNRISE = "de.christina.sonnenwecker.SUNRISE";
    public static final String ACTION_WAKE = "de.christina.sonnenwecker.WAKE";
    /** Nach einem fremden Wecker (z. B. Erinnerung einer anderen App) erneut nachsehen. */
    public static final String ACTION_RECHECK = "de.christina.sonnenwecker.RECHECK";
    public static final String EXTRA_TARGET = "target";
    /** So viel früher wird die Begrüßung vorbereitet (Wetter holen, Sprachdatei, Lautsprecher verbinden). */
    public static final long PREP_MS = 60_000;
    /** Wecker innerhalb dieser Zeit nach dem letzten Wecken gelten als Schlummern und werden ignoriert. */
    private static final long SNOOZE_WINDOW_MS = 60 * 60_000;

    /** Uhr-Apps der gängigen Hersteller. Nur deren Wecker lösen Sonnenaufgang und Begrüßung aus. */
    static final Set<String> CLOCK_APPS = new HashSet<>(Arrays.asList(
            "com.google.android.deskclock",       // Google Uhr (Pixel und viele andere)
            "com.android.deskclock",              // Android-Standard, Xiaomi
            "com.sec.android.app.clockpackage",   // Samsung
            "com.oneplus.deskclock",              // OnePlus
            "com.coloros.alarmclock",             // Oppo / Realme
            "com.huawei.deskclock",               // Huawei
            "com.motorola.timeweatherwidget",     // Motorola
            "com.sonyericsson.organizer"          // Sony
    ));

    private Scheduler() { }

    public static synchronized String reschedule(Context ctx) {
        Prefs p = new Prefs(ctx);
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pending(ctx, ACTION_SUNRISE, 0));
        am.cancel(pending(ctx, ACTION_WAKE, 0));
        am.cancel(pending(ctx, ACTION_RECHECK, 0));

        AlarmManager.AlarmClockInfo next = am.getNextAlarmClock();
        long now = System.currentTimeMillis();
        boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        SimpleDateFormat f = new SimpleDateFormat("EE dd.MM. HH:mm", Locale.GERMANY);
        SimpleDateFormat hm = new SimpleDateFormat("HH:mm", Locale.GERMANY);

        if (next == null || next.getTriggerTime() <= now + 5000) {
            p.setLong("nextTarget", 0);
            p.setLong("clockTarget", 0);
            return "Kein Wecker gestellt.";
        }
        long target = next.getTriggerTime();
        String creator = creatorOf(ctx, next.getShowIntent());
        String note = "";

        // Wecker einer anderen App (Erinnerung, Aufgabe, Routine …) → ignorieren und danach erneut nachsehen,
        // denn Android verrät immer nur den allerersten Wecker.
        if (creator != null && !CLOCK_APPS.contains(creator)) {
            set(am, exact, target + 30_000, pending(ctx, ACTION_RECHECK, target));
            note = "\nIgnoriert: " + f.format(new Date(target)) + " von " + creator;
            long remembered = p.clockTarget();
            if (remembered > target) {
                // Der zuletzt gesehene Wecker der Uhr-App liegt dahinter – an seinem Plan festhalten.
                target = remembered;
                creator = "Uhr-App, gemerkt";
            } else {
                p.setLong("nextTarget", 0);
                return "Kein Wecker der Uhr-App erkannt." + note + "\nDanach wird erneut nachgesehen.";
            }
        } else {
            p.setLong("clockTarget", target);
        }

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

        if (p.sunriseDoneFor() != target) set(am, exact, sunriseAt, pending(ctx, ACTION_SUNRISE, target));
        set(am, exact, wakeAt, pending(ctx, ACTION_WAKE, target));

        String s = "Nächster Wecker: " + f.format(new Date(target))
                + " (" + (creator != null ? creator : "Absender unbekannt") + ")"
                + "\nSonnenaufgang ab " + hm.format(new Date(sunriseAt))
                + ", Begrüßung um " + hm.format(new Date(target)) + note;
        if (creator == null) s += "\n⚠ Android verrät nicht, welche App diesen Wecker gestellt hat.";
        if (!exact) s += "\n⚠ Genaue Wecker sind nicht erlaubt – Zeiten können sich verschieben.";
        return s;
    }

    /** Ermittelt, welche App den Wecker gestellt hat. Null, wenn Android es nicht verrät. */
    static String creatorOf(Context ctx, PendingIntent pi) {
        if (pi == null) return null;
        try {
            String pkg = pi.getCreatorPackage();
            if (pkg != null && !pkg.isEmpty()) return pkg;
            int uid = pi.getCreatorUid();
            if (uid > 0) {
                PackageManager pm = ctx.getPackageManager();
                String[] pkgs = pm.getPackagesForUid(uid);
                if (pkgs != null) {
                    for (String x : pkgs) if (CLOCK_APPS.contains(x)) return x;
                    if (pkgs.length > 0) return pkgs[0];
                }
                String name = pm.getNameForUid(uid);
                if (name != null && !name.isEmpty()) return name;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static void set(AlarmManager am, boolean exact, long at, PendingIntent pi) {
        if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
    }

    private static PendingIntent pending(Context ctx, String action, long target) {
        Intent i = new Intent(ctx, TriggerReceiver.class).setAction(action).putExtra(EXTRA_TARGET, target);
        int code = ACTION_SUNRISE.equals(action) ? 1 : ACTION_WAKE.equals(action) ? 2 : 3;
        return PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
