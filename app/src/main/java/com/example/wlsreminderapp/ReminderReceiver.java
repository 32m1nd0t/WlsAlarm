package com.example.wlsreminderapp;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;

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

        // 완료 버튼 → 이 알림만 제거 + 오늘 완료 날짜 DB 저장
        if (ACTION_DONE.equals(action)) {
            int notifId = intent.getIntExtra("notifId", 0);
            NotificationManager nm = (NotificationManager)
                    context.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.cancel(notifId);
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    .format(new Date());
            PendingResult result = goAsync();
            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    ReminderDatabase.get(context).reminderDao()
                            .updateCompletedDate(notifId, today);
                } finally {
                    result.finish();
                }
            });
            // 서비스에 재확인 요청 (완료됐으면 서비스 종료)
            Intent recheck = new Intent(context, ReminderCheckService.class);
            recheck.setAction(ReminderCheckService.ACTION_RECHECK);
            context.startService(recheck);
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

        // 잠금 해제 팝업 서비스 시작
        Intent serviceIntent = new Intent(context, ReminderCheckService.class);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
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
                        .setSmallIcon(R.drawable.ic_notification_bell)
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

        // 소리 알림일 때 잠금화면 전체화면 팝업 (Android 14+ 는 권한 없으면 heads-up으로 강등)
        if (!isSilent) {
            Intent popupFsiIntent = new Intent(context, ReminderPopupActivity.class);
            popupFsiIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            popupFsiIntent.putExtra("ids", new int[]{notifId});
            popupFsiIntent.putExtra("names", new String[]{name});
            popupFsiIntent.putExtra("descs", new String[]{desc});
            PendingIntent popupFsiPending = PendingIntent.getActivity(
                    context, notifId + 800000, popupFsiIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            if (Build.VERSION.SDK_INT < 34) {
                builder.setFullScreenIntent(popupFsiPending, true);
            } else {
                NotificationManager nmCheck = (NotificationManager)
                        context.getSystemService(Context.NOTIFICATION_SERVICE);
                if (nmCheck.canUseFullScreenIntent()) {
                    builder.setFullScreenIntent(popupFsiPending, true);
                }
            }
        }

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
