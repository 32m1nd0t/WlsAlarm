package com.example.wlsreminderapp;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface ReminderDao {

    @Query("SELECT * FROM reminders ORDER BY id")
    LiveData<List<Reminder>> getAll();

    @Query("SELECT * FROM reminders")
    List<Reminder> getAllOnce();

    @Insert
    long insert(Reminder reminder);

    @Delete
    void delete(Reminder reminder);

    @Update
    void update(Reminder reminder);

    @Query("UPDATE reminders SET lastCompletedDate = :date WHERE id = :id")
    void updateCompletedDate(int id, String date);
}