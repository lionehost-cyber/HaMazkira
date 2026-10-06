package com.hamazkira.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.net.Uri;

/** Fires when a reminder is due and shows the notification. */
public class AlarmReceiver extends BroadcastReceiver {
    static final String CHANNEL = "reminders";

    @Override
    public void onReceive(Context c, Intent i) {
        String key = i.getStringExtra("key");
        AlarmStore.remove(c, key);
        show(c, key, i.getStringExtra("id"), i.getStringExtra("date"),
                i.getStringExtra("title"), i.getStringExtra("body"),
                i.getStringExtra("wa"), i.getBooleanExtra("waTap", false));
        MainActivity.pingWeb();
    }

    static void ensureChannel(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm != null && nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "תזכורות",
                    NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("תזכורות למשימות שלך");
            ch.enableVibration(true);
            nm.createNotificationChannel(ch);
        }
    }

    static void show(Context c, String key, String id, String date, String title, String body,
                     String wa, boolean waTap) {
        ensureChannel(c);
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        int nid = AlarmStore.code(key);

        Intent open = new Intent(c, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPi = PendingIntent.getActivity(c, nid, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_bell)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setContentIntent(openPi)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setColor(0xFF2C3E9E)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true);

        b.addAction(new Notification.Action.Builder((Icon) null, "בוצע",
                actionPi(c, ActionReceiver.ACTION_DONE, nid, key, id, date, title, body, wa, waTap)).build());
        b.addAction(new Notification.Action.Builder((Icon) null, "דחה 10 דק׳",
                actionPi(c, ActionReceiver.ACTION_SNOOZE, nid, key, id, date, title, body, wa, waTap)).build());

        // Open WhatsApp with the reminder text ready to send.
        if (wa != null && !wa.isEmpty()) {
            Intent waIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(wa))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent waPi = PendingIntent.getActivity(c, (key + "|wa").hashCode(), waIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            b.addAction(new Notification.Action.Builder((Icon) null, "WhatsApp", waPi).build());
            if (waTap) b.setContentIntent(waPi);
        }

        try {
            nm.notify(nid, b.build());
        } catch (SecurityException ignored) {
            // notifications were turned off by the user
        }
    }

    private static PendingIntent actionPi(Context c, String action, int nid, String key,
                                          String id, String date, String title, String body,
                                          String wa, boolean waTap) {
        Intent i = new Intent(c, ActionReceiver.class).setAction(action);
        i.putExtra("nid", nid);
        i.putExtra("id", id);
        i.putExtra("date", date);
        i.putExtra("title", title);
        i.putExtra("body", body);
        i.putExtra("wa", wa);
        i.putExtra("waTap", waTap);
        return PendingIntent.getBroadcast(c, (key + action).hashCode(), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
