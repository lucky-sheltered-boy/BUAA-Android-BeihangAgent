package com.example.beihangagent.model;

import androidx.room.Embedded;
import androidx.room.Relation;

public class PostWithUser {
    @Embedded
    public Post post;

    @Relation(
        parentColumn = "user_id",
        entityColumn = "uid"
    )
    public User user;
}
