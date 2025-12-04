package com.example.beihangagent.model;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

@Dao
public interface ClassDao {
    
    @Insert
    long insertClass(Class classEntity);

    @Query("SELECT * FROM classes WHERE teacher_id = :teacherId ORDER BY created_at DESC")
    LiveData<List<Class>> getClassesByTeacher(int teacherId);

    @Query("SELECT * FROM classes WHERE class_code = :classCode LIMIT 1")
    Class getClassByCode(String classCode);

    @Query("SELECT * FROM classes WHERE classId = :classId")
    Class getClassById(int classId);

    @Query("DELETE FROM classes WHERE classId = :classId")
    void deleteClass(int classId);

    @Insert
    void insertMember(ClassMember member);

    @Query("SELECT * FROM class_members WHERE class_id = :classId AND student_id = :studentId LIMIT 1")
    ClassMember getMembership(int classId, int studentId);

    @Query("SELECT u.* FROM users u " +
           "INNER JOIN class_members cm ON u.uid = cm.student_id " +
           "WHERE cm.class_id = :classId ORDER BY u.username")
    LiveData<List<User>> getStudentsByClass(int classId);

    @Query("SELECT c.* FROM classes c " +
           "INNER JOIN class_members cm ON c.classId = cm.class_id " +
           "WHERE cm.student_id = :studentId ORDER BY c.created_at DESC")
    LiveData<List<Class>> getClassesByStudent(int studentId);

    @Query("DELETE FROM class_members WHERE class_id = :classId AND student_id = :studentId")
    void removeMember(int classId, int studentId);

    @Query("SELECT COUNT(*) FROM class_members WHERE class_id = :classId")
    LiveData<Integer> getClassMemberCount(int classId);

    // 同步方法用于计算活跃天数
    @Query("SELECT * FROM classes WHERE teacher_id = :teacherId ORDER BY created_at DESC")
    List<Class> getClassesByTeacherSync(int teacherId);

    @Query("SELECT * FROM class_members WHERE class_id = :classId")
    List<ClassMember> getClassMembersSync(int classId);
}
