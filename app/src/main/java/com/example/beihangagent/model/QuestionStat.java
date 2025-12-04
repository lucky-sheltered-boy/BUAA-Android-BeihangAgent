package com.example.beihangagent.model;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "question_stats", primaryKeys = {"topic", "userId"})
public class QuestionStat {

    @NonNull
    public String topic;

    @ColumnInfo(name = "count")
    public int count;
    
    @ColumnInfo(name = "userId")
    public int userId;

    public QuestionStat(@NonNull String topic, int count, int userId) {
        this.topic = topic;
        this.count = count;
        this.userId = userId;
    }
}

