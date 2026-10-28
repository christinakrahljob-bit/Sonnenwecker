package de.christina.sonnenwecker;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.Switch;
import android.widget.TextView;

/** Das Erscheinungsbild aus dem Entwurf: Farben, Schriften und Bedienelemente – hell und dunkel. */
final class Ui {

    static final class Pal {
        int bg, surface, surfaceHigh, divider, text, text2, muted, outline, accent, onAccent,
                tonal, onTonal, okBg, ok, warnBg, warn, switchOff, cloud, ripple, field;
    }

    static Pal darkPal() {
        Pal p = new Pal();
        p.bg = 0xFF121016; p.surface = 0xFF1E1B24; p.surfaceHigh = 0xFF26222D; p.divider = 0xFF2A2631;
        p.text = 0xFFF2EDF5; p.text2 = 0xFFDCD5E3; p.muted = 0xFFB3AABD; p.outline = 0xFF8E8698;
        p.accent = 0xFFFFB547; p.onAccent = 0xFF2A1700; p.tonal = 0xFF3A2E1F; p.onTonal = 0xFFFFD8A0;
        p.okBg = 0xFF23302A; p.ok = 0xFFA8E6BD; p.warnBg = 0xFF2E2416; p.warn = 0xFFFFB547;
        p.switchOff = 0xFF2C2834; p.cloud = 0xFFB9C4FF; p.ripple = 0x33FFFFFF; p.field = 0xFF121016;
        return p;
    }

    static Pal lightPal() {
        Pal p = new Pal();
        p.bg = 0xFFFFF8F1; p.surface = 0xFFF6EEE4; p.surfaceHigh = 0xFFEFE3D3; p.divider = 0xFFE4D9CC;
        p.text = 0xFF1E1A16; p.text2 = 0xFF3B342C; p.muted = 0xFF615848; p.outline = 0xFF7F7464;
        p.accent = 0xFFFFB547; p.onAccent = 0xFF2A1700; p.tonal = 0xFFFFE2BC; p.onTonal = 0xFF5A3300;
        p.okBg = 0xFFDDF3E3; p.ok = 0xFF1E6B3A; p.warnBg = 0xFFFFE9C7; p.warn = 0xFF8A4D00;
        p.switchOff = 0xFFEFE3D3; p.cloud = 0xFF3D4FA8; p.ripple = 0x22000000; p.field = 0xFFFFFFFF;
        return p;
    }

