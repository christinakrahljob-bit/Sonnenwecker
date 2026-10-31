package de.christina.sonnenwecker;

import android.content.Context;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Speichert die Wecker und ermittelt den nächsten. */
public final class AlarmStore {
    private final Prefs prefs;

    public static final class Next {
        public final Alarm alarm;
        public final long time;

        Next(Alarm alarm, long time) {
            this.alarm = alarm;
            this.time = time;
        }
    }

    public AlarmStore(Context ctx) {
        prefs = new Prefs(ctx);
    }

    /** Alle Wecker, sortiert nach Uhrzeit. */
    public synchronized List<Alarm> all() {
        List<Alarm> list = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs.alarmsJson());
            int midday = prefs.defaultMiddayMask();
            for (int i = 0; i < a.length(); i++) list.add(Alarm.fromJson(a.getJSONObject(i), midday));
        } catch (Exception ignored) {
        }
        Collections.sort(list, (x, y) -> x.hour != y.hour ? x.hour - y.hour
                : x.minute != y.minute ? x.minute - y.minute : x.id - y.id);
        return list;
    }

    public synchronized void saveAll(List<Alarm> list) {
        JSONArray a = new JSONArray();
        try {
            for (Alarm x : list) a.put(x.toJson());
        } catch (Exception ignored) {
        }
        prefs.edit().putString("alarms", a.toString()).apply();
    }

    public synchronized Alarm get(int id) {
        for (Alarm a : all()) if (a.id == id) return a;
        return null;
    }

    public synchronized Alarm add(int hour, int minute) {
        List<Alarm> list = all();
        int id = 1;
        for (Alarm a : list) id = Math.max(id, a.id + 1);
        Alarm a = new Alarm(id, hour, minute);
        a.middayDays = prefs.defaultMiddayMask();
        list.add(a);
        saveAll(list);
        return a;
    }

    public synchronized void update(Alarm changed) {
        List<Alarm> list = all();
        for (int i = 0; i < list.size(); i++) if (list.get(i).id == changed.id) list.set(i, changed);
        saveAll(list);
    }

    public synchronized void delete(int id) {
        List<Alarm> list = all();
        for (int i = list.size() - 1; i >= 0; i--) if (list.get(i).id == id) list.remove(i);
        saveAll(list);
    }

    /** Der nächste eingeschaltete Wecker nach {@code now}, oder null. */
    public synchronized Next next(long now) {
        Next best = null;
        for (Alarm a : all()) {
            if (!a.enabled) continue;
            long t = a.nextAfter(now);
            if (t == Long.MAX_VALUE) continue;
            if (best == null || t < best.time) best = new Next(a, t);
        }
        return best;
    }

    /** Ein einmaliger Wecker schaltet sich nach dem Klingeln aus – wie in der Uhr-App. */
    public synchronized void afterRing(int id) {
        Alarm a = get(id);
        if (a != null && a.isOneTime() && a.enabled) {
            a.enabled = false;
            update(a);
        }
    }
}
