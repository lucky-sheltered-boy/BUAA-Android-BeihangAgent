package com.example.beihangagent.model;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "chat_messages")
public class ChatMessage {
    public static final int TYPE_TEXT = 0;
    public static final int TYPE_CODE = 1;
    public static final int TYPE_COMPARISON = 2;

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String content;

    @NonNull
    public String role; // "user" or "assistant"

    public int type;

    public long timestamp;

    @ColumnInfo(name = "userId")
    public int userId;

    @ColumnInfo(name = "conversationId")
    public long conversationId;

    @Ignore
    public boolean isPending = false;

    public ChatMessage() {
        this.content = "";
        this.role = "user";
        this.timestamp = System.currentTimeMillis();
        this.userId = -1;
        this.conversationId = -1;
    }

    @Ignore
    public ChatMessage(@NonNull String content, @NonNull String role, int type, int userId, long conversationId) {
        this.content = content;
        this.role = role;
        this.type = type;
        this.timestamp = System.currentTimeMillis();
        this.userId = userId;
        this.conversationId = conversationId;
    }
}