    static boolean isNight(Context c) {
        return (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    final Activity a;
    final Pal p;
    final boolean dark;
    private final float density;

    Ui(Activity a) {
        this(a, isNight(a));
    }

    Ui(Activity a, boolean dark) {
        this.a = a;
        this.dark = dark;
        this.p = dark ? darkPal() : lightPal();
        this.density = a.getResources().getDisplayMetrics().density;
    }

    int dp(float v) {
        return Math.round(v * density);
    }

    @SuppressWarnings("deprecation")
    void applyWindow() {
        Window w = a.getWindow();
        w.setStatusBarColor(p.bg);
        w.setNavigationBarColor(p.bg);
        w.getDecorView().setBackgroundColor(p.bg);
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = w.getInsetsController();
            if (c != null) {
                int light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                c.setSystemBarsAppearance(dark ? 0 : light, light);
            }
        } else {
            View d = w.getDecorView();
            int f = d.getSystemUiVisibility();
            int light = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            d.setSystemUiVisibility(dark ? (f & ~light) : (f | light));
        }
    }

    // ---------- Formen ----------

    GradientDrawable shape(int fill, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    GradientDrawable outlined(int fill, int stroke, float strokeDp, float radiusDp) {
        GradientDrawable g = shape(fill, radiusDp);
        g.setStroke(dp(strokeDp), stroke);
        return g;
    }

    GradientDrawable oval(int fill, int sizeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(fill);
        g.setSize(dp(sizeDp), dp(sizeDp));
        return g;
    }

    /** Hintergrund mit Berührungs-Rückmeldung. content darf null sein (dann nur Welle). */
    Drawable ripple(Drawable content, float radiusDp) {
        return new RippleDrawable(ColorStateList.valueOf(p.ripple), content, shape(0xFFFFFFFF, radiusDp));
    }

    // ---------- Text ----------

    TextView text(String s, float sp, int color) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(true);
        return t;
    }

    TextView medium(String s, float sp, int color) {
        TextView t = text(s, sp, color);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    TextView light(String s, float sp, int color) {
        TextView t = text(s, sp, color);
        t.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        t.setFontFeatureSettings("tnum");
        return t;
    }

    /** Abschnittsüberschrift („Wecker“, „Sonnenaufgang“ …). */
    TextView section(String s) {
        TextView t = medium(s, 14, p.muted);
        t.setPadding(dp(8), dp(12), dp(8), dp(4));
        heading(t);
        return t;
    }

    static void heading(View v) {
        if (Build.VERSION.SDK_INT >= 28) v.setAccessibilityHeading(true);
    }

    // ---------- Layout ----------

    LinearLayout col() {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    LinearLayout row() {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    LinearLayout.LayoutParams fill() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
    }

    LinearLayout.LayoutParams top(int dpTop) {
        LinearLayout.LayoutParams l = fill();
        l.topMargin = dp(dpTop);
        return l;
    }

    LinearLayout card(int color, float radiusDp) {
        LinearLayout c = col();
        c.setBackground(shape(color, radiusDp));
        return c;
    }

    View divider() {
        View v = new View(a);
        v.setBackgroundColor(p.divider);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1))));
        return v;
    }

    View space(int dpH) {
        View v = new View(a);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(dpH)));
        return v;
    }

    // ---------- Bedienelemente ----------

    ImageView icon(int res, int color, int sizeDp) {
        ImageView i = new ImageView(a);
        i.setImageResource(res);
        i.setImageTintList(ColorStateList.valueOf(color));
        i.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        i.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return i;
    }

    ImageButton iconButton(int res, String description, int color) {
        ImageButton b = new ImageButton(a);
        b.setImageResource(res);
        b.setImageTintList(ColorStateList.valueOf(color));
        b.setBackground(ripple(null, 24));
        b.setContentDescription(description);
        b.setScaleType(ImageView.ScaleType.CENTER);
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
        return b;
    }

    private Button baseButton(String s, int bg, int fg, float radiusDp, int heightDp) {
        Button b = new Button(a);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(fg);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setStateListAnimator(null);
        b.setBackground(ripple(bg == 0 ? null : shape(bg, radiusDp), radiusDp));
        b.setMinHeight(dp(heightDp));
        b.setMinimumHeight(dp(heightDp));
        b.setPadding(dp(16), 0, dp(16), 0);
        return b;
    }

    Button filled(String s) {
        return baseButton(s, p.accent, p.onAccent, 24, 48);
    }

    Button tonal(String s) {
        return baseButton(s, p.tonal, p.onTonal, 22, 44);
    }

    Button outlinedButton(String s) {
        Button b = baseButton(s, 0, p.onTonal, 20, 40);
        b.setBackground(ripple(outlined(0x00000000, p.outline, 1, 20), 20));
        return b;
    }

    /** Textknopf mit Symbol davor (z. B. „Löschen“). */
    LinearLayout iconText(int res, String s, int color) {
        LinearLayout r = row();
        r.setMinimumHeight(dp(44));
        r.setPadding(dp(4), 0, dp(12), 0);
        r.addView(icon(res, color, 20));
        TextView t = text(s, 15, color);
        LinearLayout.LayoutParams l = lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        l.leftMargin = dp(10);
        r.addView(t, l);
        r.setBackground(ripple(null, 22));
        r.setClickable(true);
        r.setFocusable(true);
        r.setContentDescription(s);
        return r;
    }

    /** Schalter wie im Entwurf (Spur 56 × 32 dp). */
    Switch toggle() {
        Switch s = new Switch(a);
        StateListDrawable track = new StateListDrawable();
        GradientDrawable on = shape(p.accent, 16);
        on.setSize(dp(56), dp(32));
        GradientDrawable off = outlined(p.switchOff, p.outline, 2, 16);
        off.setSize(dp(56), dp(32));
        track.addState(new int[]{android.R.attr.state_checked}, on);
        track.addState(new int[]{}, off);
        StateListDrawable thumb = new StateListDrawable();
        thumb.addState(new int[]{android.R.attr.state_checked}, new InsetDrawable(oval(p.onAccent, 24), dp(2), dp(4), dp(2), dp(4)));
        thumb.addState(new int[]{}, new InsetDrawable(oval(p.outline, 16), dp(6), dp(8), dp(6), dp(8)));
        s.setTrackDrawable(track);
        s.setThumbDrawable(thumb);
        s.setSwitchMinWidth(dp(56));
        s.setShowText(false);
        s.setText("");
        s.setMinHeight(dp(48));
        s.setMinimumHeight(dp(48));
        return s;
    }

    /** Runder Wochentag-Kreis (Mo, Di …). */
    CheckBox dayCircle(String shortLabel, String longLabel) {
        CheckBox c = new CheckBox(a);
        c.setButtonDrawable(null);
        StateListDrawable bg = new StateListDrawable();
        GradientDrawable on = oval(p.accent, 42);
        GradientDrawable off = oval(0x00000000, 42);
        off.setStroke(dp(1), p.outline);
        bg.addState(new int[]{android.R.attr.state_checked}, on);
        bg.addState(new int[]{}, off);
        c.setBackground(new RippleDrawable(ColorStateList.valueOf(p.ripple), bg, oval(0xFFFFFFFF, 42)));
        c.setText(shortLabel);
        c.setGravity(Gravity.CENTER);
        c.setPadding(0, 0, 0, 0);
        c.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        c.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        c.setTextColor(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}}, new int[]{p.onAccent, p.text2}));
        c.setContentDescription(longLabel);
        c.setLayoutParams(new LinearLayout.LayoutParams(dp(42), dp(42)));
        return c;
    }

    /** Rechteckiger Auswahl-Chip (Dauer, Mittagswetter). */
    <T extends CompoundButton> T chip(T c, String label, String description) {
        c.setButtonDrawable(null);
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_checked}, shape(p.tonal, 12));
        bg.addState(new int[]{}, outlined(0x00000000, p.outline, 1, 12));
        c.setBackground(new RippleDrawable(ColorStateList.valueOf(p.ripple), bg, shape(0xFFFFFFFF, 12)));
        c.setText(label);
        c.setGravity(Gravity.CENTER);
        c.setPadding(0, 0, 0, 0);
        c.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        c.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        c.setTextColor(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}}, new int[]{p.onTonal, p.text2}));
        c.setMinHeight(dp(44));
        c.setMinimumHeight(dp(44));
        if (description != null) c.setContentDescription(description);
        return c;
    }

    RadioButton radioChip(String label) {
        return chip(new RadioButton(a), label, null);
    }

    CheckBox checkChip(String label, String description) {
        return chip(new CheckBox(a), label, description);
    }

    /** Kopfzeile: optional Zurück, Titel, optional Knöpfe rechts. */
    LinearLayout header(String title, boolean back) {
        LinearLayout h = row();
        h.setMinimumHeight(dp(64));
        h.setPadding(back ? dp(4) : dp(20), 0, dp(8), 0);
        if (back) {
            ImageButton b = iconButton(R.drawable.i_back, "Zurück", p.text2);
            b.setOnClickListener(v -> a.finish());
            h.addView(b);
        } else {
            ImageView logo = icon(R.drawable.i_logo, p.accent, 28);
            h.addView(logo);
        }
        TextView t = medium(title, 22, p.text);
        heading(t);
        LinearLayout.LayoutParams l = weight();
        l.leftMargin = dp(back ? 4 : 10);
        h.addView(t, l);
        return h;
    }

    /** Listenzeile mit Titel, Untertitel und optionalem Element rechts. */
    LinearLayout listRow(String title, String subtitle, View trailing) {
        LinearLayout r = row();
        r.setMinimumHeight(dp(64));
        r.setPadding(dp(20), dp(8), dp(16), dp(8));
        LinearLayout texts = col();
        TextView t = text(title, 16, p.text);
        texts.addView(t);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView s = text(subtitle, 13, p.muted);
            s.setTag("subtitle");
            texts.addView(s, top(2));
        }
        r.addView(texts, weight());
        if (trailing != null) {
            LinearLayout.LayoutParams l = lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            l.leftMargin = dp(12);
            r.addView(trailing, l);
        }
        return r;
    }

    /** Untertitel einer Listenzeile ändern. */
    static void setSubtitle(LinearLayout row, String s) {
        View v = row.findViewWithTag("subtitle");
        if (v instanceof TextView) ((TextView) v).setText(s);
    }

    void clickable(View v, Runnable action) {
        v.setBackground(ripple(null, 0));
        v.setClickable(true);
        v.setFocusable(true);
        v.setOnClickListener(x -> action.run());
    }

    static String ellipsize(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    static boolean empty(CharSequence s) {
        return TextUtils.isEmpty(s);
    }
}
