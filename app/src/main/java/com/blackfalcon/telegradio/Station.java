package com.blackfalcon.telegradio;

import org.json.JSONObject;

/** One radio station. */
public final class Station {
    public String uuid = "", name = "", url = "", favicon = "", country = "", cc = "", tags = "", lang = "", codec = "";
    public int bitrate, votes, clicks;
    public boolean hls;

    public static Station fromJson(JSONObject o) {
        Station s = new Station();
        s.uuid = o.optString("stationuuid", "");
        s.name = o.optString("name", "").trim();
        String u = o.optString("url_resolved", "");
        s.url = u.isEmpty() ? o.optString("url", "") : u;
        s.favicon = o.optString("favicon", "");
        s.country = o.optString("country", "");
        s.cc = o.optString("countrycode", "");
        s.tags = o.optString("tags", "");
        s.lang = o.optString("language", "");
        s.codec = o.optString("codec", "");
        s.bitrate = o.optInt("bitrate", 0);
        s.votes = o.optInt("votes", 0);
        s.clicks = o.optInt("clickcount", 0);
        s.hls = o.optInt("hls", 0) == 1;
        return s;
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("stationuuid", uuid); o.put("name", name); o.put("url_resolved", url); o.put("favicon", favicon);
            o.put("country", country); o.put("countrycode", cc); o.put("tags", tags); o.put("language", lang);
            o.put("codec", codec); o.put("bitrate", bitrate); o.put("votes", votes); o.put("clickcount", clicks);
            o.put("hls", hls ? 1 : 0);
        } catch (Exception ignored) { }
        return o;
    }

    public String flag() { return Station.flagOf(cc); }

    /** Flag emoji from a 2-letter country code. */
    public static String flagOf(String cc) {
        if (cc == null || cc.length() != 2) return "\uD83C\uDF10";
        String u = cc.toUpperCase();
        char a = u.charAt(0), b = u.charAt(1);
        if (a < 'A' || a > 'Z' || b < 'A' || b > 'Z') return "\uD83C\uDF10";
        return new String(Character.toChars(0x1F1E6 + (a - 'A'))) + new String(Character.toChars(0x1F1E6 + (b - 'A')));
    }

    public String genre() {
        if (tags == null || tags.isEmpty()) return "";
        String g = tags.split(",")[0].trim();
        return g.isEmpty() ? "" : Character.toUpperCase(g.charAt(0)) + g.substring(1);
    }

    public String subtitle() {
        StringBuilder sb = new StringBuilder();
        sb.append(flag()).append(" ").append(country.isEmpty() ? "" : country);
        String g = genre();
        if (!g.isEmpty()) sb.append(" \u2022 ").append(g);
        if (bitrate > 0) sb.append(" \u2022 ").append(bitrate).append(" kbps");
        if (!codec.isEmpty() && !codec.equalsIgnoreCase("UNKNOWN")) sb.append(" ").append(codec);
        return sb.toString();
    }
}
