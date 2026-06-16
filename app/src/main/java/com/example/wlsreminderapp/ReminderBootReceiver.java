package com.example.wlsreminderapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import java.util.List;
import java.util.concurrent.Executors;

public class ReminderBootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Executors.newSingleThreadExecutor().execute(() -> {
                List<Reminder> list = ReminderDatabase.get(context)
                        .reminderDao().getAllOnce();
                for (Reminder r : list) {
                    if (r.isEnabled) {
                        AlarmScheduler.schedule(context, r);
                    }
                }
            });
        }
    }
}