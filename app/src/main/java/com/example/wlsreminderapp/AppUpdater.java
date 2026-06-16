package com.example.wlsreminderapp;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import java.io.File;

public class AppUpdater {

    private static final String FILE_NAME = "WlsReminder_update.apk";

    public static void start(Context context, String downloadUrl) {
        // Android 8+: 알 수 없는 앱 설치 권한 확인
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.getPackageManager().canRequestPackageInstalls()) {
                Toast.makeText(context,
                        "'알 수 없는 앱 설치' 권한을 허용해주세요",
                        Toast.LENGTH_LONG).show();
                Intent intent = new Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + context.getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return;
            }
        }
        download(context, downloadUrl);
    }

    private static void download(Context context, String downloadUrl) {
        // 기존 파일 삭제
        File destFile = new File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), FILE_NAME);
        if (destFile.exists()) destFile.delete();

        DownloadManager dm =
                (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);

        DownloadManager.Request request =
                new DownloadManager.Request(Uri.parse(downloadUrl))
                        .setTitle("Wls리마인더 업데이트")
                        .setDescription("업데이트를 다운로드하고 있습니다...")
                        .setMimeType("application/vnd.android.package-archive")
                        .setNotificationVisibility(
                                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .setDestinationInExternalFilesDir(
                                context, Environment.DIRECTORY_DOWNLOADS, FILE_NAME);

        long downloadId = dm.enqueue(request);
        Toast.makeText(context, "다운로드를 시작합니다...", Toast.LENGTH_SHORT).show();

        // 다운로드 완료 감지 → 성공이면 설치, 실패면 Toast
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (id != downloadId) return;
                ctx.unregisterReceiver(this);

                DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
                android.database.Cursor cursor = dm.query(query);
                boolean success = false;
                if (cursor != null && cursor.moveToFirst()) {
                    int status = cursor.getInt(
                            cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                    success = (status == DownloadManager.STATUS_SUCCESSFUL);
                    cursor.close();
                }
                if (success) {
                    installApk(ctx);
                } else {
                    Toast.makeText(ctx, "다운로드 실패. 다시 시도해주세요.", Toast.LENGTH_SHORT).show();
                }
            }
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            context.registerReceiver(receiver,
                    new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                    Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(receiver,
                    new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        }
    }

    private static void installApk(Context context) {
        File apkFile = new File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), FILE_NAME);

        if (!apkFile.exists()) {
            Toast.makeText(context, "다운로드 실패. 다시 시도해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        Uri apkUri;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            apkUri = FileProvider.getUriForFile(context,
                    context.getPackageName() + ".provider", apkFile);
        } else {
            apkUri = Uri.fromFile(apkFile);
        }

        Intent installIntent = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(apkUri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        context.startActivity(installIntent);
    }
}