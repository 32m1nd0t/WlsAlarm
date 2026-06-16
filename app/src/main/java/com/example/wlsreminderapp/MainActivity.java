package com.example.wlsreminderapp;

import android.Manifest;
import android.app.AlarmManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.wlsreminderapp.databinding.ActivityMainBinding;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private ReminderAdapter adapter;

    private final ActivityResultLauncher<String> notifPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    granted -> {});

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            if (!am.canScheduleExactAlarms()) {
                startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM));
            }
        }

        // 배터리 최적화 제외 (삼성 절전으로 알람이 멈추는 것 방지)
        requestIgnoreBatteryOptimization();

        // 업데이트 체크
        VersionChecker.check(this, new VersionChecker.Listener() {
            @Override
            public void onUpdateAvailable(String newVersion, String downloadUrl) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("업데이트 알림")
                        .setMessage("새 버전 " + newVersion + "이 출시됐습니다.\n업데이트하시겠습니까?")
                        .setPositiveButton("업데이트", (d, w) ->
                                AppUpdater.start(MainActivity.this, downloadUrl))
                        .setNegativeButton("나중에", null)
                        .show();
            }
            @Override public void onNoUpdate() {}
            @Override public void onError() {}
        });

        adapter = new ReminderAdapter(
                // 삭제
                reminder -> new AlertDialog.Builder(this)
                        .setTitle("삭제")
                        .setMessage("'" + reminder.name + "' 리마인더를 삭제할까요?")
                        .setPositiveButton("삭제", (d, w) ->
                                Executors.newSingleThreadExecutor().execute(() -> {
                                    ReminderDatabase.get(this).reminderDao().delete(reminder);
                                    AlarmScheduler.cancelAll(this, reminder.id);
                                    runOnUiThread(() ->
                                            Toast.makeText(this, "삭제됨", Toast.LENGTH_SHORT).show());
                                }))
                        .setNegativeButton("취소", null)
                        .show(),
                // 토글
                (reminder, enabled) ->
                        Executors.newSingleThreadExecutor().execute(() -> {
                            reminder.isEnabled = enabled;
                            ReminderDatabase.get(this).reminderDao().update(reminder);
                            if (enabled) AlarmScheduler.schedule(this, reminder);
                            else AlarmScheduler.cancelAll(this, reminder.id);
                        }),
                // 편집 ← 추가
                reminder -> {
                    Intent intent = new Intent(this, AddReminderActivity.class);
                    intent.putExtra("reminder_id", reminder.id);
                    startActivity(intent);
                }
        );

        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerView.setAdapter(adapter);

        ReminderDatabase.get(this).reminderDao().getAll()
                .observe(this, list -> adapter.submitList(list));

        binding.fabAdd.setOnClickListener(v ->
                startActivity(new Intent(this, AddReminderActivity.class)));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_inquiry) {
            startActivity(new Intent(this, InquiryActivity.class));
            return true;
        }
        if (item.getItemId() == R.id.menu_about) {
            showAboutDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showAboutDialog() {
        String ver = "알 수 없음";
        try {
            ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException ignored) {}
        final String currentVer = ver;

        new AlertDialog.Builder(this)
                .setTitle("Wls리마인더")
                .setMessage("현재 버전: " + currentVer)
                .setPositiveButton("업데이트 확인", (d, w) ->
                        VersionChecker.check(this, new VersionChecker.Listener() {
                            @Override
                            public void onUpdateAvailable(String newVersion, String downloadUrl) {
                                new AlertDialog.Builder(MainActivity.this)
                                        .setTitle("업데이트 알림")
                                        .setMessage("새 버전 " + newVersion + "이 출시됐습니다.\n업데이트하시겠습니까?")
                                        .setPositiveButton("업데이트", (d2, w2) ->
                                                AppUpdater.start(MainActivity.this, downloadUrl))
                                        .setNegativeButton("나중에", null)
                                        .show();
                            }
                            @Override
                            public void onNoUpdate() {
                                Toast.makeText(MainActivity.this,
                                        "최신 버전입니다 (" + currentVer + ")",
                                        Toast.LENGTH_SHORT).show();
                            }
                            @Override
                            public void onError() {
                                Toast.makeText(MainActivity.this,
                                        "업데이트 확인 실패 (네트워크 확인)",
                                        Toast.LENGTH_SHORT).show();
                            }
                        }))
                .setNegativeButton("닫기", null)
                .show();
    }

    // 배터리 최적화에서 앱 제외 요청 + 삼성 절전 수동 안내
    private void requestIgnoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm == null || pm.isIgnoringBatteryOptimizations(getPackageName())) return;

        new AlertDialog.Builder(this)
                .setTitle("알람이 멈추지 않게 하려면")
                .setMessage("배터리 절전 기능 때문에 알림이 며칠 뒤 멈출 수 있어요.\n\n"
                        + "1) '허용'을 눌러 배터리 최적화에서 제외\n"
                        + "2) (삼성) 설정 > 배터리 > 백그라운드 사용 제한 >\n"
                        + "   '절전 앱'에서 이 앱을 빼주세요")
                .setPositiveButton("허용", (d, w) -> {
                    try {
                        Intent intent = new Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                        intent.setData(Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                    } catch (Exception e) {
                        // 일부 기기는 해당 인텐트 미지원 → 일반 설정 화면으로
                        startActivity(new Intent(
                                Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                    }
                })
                .setNegativeButton("나중에", null)
                .show();
    }
}