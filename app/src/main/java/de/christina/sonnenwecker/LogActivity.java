package de.christina.sonnenwecker;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Protokoll: was die App wann gemacht hat – zur Fehlersuche. */
public class LogActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui ui = new Ui(this);
        ui.applyWindow();
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = ui.col();
        scroll.addView(root);
        root.addView(ui.header("Protokoll", true));
        String log = new Prefs(this).log();
        TextView t = ui.text(log.isEmpty() ? "Noch keine Einträge." : log, 12, ui.p.text2);
        t.setTypeface(Typeface.MONOSPACE);
        t.setTextIsSelectable(true);
        t.setPadding(ui.dp(20), ui.dp(4), ui.dp(20), ui.dp(32));
        root.addView(t, ui.fill());
        setContentView(scroll);
    }
}
