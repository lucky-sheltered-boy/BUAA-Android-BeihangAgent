package com.example.beihangagent.model;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

@Dao
public interface CommentDao {
    @Insert
    void insert(Comment comment);

    @androidx.room.Transaction
    @Query("SELECT * FROM comments WHERE post_id = :postId ORDER BY timestamp ASC")
    LiveData<List<CommentWithUser>> getCommentsByPost(int postId);
}
