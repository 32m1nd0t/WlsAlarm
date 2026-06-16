package com.example.wlsreminderapp;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import android.widget.ToggleButton;
import com.google.android.material.chip.Chip;
import androidx.appcompat.app.AppCompatActivity;
import com.example.wlsreminderapp.databinding.ActivityAddReminderBinding;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

public class AddReminderActivity extends AppCompatActivity {

    private ActivityAddReminderBinding binding;
    private final List<String> timeList = new ArrayList<>();
    private ToggleButton[] dayButtons;

    private static final int[] DAY_VALUES = {2, 3, 4, 5, 6, 7, 1}; // 월화수목금토일

    private int editReminderId = -1;         // -1이면 추가 모드, 그 외면 편집 모드
    private boolean editEnabled = true;      // 편집 진입 시 원래 활성화 상태 보존
    private String editLastCompletedDate = null; // 완료 날짜 보존

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAddReminderBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // 요일 버튼 초기화
        dayButtons = new ToggleButton[]{
                binding.btnMon, binding.btnTue, binding.btnWed,
                binding.btnThu, binding.btnFri, binding.btnSat, binding.btnSun
        };

        // 편집 모드 확인
        editReminderId = getIntent().getIntExtra("reminder_id", -1);
        if (editReminderId != -1) {
            setTitle("리마인더 편집");
            binding.btnSave.setText("수정");
            loadReminderForEdit(editReminderId);
        } else {
            setTitle("리마인더 추가");
            for (ToggleButton btn : dayButtons) btn.setChecked(true);
        }

        // 시간 추가 버튼
        binding.btnAddTime.setOnClickListener(v -> {
            int hour   = binding.timePicker.getHour();
            int minute = binding.timePicker.getMinute();
            String timeStr = String.format(Locale.getDefault(), "%02d:%02d", hour, minute);
            if (timeList.contains(timeStr)) {
                Toast.makeText(this, "이미 추가된 시간입니다", Toast.LENGTH_SHORT).show();
                return;
            }
            if (timeList.size() >= 10) {
                Toast.makeText(this, "최대 10개까지 가능합니다", Toast.LENGTH_SHORT).show();
                return;
            }
            timeList.add(timeStr);
            addTimeChip(timeStr);
        });

