package de.christina.sonnenwecker;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Holt das Wetter (Open-Meteo, ohne Konto) und baut daraus den Begrüßungstext. */
public final class Weather {
    private Weather() { }

    public static String fetchForecast(double lat, double lon) throws Exception {
        String url = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                + "&daily=temperature_2m_max,weather_code"
                + "&hourly=temperature_2m,weather_code,precipitation_probability"
                + "&timezone=auto&forecast_days=2";
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

    /**
     * Baut den Begrüßungstext.
     * @param forecastJson Antwort von Open-Meteo, oder null, wenn das Wetter nicht abrufbar war
     * @param wakeTime     Weckzeit (für Uhrzeit, Datum und Wochentag)
     * @param middayDays   Wochentage als Ziffern (Calendar.DAY_OF_WEEK), z. B. "24" für Mo und Mi
     */
    public static String greeting(String name, String forecastJson, long wakeTime, String middayDays) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(wakeTime);
        int h = cal.get(Calendar.HOUR_OF_DAY);
        int m = cal.get(Calendar.MINUTE);

        StringBuilder t = new StringBuilder();
        t.append("Guten Morgen, ").append(name).append("! ");
        t.append("Es ist ").append(h).append(" Uhr");
        if (m != 0) t.append(' ').append(m);
        t.append(". ");

        if (forecastJson == null) {
            t.append("Das Wetter konnte ich gerade leider nicht abrufen.");
            return t.toString();
        }
        try {
            JSONObject j = new JSONObject(forecastJson);
            String day = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(wakeTime));

            JSONObject daily = j.getJSONObject("daily");
            JSONArray days = daily.getJSONArray("time");
            int di = -1;
            for (int i = 0; i < days.length(); i++) if (day.equals(days.getString(i))) di = i;
            if (di < 0) di = 0;
            long max = Math.round(daily.getJSONArray("temperature_2m_max").getDouble(di));
            int code = daily.getJSONArray("weather_code").getInt(di);
            t.append("Heute wird es bis zu ").append(degrees(max)).append(join(describe(code))).append('.');

            int dow = cal.get(Calendar.DAY_OF_WEEK);
            if (middayDays != null && middayDays.indexOf(Character.forDigit(dow, 10)) >= 0) {
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
                    long avg = Math.round(sum / count);
                    t.append(" Zwischen 12 und 14 Uhr wird es etwa ").append(degrees(avg))
                            .append(join(describe(worst))).append('.');
                    if (rainMax >= 0) {
                        t.append(" Die Regenwahrscheinlichkeit liegt bei ").append(rainMax).append(" Prozent.");
                    }
                }
            }
        } catch (Exception e) {
            t.append("Das Wetter konnte ich gerade leider nicht auswerten.");
        }
        return t.toString();
    }

    private static String degrees(long v) {
        if (v == 1) return "ein Grad";
        if (v == -1) return "minus ein Grad";
        return (v < 0 ? "minus " + (-v) : String.valueOf(v)) + " Grad";
    }

    private static String join(String desc) {
        return desc.startsWith("mit ") ? ", " + desc : " und " + desc;
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
