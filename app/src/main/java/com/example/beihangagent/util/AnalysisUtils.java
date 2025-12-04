package com.example.beihangagent.util;

import android.text.TextUtils;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AnalysisUtils {

    public static String classifyTopic(String message) {
        if (TextUtils.isEmpty(message)) {
            return "其他";
        }
        String lower = message.toLowerCase(Locale.ROOT);
        
        // Languages
        if (lower.contains("java")) return "Java";
        if (lower.contains("python")) return "Python";
        if (lower.contains("c++") || lower.contains("cpp")) return "C++";
        if (lower.contains("kotlin")) return "Kotlin";
        if (lower.contains("sql")) return "SQL";
        
        // Android
        if (lower.contains("android") || lower.contains("activity") || lower.contains("fragment") || lower.contains("view") || lower.contains("layout")) return "Android";
        
        // CS Concepts
        if (lower.contains("算法") || lower.contains("algorithm") || lower.contains("sort") || lower.contains("search")) return "算法";
        if (lower.contains("数据库") || lower.contains("database") || lower.contains("db")) return "数据库";
        if (lower.contains("网络") || lower.contains("network") || lower.contains("http") || lower.contains("tcp")) return "计算机网络";
        if (lower.contains("os") || lower.contains("操作系统") || lower.contains("thread") || lower.contains("process")) return "操作系统";
        
        // Programming Basics
        if (lower.contains("class") || lower.contains("object") || lower.contains("oop") || lower.contains("面向对象")) return "面向对象";
        if (lower.contains("array") || lower.contains("list") || lower.contains("map") || lower.contains("set") || lower.contains("collection")) return "数据结构";
        if (lower.contains("loop") || lower.contains("for") || lower.contains("while") || lower.contains("if")) return "基础语法";
        
        return "其他";
    }

    public static String inferErrorType(String message) {
        if (TextUtils.isEmpty(message)) {
            return "";
        }
        String lower = message.toLowerCase(Locale.ROOT);
        
        if (lower.contains("nullpointer") || lower.contains("空指针")) return "NullPointerException";
        if (lower.contains("indexoutofbounds") || lower.contains("数组越界")) return "IndexOutOfBounds";
        if (lower.contains("stackoverflow") || lower.contains("栈溢出")) return "StackOverflowError";
        if (lower.contains("classcast") || lower.contains("类型转换")) return "ClassCastException";
        if (lower.contains("numberformat") || lower.contains("数字格式")) return "NumberFormatException";
        if (lower.contains("timeout") || lower.contains("超时")) return "Timeout";
        if (lower.contains("deadlock") || lower.contains("死锁")) return "Deadlock";
        if (lower.contains("memory leak") || lower.contains("内存泄漏") || lower.contains("oom")) return "Memory Leak";
        
        Matcher matcher = Pattern.compile("[A-Za-z0-9_]+Exception").matcher(message);
        if (matcher.find()) {
            return matcher.group();
        }
        
        Matcher errorMatcher = Pattern.compile("[A-Za-z0-9_]+Error").matcher(message);
        if (errorMatcher.find()) {
            return errorMatcher.group();
        }
        
        if (lower.contains("error") || lower.contains("fail") || lower.contains("crash") || lower.contains("崩溃") || lower.contains("报错")) {
            return "Runtime Error";
        }
        
        return "";
    }
}
