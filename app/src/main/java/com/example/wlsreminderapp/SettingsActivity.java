package com.example.wlsreminderapp;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import androidx.appcompat.app.AppCompatActivity;
import com.example.wlsreminderapp.databinding.ActivitySettingsBinding;

public class SettingsActivity extends AppCompatActivity {

    private ActivitySettingsBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("설정");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // 현재 값 반영
        boolean nagOn = NagSettings.isEnabled(this);
        binding.swNagEnabled.setChecked(nagOn);
        binding.swNagSound.setChecked(NagSettings.isSound(this));
        binding.etInterval.setText(String.valueOf(NagSettings.getIntervalMin(this)));
        setSubOptionsEnabled(nagOn);

        // 반복 재알림 on/off
        binding.swNagEnabled.setOnCheckedChangeListener((b, checked) -> {
            NagSettings.setEnabled(this, checked);
            setSubOptionsEnabled(checked); // 꺼지면 하위 옵션도 비활성
        });

        // 재알림 소리 on/off
        binding.swNagSound.setOnCheckedChangeListener((b, checked) ->
                NagSettings.setSound(this, checked));

        // 재알림 간격 직접 입력 (입력 즉시 저장, 범위는 NagSettings에서 보정)
        binding.etInterval.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                try {
                    NagSettings.setIntervalMin(SettingsActivity.this,
                            Integer.parseInt(s.toString().trim()));
                } catch (NumberFormatException ignored) {
                    // 빈 칸/잘못된 입력은 무시 (직전 값 유지, onPause에서 정리)
                }
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 빈 칸이거나 범위 밖이면 저장된 정상값으로 표시 정리
        binding.etInterval.setText(String.valueOf(NagSettings.getIntervalMin(this)));
    }

    private void setSubOptionsEnabled(boolean enabled) {
        binding.swNagSound.setEnabled(enabled);
        binding.etInterval.setEnabled(enabled);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
