package de.christina.sonnenwecker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Alle geplanten Zeitpunkte landen hier. */
public class TriggerReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        Prefs p = new Prefs(ctx);
        String action = intent.getAction();
        long target = intent.getLongExtra(Scheduler.EXTRA_TARGET, 0);
        int alarmId = intent.getIntExtra(Scheduler.EXTRA_ALARM, 0);
        if (action == null) return;

        switch (action) {
            case Scheduler.ACTION_PREP:
                if (target != 0 && target == p.nextTarget()) startGreeting(ctx, target);
                break;

            case Scheduler.ACTION_RING: {
                p.edit().putLong("ringTarget", target).apply();
                new AlarmStore(ctx).afterRing(alarmId);
                Ringing.show(ctx, target);
                if (p.backupEnabled()) Scheduler.scheduleBackup(ctx, target);
                // Falls die Vorbereitung ausgefallen ist (z. B. Wecker erst vor Sekunden gestellt): jetzt begrüßen
                if (p.greetStartedFor() != target) startGreeting(ctx, target);
                p.log("Wecken " + new SimpleDateFormat("HH:mm", Locale.GERMANY).format(new Date(target)));
                syncLater(ctx, false);
                break;
            }

            case Scheduler.ACTION_BACKUP:
                if (p.confirmedTarget() != target && p.backupEnabled()) {
                    ctx.startForegroundService(new Intent(ctx, AlarmService.class).putExtra(Scheduler.EXTRA_TARGET, target));
                }
                break;

            case Scheduler.ACTION_DISMISS:
                Ringing.confirm(ctx, target);
                break;

            case Scheduler.ACTION_SYNC:
                syncLater(ctx, true);
                break;

            default:
                break;
        }
    }

    private static void startGreeting(Context ctx, long target) {
        ctx.startForegroundService(new Intent(ctx, WakeService.class)
                .setAction(WakeService.ACTION_WAKE)
                .putExtra(Scheduler.EXTRA_TARGET, target));
    }

    /** Neu planen und die Hue-Automation abgleichen – im Hintergrund, weil die Bridge übers Netz angesprochen wird. */
    private void syncLater(Context ctx, boolean force) {
        PendingResult pr = goAsync();
        new Thread(() -> {
            try {
                Scheduler.reschedule(ctx);
                BridgeSync.sync(ctx, force);
            } finally {
                pr.finish();
            }
        }, "sonnenwecker-sync").start();
    }
}
