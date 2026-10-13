package com.blackfalcon.telegradio;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/** Station data from the open Radio Browser database (the same kind of data telegradio.com shows). */
public final class Radio {
    private Radio() { }

    private static final String[] SERVERS = {
            "https://de1.api.radio-browser.info", "https://de2.api.radio-browser.info",
            "https://nl1.api.radio-browser.info", "https://fi1.api.radio-browser.info"};
    private static int serverIdx = 0;

    public static final class Country { public String name = "", cc = ""; public int count; }

    private static String enc(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; }
    }

    static String get(String path) throws Exception {
        Exception last = null;
        for (int k = 0; k < SERVERS.length; k++) {
            int i = (serverIdx + k) % SERVERS.length;
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(SERVERS[i] + path).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(20000);
                c.setRequestProperty("User-Agent", "Telegradio-App/1.0 (BlackFalcon)");
                if (c.getResponseCode() / 100 != 2) { c.disconnect(); throw new Exception("HTTP " + c.getResponseCode()); }
                InputStream in = c.getInputStream();
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                in.close();
                c.disconnect();
                serverIdx = i;
                return bo.toString("UTF-8");
            } catch (Exception e) {
                last = e;
            }
        }
        throw last != null ? last : new Exception("no server");
    }

    public static List<Station> search(String name, String cc, String tag, String lang, int offset, int limit) throws Exception {
        StringBuilder sb = new StringBuilder("/json/stations/search?hidebroken=true&order=clickcount&reverse=true&limit="
                + limit + "&offset=" + offset);
        if (name != null && !name.trim().isEmpty()) sb.append("&name=").append(enc(name.trim()));
        if (cc != null && !cc.isEmpty()) sb.append("&countrycode=").append(enc(cc));
        if (tag != null && !tag.isEmpty()) sb.append("&tag=").append(enc(tag));
        if (lang != null && !lang.isEmpty()) sb.append("&language=").append(enc(lang));
        JSONArray a = new JSONArray(get(sb.toString()));
        List<Station> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            Station s = Station.fromJson(a.getJSONObject(i));
            if (!s.url.isEmpty() && !s.name.isEmpty()) out.add(s);
        }
        return out;
    }

    public static List<Country> countries() throws Exception {
        JSONArray a = new JSONArray(get("/json/countries?hidebroken=true&order=stationcount&reverse=true"));
        List<Country> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.getJSONObject(i);
            Country c = new Country();
            c.name = o.optString("name", "");
            c.cc = o.optString("iso_3166_1", "");
            c.count = o.optInt("stationcount", 0);
            if (!c.name.isEmpty() && c.count > 0) out.add(c);
        }
        return out;
    }

    /** Tells the database somebody listened (it ranks stations by this). Fire and forget. */
    public static void click(final String uuid) {
        if (uuid == null || uuid.isEmpty()) return;
        new Thread(() -> { try { get("/json/url/" + uuid); } catch (Exception ignored) { } }).start();
    }
}
