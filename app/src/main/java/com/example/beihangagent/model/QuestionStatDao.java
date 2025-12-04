package com.example.beihangagent.model;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import java.util.List;

@Dao
public abstract class QuestionStatDao {

    @Query("SELECT topic, SUM(count) as count, 0 as userId FROM question_stats GROUP BY topic ORDER BY count DESC")
    public abstract LiveData<List<QuestionStat>> observeAll();
    
    @Query("SELECT qs.topic, SUM(qs.count) as count, 0 as userId FROM question_stats qs " +
           "INNER JOIN class_members cm ON qs.userId = cm.student_id " +
           "INNER JOIN classes c ON cm.class_id = c.classId " +
           "WHERE c.teacher_id = :teacherId " +
           "GROUP BY qs.topic ORDER BY count DESC")
    public abstract LiveData<List<QuestionStat>> observeByTeacher(int teacherId);
    
    @Query("SELECT qs.topic, SUM(qs.count) as count, 0 as userId FROM question_stats qs " +
           "INNER JOIN class_members cm ON qs.userId = cm.student_id " +
           "WHERE cm.class_id = :classId " +
           "GROUP BY qs.topic ORDER BY count DESC")
    public abstract LiveData<List<QuestionStat>> observeByClass(int classId);
    
    @Query("SELECT * FROM question_stats WHERE userId = :userId ORDER BY count DESC")
    public abstract List<QuestionStat> getStatsByUser(int userId);

    @Query("SELECT * FROM question_stats WHERE topic = :topic AND userId = :userId LIMIT 1")
    protected abstract QuestionStat findByTopicAndUser(String topic, int userId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract void upsert(QuestionStat stat);

    @Transaction
    public void incrementTopic(String topic, int userId) {
        QuestionStat current = findByTopicAndUser(topic, userId);
        int next = current == null ? 1 : current.count + 1;
        upsert(new QuestionStat(topic, next, userId));
    }
}
