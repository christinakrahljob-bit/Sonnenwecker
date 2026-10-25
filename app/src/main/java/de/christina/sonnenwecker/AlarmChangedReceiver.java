package de.christina.sonnenwecker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Wird aufgerufen, wenn sich der Handywecker ändert, nach dem Neustart und nach App-Updates. */
public class AlarmChangedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        String status = Scheduler.reschedule(ctx);
        new Prefs(ctx).log("Neu geplant: " + status.replace('\n', ' '));
    }
}
