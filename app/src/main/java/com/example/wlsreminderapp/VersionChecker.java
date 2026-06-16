package com.example.wlsreminderapp;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;

public class VersionChecker {

    // ↓ GitHub Raw URL로 교체하세요
    private static final String VERSION_URL =
            "https://raw.githubusercontent.com/32m1nd0t/WlsAlarm/refs/heads/main/version.json";

    public interface Listener {
        void onUpdateAvailable(String newVersion, String downloadUrl);
        void onNoUpdate();
        void onError();
    }

    // "1.10" > "1.2" 처럼 각 구간을 숫자로 비교. latest가 current보다 높으면 true.
    static boolean isNewer(String latest, String current) {
        if (latest == null) return false;
        if (current == null) return true;
        String[] a = latest.trim().split("\\.");
        String[] b = current.trim().split("\\.");
        int len = Math.max(a.length, b.length);
        for (int i = 0; i < len; i++) {
            int x = i < a.length ? parseIntSafe(a[i]) : 0;
            int y = i < b.length ? parseIntSafe(b[i]) : 0;
            if (x != y) return x > y;
        }
        return false; // 동일 버전
    }

    private static int parseIntSafe(String s) {
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return 0; }
    }

    public static void check(Context context, Listener listener) {
        String currentVersion;
        try {
            currentVersion = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            listener.onError();
            return;
        }

        Handler handler = new Handler(Looper.getMainLooper());
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                HttpURLConnection conn =
                        (HttpURLConnection) new URL(VERSION_URL).openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONObject json    = new JSONObject(sb.toString());
                String latest      = json.getString("version");
                String downloadUrl = json.getString("url");

                // 최신 버전이 현재보다 "더 높을 때만" 업데이트 알림
                if (isNewer(latest, currentVersion)) {
                    handler.post(() -> listener.onUpdateAvailable(latest, downloadUrl));
                } else {
                    handler.post(listener::onNoUpdate);
                }
            } catch (Exception e) {
                handler.post(listener::onError);
            }
        });
    }
}