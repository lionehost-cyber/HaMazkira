package com.hamazkira.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Keeps the list of upcoming reminders and hands them to AlarmManager. */
final class AlarmStore {
    static final String ACTION_FIRE = "com.hamazkira.app.ALARM";
    private static final String PREFS = "mazkira";
    private static final String KEY_ALARMS = "alarms";
    private static final String KEY_ACTIONS = "actions";
    private static final int MAX_ALARMS = 400;

    private AlarmStore() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static int code(String key) {
        return key == null ? 0 : key.hashCode();
    }

    private static List<JSONObject> load(Context c) {
        List<JSONObject> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString(KEY_ALARMS, "[]"));
            for (int i = 0; i < a.length(); i++) out.add(a.getJSONObject(i));
        } catch (JSONException ignored) { }
        return out;
    }

    private static void save(Context c, List<JSONObject> list) {
        JSONArray a = new JSONArray();
        for (JSONObject o : list) a.put(o);
        prefs(c).edit().putString(KEY_ALARMS, a.toString()).apply();
    }

    private static PendingIntent pending(Context c, JSONObject a) {
        Intent i = new Intent(c, AlarmReceiver.class).setAction(ACTION_FIRE);
        i.putExtra("key", a.optString("key"));
        i.putExtra("id", a.optString("id"));
        i.putExtra("date", a.optString("date"));
        i.putExtra("title", a.optString("title"));
        i.putExtra("body", a.optString("body"));
        return PendingIntent.getBroadcast(c, code(a.optString("key")), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void schedule(Context c, JSONObject a) {
        long at = a.optLong("at", 0);
        if (at <= System.currentTimeMillis()) return;
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent p = pending(c, a);
        boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        try {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
        }
    }

    private static void cancel(Context c, JSONObject a) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(pending(c, a));
    }

    /** Replace every scheduled reminder with the list the app sends. */
    static synchronized void replaceAll(Context c, String json) {
        for (JSONObject old : load(c)) cancel(c, old);
        List<JSONObject> next = new ArrayList<>();
        long now = System.currentTimeMillis();
        try {
            JSONArray a = new JSONArray(json);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                if (o.optLong("at", 0) > now) next.add(o);
            }
        } catch (JSONException ignored) { }
        next.sort((x, y) -> Long.compare(x.optLong("at"), y.optLong("at")));
        if (next.size() > MAX_ALARMS) next = new ArrayList<>(next.subList(0, MAX_ALARMS));
        save(c, next);
        for (JSONObject o : next) schedule(c, o);
    }

    static synchronized void add(Context c, JSONObject a) {
        List<JSONObject> list = load(c);
        list.add(a);
        save(c, list);
        schedule(c, a);
    }

    static synchronized void remove(Context c, String key) {
        List<JSONObject> list = load(c);
        List<JSONObject> keep = new ArrayList<>();
        for (JSONObject o : list) if (!o.optString("key").equals(key)) keep.add(o);
        save(c, keep);
    }

    /** After a reboot or app update the system forgets alarms; set them again. */
    static synchronized void rescheduleAll(Context c) {
        long now = System.currentTimeMillis();
        List<JSONObject> keep = new ArrayList<>();
        for (JSONObject o : load(c)) if (o.optLong("at", 0) > now) keep.add(o);
        save(c, keep);
        for (JSONObject o : keep) schedule(c, o);
    }

    /** Taps on notification buttons wait here until the app is opened. */
    static synchronized void pushAction(Context c, JSONObject act) {
        try {
            JSONArray a = new JSONArray(prefs(c).getString(KEY_ACTIONS, "[]"));
            a.put(act);
            prefs(c).edit().putString(KEY_ACTIONS, a.toString()).apply();
        } catch (JSONException ignored) { }
    }

    static synchronized String takeActions(Context c) {
        String s = prefs(c).getString(KEY_ACTIONS, "[]");
        prefs(c).edit().putString(KEY_ACTIONS, "[]").apply();
        return s;
    }
}
