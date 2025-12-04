package com.example.beihangagent.util;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import com.example.beihangagent.model.UserProfile;
import com.example.beihangagent.model.ConversationRecord;
import java.util.List;

/**
 * 用户档案数据访问对象
 */
@Dao
public interface UserProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertProfile(UserProfile profile);

    @Update
    void updateProfile(UserProfile profile);

    @Query("SELECT * FROM user_profiles WHERE userId = :userId LIMIT 1")
    UserProfile getProfile(int userId);

    @Query("SELECT EXISTS(SELECT 1 FROM user_profiles WHERE userId = :userId)")
    boolean profileExists(int userId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertConversationRecord(ConversationRecord record);

    @Query("SELECT * FROM conversation_records WHERE user_id = :userId ORDER BY timestamp DESC LIMIT :limit")
    List<ConversationRecord> getRecentConversations(int userId, int limit);

    @Query("SELECT * FROM conversation_records WHERE user_id = :userId ORDER BY timestamp DESC")
    List<ConversationRecord> getAllConversations(int userId);

    @Query("SELECT COUNT(*) FROM conversation_records WHERE user_id = :userId AND timestamp > :since")
    int getConversationCountSince(int userId, long since);

    @Query("SELECT * FROM conversation_records WHERE user_id = :userId AND knowledge_area = :area ORDER BY timestamp DESC LIMIT :limit")
    List<ConversationRecord> getConversationsByArea(int userId, String area, int limit);

    @Query("SELECT knowledge_area, COUNT(*) as count FROM conversation_records WHERE user_id = :userId GROUP BY knowledge_area ORDER BY count DESC")
    List<KnowledgeAreaStat> getKnowledgeAreaStats(int userId);

    @Query("DELETE FROM conversation_records WHERE user_id = :userId AND timestamp < :before")
    void cleanOldConversations(int userId, long before);

    /**
     * 知识领域统计结果
     */
    class KnowledgeAreaStat {
        public String knowledge_area;
        public int count;
    }
}