package com.example.wlsreminderapp;

import android.content.Context;
import android.content.SharedPreferences;

/** 반복 재알림(nag) 설정 - SharedPreferences "app_prefs" 사용 */
public class NagSettings {

    private static final String PREFS = "app_prefs";
    static final String KEY_ENABLED  = "nag_enabled";
    static final String KEY_SOUND    = "nag_sound";
    static final String KEY_INTERVAL = "nag_interval_min";

    /** 재알림 간격 기본값(분) 및 허용 범위 */
    public static final int DEFAULT_INTERVAL_MIN = 10;
    public static final int MIN_INTERVAL_MIN = 1;
    public static final int MAX_INTERVAL_MIN = 1440; // 24시간

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** 반복 재알림 사용 여부 (기본: 켜짐) */
    public static boolean isEnabled(Context c) {
        return prefs(c).getBoolean(KEY_ENABLED, true);
    }

    /** 재알림 시 소리/진동 여부 (기본: 무음) */
    public static boolean isSound(Context c) {
        return prefs(c).getBoolean(KEY_SOUND, false);
    }

    /** 재알림 간격(분). 범위를 벗어나면 안전하게 보정 */
    public static int getIntervalMin(Context c) {
        return clamp(prefs(c).getInt(KEY_INTERVAL, DEFAULT_INTERVAL_MIN));
    }

    public static void setEnabled(Context c, boolean v) {
        prefs(c).edit().putBoolean(KEY_ENABLED, v).apply();
    }

    public static void setSound(Context c, boolean v) {
        prefs(c).edit().putBoolean(KEY_SOUND, v).apply();
    }

    public static void setIntervalMin(Context c, int v) {
        prefs(c).edit().putInt(KEY_INTERVAL, clamp(v)).apply();
    }

    private static int clamp(int v) {
        if (v < MIN_INTERVAL_MIN) return MIN_INTERVAL_MIN;
        if (v > MAX_INTERVAL_MIN) return MAX_INTERVAL_MIN;
        return v;
    }
}
