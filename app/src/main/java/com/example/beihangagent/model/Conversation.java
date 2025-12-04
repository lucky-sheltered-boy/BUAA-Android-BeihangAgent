package com.example.beihangagent.model;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "conversations")
public class Conversation {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public int userId;
    public String title;
    public long timestamp;

    public Conversation() {
    }

    @Ignore
    public Conversation(int userId, String title) {
        this.userId = userId;
        this.title = title;
        this.timestamp = System.currentTimeMillis();
    }
}
