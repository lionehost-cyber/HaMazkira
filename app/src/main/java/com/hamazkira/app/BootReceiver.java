package com.hamazkira.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Restores reminders after the phone restarts or the app is updated. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        AlarmStore.rescheduleAll(c);
    }
}
