package com.example.wlsreminderapp;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.wlsreminderapp.databinding.ActivityMainBinding;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private ReminderAdapter adapter;

    // 권한 체이닝 단계
    private static final int STEP_BATTERY      = 1;
    private static final int STEP_FULLSCREEN   = 2;
    private static final int STEP_EXACT_ALARM  = 3;
    private int nextPermStep = STEP_BATTERY;

    // 알림 권한 (시스템 다이얼로그, 앱 안에서 처리)
    private final ActivityResultLauncher<String> notifLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    granted -> proceedToPermStep(STEP_BATTERY));

    // 설정화면으로 이탈하는 권한들 공용 런처
    private final ActivityResultLauncher<Intent> settingsLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> proceedToPermStep(nextPermStep));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 권한 체이닝 시작
        startPermissionChain();

        // 새 버전 첫 실행 시 패치노트 표시
        checkAndShowWhatsNew();

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
                                    ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                                            .cancel(reminder.id);
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
                // 편집
                reminder -> {
                    Intent intent = new Intent(this, AddReminderActivity.class);
                    intent.putExtra("reminder_id", reminder.id);
                    startActivity(intent);
                }
        );

        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerView.setAdapter(adapter);

        ReminderDatabase.get(this).reminderDao().getAll()
                .observe(this, list -> {
                    adapter.submitList(list);
                    binding.tvEmpty.setVisibility(
                            list == null || list.isEmpty() ? View.VISIBLE : View.GONE);
                });

        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override public void onChanged() {
                binding.tvEmpty.setVisibility(
                        adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
            }
        });

        binding.fabAdd.setOnClickListener(v ->
                startActivity(new Intent(this, AddReminderActivity.class)));
    }

    // ── 권한 체이닝 ─────────────────────────────────────────────

    /** 체인 시작: 알림 권한(앱 내 다이얼로그) → 배터리 → 잠금화면 → 정확한 알람 */
    private void startPermissionChain() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                return;
            }
        }
        proceedToPermStep(STEP_BATTERY);
    }

    /** 각 단계를 순서대로 실행. 조건 불충족 시 다음 단계로 자동 진행. */
    private void proceedToPermStep(int step) {
        switch (step) {

            case STEP_BATTERY: {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                    proceedToPermStep(STEP_FULLSCREEN);
                    return;
                }
                PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                if (pm == null || pm.isIgnoringBatteryOptimizations(getPackageName())) {
                    proceedToPermStep(STEP_FULLSCREEN);
                    return;
                }
                new AlertDialog.Builder(this)
                        .setTitle("알람이 멈추지 않게 하려면")
                        .setMessage("배터리 절전 기능 때문에 알림이 며칠 뒤 멈출 수 있어요.\n\n"
                                + "1) '허용'을 눌러 배터리 최적화에서 제외\n"
                                + "2) (삼성) 설정 > 배터리 > 백그라운드 사용 제한 >\n"
                                + "   '절전 앱'에서 이 앱을 빼주세요")
                        .setPositiveButton("허용", (d, w) -> {
                            nextPermStep = STEP_FULLSCREEN;
                            try {
                                Intent i = new Intent(
                                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                        Uri.parse("package:" + getPackageName()));
                                settingsLauncher.launch(i);
                            } catch (Exception e) {
                                // 일부 기기 미지원 → 일반 배터리 설정으로
                                startActivity(new Intent(
                                        Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                                proceedToPermStep(STEP_FULLSCREEN);
                            }
                        })
                        .setNegativeButton("나중에", (d, w) -> proceedToPermStep(STEP_FULLSCREEN))
                        .show();
                break;
            }

            case STEP_FULLSCREEN: {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    proceedToPermStep(STEP_EXACT_ALARM);
                    return;
                }
                NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (nm == null || nm.canUseFullScreenIntent()) {
                    proceedToPermStep(STEP_EXACT_ALARM);
                    return;
                }
                new AlertDialog.Builder(this)
                        .setTitle("잠금화면 팝업 허용")
                        .setMessage("알람 시간에 잠금화면 위로 알림이 크게 뜨게 하려면\n"
                                + "'다른 앱 위에 표시' 권한이 필요해요.\n\n"
                                + "허용하지 않으면 상단 배너로만 표시돼요.")
                        .setPositiveButton("허용하러 가기", (d, w) -> {
                            nextPermStep = STEP_EXACT_ALARM;
                            settingsLauncher.launch(new Intent(
                                    Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                    Uri.parse("package:" + getPackageName())));
                        })
                        .setNegativeButton("나중에", (d, w) -> proceedToPermStep(STEP_EXACT_ALARM))
                        .show();
                break;
            }

            case STEP_EXACT_ALARM: {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
                AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
                if (am == null || am.canScheduleExactAlarms()) return;
                nextPermStep = -1; // 마지막 단계
                settingsLauncher.launch(
                        new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM));
                break;
            }
        }
    }

    // ── 메뉴 ────────────────────────────────────────────────────

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
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

    // ── 패치노트 ─────────────────────────────────────────────────

    private void checkAndShowWhatsNew() {
        String currentVersion;
        try {
            currentVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return;
        }
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        String lastSeen = prefs.getString("last_seen_version", "");
        if (currentVersion.equals(lastSeen)) return;

        prefs.edit().putString("last_seen_version", currentVersion).apply();
        if (lastSeen.isEmpty()) return; // 최초 설치는 생략

        String notes = getString(R.string.whats_new);
        if (notes.isEmpty()) return;

        new AlertDialog.Builder(this)
                .setTitle("v" + currentVersion + " 업데이트 내용")
                .setMessage(notes)
                .setPositiveButton("확인", null)
                .show();
    }
}
