package com.example.beihangagent.model;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface PostDao {
    @Insert
    long insert(Post post);

    @Update
    void update(Post post);

    @Delete
    void delete(Post post);

    @androidx.room.Transaction
    @Query("SELECT * FROM posts WHERE class_id = :classId ORDER BY is_pinned DESC, timestamp DESC")
    LiveData<List<PostWithUser>> getPostsByClass(int classId);

    @androidx.room.Transaction
    @Query("SELECT * FROM posts WHERE id = :postId")
    LiveData<PostWithUser> getPostById(int postId);
    
    @Query("SELECT * FROM posts WHERE id = :postId")
    Post getPostByIdSync(int postId);
    
    @Query("SELECT COUNT(*) FROM post_likes WHERE post_id = :postId")
    LiveData<Integer> getLikeCount(int postId);
    
    @Query("SELECT EXISTS(SELECT 1 FROM post_likes WHERE post_id = :postId AND user_id = :userId)")
    LiveData<Boolean> isLikedByUser(int postId, int userId);
}
