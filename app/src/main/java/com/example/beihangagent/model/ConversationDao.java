package com.example.beihangagent.model;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface ConversationDao {
    @Insert
    long insert(Conversation conversation);

    @Query("SELECT * FROM conversations WHERE userId = :userId ORDER BY timestamp DESC")
    LiveData<List<Conversation>> getConversations(int userId);

    @Query("SELECT * FROM conversations WHERE userId = :userId ORDER BY timestamp DESC")
    List<Conversation> getConversationsByUserId(int userId);

    @Query("SELECT * FROM conversations WHERE id = :id")
    Conversation getConversationById(long id);

    @Update
    void update(Conversation conversation);

    @Delete
    void delete(Conversation conversation);
    
    @Query("DELETE FROM conversations WHERE userId = :userId")
    void clearForUser(int userId);
}
