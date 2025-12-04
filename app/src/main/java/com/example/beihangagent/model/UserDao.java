package com.example.beihangagent.model;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

@Dao
public interface UserDao {
    @Query("SELECT * FROM users WHERE username = :username AND password = :password LIMIT 1")
    User login(String username, String password);

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    User checkUser(String username);

    @Query("SELECT * FROM users WHERE uid = :uid LIMIT 1")
    User getUserById(int uid);

    @Insert
    void insert(User user);

    @Update
    void updateUser(User user);
}
