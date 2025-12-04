package com.example.beihangagent.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "notifications",
        foreignKeys = {
            @ForeignKey(entity = User.class, parentColumns = "uid", childColumns = "user_id", onDelete = ForeignKey.CASCADE)
        },
        indices = {@Index("user_id")})
public class Notification {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "user_id")
    public int userId; // Receiver

    @ColumnInfo(name = "sender_id")
    public int senderId; // Sender

    @ColumnInfo(name = "related_post_id")
    public int relatedPostId;

    public String type; // "like", "comment"

    public String message;
    
    public long timestamp;
    
    @ColumnInfo(name = "is_read")
    public boolean isRead;

    public Notification(int userId, int senderId, int relatedPostId, String type, String message, long timestamp) {
        this.userId = userId;
        this.senderId = senderId;
        this.relatedPostId = relatedPostId;
        this.type = type;
        this.message = message;
        this.timestamp = timestamp;
        this.isRead = false;
    }
}
