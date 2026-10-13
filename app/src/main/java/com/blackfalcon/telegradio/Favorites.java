package com.blackfalcon.telegradio;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Stations the user hearted, kept on the phone. */
public final class Favorites {
    private final SharedPreferences sp;
    private final List<Station> list = new ArrayList<>();

    public Favorites(Context c) {
        sp = c.getSharedPreferences("telegradio", Context.MODE_PRIVATE);
        try {
            JSONArray a = new JSONArray(sp.getString("favs", "[]"));
            for (int i = 0; i < a.length(); i++) list.add(Station.fromJson(a.getJSONObject(i)));
        } catch (Exception ignored) { }
    }

    public List<Station> all() { return list; }

    public boolean has(String uuid) {
        for (Station s : list) if (s.uuid.equals(uuid)) return true;
        return false;
    }

    /** Returns true if the station is now a favourite. */
    public boolean toggle(Station st) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).uuid.equals(st.uuid)) { list.remove(i); save(); return false; }
        }
        list.add(0, st);
        save();
        return true;
    }

    private void save() {
        JSONArray a = new JSONArray();
        for (Station s : list) a.put(s.toJson());
        sp.edit().putString("favs", a.toString()).apply();
    }
}
