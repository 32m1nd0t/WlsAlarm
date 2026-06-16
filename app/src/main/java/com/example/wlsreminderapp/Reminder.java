package com.example.wlsreminderapp;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import java.util.ArrayList;
import java.util.List;

@Entity(tableName = "reminders")
public class Reminder {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public String name;
    public String description;
    public String times; // "08:00,20:00"
    public String days;  // "2,3,4,5,6" (Calendar.DAY_OF_WEEK 값, 1=일 2=월 ... 7=토)
    public boolean isEnabled;
    public String lastCompletedDate; // "yyyy-MM-dd", 오늘 날짜면 완료 표시

    public Reminder(String name, String description,
                    String times, String days, boolean isEnabled) {
        this.name = name;
        this.description = description;
        this.times = times;
        this.days = days;
        this.isEnabled = isEnabled;
    }

    public List<int[]> getTimeList() {
        List<int[]> list = new ArrayList<>();
        if (times == null || times.isEmpty()) return list;
        for (String t : times.split(",")) {
            String[] parts = t.trim().split(":");
            if (parts.length == 2) {
                try {
                    list.add(new int[]{
                            Integer.parseInt(parts[0]),
                            Integer.parseInt(parts[1])
                    });
                } catch (NumberFormatException ignored) {}
            }
        }
        return list;
    }

}