package de.christina.sonnenwecker;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Hält die Hue-Aufwachen-Automation „Sonnenwecker“ auf der Bridge passend zum Handywecker:
 * Uhrzeit = Weckzeit (dann ist es hell), Wochentag = Tag des Weckers, ausgeschaltet ohne Wecker.
 * Den Sonnenaufgang selbst führt die Bridge aus – genau wie bei einer in der Hue-App gestellten Automation.
 * Lampen, Stil und Dauer stellt Christina in der Hue-App ein; die App ändert nur Zeit, Tag und An/Aus.
 */
public final class BridgeSync {
    public static final String AUTOMATION_NAME = "Sonnenwecker";
    private static final String[] DAYS = {"", "sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday"};
    /** Nach der Weckzeit bleibt die Automation noch so lange unangetastet. */
    static final long AFTER_MS = 2 * 60_000L;

    private BridgeSync() { }

    /**
     * Bis wann die Automation gerade nicht verändert werden darf, weil ein Sonnenaufgang oder ein Test läuft.
     * 0 = frei.
     */
    public static long protectedUntil(Prefs p, long now) {
        if (p.testUntil() > now) return p.testUntil();
        long t = syncedTarget(p);
        if (t > 0 && now >= t - p.fadeMs() - 60_000L && now < t + AFTER_MS) return t + AFTER_MS;
        return 0;
    }

