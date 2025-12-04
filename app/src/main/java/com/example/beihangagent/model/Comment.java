package com.example.beihangagent.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "comments",
        foreignKeys = {
            @ForeignKey(entity = Post.class, parentColumns = "id", childColumns = "post_id", onDelete = ForeignKey.CASCADE),
            @ForeignKey(entity = User.class, parentColumns = "uid", childColumns = "user_id", onDelete = ForeignKey.CASCADE)
        },
        indices = {@Index("post_id"), @Index("user_id")})
public class Comment {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "post_id")
    public int postId;

    @ColumnInfo(name = "user_id")
    public int userId;

    public String content;
    public long timestamp;

    public Comment(int postId, int userId, String content) {
        this.postId = postId;
        this.userId = userId;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
    }
}
