package de.christina.sonnenwecker;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Holt das Wetter (Open-Meteo, ohne Konto), baut Begrüßungstext und Anzeige, sucht Orte. */
public final class Weather {
    private Weather() { }

    /** Ausgewertetes Wetter für einen Tag. */
    public static final class Day {
        public boolean ok;
        public long max;
        public int code;
        public boolean midday;      // Mittagswetter heute ansagen?
        public boolean middayOk;
        public long middayTemp;
        public int middayCode;
        public int middayRain = -1; // Prozent, -1 = unbekannt
    }

    public static final class Place {
        public final String name, detail;
        public final double lat, lon;

        Place(String name, String detail, double lat, double lon) {
            this.name = name;
            this.detail = detail;
            this.lat = lat;
            this.lon = lon;
        }
    }

    public static String fetchForecast(double lat, double lon) throws Exception {
        return get("https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                + "&daily=temperature_2m_max,weather_code"
                + "&hourly=temperature_2m,weather_code,precipitation_probability"
                + "&timezone=auto&forecast_days=2");
    }

    /** Orte zu einem Namen suchen (für die Einstellung „Ort fürs Wetter“). */
    public static List<Place> searchPlaces(String query) throws Exception {
        String json = get("https://geocoding-api.open-meteo.com/v1/search?count=8&language=de&format=json&name="
                + URLEncoder.encode(query.trim(), "UTF-8"));
        List<Place> out = new ArrayList<>();
        JSONArray r = new JSONObject(json).optJSONArray("results");
        if (r == null) return out;
        for (int i = 0; i < r.length(); i++) {
            JSONObject o = r.getJSONObject(i);
            StringBuilder d = new StringBuilder();
            for (String k : new String[]{"admin1", "country"}) {
                String v = o.optString(k, "");
                if (!v.isEmpty()) {
                    if (d.length() > 0) d.append(", ");
                    d.append(v);
                }
            }
            out.add(new Place(o.optString("name"), d.toString(), o.optDouble("latitude"), o.optDouble("longitude")));
        }
        return out;
    }

