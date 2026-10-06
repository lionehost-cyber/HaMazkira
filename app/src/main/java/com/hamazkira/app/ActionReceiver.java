package com.hamazkira.app;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.json.JSONException;
import org.json.JSONObject;

/** Handles the "done" and "snooze" buttons on a reminder notification. */
public class ActionReceiver extends BroadcastReceiver {
    static final String ACTION_DONE = "com.hamazkira.app.DONE";
    static final String ACTION_SNOOZE = "com.hamazkira.app.SNOOZE";
    private static final long SNOOZE_MS = 10 * 60 * 1000L;

    @Override
    public void onReceive(Context c, Intent i) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(i.getIntExtra("nid", 0));
        String id = i.getStringExtra("id");
        String date = i.getStringExtra("date");
        try {
            if (ACTION_DONE.equals(i.getAction())) {
                AlarmStore.pushAction(c, new JSONObject()
                        .put("type", "done").put("id", id).put("date", date));
            } else if (ACTION_SNOOZE.equals(i.getAction())) {
                long until = System.currentTimeMillis() + SNOOZE_MS;
                AlarmStore.add(c, new JSONObject()
                        .put("key", "snooze|" + id + "|" + until)
                        .put("id", id).put("date", date).put("at", until)
                        .put("title", i.getStringExtra("title"))
                        .put("body", i.getStringExtra("body"))
                        .put("wa", i.getStringExtra("wa") == null ? "" : i.getStringExtra("wa"))
                        .put("waTap", i.getBooleanExtra("waTap", false)));
                AlarmStore.pushAction(c, new JSONObject()
                        .put("type", "snooze").put("id", id).put("until", until));
            }
        } catch (JSONException ignored) { }
        MainActivity.pingWeb();
    }
}
