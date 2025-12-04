package com.example.beihangagent.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "class_members",
        foreignKeys = {
            @ForeignKey(entity = Class.class,
                    parentColumns = "classId",
                    childColumns = "class_id",
                    onDelete = ForeignKey.CASCADE),
            @ForeignKey(entity = User.class,
                    parentColumns = "uid",
                    childColumns = "student_id",
                    onDelete = ForeignKey.CASCADE)
        },
        indices = {@Index("class_id"), @Index("student_id")})
public class ClassMember {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "class_id")
    public int classId;

    @ColumnInfo(name = "student_id")
    public int studentId;

    @ColumnInfo(name = "joined_at")
    public long joinedAt;

    public ClassMember(int classId, int studentId) {
        this.classId = classId;
        this.studentId = studentId;
        this.joinedAt = System.currentTimeMillis();
    }
}
