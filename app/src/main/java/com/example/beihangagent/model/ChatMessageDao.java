package com.example.beihangagent.model;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

@Dao
public interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE userId = :userId ORDER BY timestamp ASC")
    List<ChatMessage> getByUser(int userId);

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    List<ChatMessage> getByConversation(long conversationId);

    @Insert
    void insert(ChatMessage message);

    @Query("DELETE FROM chat_messages")
    void clearAll();

    @Query("DELETE FROM chat_messages WHERE userId = :userId")
    void clearForUser(int userId);
    
    @Query("DELETE FROM chat_messages WHERE conversationId = :conversationId")
    void deleteByConversation(long conversationId);

    @Query("SELECT * FROM chat_messages WHERE role = 'user' ORDER BY timestamp DESC LIMIT :limit")
    LiveData<List<ChatMessage>> observeRecentUserMessages(int limit);
    
    @Query("SELECT cm.* FROM chat_messages cm " +
           "INNER JOIN class_members cls ON cm.userId = cls.student_id " +
           "INNER JOIN classes c ON cls.class_id = c.classId " +
           "WHERE c.teacher_id = :teacherId AND cm.role = 'user' " +
           "ORDER BY cm.timestamp DESC LIMIT :limit")
    LiveData<List<ChatMessage>> observeRecentUserMessagesByTeacher(int teacherId, int limit);
    
    @Query("SELECT cm.* FROM chat_messages cm " +
           "INNER JOIN class_members cls ON cm.userId = cls.student_id " +
           "WHERE cls.class_id = :classId AND cm.role = 'user' " +
           "ORDER BY cm.timestamp DESC LIMIT :limit")
    LiveData<List<ChatMessage>> observeRecentUserMessagesByClass(int classId, int limit);
    
    @Query("SELECT COUNT(*) FROM chat_messages cm " +
           "INNER JOIN class_members cls ON cm.userId = cls.student_id " +
           "INNER JOIN classes c ON cls.class_id = c.classId " +
           "WHERE c.teacher_id = :teacherId AND cm.role = 'user'")
    int countStudentMessagesByTeacher(int teacherId);
}
