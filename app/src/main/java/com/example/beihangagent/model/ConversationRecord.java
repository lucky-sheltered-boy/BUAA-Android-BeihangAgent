package com.example.beihangagent.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 对话记录 - 用于分析用户提问模式和学习轨迹
 */
@Entity(tableName = "conversation_records")
public class ConversationRecord {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "user_id")
    public int userId;

    @ColumnInfo(name = "question")
    public String question;

    @ColumnInfo(name = "question_type")
    public String questionType; // "concept", "code", "debug", "theory", "practical"

    @ColumnInfo(name = "knowledge_area")
    public String knowledgeArea; // "Java", "算法", "数据库", "Android"等

    @ColumnInfo(name = "difficulty_level")
    public int difficultyLevel; // 1-5

    @ColumnInfo(name = "keywords")
    public String keywords; // JSON格式存储提取的关键词

    @ColumnInfo(name = "response_length")
    public int responseLength; // AI回答的字符数

    @ColumnInfo(name = "session_id")
    public String sessionId; // 同一会话的标识

    @ColumnInfo(name = "timestamp")
    public long timestamp;

    @ColumnInfo(name = "satisfaction_implied")
    public float satisfactionImplied; // 基于后续提问推断的满意度

    public ConversationRecord(int userId, String question, String sessionId) {
        this.userId = userId;
        this.question = question;
        this.sessionId = sessionId;
        this.timestamp = System.currentTimeMillis();
        this.satisfactionImplied = 0.5f; // 默认中性
        
        // 自动分析问题类型和难度
        analyzeQuestion();
    }

    /**
     * 自动分析问题类型和知识领域
     */
    private void analyzeQuestion() {
        String lowerQuestion = question.toLowerCase();
        
        // 分析问题类型
        if (lowerQuestion.contains("是什么") || lowerQuestion.contains("概念") || lowerQuestion.contains("定义")) {
            questionType = "concept";
        } else if (lowerQuestion.contains("代码") || lowerQuestion.contains("实现") || lowerQuestion.contains("编写")) {
            questionType = "code";
        } else if (lowerQuestion.contains("错误") || lowerQuestion.contains("bug") || lowerQuestion.contains("调试")) {
            questionType = "debug";
        } else if (lowerQuestion.contains("原理") || lowerQuestion.contains("为什么") || lowerQuestion.contains("理论")) {
            questionType = "theory";
        } else {
            questionType = "practical";
        }

        // 分析知识领域
        if (lowerQuestion.contains("java") || lowerQuestion.contains("面向对象")) {
            knowledgeArea = "Java";
        } else if (lowerQuestion.contains("算法") || lowerQuestion.contains("排序") || lowerQuestion.contains("搜索")) {
            knowledgeArea = "算法";
        } else if (lowerQuestion.contains("数据库") || lowerQuestion.contains("sql") || lowerQuestion.contains("mysql")) {
            knowledgeArea = "数据库";
        } else if (lowerQuestion.contains("android") || lowerQuestion.contains("安卓") || lowerQuestion.contains("移动开发")) {
            knowledgeArea = "Android";
        } else if (lowerQuestion.contains("网络") || lowerQuestion.contains("http") || lowerQuestion.contains("协议")) {
            knowledgeArea = "网络";
        } else if (lowerQuestion.contains("数据结构") || lowerQuestion.contains("链表") || lowerQuestion.contains("树")) {
            knowledgeArea = "数据结构";
        } else {
            knowledgeArea = "通用";
        }

        // 分析难度等级
        if (lowerQuestion.contains("基础") || lowerQuestion.contains("简单") || lowerQuestion.contains("入门")) {
            difficultyLevel = 1;
        } else if (lowerQuestion.contains("复杂") || lowerQuestion.contains("高级") || lowerQuestion.contains("深入")) {
            difficultyLevel = 5;
        } else if (lowerQuestion.contains("中级") || lowerQuestion.contains("进阶")) {
            difficultyLevel = 4;
        } else {
            // 基于问题长度和复杂词汇推断难度
            int wordCount = question.split("\\s+").length;
            if (wordCount > 20) {
                difficultyLevel = 4;
            } else if (wordCount > 10) {
                difficultyLevel = 3;
            } else {
                difficultyLevel = 2;
            }
        }

        // 提取关键词（简化版本）
        keywords = extractKeywords();
    }

    /**
     * 提取问题中的关键词
     */
    private String extractKeywords() {
        // 简化的关键词提取逻辑
        String[] techKeywords = {
            "java", "android", "数据库", "算法", "sql", "http", "json", 
            "界面", "布局", "网络", "异步", "线程", "内存", "性能",
            "list", "array", "map", "string", "class", "method", "function"
        };
        
        StringBuilder keywordBuilder = new StringBuilder("[");
        String lowerQuestion = question.toLowerCase();
        boolean first = true;
        
        for (String keyword : techKeywords) {
            if (lowerQuestion.contains(keyword)) {
                if (!first) keywordBuilder.append(",");
                keywordBuilder.append("\"").append(keyword).append("\"");
                first = false;
            }
        }
        
        keywordBuilder.append("]");
        return keywordBuilder.toString();
    }
}