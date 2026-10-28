package de.christina.sonnenwecker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Wird zum geplanten Zeitpunkt ausgelöst. */
public class TriggerReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        Prefs p = new Prefs(ctx);
        String action = intent.getAction();

        if (Scheduler.ACTION_RECHECK.equals(action) || Scheduler.ACTION_SYNC.equals(action)) {
            boolean force = Scheduler.ACTION_SYNC.equals(action);
            PendingResult pr = goAsync();
            new Thread(() -> {
                try {
                    p.log("Neu geplant: " + Scheduler.reschedule(ctx).replace('\n', ' '));
                    BridgeSync.sync(ctx, force);
                } finally {
                    pr.finish();
                }
            }, "sonnenwecker-sync").start();
            return;
        }

        if (Scheduler.ACTION_SUNRISE.equals(action)) return; // ältere Version – den Sonnenaufgang macht jetzt die Bridge

        long target = intent.getLongExtra(Scheduler.EXTRA_TARGET, 0);
        if (target == 0 || target != p.nextTarget()) {
            p.log("Veralteter Auslöser ignoriert");
            return;
        }
        Intent svc = new Intent(ctx, WakeService.class)
                .setAction(action)
                .putExtra(Scheduler.EXTRA_TARGET, target);
        ctx.startForegroundService(svc);
    }
}
