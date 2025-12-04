package com.example.beihangagent.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "classes")
public class Class {
    @PrimaryKey(autoGenerate = true)
    public int classId;

    @ColumnInfo(name = "class_name")
    public String className;

    @ColumnInfo(name = "class_code")
    public String classCode; // 班级邀请码

    @ColumnInfo(name = "teacher_id")
    public int teacherId;

    @ColumnInfo(name = "created_at")
    public long createdAt;

    public Class(String className, String classCode, int teacherId) {
        this.className = className;
        this.classCode = classCode;
        this.teacherId = teacherId;
        this.createdAt = System.currentTimeMillis();
    }
}
