package com.example.wlsreminderapp;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.wlsreminderapp.databinding.ActivityInquiryBinding;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public class InquiryActivity extends AppCompatActivity {

    // ← 웹훅 URL을 여기에 붙여넣으세요
    private static final String WEBHOOK_URL = "https://discord.com/api/webhooks/1516500939299750011/bR66aSPttnvjjc6ZOGuqkScG4JMNLhQX5TBkekV9z4TehVx9-rUchbTUcNQzvXjMSJQw";

    private ActivityInquiryBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityInquiryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setTitle("문의하기");

        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        binding.btnSend.setOnClickListener(v -> {
            String msg = binding.etMessage.getText() != null
                    ? binding.etMessage.getText().toString().trim() : "";
            if (msg.isEmpty()) {
                Toast.makeText(this, "내용을 입력해 주세요", Toast.LENGTH_SHORT).show();
                return;
            }
            sendToDiscord(msg);
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void sendToDiscord(String message) {
        binding.btnSend.setEnabled(false);
        binding.btnSend.setText("전송 중...");

        String version = "알 수 없음";
        try {
            version = getPackageManager()
                    .getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException ignored) {}

        final String content =
                "📩 **문의 도착**\n" +
                "버전: `" + version + "`\n" +
                "───────────────\n" +
                message;

        Executors.newSingleThreadExecutor().execute(() -> {
            boolean success = false;
            try {
                JSONObject json = new JSONObject();
                json.put("content", content);
                byte[] body = json.toString().getBytes(StandardCharsets.UTF_8);

                HttpURLConnection conn =
                        (HttpURLConnection) new URL(WEBHOOK_URL).openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                try (OutputStream os = conn.getOutputStream()) { os.write(body); }
                int code = conn.getResponseCode();
                conn.disconnect();
                success = (code == 200 || code == 204);
            } catch (Exception ignored) {}

            final boolean ok = success;
            runOnUiThread(() -> {
                binding.btnSend.setEnabled(true);
                binding.btnSend.setText("전송");
                if (ok) {
                    Toast.makeText(this, "문의가 접수됐습니다. 감사합니다!", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    Toast.makeText(this, "전송 실패. 네트워크를 확인해 주세요.", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }
}
