package de.christina.sonnenwecker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Wird zum geplanten Zeitpunkt ausgelöst und startet die eigentliche Arbeit im Hintergrunddienst. */
public class TriggerReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        Prefs p = new Prefs(ctx);
        if (Scheduler.ACTION_RECHECK.equals(intent.getAction())) {
            p.log("Neu geplant: " + Scheduler.reschedule(ctx).replace('\n', ' '));
            return;
        }
        long target = intent.getLongExtra(Scheduler.EXTRA_TARGET, 0);
        if (target == 0 || target != p.nextTarget()) {
            p.log("Veralteter Auslöser ignoriert");
            return;
        }
        Intent svc = new Intent(ctx, WakeService.class)
                .setAction(intent.getAction())
                .putExtra(Scheduler.EXTRA_TARGET, target);
        ctx.startForegroundService(svc);
    }
}
