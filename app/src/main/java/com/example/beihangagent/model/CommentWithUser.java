package com.example.beihangagent.model;

import androidx.room.Embedded;
import androidx.room.Relation;

public class CommentWithUser {
    @Embedded
    public Comment comment;

    @Relation(
        parentColumn = "user_id",
        entityColumn = "uid"
    )
    public User user;
}