        setupSaveButton();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void setupSaveButton() {
        binding.btnSave.setOnClickListener(v -> {
            String name = binding.etName.getText().toString().trim();
            String desc = binding.etDesc.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, "이름을 입력해주세요", Toast.LENGTH_SHORT).show();
                return;
            }
            if (timeList.isEmpty()) {
                Toast.makeText(this, "시간을 하나 이상 추가해주세요", Toast.LENGTH_SHORT).show();
                return;
            }

            // 선택된 요일 수집
            StringBuilder daysBuilder = new StringBuilder();
            for (int i = 0; i < dayButtons.length; i++) {
                if (dayButtons[i].isChecked()) {
                    if (daysBuilder.length() > 0) daysBuilder.append(",");
                    daysBuilder.append(DAY_VALUES[i]);
                }
            }
            if (daysBuilder.length() == 0) {
                Toast.makeText(this, "요일을 하나 이상 선택해주세요", Toast.LENGTH_SHORT).show();
                return;
            }

            String timesStr = String.join(",", timeList);
            String daysStr  = daysBuilder.toString();

            if (editReminderId != -1) {
                // 편집 모드: 기존 알람 취소 후 업데이트
                saveEdit(name, desc, timesStr, daysStr);
            } else {
                // 추가 모드
                saveNew(name, desc, timesStr, daysStr);
            }
        });
    }

    private void saveNew(String name, String desc, String timesStr, String daysStr) {
        Executors.newSingleThreadExecutor().execute(() -> {
            ReminderDao dao = ReminderDatabase.get(this).reminderDao();
            Reminder reminder = new Reminder(name, desc, timesStr, daysStr, true);
            long newId = dao.insert(reminder);
            reminder.id = (int) newId;
            AlarmScheduler.schedule(this, reminder);

            // 즉시 무음 알림 표시 (오늘이 선택된 요일일 때만)
            if (isTodaySelected(daysStr)) {
                List<int[]> times = reminder.getTimeList();
                int[] next = getNextUpcomingTime(times);
                if (next != null) {
                    int idx = getTimeIndex(times, next);
                    ReminderReceiver.showNotification(
                            this, reminder.id * 1000 + idx,
                            name, desc, next[0], next[1], true); // true = 무음
                }
            }

            runOnUiThread(() -> {
                Toast.makeText(this, "저장됨!", Toast.LENGTH_SHORT).show();
                finish();
            });
        });
    }

    private void saveEdit(String name, String desc, String timesStr, String daysStr) {
        Executors.newSingleThreadExecutor().execute(() -> {
            // 기존 알람 전부 취소
            AlarmScheduler.cancelAll(this, editReminderId);

            Reminder updated = new Reminder(name, desc, timesStr, daysStr, editEnabled);
            updated.lastCompletedDate = editLastCompletedDate; // 완료 날짜 보존
            updated.id = editReminderId;
            ReminderDatabase.get(this).reminderDao().update(updated);

            // 비활성 리마인더는 예약/표시하지 않음
            if (editEnabled) {
                AlarmScheduler.schedule(this, updated);

                // 즉시 무음 알림 업데이트 (오늘이 선택된 요일일 때만)
                if (isTodaySelected(daysStr)) {
                    List<int[]> times = updated.getTimeList();
                    int[] next = getNextUpcomingTime(times);
                    if (next != null) {
                        int idx = getTimeIndex(times, next);
                        ReminderReceiver.showNotification(
                                this, updated.id * 1000 + idx,
                                name, desc, next[0], next[1], true);
                    }
                }
            }

            runOnUiThread(() -> {
                Toast.makeText(this, "수정됨!", Toast.LENGTH_SHORT).show();
                finish();
            });
        });
    }

    private void loadReminderForEdit(int id) {
        Executors.newSingleThreadExecutor().execute(() -> {
            Reminder r = null;
            for (Reminder rem : ReminderDatabase.get(this).reminderDao().getAllOnce()) {
                if (rem.id == id) { r = rem; break; }
            }
            if (r == null) return;

            editEnabled = r.isEnabled;
            editLastCompletedDate = r.lastCompletedDate; // 완료 날짜도 보존
            final Reminder reminder = r;
            runOnUiThread(() -> {
                binding.etName.setText(reminder.name);
                binding.etDesc.setText(reminder.description);

                // 시간 복원
                if (reminder.times != null) {
                    for (String t : reminder.times.split(",")) {
                        t = t.trim();
                        if (!t.isEmpty()) {
                            timeList.add(t);
                            addTimeChip(t);
                        }
                    }
                }

                // 요일 복원
                java.util.Set<Integer> selectedDays =
                        AlarmScheduler.parseDays(reminder.days);
                for (int i = 0; i < dayButtons.length; i++) {
                    dayButtons[i].setChecked(selectedDays.contains(DAY_VALUES[i]));
                }
            });
        });
    }

    // 오늘 요일이 선택된 요일 집합에 포함되는지
    private boolean isTodaySelected(String daysStr) {
        int today = java.util.Calendar.getInstance()
                .get(java.util.Calendar.DAY_OF_WEEK);
        return AlarmScheduler.parseDays(daysStr).contains(today);
    }

    private int[] getNextUpcomingTime(List<int[]> times) {
        java.util.Calendar now = java.util.Calendar.getInstance();
        int cur = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 +
                now.get(java.util.Calendar.MINUTE);
        int[] next = null;
        int minDiff = Integer.MAX_VALUE;
        for (int[] t : times) {
            int diff = (t[0] * 60 + t[1]) - cur;
            if (diff > 0 && diff < minDiff) { minDiff = diff; next = t; }
        }
        if (next == null && !times.isEmpty()) next = times.get(0);
        return next;
    }

    private int getTimeIndex(List<int[]> times, int[] target) {
        for (int i = 0; i < times.size(); i++) {
            if (times.get(i)[0] == target[0] && times.get(i)[1] == target[1]) return i;
        }
        return 0;
    }

    private void addTimeChip(String timeStr) {
        Chip chip = new Chip(this);
        chip.setText(timeStr);
        chip.setCloseIconVisible(true);
        chip.setOnCloseIconClickListener(v -> {
            timeList.remove(timeStr);
            binding.timeChipsContainer.removeView(chip);
            binding.tvAddedLabel.setVisibility(
                    timeList.isEmpty() ? View.GONE : View.VISIBLE);
        });
        binding.timeChipsContainer.addView(chip);
        binding.tvAddedLabel.setVisibility(View.VISIBLE);
    }
}