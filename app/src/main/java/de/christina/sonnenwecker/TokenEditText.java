package de.christina.sonnenwecker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ReplacementSpan;
import android.widget.EditText;

/**
 * Textfeld für die Begrüßung: Bausteine wie {Datum} erscheinen als Pillen, werden nur als Ganzes gelöscht,
 * und der Cursor springt nicht mitten in einen Baustein.
 */
public final class TokenEditText extends EditText {
    private final int pillBg, pillFg;
    private final float padH, padV, gap, radius;
    private boolean selfEdit;
    private int[] pending;
    private Runnable onChanged;

    public TokenEditText(Context ctx, int pillBg, int pillFg, float density) {
        super(ctx);
        this.pillBg = pillBg;
        this.pillFg = pillFg;
        this.padH = 7 * density;
        this.padV = 2 * density;
        this.gap = 1.5f * density;
        this.radius = 8 * density;
        addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                pending = selfEdit ? null : Greeting.partialTokenRemoval(s, start, count, after);
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable e) {
                if (selfEdit) return;
                selfEdit = true;
                try {
                    int[] p = pending;
                    pending = null;
                    if (p != null) {
                        // Reste eines angeschnittenen Bausteins entfernen – rechts zuerst, damit links stimmt
                        if (p[3] > p[2] && p[3] <= e.length()) e.delete(p[2], p[3]);
                        if (p[1] > p[0] && p[1] <= e.length()) e.delete(p[0], p[1]);
                    }
                    applySpans(e);
                } finally {
                    selfEdit = false;
                }
                if (onChanged != null) onChanged.run();
            }
        });
    }

    public void setOnChanged(Runnable r) {
        onChanged = r;
    }

    /** Text setzen (Bausteine werden zu Pillen), Cursor ans Ende. */
    public void setTokenText(String text) {
        setText(text);
        Editable e = getText();
        setSelection(e != null ? e.length() : 0);
    }

    /** Baustein an der Cursor-Stelle einfügen (ersetzt eine Markierung). */
    public void insertToken(String token) {
        Editable e = getText();
        if (e == null) return;
        int s = getSelectionStart(), en = getSelectionEnd();
        if (s < 0 || en < 0) {
            s = e.length();
            en = s;
        }
        if (s > en) {
            int t = s;
            s = en;
            en = t;
        }
        String ins = Greeting.insertion(e, s, token);
        e.replace(s, en, ins);
        setSelection(Math.min(e.length(), s + ins.length()));
    }

    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd);
        Editable e = getText();
        if (e == null || selStart < 0 || selEnd < 0) return;
        int a, b;
        if (selStart == selEnd) {
            a = b = Greeting.snap(e, selStart, false);
        } else {
            a = Greeting.snap(e, Math.min(selStart, selEnd), true);
            b = Greeting.snap(e, Math.max(selStart, selEnd), false);
        }
        if (a != Math.min(selStart, selEnd) || b != Math.max(selStart, selEnd)) {
            setSelection(Math.min(a, e.length()), Math.min(b, e.length()));
        }
    }

    private void applySpans(Editable e) {
        for (TokenSpan s : e.getSpans(0, e.length(), TokenSpan.class)) e.removeSpan(s);
        for (int[] r : Greeting.ranges(e)) {
            String label = e.subSequence(r[0] + 1, r[1] - 1).toString();
            e.setSpan(new TokenSpan(label), r[0], r[1], Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    /** Zeichnet „{Datum}“ als Pille mit „Datum“. */
    private final class TokenSpan extends ReplacementSpan {
        private final String label;

        TokenSpan(String label) {
            this.label = label;
        }

        private Paint labelPaint(Paint base) {
            Paint p = new Paint(base);
            p.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            p.setTextSize(base.getTextSize() * 0.92f);
            return p;
        }

        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            if (fm != null) {
                Paint.FontMetricsInt m = paint.getFontMetricsInt();
                fm.ascent = m.ascent;
                fm.descent = m.descent;
                fm.top = m.top;
                fm.bottom = m.bottom;
                fm.leading = m.leading;
            }
            return Math.round(labelPaint(paint).measureText(label) + 2 * padH + 2 * gap);
        }

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom,
                         Paint paint) {
            Paint p = labelPaint(paint);
            Paint.FontMetrics m = p.getFontMetrics();
            float w = p.measureText(label) + 2 * padH;
            RectF r = new RectF(x + gap, y + m.ascent - padV, x + gap + w, y + m.descent + padV);
            Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
            bg.setColor(pillBg);
            canvas.drawRoundRect(r, radius, radius, bg);
            p.setColor(pillFg);
            canvas.drawText(label, x + gap + padH, y, p);
        }
    }
}
