package com.example.beihangagent.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "post_likes",
        foreignKeys = {
            @ForeignKey(entity = Post.class, parentColumns = "id", childColumns = "post_id", onDelete = ForeignKey.CASCADE),
            @ForeignKey(entity = User.class, parentColumns = "uid", childColumns = "user_id", onDelete = ForeignKey.CASCADE)
        },
        indices = {@Index("post_id"), @Index("user_id")})
public class PostLike {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "post_id")
    public int postId;

    @ColumnInfo(name = "user_id")
    public int userId;

    public PostLike(int postId, int userId) {
        this.postId = postId;
        this.userId = userId;
    }
}
