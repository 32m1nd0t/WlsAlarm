package com.example.wlsreminderapp;

import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.android.material.button.MaterialButton;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;

/**
 * 다른 앱 위에 뜨는 팝업(오버레이 창). 액티비티가 아니라 WindowManager로 직접 그려서
 * 앱 전환이 일어나지 않고, 뒤의 앱(유튜브 등)은 계속 재생된다.
 * SYSTEM_ALERT_WINDOW("다른 앱 위에 표시") 권한 필요.
 */
public class ReminderOverlay {

    private static View overlayView;
    private static LinearLayout container;
    private static WindowManager wm;
    private static final Set<Integer> shownIds = new HashSet<>();

    public static boolean canShow(Context ctx) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(ctx);
    }

    /** 어느 스레드에서 호출하든 안전 (내부에서 메인 스레드로 전환) */
    public static void show(Context appCtx, int[] ids, String[] names, String[] descs) {
        if (ids == null || ids.length == 0) return;
        if (!canShow(appCtx)) return;
        new Handler(Looper.getMainLooper()).post(() -> showOnMain(appCtx, ids, names, descs));
    }

    @SuppressLint("InflateParams")
    private static void showOnMain(Context appCtx, int[] ids, String[] names, String[] descs) {
        try {
            Context themed = new ContextThemeWrapper(appCtx, R.style.Theme_ReminderPopup);
            if (wm == null) wm = (WindowManager) appCtx.getSystemService(Context.WINDOW_SERVICE);
            if (wm == null) return;

            if (overlayView == null) {
                View v = LayoutInflater.from(themed)
                        .inflate(R.layout.activity_reminder_popup, null);
                container = v.findViewById(R.id.reminderContainer);
                v.findViewById(R.id.dimBackground).setOnClickListener(x -> dismiss());
                v.findViewById(R.id.popupCard).setOnClickListener(x -> {});
                v.findViewById(R.id.btnLater).setOnClickListener(x -> dismiss());

                WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        // NOT_FOCUSABLE: 뒤 앱이 포커스 유지 → 유튜브 계속 재생
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                        PixelFormat.TRANSLUCENT);
                lp.gravity = Gravity.CENTER;
                wm.addView(v, lp);
                overlayView = v;
                shownIds.clear();
            }

            for (int i = 0; i < ids.length; i++) {
                int id = ids[i];
                if (shownIds.contains(id)) continue;
                shownIds.add(id);
                addRow(themed, id,
                        names != null && i < names.length ? names[i] : "",
                        descs != null && i < descs.length ? descs[i] : "");
            }
        } catch (Exception ignored) {
            // 오버레이 실패해도 알림은 이미 떠 있으므로 조용히 무시
            overlayView = null;
            container = null;
        }
    }

    private static void addRow(Context ctx, int reminderId, String name, String desc) {
        int dp8 = dp(ctx, 8), dp10 = dp(ctx, 10), dp12 = dp(ctx, 12);

        if (container.getChildCount() > 0) {
            View div = new View(ctx);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1);
            dlp.setMargins(0, dp8, 0, dp8);
            div.setLayoutParams(dlp);
            div.setBackgroundColor(0x1A000000);
            container.addView(div);
        }

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout textCol = new LinearLayout(ctx);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textLp.setMarginEnd(dp12);
        textCol.setLayoutParams(textLp);
        textCol.setPadding(dp12, dp8, dp12, dp8);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFF5F6FA);
        bg.setCornerRadius(dp10);
        textCol.setBackground(bg);

        TextView tvName = new TextView(ctx);
        tvName.setText(name);
        tvName.setTextSize(14f);
        tvName.setTypeface(null, Typeface.BOLD);
        tvName.setTextColor(0xFF1A1A1A);
        textCol.addView(tvName);

        if (desc != null && !desc.isEmpty()) {
            TextView tvDesc = new TextView(ctx);
            tvDesc.setText(desc);
            tvDesc.setTextSize(12f);
            tvDesc.setTextColor(0xFF888888);
            textCol.addView(tvDesc);
        }
        row.addView(textCol);

        MaterialButton btn = new MaterialButton(ctx);
        btn.setText("완료");
        btn.setTextSize(13f);
        btn.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        btn.setOnClickListener(v -> {
            markDone(ctx.getApplicationContext(), reminderId);
            shownIds.remove(reminderId);
            int idx = container.indexOfChild(row);
            if (idx > 0) container.getChildAt(idx - 1).setVisibility(View.GONE);
            row.setVisibility(View.GONE);
            if (shownIds.isEmpty()) dismiss();
        });
        row.addView(btn);
        container.addView(row);
    }

    private static void markDone(Context appCtx, int reminderId) {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        Executors.newSingleThreadExecutor().execute(() -> {
            ReminderDatabase.get(appCtx).reminderDao().updateCompletedDate(reminderId, today);
            ((NotificationManager) appCtx.getSystemService(Context.NOTIFICATION_SERVICE))
                    .cancel(reminderId);
            ReminderReceiver.cancelNag(appCtx, reminderId);
        });
    }

    public static void dismiss() {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                if (overlayView != null && wm != null) wm.removeView(overlayView);
            } catch (Exception ignored) {}
            overlayView = null;
            container = null;
            shownIds.clear();
        });
    }

    private static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }
}
