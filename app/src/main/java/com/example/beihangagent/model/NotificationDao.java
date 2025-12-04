package com.example.beihangagent.model;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

@Dao
public interface NotificationDao {
    @Insert
    void insert(Notification notification);

    @Query("SELECT * FROM notifications WHERE user_id = :userId ORDER BY is_read ASC, timestamp DESC")
    LiveData<List<Notification>> getNotificationsByUser(int userId);

    @Query("UPDATE notifications SET is_read = 1 WHERE user_id = :userId")
    void markAllAsRead(int userId);

    @Query("UPDATE notifications SET is_read = 1 WHERE id = :notificationId")
    void markAsRead(int notificationId);

    @Query("SELECT COUNT(*) FROM notifications WHERE user_id = :userId AND is_read = 0")
    LiveData<Integer> getUnreadCount(int userId);
}
