package com.example.beihangagent.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "users")
public class User {
    @PrimaryKey(autoGenerate = true)
    public int uid;

    @ColumnInfo(name = "username")
    public String username;

    @ColumnInfo(name = "password")
    public String password;

    @ColumnInfo(name = "role")
    public int role; // 0=Student, 1=Teacher

    @ColumnInfo(name = "preference")
    public String preference;

    @ColumnInfo(name = "name")
    public String name;

    @ColumnInfo(name = "avatar_path")
    public String avatarPath;

    @ColumnInfo(name = "avatar_type") 
    public Integer avatarType; // 改为Integer以支持可空值

    public User(String username, String password, int role) {
        this.username = username;
        this.password = password;
        this.role = role;
        this.preference = "";
        this.name = "";
        this.avatarPath = "";
        this.avatarType = 0;
    }
}
