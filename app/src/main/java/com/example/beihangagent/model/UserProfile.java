package com.example.beihangagent.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 用户个性化档案 - 记录用户学习特征和偏好
 */
@Entity(tableName = "user_profiles")
public class UserProfile {
    @PrimaryKey
    public int userId;

    // 学习风格偏好
    @ColumnInfo(name = "learning_style")
    public String learningStyle; // "visual", "theoretical", "practical", "mixed"

    // 主要知识领域（JSON格式存储）
    @ColumnInfo(name = "knowledge_areas")
    public String knowledgeAreas; // JSON: {"算法": 5, "数据结构": 8, "数据库": 3}

    // 难度偏好
    @ColumnInfo(name = "difficulty_preference")
    public int difficultyPreference; // 1-5: 1=基础，5=高深

    // 提问频率最高的主题（JSON格式）
    @ColumnInfo(name = "frequent_topics")
    public String frequentTopics; // JSON: ["Java基础", "算法分析", "数据库设计"]

    // 学习行为特征（JSON格式）
    @ColumnInfo(name = "behavior_patterns")
    public String behaviorPatterns; // JSON: {"session_length": 15.5, "question_frequency": 3.2}

    // 语言风格偏好
    @ColumnInfo(name = "communication_style")
    public String communicationStyle; // "formal", "casual", "detailed", "concise"

    // 错误类型倾向（JSON格式）
    @ColumnInfo(name = "common_mistakes")
    public String commonMistakes; // JSON: ["NullPointerException", "逻辑错误"]

    // 更新时间
    @ColumnInfo(name = "last_updated")
    public long lastUpdated;

    // 总对话次数
    @ColumnInfo(name = "total_conversations")
    public int totalConversations;

    public UserProfile(int userId) {
        this.userId = userId;
        this.learningStyle = "mixed";
        this.knowledgeAreas = "{}";
        this.difficultyPreference = 3;
        this.frequentTopics = "[]";
        this.behaviorPatterns = "{}";
        this.communicationStyle = "formal";
        this.commonMistakes = "[]";
        this.lastUpdated = System.currentTimeMillis();
        this.totalConversations = 0;
    }

    /**
     * 更新对话计数和时间戳
     */
    public void incrementConversations() {
        this.totalConversations++;
        this.lastUpdated = System.currentTimeMillis();
    }

    /**
     * 获取用户经验等级
     */
    public String getExperienceLevel() {
        if (totalConversations < 10) return "新手";
        if (totalConversations < 50) return "入门";
        if (totalConversations < 150) return "进阶";
        return "专家";
    }

    /**
     * 检查是否需要更新档案
     */
    public boolean needsUpdate() {
        long dayInMillis = 24 * 60 * 60 * 1000;
        return System.currentTimeMillis() - lastUpdated > dayInMillis;
    }
}