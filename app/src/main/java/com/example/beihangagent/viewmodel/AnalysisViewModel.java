package com.example.beihangagent.viewmodel;

import android.app.Application;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import android.text.TextUtils;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import com.example.beihangagent.model.AppDatabase;
import com.example.beihangagent.model.AnalysisHotspot;
import com.example.beihangagent.model.ChatMessage;
import com.example.beihangagent.model.QuestionStat;
import com.example.beihangagent.model.Class;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AnalysisViewModel extends AndroidViewModel {

    private final AppDatabase database;
    private final MutableLiveData<Integer> selectedClassId;
    private final LiveData<List<QuestionStat>> stats;
    private final LiveData<List<AnalysisHotspot>> hotspots;
    private final LiveData<List<Class>> teacherClasses;
    private final int currentUserId;
    private final int userRole;
    private static final int RECENT_LIMIT = 50;
    private static final int ALL_CLASSES = -1;

    public AnalysisViewModel(@NonNull Application application) {
        super(application);
        database = AppDatabase.getDatabase(application);
        
        // Get current user info from SharedPreferences
        SharedPreferences prefs = application.getSharedPreferences("user_session", application.MODE_PRIVATE);
        currentUserId = prefs.getInt("uid", -1);
        userRole = prefs.getInt("role", 0); // 0=Student, 1=Teacher
        
        selectedClassId = new MutableLiveData<>(ALL_CLASSES); // Default to all classes
        
        if (userRole == 1 && currentUserId != -1) {
            // Teacher: load their classes and set up dynamic filtering
            teacherClasses = database.classDao().getClassesByTeacher(currentUserId);
            
            // Use switchMap to dynamically switch data sources based on selected class
            stats = Transformations.switchMap(selectedClassId, classId -> {
                if (classId == null || classId == ALL_CLASSES) {
                    // Show all classes' data
                    return database.questionStatDao().observeByTeacher(currentUserId);
                } else {
                    // Show specific class data
                    return database.questionStatDao().observeByClass(classId);
                }
            });
            
            hotspots = Transformations.switchMap(selectedClassId, classId -> {
                if (classId == null || classId == ALL_CLASSES) {
                    // Show all classes' data
                    return Transformations.map(
                        database.chatMessageDao().observeRecentUserMessagesByTeacher(currentUserId, RECENT_LIMIT),
                        this::buildHotspots
                    );
                } else {
                    // Show specific class data
                    return Transformations.map(
                        database.chatMessageDao().observeRecentUserMessagesByClass(classId, RECENT_LIMIT),
                        this::buildHotspots
                    );
                }
            });
        } else {
            // Student: show all data (original behavior)
            teacherClasses = new MutableLiveData<>(Collections.emptyList());
            stats = database.questionStatDao().observeAll();
            LiveData<List<ChatMessage>> recentMessages = database.chatMessageDao().observeRecentUserMessages(RECENT_LIMIT);
            hotspots = Transformations.map(recentMessages, this::buildHotspots);
        }
    }

    public LiveData<List<QuestionStat>> getStats() {
        return stats;
    }

    public LiveData<List<AnalysisHotspot>> getHotspots() {
        return hotspots;
    }
    
    public LiveData<List<Class>> getTeacherClasses() {
        return teacherClasses;
    }
    
    public boolean isTeacher() {
        return userRole == 1;
    }
    
    public void selectClass(int classId) {
        selectedClassId.setValue(classId);
    }
    
    public void selectAllClasses() {
        selectedClassId.setValue(ALL_CLASSES);
    }

    private List<AnalysisHotspot> buildHotspots(List<ChatMessage> messages) {
        android.util.Log.d("AnalysisViewModel", "buildHotspots called with " + 
            (messages != null ? messages.size() : "null") + " messages");
        
        if (messages == null || messages.isEmpty()) {
            android.util.Log.d("AnalysisViewModel", "No messages found, returning empty list");
            return Collections.emptyList();
        }

        Map<String, HotspotAgg> errorMap = new HashMap<>();
        Map<String, HotspotAgg> topicMap = new HashMap<>();

        for (ChatMessage message : messages) {
            if (message == null || TextUtils.isEmpty(message.content)) {
                continue;
            }
            android.util.Log.d("AnalysisViewModel", "Processing message: " + message.content);
            
            String error = com.example.beihangagent.util.AnalysisUtils.inferErrorType(message.content);
            if (!TextUtils.isEmpty(error)) {
                android.util.Log.d("AnalysisViewModel", "Found error type: " + error);
                mergeHotspot(errorMap, error, message);
                continue;
            }
            String topic = com.example.beihangagent.util.AnalysisUtils.classifyTopic(message.content);
            android.util.Log.d("AnalysisViewModel", "Found topic: " + topic);
            if (!TextUtils.isEmpty(topic) && !"其他".equals(topic)) {
                mergeHotspot(topicMap, topic, message);
            }
        }

        List<AnalysisHotspot> result = new ArrayList<>();
        for (HotspotAgg agg : errorMap.values()) {
            result.add(new AnalysisHotspot(agg.label, agg.sample, agg.count, agg.lastSeen, AnalysisHotspot.Type.ERROR));
        }
        for (HotspotAgg agg : topicMap.values()) {
            result.add(new AnalysisHotspot(agg.label, agg.sample, agg.count, agg.lastSeen, AnalysisHotspot.Type.TOPIC));
        }

        result.sort(Comparator.comparingInt((AnalysisHotspot h) -> h.count).reversed()
            .thenComparing(Comparator.comparingLong((AnalysisHotspot h) -> h.lastSeen).reversed()));

        if (result.size() > 5) {
            return new ArrayList<>(result.subList(0, 5));
        }
        return result;
    }

    private void mergeHotspot(Map<String, HotspotAgg> map, String label, ChatMessage message) {
        HotspotAgg agg = map.get(label);
        if (agg == null) {
            agg = new HotspotAgg(label);
            map.put(label, agg);
        }
        agg.count += 1;
        if (message.timestamp > agg.lastSeen) {
            agg.lastSeen = message.timestamp;
            agg.sample = buildSample(message.content);
        }
    }

    private String buildSample(String content) {
        if (TextUtils.isEmpty(content)) {
            return "";
        }
        String singleLine = content.replaceAll("\\s+", " ").trim();
        if (singleLine.length() <= 60) {
            return singleLine;
        }
        return singleLine.substring(0, 57) + "...";
    }

    private static class HotspotAgg {
        final String label;
        int count;
        long lastSeen;
        String sample;

        HotspotAgg(String label) {
            this.label = label;
            this.count = 0;
            this.lastSeen = 0L;
            this.sample = "";
        }
    }
}