    private static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(10000);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return b.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }

    /** Wertet die Vorhersage für den Tag der Weckzeit aus. */
    public static Day evaluate(String forecastJson, long wakeTime, String middayDays) {
        Day d = new Day();
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(wakeTime);
        int dow = cal.get(Calendar.DAY_OF_WEEK);
        d.midday = middayDays != null && middayDays.indexOf(Character.forDigit(dow, 10)) >= 0;
        if (forecastJson == null) return d;
        try {
            JSONObject j = new JSONObject(forecastJson);
            String day = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(wakeTime));
            JSONObject daily = j.getJSONObject("daily");
            JSONArray days = daily.getJSONArray("time");
            int di = 0;
            for (int i = 0; i < days.length(); i++) if (day.equals(days.getString(i))) di = i;
            d.max = Math.round(daily.getJSONArray("temperature_2m_max").getDouble(di));
            d.code = daily.getJSONArray("weather_code").getInt(di);
            d.ok = true;
            if (d.midday) {
                JSONObject hourly = j.getJSONObject("hourly");
                JSONArray times = hourly.getJSONArray("time");
                JSONArray temps = hourly.getJSONArray("temperature_2m");
                JSONArray codes = hourly.getJSONArray("weather_code");
                JSONArray rain = hourly.getJSONArray("precipitation_probability");
                double sum = 0;
                int count = 0, worst = -1, rainMax = -1;
                for (int i = 0; i < times.length(); i++) {
                    String ts = times.getString(i); // z. B. 2026-10-26T12:00
                    if (!ts.startsWith(day + "T")) continue;
                    int hour = Integer.parseInt(ts.substring(11, 13));
                    if (hour < 12 || hour > 14) continue;
                    sum += temps.getDouble(i);
                    count++;
                    worst = Math.max(worst, codes.getInt(i));
                    if (!rain.isNull(i)) rainMax = Math.max(rainMax, rain.getInt(i));
                }
                if (count > 0) {
                    d.middayOk = true;
                    d.middayTemp = Math.round(sum / count);
                    d.middayCode = worst;
                    d.middayRain = rainMax;
                }
            }
        } catch (Exception e) {
            d.ok = false;
        }
        return d;
    }

    /** Begrüßungstext zum Vorlesen. */
    public static String greeting(String name, Day d, long wakeTime) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(wakeTime);
        int h = cal.get(Calendar.HOUR_OF_DAY);
        int m = cal.get(Calendar.MINUTE);
        StringBuilder t = new StringBuilder();
        t.append("Guten Morgen, ").append(name).append("! ");
        t.append("Es ist ").append(h).append(" Uhr");
        if (m != 0) t.append(' ').append(m);
        t.append(". ");
        if (!d.ok) {
            t.append("Das Wetter konnte ich gerade leider nicht abrufen.");
            return t.toString();
        }
        t.append("Heute wird es bis zu ").append(degrees(d.max)).append(join(describe(d.code))).append('.');
        if (d.middayOk) {
            t.append(" Zwischen 12 und 14 Uhr wird es etwa ").append(degrees(d.middayTemp))
                    .append(join(describe(d.middayCode))).append('.');
            if (d.middayRain >= 0) t.append(" Die Regenwahrscheinlichkeit liegt bei ").append(d.middayRain).append(" Prozent.");
        }
        return t.toString();
    }

    /** Zeilen für den Weck-Bildschirm: {heute, mittags, Regen} – leere Zeichenkette, wenn nicht zutreffend. */
    public static String[] display(Day d) {
        if (!d.ok) return new String[]{"Wetter nicht verfügbar", "", ""};
        String today = "Heute bis " + d.max + " °C, " + plain(describe(d.code));
        String midday = "", rain = "";
        if (d.middayOk) {
            midday = "Mittags (12–14 Uhr): etwa " + d.middayTemp + " °C, " + plain(describe(d.middayCode));
            if (d.middayRain >= 0) rain = "Regenwahrscheinlichkeit " + d.middayRain + " %";
        }
        return new String[]{today, midday, rain};
    }

    /** Kompatibilität: Text direkt aus der Vorhersage. */
    public static String greeting(String name, String forecastJson, long wakeTime, String middayDays) {
        return greeting(name, evaluate(forecastJson, wakeTime, middayDays), wakeTime);
    }

    private static String degrees(long v) {
        if (v == 1) return "ein Grad";
        if (v == -1) return "minus ein Grad";
        return (v < 0 ? "minus " + (-v) : String.valueOf(v)) + " Grad";
    }

    private static String join(String desc) {
        return desc.startsWith("mit ") ? ", " + desc : " und " + desc;
    }

    /** „mit leichtem Regen“ → „leichter Regen“ für die Anzeige. */
    static String plain(String desc) {
        switch (desc) {
            case "mit Nieselregen": return "Nieselregen";
            case "mit gefrierendem Nieselregen": return "gefrierender Nieselregen";
            case "mit leichtem Regen": return "leichter Regen";
            case "mit Regen": return "Regen";
            case "mit starkem Regen": return "starker Regen";
            case "mit gefrierendem Regen": return "gefrierender Regen";
            case "mit leichtem Schneefall": return "leichter Schneefall";
            case "mit Schneefall": return "Schneefall";
            case "mit starkem Schneefall": return "starker Schneefall";
            case "mit Schneegriesel": return "Schneegriesel";
            case "mit leichten Regenschauern": return "leichte Regenschauer";
            case "mit Regenschauern": return "Regenschauer";
            case "mit heftigen Regenschauern": return "heftige Regenschauer";
            case "mit Schneeschauern": return "Schneeschauer";
            case "mit Gewitter": return "Gewitter";
            case "mit Gewitter und Hagel": return "Gewitter und Hagel";
            default: return desc;
        }
    }

    /** WMO-Wettercode in deutsche Worte. */
    static String describe(int code) {
        switch (code) {
            case 0: return "sonnig";
            case 1: return "überwiegend sonnig";
            case 2: return "teils bewölkt";
            case 3: return "bewölkt";
            case 45: case 48: return "neblig";
            case 51: case 53: case 55: return "mit Nieselregen";
            case 56: case 57: return "mit gefrierendem Nieselregen";
            case 61: return "mit leichtem Regen";
            case 63: return "mit Regen";
            case 65: return "mit starkem Regen";
            case 66: case 67: return "mit gefrierendem Regen";
            case 71: return "mit leichtem Schneefall";
            case 73: return "mit Schneefall";
            case 75: return "mit starkem Schneefall";
            case 77: return "mit Schneegriesel";
            case 80: return "mit leichten Regenschauern";
            case 81: return "mit Regenschauern";
            case 82: return "mit heftigen Regenschauern";
            case 85: case 86: return "mit Schneeschauern";
            case 95: return "mit Gewitter";
            case 96: case 99: return "mit Gewitter und Hagel";
            default: return "wechselhaft";
        }
    }
}
