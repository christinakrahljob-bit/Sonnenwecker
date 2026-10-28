package de.christina.sonnenwecker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Nach einem Neustart (auch vor dem ersten Entsperren), nach App-Updates und bei Zeit- oder Zeitzonenänderungen
 * müssen die Wecker neu gestellt werden – Android vergisst sie sonst.
 */
public class SystemReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        PendingResult pr = goAsync();
        new Thread(() -> {
            try {
                Ringing.ensureChannels(ctx);
                String status = Scheduler.reschedule(ctx);
                new Prefs(ctx).log("Neu geplant (" + shortName(intent.getAction()) + "): " + status);
                BridgeSync.sync(ctx, true);
            } finally {
                pr.finish();
            }
        }, "sonnenwecker-boot").start();
    }

    private static String shortName(String action) {
        if (action == null) return "?";
        int i = action.lastIndexOf('.');
        return i >= 0 ? action.substring(i + 1) : action;
    }
}
