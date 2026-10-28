package de.christina.sonnenwecker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Wird aufgerufen, wenn sich der Handywecker ändert, nach dem Neustart und nach App-Updates. */
public class AlarmChangedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        // Nach Neustart oder Update auf jeden Fall bei der Bridge nachsehen, sonst nur bei Änderungen.
        boolean force = !"android.app.action.NEXT_ALARM_CLOCK_CHANGED".equals(intent.getAction());
        PendingResult pr = goAsync();
        new Thread(() -> {
            try {
                String status = Scheduler.reschedule(ctx);
                new Prefs(ctx).log("Neu geplant: " + status.replace('\n', ' '));
                BridgeSync.sync(ctx, force);
            } finally {
                pr.finish();
            }
        }, "sonnenwecker-sync").start();
    }
}
