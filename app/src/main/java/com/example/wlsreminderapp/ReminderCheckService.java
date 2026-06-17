package com.example.wlsreminderapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

public class ReminderCheckService extends Service {

    static final String ACTION_RECHECK = "com.example.wlsreminderapp.ACTION_RECHECK";
    private static final String CHANNEL_SERVICE = "reminder_service_channel";
    private static final int SERVICE_NOTIF_ID = 999998;

    private BroadcastReceiver unlockReceiver;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startAsForeground();
        if (intent != null && ACTION_RECHECK.equals(intent.getAction())) {
            checkAndStopIfDone();
        } else {
            registerUnlockReceiver();
        }
        return START_STICKY;
    }

    private void startAsForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_SERVICE, "리마인더 서비스",
                    NotificationManager.IMPORTANCE_MIN);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        Notification n = new NotificationCompat.Builder(this, CHANNEL_SERVICE)
                .setSmallIcon(R.drawable.ic_notification_bell)
                .setContentTitle("Wls리마인더")
                .setContentText("미완료 알림 확인 중")
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setSilent(true)
                .build();
        startForeground(SERVICE_NOTIF_ID, n);
    }

    private void registerUnlockReceiver() {
        if (unlockReceiver != null) return;
        unlockReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent i) {
                checkAndShowPopup();
            }
        };
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(unlockReceiver,
                    new IntentFilter(Intent.ACTION_USER_PRESENT),
                    Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(unlockReceiver, new IntentFilter(Intent.ACTION_USER_PRESENT));
        }
    }

    private void checkAndShowPopup() {
        Executors.newSingleThreadExecutor().execute(() -> {
            List<Reminder> pending = getPendingReminders();
            if (pending.isEmpty()) {
                stopSelf();
                return;
            }
            int[] ids = new int[pending.size()];
            String[] names = new String[pending.size()];
            String[] descs = new String[pending.size()];
            for (int i = 0; i < pending.size(); i++) {
                ids[i] = pending.get(i).id;
                names[i] = pending.get(i).name;
                descs[i] = pending.get(i).description != null ? pending.get(i).description : "";
            }
            Intent popup = new Intent(this, ReminderPopupActivity.class);
            popup.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            popup.putExtra("ids", ids);
            popup.putExtra("names", names);
            popup.putExtra("descs", descs);
            startActivity(popup);
        });
    }

    void checkAndStopIfDone() {
        Executors.newSingleThreadExecutor().execute(() -> {
            if (getPendingReminders().isEmpty()) {
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
            }
        });
    }

    List<Reminder> getPendingReminders() {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        int todayDow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
        int curMin = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) * 60
                + Calendar.getInstance().get(Calendar.MINUTE);

        List<Reminder> result = new ArrayList<>();
        for (Reminder r : ReminderDatabase.get(this).reminderDao().getAllOnce()) {
            if (!r.isEnabled) continue;
            if (today.equals(r.lastCompletedDate)) continue;
            if (!AlarmScheduler.parseDays(r.days).contains(todayDow)) continue;
            for (int[] t : r.getTimeList()) {
                if (t[0] * 60 + t[1] <= curMin) {
                    result.add(r);
                    break;
                }
            }
        }
        return result;
    }

    @Override
    public void onDestroy() {
        if (unlockReceiver != null) {
            try { unregisterReceiver(unlockReceiver); } catch (Exception ignored) {}
            unlockReceiver = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
