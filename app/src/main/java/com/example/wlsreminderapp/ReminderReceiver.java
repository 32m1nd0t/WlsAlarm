package com.example.wlsreminderapp;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;

public class ReminderReceiver extends BroadcastReceiver {

    // 채널 2개로 소리/무음 분리
    public static final String CHANNEL_HIGH = "reminder_channel_high"; // 소리/진동
    public static final String CHANNEL_LOW  = "reminder_channel_low";  // 무음

    public static final String EXTRA_ID   = "reminder_id";
    public static final String EXTRA_NAME = "reminder_name";
    public static final String EXTRA_DESC = "reminder_desc";

    // 알림 액션 (자기 자신에게 보내는 명시적 브로드캐스트)
    public static final String ACTION_DONE   = "com.example.wlsreminderapp.ACTION_DONE";
    public static final String ACTION_RESHOW = "com.example.wlsreminderapp.ACTION_RESHOW";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        // 완료 버튼 → 이 알림만 제거
        if (ACTION_DONE.equals(action)) {
            int notifId = intent.getIntExtra("notifId", 0);
            NotificationManager nm = (NotificationManager)
                    context.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.cancel(notifId);
            return;
        }

        int alarmId    = intent.getIntExtra(EXTRA_ID, 0);
        String name    = intent.getStringExtra(EXTRA_NAME);
        String desc    = intent.getStringExtra(EXTRA_DESC);
        int hour       = intent.getIntExtra("hour", 8);
        int minute     = intent.getIntExtra("minute", 0);
        String days    = intent.getStringExtra("days");
        if (name == null) name = "리마인더";
        if (desc == null) desc = "";

        // 스와이프 후 재표시 → 무음 (재예약은 하지 않음)
        if (ACTION_RESHOW.equals(action)) {
            showNotification(context, alarmId, name, desc, hour, minute, true);
            return;
        }

        // 알람 정시 발동: 다음 요일/시간 재예약 + 소리/진동 알림
        AlarmScheduler.scheduleOne(context, alarmId, hour, minute, name, desc, days);
        showNotification(context, alarmId, name, desc, hour, minute, false);
    }

    // isSilent: true = 무음(초기세팅/재표시), false = 소리+진동(알람 시간)
    public static void showNotification(Context context, int alarmId,
                                        String name, String desc,
                                        int hour, int minute, boolean isSilent) {
        createChannels(context);

        int notifId = alarmId / 1000; // 리마인더 1개당 알림 1개
        String channelId = isSilent ? CHANNEL_LOW : CHANNEL_HIGH;

        // 완료 버튼 → DONE 브로드캐스트
        Intent doneIntent = new Intent(context, ReminderReceiver.class);
        doneIntent.setAction(ACTION_DONE);
        doneIntent.putExtra("notifId", notifId);
        PendingIntent donePending = PendingIntent.getBroadcast(
                context, notifId + 500000, doneIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // 스와이프(삭제) → RESHOW 브로드캐스트로 무음 재표시
        Intent reshowIntent = new Intent(context, ReminderReceiver.class);
        reshowIntent.setAction(ACTION_RESHOW);
        reshowIntent.putExtra(EXTRA_ID, alarmId);
        reshowIntent.putExtra(EXTRA_NAME, name);
        reshowIntent.putExtra(EXTRA_DESC, desc);
        reshowIntent.putExtra("hour", hour);
        reshowIntent.putExtra("minute", minute);
        PendingIntent reshowPending = PendingIntent.getBroadcast(
                context, notifId + 600000, reshowIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // 알림 탭 → 앱(MainActivity) 열기
        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPending = PendingIntent.getActivity(
                context, notifId + 700000, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String timeStr  = String.format("%02d:%02d", hour, minute);
        String bodyText = desc.isEmpty()
                ? timeStr + " - 아직 완료하지 않은 일정입니다"
                : desc + "\n" + timeStr + " - 아직 완료하지 않은 일정입니다";

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, channelId)
                        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                        .setContentTitle("⏰ " + name)
                        .setContentText(timeStr + " - 아직 완료하지 않은 일정입니다")
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(bodyText))
                        .setPriority(isSilent
                                ? NotificationCompat.PRIORITY_LOW
                                : NotificationCompat.PRIORITY_HIGH)
                        .setOngoing(true)
                        .setAutoCancel(false)
                        .setSilent(isSilent)
                        .setContentIntent(openPending)
                        .setDeleteIntent(reshowPending)
                        .addAction(0, "✅ 완료", donePending);

        NotificationManager nm = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(notifId, builder.build());
    }

    public static void createChannels(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager)
                    context.getSystemService(Context.NOTIFICATION_SERVICE);

            NotificationChannel high = new NotificationChannel(
                    CHANNEL_HIGH, "리마인더 알림 (소리)",
                    NotificationManager.IMPORTANCE_HIGH);
            high.setDescription("알람 시간에 소리/진동으로 알림");

            NotificationChannel low = new NotificationChannel(
                    CHANNEL_LOW, "리마인더 알림 (무음)",
                    NotificationManager.IMPORTANCE_LOW);
            low.setDescription("등록 직후/재표시 시 무음 알림");

            nm.createNotificationChannel(high);
            nm.createNotificationChannel(low);
        }
    }
}
