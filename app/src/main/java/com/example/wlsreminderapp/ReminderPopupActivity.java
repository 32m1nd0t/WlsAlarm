package com.example.wlsreminderapp;

import android.app.NotificationManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;

public class ReminderPopupActivity extends AppCompatActivity {

    private LinearLayout container;
    private int visibleCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reminder_popup);

        int[] ids = getIntent().getIntArrayExtra("ids");
        String[] names = getIntent().getStringArrayExtra("names");
        String[] descs = getIntent().getStringArrayExtra("descs");

        if (ids == null || ids.length == 0) { finish(); return; }

        container = findViewById(R.id.reminderContainer);
        visibleCount = ids.length;

        for (int i = 0; i < ids.length; i++) {
            addReminderRow(
                    ids[i],
                    names != null && i < names.length ? names[i] : "",
                    descs != null && i < descs.length ? descs[i] : "");
        }

        // 카드 외부(어두운 배경) 탭 → 닫기
        findViewById(R.id.dimBackground).setOnClickListener(v -> finish());
        // 카드 내부는 이벤트 차단
        findViewById(R.id.popupCard).setOnClickListener(v -> {});
        // 나중에 버튼
        findViewById(R.id.btnLater).setOnClickListener(v -> finish());
    }

    private void addReminderRow(int reminderId, String name, String desc) {
        int dp8  = dp(8);
        int dp12 = dp(12);

        // 구분선 (첫 항목 제외)
        if (container.getChildCount() > 0) {
            View div = new View(this);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1);
            dlp.setMargins(0, dp8, 0, dp8);
            div.setLayoutParams(dlp);
            div.setBackgroundColor(0x1A000000);
            container.addView(div);
        }

        // 행: [텍스트 영역] [완료 버튼]
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // 텍스트 영역 (이름 + 설명)
        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textLp.setMarginEnd(dp12);
        textCol.setLayoutParams(textLp);

        // 리마인더 항목 배경
        textCol.setBackgroundColor(0xFFF5F6FA);
        textCol.setPadding(dp12, dp8, dp12, dp8);
        setRoundedBackground(textCol);

        TextView tvName = new TextView(this);
        tvName.setText(name);
        tvName.setTextSize(14f);
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        tvName.setTextColor(0xFF1A1A1A);
        textCol.addView(tvName);

        if (!desc.isEmpty()) {
            TextView tvDesc = new TextView(this);
            tvDesc.setText(desc);
            tvDesc.setTextSize(12f);
            tvDesc.setTextColor(0xFF888888);
            textCol.addView(tvDesc);
        }

        row.addView(textCol);

        // 완료 버튼
        MaterialButton btn = new MaterialButton(this);
        btn.setText("완료");
        btn.setTextSize(13f);
        btn.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        btn.setOnClickListener(v -> {
            markDone(reminderId, row);
        });
        row.addView(btn);
        container.addView(row);
    }

    private void setRoundedBackground(View v) {
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xFFF5F6FA);
        bg.setCornerRadius(dp(10));
        v.setBackground(bg);
    }

    private void markDone(int reminderId, LinearLayout row) {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        Executors.newSingleThreadExecutor().execute(() -> {
            ReminderDatabase.get(this).reminderDao().updateCompletedDate(reminderId, today);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(reminderId);
            Intent recheck = new Intent(this, ReminderCheckService.class);
            recheck.setAction(ReminderCheckService.ACTION_RECHECK);
            startService(recheck);
        });
        runOnUiThread(() -> {
            // 행과 바로 앞 구분선 숨김
            int idx = container.indexOfChild(row);
            if (idx > 0) container.getChildAt(idx - 1).setVisibility(View.GONE);
            row.setVisibility(View.GONE);
            visibleCount--;
            if (visibleCount == 0) finish();
        });
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
