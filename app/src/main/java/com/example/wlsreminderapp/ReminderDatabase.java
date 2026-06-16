package com.example.wlsreminderapp;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {Reminder.class}, version = 3)
public abstract class ReminderDatabase extends RoomDatabase {
    public abstract ReminderDao reminderDao();

    private static volatile ReminderDatabase INSTANCE;

    // 버전 1 → 2: hour/minute 컬럼을 times 문자열로 변환
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            // 새 구조의 테이블 생성
            db.execSQL("CREATE TABLE reminders_new (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "name TEXT, " +
                    "description TEXT, " +
                    "times TEXT, " +
                    "isEnabled INTEGER NOT NULL DEFAULT 1)");
            // 기존 데이터 복사 (hour:minute → "HH:MM" 형식으로)
            db.execSQL("INSERT INTO reminders_new (id, name, description, times, isEnabled) " +
                    "SELECT id, name, description, " +
                    "printf('%02d:%02d', hour, minute), isEnabled FROM reminders");
            db.execSQL("DROP TABLE reminders");
            db.execSQL("ALTER TABLE reminders_new RENAME TO reminders");
        }
    };

    // 버전 2 → 3: days 컬럼 추가 (기본값: 매일)
    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE reminders ADD COLUMN days TEXT DEFAULT '1,2,3,4,5,6,7'");
        }
    };

    public static ReminderDatabase get(Context context) {
        if (INSTANCE == null) {
            synchronized (ReminderDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    ReminderDatabase.class,
                                    "reminder_db"
                            )
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3) // 데이터 보존 마이그레이션
                            // fallbackToDestructiveMigration() 제거!
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}