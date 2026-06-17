package com.example.wlsreminderapp;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AlarmScheduler {

    public static void schedule(Context context, Reminder reminder) {
        List<int[]> times = reminder.getTimeList();
        for (int i = 0; i < times.size(); i++) {
            int[] time = times.get(i);
            scheduleOne(context, reminder.id * 1000 + i,
                    time[0], time[1], reminder.name, reminder.description, reminder.days);
        }
    }

    public static void scheduleOne(Context context, int alarmId, int hour, int minute,
                                   String name, String desc, String days) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        // 선택된 요일 기준으로 다음 발동 시각 계산
        Calendar cal = getNextOccurrence(hour, minute, parseDays(days));

        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.putExtra(ReminderReceiver.EXTRA_ID, alarmId);
        intent.putExtra(ReminderReceiver.EXTRA_NAME, name);
        intent.putExtra(ReminderReceiver.EXTRA_DESC, desc);
        intent.putExtra("hour", hour);
        intent.putExtra("minute", minute);
        intent.putExtra("days", days);

        PendingIntent pending = PendingIntent.getBroadcast(
                context, alarmId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pending);
    }

    // 선택된 요일 중 가장 가까운 다음 시각 계산
    private static Calendar getNextOccurrence(int hour, int minute, Set<Integer> days) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        for (int i = 0; i <= 7; i++) {
            if (i > 0) cal.add(Calendar.DAY_OF_YEAR, 1);
            if (days.contains(cal.get(Calendar.DAY_OF_WEEK)) &&
                    cal.getTimeInMillis() > System.currentTimeMillis()) {
                return cal;
            }
        }
        return cal;
    }

    // "2,3,4,5,6" → Set{2,3,4,5,6}
    public static Set<Integer> parseDays(String days) {
        Set<Integer> set = new HashSet<>();
        if (days == null || days.isEmpty()) {
            for (int i = 1; i <= 7; i++) set.add(i);
            return set;
        }
        for (String d : days.split(",")) {
            try { set.add(Integer.parseInt(d.trim())); }
            catch (NumberFormatException ignored) {}
        }
        return set;
    }

    public static void cancelAll(Context context, int reminderId) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        for (int i = 0; i < 10; i++) {
            Intent intent = new Intent(context, ReminderReceiver.class);
            PendingIntent pending = PendingIntent.getBroadcast(
                    context, reminderId * 1000 + i, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            am.cancel(pending);
        }
        // 반복 재알림도 취소
        ReminderReceiver.cancelNag(context, reminderId);
    }
}