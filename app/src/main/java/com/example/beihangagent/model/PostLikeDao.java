package com.example.beihangagent.model;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

@Dao
public interface PostLikeDao {
    @Insert
    void insert(PostLike like);

    @Query("DELETE FROM post_likes WHERE post_id = :postId AND user_id = :userId")
    void delete(int postId, int userId);
}