    private static long syncedTarget(Prefs p) {
        String k = p.syncedKey();
        if (k.startsWith("on:")) {
            try {
                return Long.parseLong(k.substring(3));
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    /**
     * Gleicht die Automation mit dem gewünschten Zustand ab (Prefs.lightTarget, 0 = aus).
     * Blockierend – nicht im UI-Thread aufrufen.
     * @param force auch dann bei der Bridge nachsehen, wenn sich am gewünschten Zustand nichts geändert hat
     */
    public static synchronized void sync(Context ctx, boolean force) {
        Prefs p = new Prefs(ctx);
        long now = System.currentTimeMillis();
        if (protectedUntil(p, now) > 0) return; // Sonnenaufgang oder Test läuft gerade – nicht stören
        long target = p.lightTarget();
        String key = target > 0 ? "on:" + target : "off";
        if (!force && key.equals(p.syncedKey())) return;
        if (p.hueUser().isEmpty() || p.hueIp().isEmpty()) {
            setStatus(p, "Bridge noch nicht gekoppelt.");
            return;
        }
        try {
            JSONObject inst = find(p);
            apply(p, inst, target, false);
            p.edit().putString("syncedKey", key).apply();
        } catch (Exception e) {
            setStatus(p, "⚠ Hue-Automation nicht gestellt: " + e.getMessage());
            p.edit().putString("syncedKey", "").apply();
            Scheduler.scheduleRetry(ctx);
        }
    }

    /**
     * Test: stellt die Automation so, dass der Sonnenaufgang in 1–2 Minuten beginnt.
     * Danach stellt die App sie automatisch auf den echten Wecker zurück. Blockierend.
     */
    public static synchronized String test(Context ctx) {
        Prefs p = new Prefs(ctx);
        try {
            if (p.hueUser().isEmpty() || p.hueIp().isEmpty()) throw new Exception("Bridge noch nicht gekoppelt.");
            long now = System.currentTimeMillis();
            if (protectedUntil(p, now) > 0) throw new Exception("Gerade läuft schon ein Sonnenaufgang oder Test.");
            JSONObject inst = find(p);
            long fade = fadeSeconds(inst.optJSONObject("configuration")) * 1000L;
            long t = now + fade + 60_000L;
            t = ((t + 59_999L) / 60_000L) * 60_000L; // auf die nächste volle Minute
            String s = apply(p, inst, t, true);
            p.edit().putLong("testUntil", t + AFTER_MS).putString("syncedKey", "test").apply();
            Scheduler.reschedule(ctx); // plant das Zurückstellen nach dem Test
            return s;
        } catch (Exception e) {
            return setStatus(p, "⚠ Test nicht möglich: " + e.getMessage());
        }
    }

    private static String apply(Prefs p, JSONObject inst, long target, boolean test) throws Exception {
        String id = inst.getString("id");
        String name = displayName(inst);
        JSONObject config = inst.optJSONObject("configuration");
        if (config == null) throw new Exception("„" + name + "“ hat keine Einstellungen.");
        boolean enabled = inst.optBoolean("enabled", false);
        int fadeSec = fadeSeconds(config);
        p.setLong("fadeMs", fadeSec * 1000L);

        if (target <= 0) {
            if (enabled) {
                HueClient.putBehavior(p.hueIp(), p.hueUser(), id, new JSONObject().put("enabled", false));
            }
            return setStatus(p, "Hue-Automation „" + name + "“: ausgeschaltet (kein Wecker) ✓");
        }

        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(target);
        int hour = c.get(Calendar.HOUR_OF_DAY);
        int minute = c.get(Calendar.MINUTE);
        String day = DAYS[c.get(Calendar.DAY_OF_WEEK)];

        if (!(enabled && matches(config, hour, minute, day))) {
            JSONObject cfg = new JSONObject(config.toString()); // alle anderen Einstellungen bleiben unverändert
            JSONObject when = cfg.optJSONObject("when");
            if (when == null) {
                throw new Exception("„" + name + "“ hat keine Weckzeit – ist es eine Aufwachen-Automation?");
            }
            JSONObject tp = when.optJSONObject("time_point");
            if (tp == null) {
                tp = new JSONObject();
                when.put("time_point", tp);
            }
            tp.put("type", "time");
            JSONObject time = tp.optJSONObject("time");
            if (time == null) {
                time = new JSONObject();
                tp.put("time", time);
            }
            time.put("hour", hour);
            time.put("minute", minute);
            if (time.has("second")) time.put("second", 0);
            when.put("recurrence_days", new JSONArray().put(day));
            HueClient.putBehavior(p.hueIp(), p.hueUser(), id,
                    new JSONObject().put("enabled", true).put("configuration", cfg));
        }

        SimpleDateFormat f = new SimpleDateFormat("EE dd.MM. HH:mm", Locale.GERMANY);
        SimpleDateFormat hm = new SimpleDateFormat("HH:mm", Locale.GERMANY);
        String s = (test ? "Test: " : "") + "Hue-Automation „" + name + "“ gestellt auf " + f.format(new Date(target));
        if (fadeSec > 0) s += " – Sonnenaufgang ab " + hm.format(new Date(target - fadeSec * 1000L));
        return setStatus(p, s + " ✓");
    }

    static boolean matches(JSONObject config, int hour, int minute, String day) {
        JSONObject when = config.optJSONObject("when");
        if (when == null) return false;
        JSONObject tp = when.optJSONObject("time_point");
        JSONObject time = tp != null ? tp.optJSONObject("time") : null;
        if (time == null || time.optInt("hour", -1) != hour || time.optInt("minute", -1) != minute) return false;
        JSONArray days = when.optJSONArray("recurrence_days");
        return days != null && days.length() == 1 && day.equals(days.optString(0));
    }

    static int fadeSeconds(JSONObject config) {
        if (config == null) return 0;
        JSONObject f = config.optJSONObject("fade_in_duration");
        return f != null ? f.optInt("seconds", 0) : 0;
    }

    private static JSONObject find(Prefs p) throws Exception {
        JSONArray all = HueClient.behaviorInstances(p.hueIp(), p.hueUser());
        List<String> others = new ArrayList<>();
        for (int i = 0; i < all.length(); i++) {
            JSONObject b = all.getJSONObject(i);
            String n = rawName(b);
            if (n.trim().equalsIgnoreCase(AUTOMATION_NAME)) return b;
            if (!n.isEmpty() && others.size() < 8) others.add(n);
        }
        throw new Exception("Keine Automation namens „" + AUTOMATION_NAME + "“ gefunden."
                + (others.isEmpty() ? "" : " Vorhanden: " + String.join(", ", others)));
    }

    private static String rawName(JSONObject inst) {
        JSONObject md = inst.optJSONObject("metadata");
        return md != null ? md.optString("name", "") : "";
    }

    private static String displayName(JSONObject inst) {
        String n = rawName(inst).trim();
        return n.isEmpty() ? AUTOMATION_NAME : n;
    }

    private static String setStatus(Prefs p, String s) {
        if (!s.equals(p.hueSync())) {
            p.edit().putString("hueSync", s).apply();
            p.log(s);
        }
        return s;
    }
}
