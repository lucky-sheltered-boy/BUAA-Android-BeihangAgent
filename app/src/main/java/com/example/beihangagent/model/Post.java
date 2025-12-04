package com.example.beihangagent.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "posts",
        foreignKeys = {
            @ForeignKey(entity = Class.class, parentColumns = "classId", childColumns = "class_id", onDelete = ForeignKey.CASCADE),
            @ForeignKey(entity = User.class, parentColumns = "uid", childColumns = "user_id", onDelete = ForeignKey.CASCADE)
        },
        indices = {@Index("class_id"), @Index("user_id")})
public class Post {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "class_id")
    public int classId;

    @ColumnInfo(name = "user_id")
    public int userId;

    public String title;
    public String content;
    public long timestamp;
    
    @ColumnInfo(name = "is_pinned")
    public boolean isPinned;

    public Post(int classId, int userId, String title, String content) {
        this.classId = classId;
        this.userId = userId;
        this.title = title;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
        this.isPinned = false;
    }
}
