package com.example.wlsreminderapp;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.KeyguardManager;
import android.app.Service;
import android.content.pm.ServiceInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.app.PendingIntent;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
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
    private static final int POPUP_NOTIF_ID   = 999997;

    private BroadcastReceiver unlockReceiver;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startAsForeground();
        if (intent != null && ACTION_RECHECK.equals(intent.getAction())) {
            checkAndStopIfDone();
        } else {
            registerUnlockReceiver();
            // 서비스 시작 시 이미 잠금 해제 상태면 즉시 팝업 (레이스 컨디션 방지)
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
            if (pm != null && pm.isInteractive() && km != null && !km.isKeyguardLocked()) {
                checkAndShowPopup();
            }
        }
        return START_STICKY;
    }

    @SuppressLint("InlinedApi")
    private void startAsForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+: SHORT_SERVICE는 알림 표시 없음, 최대 3분 실행
            Notification n = new NotificationCompat.Builder(this, CHANNEL_SERVICE)
                    .setSmallIcon(R.drawable.ic_notification_bell)
                    .setPriority(NotificationCompat.PRIORITY_MIN)
                    .setSilent(true)
                    .build();
            startForeground(SERVICE_NOTIF_ID, n,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE);
        } else {
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
        ReminderReceiver.createChannels(this);
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
            Intent popupIntent = new Intent(this, ReminderPopupActivity.class);
            popupIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            popupIntent.putExtra("ids", ids);
            popupIntent.putExtra("names", names);
            popupIntent.putExtra("descs", descs);

            PendingIntent pi = PendingIntent.getActivity(this, 888888, popupIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            String summary = names[0] + (pending.size() > 1 ? " 외 " + (pending.size() - 1) + "개" : "");
            Notification popup = new NotificationCompat.Builder(this, ReminderReceiver.CHANNEL_HIGH)
                    .setSmallIcon(R.drawable.ic_notification_bell)
                    .setContentTitle("미완료 알림을 확인해주세요")
                    .setContentText(summary)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setFullScreenIntent(pi, true)
                    .setContentIntent(pi)
                    .setSilent(true)
                    .setAutoCancel(true)
                    .build();
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                    .notify(POPUP_NOTIF_ID, popup);
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
