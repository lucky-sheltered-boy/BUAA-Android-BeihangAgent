package com.example.beihangagent.viewmodel;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import com.example.beihangagent.model.AppDatabase;
import com.example.beihangagent.model.ChatMessage;
import com.example.beihangagent.model.ChatMessageDao;
import com.example.beihangagent.model.ChatRequest;
import com.example.beihangagent.model.ChatResponse;
import com.example.beihangagent.model.Conversation;
import com.example.beihangagent.model.ConversationDao;
import com.example.beihangagent.model.QuestionStatDao;
import com.example.beihangagent.network.RetrofitClient;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChatViewModel extends AndroidViewModel {

    public MutableLiveData<List<ChatMessage>> messages = new MutableLiveData<>(new ArrayList<>());
    public MutableLiveData<List<Conversation>> conversations = new MutableLiveData<>(new ArrayList<>());
    public MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    public MutableLiveData<String> error = new MutableLiveData<>();
    
    private final QuestionStatDao questionStatDao;
    private final ChatMessageDao chatMessageDao;
    private final ConversationDao conversationDao;
    
    private final ExecutorService statsExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService chatHistoryExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    private final String studentName;
    private final String studentPreference;
    private final int currentUserId;
    
    private long currentConversationId = -1;
    public MutableLiveData<Long> currentConversationIdLiveData = new MutableLiveData<>(-1L);
    private boolean isNewConversationMode = true;

    public ChatViewModel(@NonNull Application application) {
        super(application);
        AppDatabase database = AppDatabase.getDatabase(application);
        questionStatDao = database.questionStatDao();
        chatMessageDao = database.chatMessageDao();
        conversationDao = database.conversationDao();
        
        SharedPreferences sessionPrefs = application.getSharedPreferences("user_session", Context.MODE_PRIVATE);
        String name = sessionPrefs.getString("name", "");
        String username = sessionPrefs.getString("username", "");
        studentName = !TextUtils.isEmpty(name) && !name.trim().isEmpty() ? name : username;
        studentPreference = sessionPrefs.getString("user_preference", "");
        currentUserId = sessionPrefs.getInt("uid", -1);
        
        loadConversations();
    }

    public void loadConversations() {
        if (currentUserId == -1) return;
        chatHistoryExecutor.execute(() -> {
            List<Conversation> list = conversationDao.getConversationsByUserId(currentUserId);
            mainHandler.post(() -> {
                conversations.setValue(list);
                // Only switch to the first conversation if no conversation is currently selected AND not in new conversation mode
                if (!isNewConversationMode && list != null && !list.isEmpty() && currentConversationId == -1) {
                    switchConversation(list.get(0).id);
                }
            });
        });
    }

    public void createNewConversation(String title) {
        isNewConversationMode = true;
        currentConversationId = -1;
        currentConversationIdLiveData.setValue(-1L);
        messages.setValue(new ArrayList<>());
    }

    public void switchConversation(long conversationId) {
        if (currentConversationId == conversationId) return;
        isNewConversationMode = false;
        currentConversationId = conversationId;
        currentConversationIdLiveData.setValue(conversationId);
        loadChatHistory(conversationId);
    }
    
    public long getCurrentConversationId() {
        return currentConversationId;
    }

    private void loadChatHistory(long conversationId) {
        chatHistoryExecutor.execute(() -> {
            List<ChatMessage> history = chatMessageDao.getByConversation(conversationId);
            mainHandler.post(() -> messages.setValue(history != null ? history : new ArrayList<>()));
        });
    }

    public void sendToAi(String msg, String systemPrompt, String modelName) {
        if (currentUserId == -1) {
            error.setValue("用户信息缺失，请重新登录");
            return;
        }

        if (currentConversationId == -1) {
            chatHistoryExecutor.execute(() -> {
                Conversation newConv = new Conversation();
                newConv.userId = currentUserId;
                newConv.title = msg.length() > 20 ? msg.substring(0, 20) + "..." : msg;
                newConv.timestamp = System.currentTimeMillis();
                long id = conversationDao.insert(newConv);
                
                mainHandler.post(() -> {
                    currentConversationId = id;
                    currentConversationIdLiveData.setValue(id);
                    isNewConversationMode = false;
                    loadConversations();
                    sendToAiInternal(msg, systemPrompt, modelName);
                    generateConversationTitle(id, msg);
                });
            });
        } else {
            sendToAiInternal(msg, systemPrompt, modelName);
        }
    }

    private void sendToAiInternal(String msg, String systemPrompt, String modelName) {
        try {
            List<ChatMessage> currentMessages = messages.getValue();
            if (currentMessages == null) currentMessages = new ArrayList<>();
            else currentMessages = new ArrayList<>(currentMessages);

            ChatMessage userMessage = new ChatMessage(msg, "user", ChatMessage.TYPE_TEXT, currentUserId, currentConversationId);
            currentMessages.add(userMessage);
            
            // Add pending AI message
            ChatMessage pendingAiMessage = new ChatMessage("", "assistant", ChatMessage.TYPE_TEXT, currentUserId, currentConversationId);
            pendingAiMessage.isPending = true;
            currentMessages.add(pendingAiMessage);
            messages.setValue(currentMessages);
            persistMessage(userMessage);

            // Check if we need to generate title (if it's the first message)
            if (currentMessages.size() == 2) {
                generateConversationTitle(currentConversationId, msg);
            }

            trackQuestionStat(msg);
            isLoading.setValue(true);

            List<ChatRequest.Message> apiMessages = new ArrayList<>();
            if (systemPrompt != null && !systemPrompt.isEmpty()) {
                apiMessages.add(new ChatRequest.Message("system", systemPrompt));
            }

            String personalizationPrompt = buildPersonalizationPrompt(msg, currentMessages);
            if (!TextUtils.isEmpty(personalizationPrompt)) {
                apiMessages.add(new ChatRequest.Message("system", personalizationPrompt));
            }
            
            int start = Math.max(0, currentMessages.size() - 10);
            for (int i = start; i < currentMessages.size(); i++) {
                ChatMessage m = currentMessages.get(i);
                apiMessages.add(new ChatRequest.Message(m.role, m.content));
            }

            ChatRequest request = new ChatRequest(modelName, apiMessages);

            RetrofitClient.getInstance().getApiService().sendMessage(request).enqueue(new Callback<ChatResponse>() {
                @Override
                public void onResponse(Call<ChatResponse> call, Response<ChatResponse> response) {
                    isLoading.setValue(false);
                    if (response.isSuccessful() && response.body() != null && response.body().choices != null && !response.body().choices.isEmpty()) {
                        String aiContent = response.body().choices.get(0).message.content;
                        List<ChatMessage> updatedMessages = messages.getValue();
                        if (updatedMessages != null) {
                            updatedMessages = new ArrayList<>(updatedMessages);
                            // Remove pending message
                            for (int i = updatedMessages.size() - 1; i >= 0; i--) {
                                if (updatedMessages.get(i).isPending) {
                                    updatedMessages.remove(i);
                                    break;
                                }
                            }
                            int contentType = containsCodeBlock(aiContent) ? ChatMessage.TYPE_CODE : ChatMessage.TYPE_TEXT;
                            ChatMessage aiMessage = new ChatMessage(aiContent, "assistant", contentType, currentUserId, currentConversationId);
                            updatedMessages.add(aiMessage);
                            messages.setValue(updatedMessages);
                            persistMessage(aiMessage);
                        }
                    } else {
                        // Remove pending message on error
                        List<ChatMessage> updatedMessages = messages.getValue();
                        if (updatedMessages != null) {
                            updatedMessages = new ArrayList<>(updatedMessages);
                            for (int i = updatedMessages.size() - 1; i >= 0; i--) {
                                if (updatedMessages.get(i).isPending) {
                                    updatedMessages.remove(i);
                                    break;
                                }
                            }
                            messages.setValue(updatedMessages);
                        }
                        String errorMsg = "Error: " + response.code();
                        if (response.errorBody() != null) {
                            try {
                                errorMsg += " " + response.errorBody().string();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        error.setValue(errorMsg);
                    }
                }

                @Override
                public void onFailure(Call<ChatResponse> call, Throwable t) {
                    isLoading.setValue(false);
                    // Remove pending message on failure
                    List<ChatMessage> updatedMessages = messages.getValue();
                    if (updatedMessages != null) {
                        updatedMessages = new ArrayList<>(updatedMessages);
                        for (int i = updatedMessages.size() - 1; i >= 0; i--) {
                            if (updatedMessages.get(i).isPending) {
                                updatedMessages.remove(i);
                                break;
                            }
                        }
                        messages.setValue(updatedMessages);
                    }
                    
                    // Provide detailed error message
                    String errorMessage;
                    if (t instanceof java.net.SocketTimeoutException) {
                        errorMessage = "请求超时，请检查网络连接或稍后重试";
                    } else if (t instanceof java.net.ConnectException) {
                        errorMessage = "网络连接失败，请检查网络设置";
                    } else if (t instanceof java.net.UnknownHostException) {
                        errorMessage = "无法连接到服务器，请检查网络";
                    } else {
                        errorMessage = "网络错误: " + t.getMessage();
                    }
                    
                    // Log detailed error information
                    android.util.Log.e("ChatViewModel", "API call failed", t);
                    android.util.Log.e("ChatViewModel", "Error type: " + t.getClass().getSimpleName());
                    android.util.Log.e("ChatViewModel", "Error message: " + t.getMessage());
                    
                    error.setValue(errorMessage);
                }
            });
        } catch (Exception e) {
            isLoading.setValue(false);
            error.setValue("App Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void sendComparisonToAi(String msg, String systemPrompt, List<String> models) {
        if (currentUserId == -1) {
            error.setValue("用户信息缺失，请重新登录");
            return;
        }

        // Ensure conversation exists
        if (currentConversationId == -1) {
            chatHistoryExecutor.execute(() -> {
                Conversation newConv = new Conversation();
                newConv.userId = currentUserId;
                newConv.title = "对比: " + (msg.length() > 15 ? msg.substring(0, 15) + "..." : msg);
                newConv.timestamp = System.currentTimeMillis();
                long id = conversationDao.insert(newConv);
                
                mainHandler.post(() -> {
                    currentConversationId = id;
                    loadConversations();
                    sendComparisonInternal(msg, systemPrompt, models);
                    generateConversationTitle(id, msg);
                });
            });
        } else {
            sendComparisonInternal(msg, systemPrompt, models);
        }
    }

    private void sendComparisonInternal(String msg, String systemPrompt, List<String> models) {
        try {
            List<ChatMessage> currentMessages = messages.getValue();
            if (currentMessages == null) currentMessages = new ArrayList<>();
            else currentMessages = new ArrayList<>(currentMessages);

            // 1. Add User Message
            ChatMessage userMessage = new ChatMessage(msg, "user", ChatMessage.TYPE_TEXT, currentUserId, currentConversationId);
            currentMessages.add(userMessage);
            persistMessage(userMessage);

            // Check if we need to generate title (if it's the first message)
            if (currentMessages.size() == 1) {
                generateConversationTitle(currentConversationId, msg);
            }

            // 2. Add Placeholder Comparison Message
            JSONObject placeholderJson = new JSONObject();
            JSONArray comparisonsArray = new JSONArray();
            for (String model : models) {
                JSONObject item = new JSONObject();
                item.put("model", model);
                item.put("content", "");
                comparisonsArray.put(item);
            }
            placeholderJson.put("comparisons", comparisonsArray);
            
            ChatMessage comparisonMessage = new ChatMessage(placeholderJson.toString(), "assistant", ChatMessage.TYPE_COMPARISON, currentUserId, currentConversationId);
            comparisonMessage.isPending = true;
            currentMessages.add(comparisonMessage);
            messages.setValue(currentMessages);
            
            isLoading.setValue(true);

            // 3. Prepare Requests
            List<ChatRequest.Message> apiMessages = new ArrayList<>();
            if (systemPrompt != null && !systemPrompt.isEmpty()) {
                apiMessages.add(new ChatRequest.Message("system", systemPrompt));
            }
            apiMessages.add(new ChatRequest.Message("user", msg));

            // 4. Execute Parallel Requests
            statsExecutor.execute(() -> {
                int requestCount = models.size();
                CountDownLatch latch = new CountDownLatch(requestCount);
                String[] results = new String[requestCount];

                for (int i = 0; i < requestCount; i++) {
                    final int index = i;
                    String modelName = models.get(i);
                    ChatRequest request = new ChatRequest(modelName, apiMessages);
                    
                    RetrofitClient.getInstance().getApiService().sendMessage(request).enqueue(new Callback<ChatResponse>() {
                        @Override
                        public void onResponse(Call<ChatResponse> call, Response<ChatResponse> response) {
                            if (response.isSuccessful() && response.body() != null && !response.body().choices.isEmpty()) {
                                results[index] = response.body().choices.get(0).message.content;
                            } else {
                                results[index] = "Error: " + response.code();
                            }
                            latch.countDown();
                            updateComparisonProgress(comparisonMessage, index, results[index]);
                        }
                        @Override
                        public void onFailure(Call<ChatResponse> call, Throwable t) {
                            results[index] = "Network Error: " + t.getMessage();
                            latch.countDown();
                            updateComparisonProgress(comparisonMessage, index, results[index]);
                        }
                    });
                }

                try {
                    latch.await(); // Wait for all requests
                    // Final update and persist
                    mainHandler.post(() -> {
                        isLoading.setValue(false);
                        comparisonMessage.isPending = false;
                        List<ChatMessage> finalMessages = messages.getValue();
                        if (finalMessages != null) {
                            messages.setValue(new ArrayList<>(finalMessages));
                        }
                        persistMessage(comparisonMessage); // Save the final result
                    });
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });

        } catch (Exception e) {
            isLoading.setValue(false);
            error.setValue("Comparison Error: " + e.getMessage());
        }
    }

    private void updateComparisonProgress(ChatMessage message, int index, String content) {
        mainHandler.post(() -> {
            try {
                JSONObject json = new JSONObject(message.content);
                JSONArray comparisons = json.getJSONArray("comparisons");
                if (index >= 0 && index < comparisons.length()) {
                    JSONObject item = comparisons.getJSONObject(index);
                    item.put("content", content);
                }
                message.content = json.toString();
                
                // Trigger LiveData update to refresh UI
                List<ChatMessage> current = messages.getValue();
                messages.setValue(current); 
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private String buildPersonalizationPrompt(String latestUserMessage, List<ChatMessage> history) {
        String displayName = TextUtils.isEmpty(studentName) || studentName.trim().isEmpty() ? "这位同学" : studentName;
        String college = TextUtils.isEmpty(studentPreference) ? "" : studentPreference;
        
        // 提取用户消息中的代码
        String extractedCode = extractCode(latestUserMessage, history);
        
        // 使用新的个性化分析系统
        String codeStyle = com.example.beihangagent.util.PersonalizedGuidance.CodeStyleAnalyzer
            .analyzeStyle(extractedCode, latestUserMessage);
        String errorPattern = com.example.beihangagent.util.PersonalizedGuidance.ErrorAnalyzer
            .analyzeErrorPattern(latestUserMessage, extractedCode);
        
        // 如果没有检测到明显特征，使用原有的推理方法作为补充
        if (TextUtils.isEmpty(codeStyle)) {
            codeStyle = inferCodeStyle(latestUserMessage, history);
        }
        if (TextUtils.isEmpty(errorPattern)) {
            errorPattern = com.example.beihangagent.util.AnalysisUtils.inferErrorType(latestUserMessage);
        }

        boolean hasStyle = !TextUtils.isEmpty(codeStyle);
        boolean hasError = !TextUtils.isEmpty(errorPattern);
        boolean hasCollege = !TextUtils.isEmpty(college);
        
        if (!hasStyle && !hasError && "这位同学".equals(displayName) && !hasCollege) {
            return "";
        }

        // 生成个性化指导建议
        String personalizedGuidance = com.example.beihangagent.util.PersonalizedGuidance.GuidanceGenerator
            .generatePersonalizedGuidance(displayName, codeStyle, errorPattern, latestUserMessage);

        StringBuilder builder = new StringBuilder("请结合以下学生画像生成有针对性的指导。学生姓名：")
                .append(displayName);
        
        if (hasCollege) {
            builder.append("；所属学院：").append(college)
                   .append("（请结合该学院的专业特色和课程体系，提供更相关的学习建议和案例）");
        }
        
        if (hasStyle) {
            builder.append("；代码风格特征：").append(codeStyle);
        }
        if (hasError) {
            builder.append("；主要问题模式：").append(errorPattern);
        }
        builder.append("。\n\n参考个性化建议：\n").append(personalizedGuidance);
        builder.append("\n\n请基于以上分析，针对学生的具体问题给出专业指导，并在回答中称呼")
                .append(displayName).append("。");
        
        if (hasCollege) {
            builder.append("在回答时，可以适当结合").append(college)
                   .append("的专业背景，提及相关的专业课程、应用场景或行业前景，帮助学生更好地理解知识的实际价值。");
        }
        
        return builder.toString();
    }
    
    private String extractCode(String message, List<ChatMessage> history) {
        StringBuilder codeBuilder = new StringBuilder();
        
        // 从当前消息提取代码
        String[] lines = message.split("\n");
        boolean inCodeBlock = false;
        for (String line : lines) {
            if (line.trim().startsWith("```")) {
                inCodeBlock = !inCodeBlock;
                continue;
            }
            if (inCodeBlock || line.trim().matches(".*[{}();].*")) {
                codeBuilder.append(line).append("\n");
            }
        }
        
        // 从历史消息中获取相关代码（最近3条用户消息）
        if (history != null) {
            int count = 0;
            for (int i = history.size() - 1; i >= 0 && count < 3; i--) {
                ChatMessage msg = history.get(i);
                if ("user".equals(msg.role)) {
                    String[] historyLines = msg.content.split("\n");
                    boolean inHistoryCodeBlock = false;
                    for (String line : historyLines) {
                        if (line.trim().startsWith("```")) {
                            inHistoryCodeBlock = !inHistoryCodeBlock;
                            continue;
                        }
                        if (inHistoryCodeBlock || line.trim().matches(".*[{}();].*")) {
                            codeBuilder.append(line).append("\n");
                        }
                    }
                    count++;
                }
            }
        }
        
        return codeBuilder.toString();
    }

    private String inferCodeStyle(String message, List<ChatMessage> history) {
        if (TextUtils.isEmpty(message)) {
            return "";
        }
        String lower = message.toLowerCase();
        if (lower.contains("函数式") || lower.contains("functional")) {
            return "倾向函数式思维";
        }
        if (lower.contains("面向对象") || lower.contains("oop") || lower.contains("object-oriented")) {
            return "习惯面向对象建模";
        }
        if (lower.contains("简洁") || lower.contains("精炼") || lower.contains("concise")) {
            return "偏好简洁代码";
        }
        if (lower.contains("可读性") || lower.contains("readability")) {
            return "追求可读性优先";
        }
        if (lower.contains("性能") || lower.contains("performance")) {
            return "注重性能优化";
        }
        if (lower.contains("注释") || lower.contains("comment")) {
            return "需要更多注释来辅助理解";
        }
        if (lower.contains("命名") || lower.contains("命令式") || lower.contains("imperative")) {
            return "更习惯命令式编程";
        }

        // Look into last few user messages for hints
        if (history != null && !history.isEmpty()) {
            int inspected = 0;
            for (int i = history.size() - 1; i >= 0 && inspected < 5; i--) {
                ChatMessage m = history.get(i);
                if (!"user".equals(m.role)) {
                    continue;
                }
                inspected++;
                String contentLower = m.content == null ? "" : m.content.toLowerCase();
                if (contentLower.contains("重构") || contentLower.contains("refactor")) {
                    return "喜欢持续重构代码";
                }
                if (contentLower.contains("设计模式") || contentLower.contains("design pattern")) {
                    return "善用设计模式";
                }
            }
        }
        return "";
    }

    private boolean containsCodeBlock(String content) {
        if (TextUtils.isEmpty(content)) {
            return false;
        }
        return content.contains("```");
    }

    private void trackQuestionStat(String message) {
        if (questionStatDao == null || currentUserId == -1) {
            return;
        }
        final String topic = com.example.beihangagent.util.AnalysisUtils.classifyTopic(message);
        statsExecutor.execute(() -> questionStatDao.incrementTopic(topic, currentUserId));
    }

    private void persistMessage(ChatMessage chatMessage) {
        if (chatMessageDao == null || currentUserId == -1) {
            return;
        }
        if (chatMessage.userId == -1) {
            chatMessage.userId = currentUserId;
        }
        chatHistoryExecutor.execute(() -> chatMessageDao.insert(chatMessage));
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        statsExecutor.shutdownNow();
        chatHistoryExecutor.shutdownNow();
    }

    public void deleteConversation(Conversation conversation) {
        if (currentUserId == -1) return;
        chatHistoryExecutor.execute(() -> {
            // Delete messages first
            chatMessageDao.deleteByConversation(conversation.id);
            // Delete conversation
            conversationDao.delete(conversation);
            
            mainHandler.post(() -> {
                // If deleted conversation was the current one, switch to another or create new
                if (currentConversationId == conversation.id) {
                    currentConversationId = -1;
                    currentConversationIdLiveData.setValue(-1L);
                    messages.setValue(new ArrayList<>());
                }
                loadConversations();
            });
        });
    }

    public void renameConversation(long conversationId, String newTitle) {
        if (conversationId == -1) {
            // Create new conversation with this title
            if (currentUserId == -1) return;
            chatHistoryExecutor.execute(() -> {
                Conversation newConv = new Conversation();
                newConv.userId = currentUserId;
                newConv.title = newTitle;
                newConv.timestamp = System.currentTimeMillis();
                long id = conversationDao.insert(newConv);
                
                mainHandler.post(() -> {
                    currentConversationId = id;
                    currentConversationIdLiveData.setValue(id);
                    isNewConversationMode = false;
                    loadConversations();
                });
            });
        } else {
            chatHistoryExecutor.execute(() -> {
                Conversation conv = conversationDao.getConversationById(conversationId);
                if (conv != null) {
                    conv.title = newTitle;
                    conversationDao.update(conv);
                    mainHandler.post(this::loadConversations);
                }
            });
        }
    }

    public void deleteEmptyConversations() {
        if (currentUserId == -1) return;
        chatHistoryExecutor.execute(() -> {
            List<Conversation> allConversations = conversationDao.getConversationsByUserId(currentUserId);
            boolean changed = false;
            if (allConversations != null) {
                for (Conversation conv : allConversations) {
                    // Check if conversation has messages
                    List<ChatMessage> msgs = chatMessageDao.getByConversation(conv.id);
                    if (msgs == null || msgs.isEmpty()) {
                        // It's empty, check if it's a default "New Chat" or similar
                        if ("新对话".equals(conv.title) || "New Chat".equals(conv.title)) {
                            conversationDao.delete(conv);
                            changed = true;
                        }
                    }
                }
            }
            if (changed) {
                mainHandler.post(this::loadConversations);
            }
        });
    }

    private void generateConversationTitle(long conversationId, String userMessage) {
        // Use a lightweight request to generate title
        List<ChatRequest.Message> messages = new ArrayList<>();
        
        // Check if this is likely a code optimization/review request
        boolean isCodeOptimize = userMessage.toLowerCase().contains("class ") || 
                               userMessage.toLowerCase().contains("public ") ||
                               userMessage.toLowerCase().contains("private ") ||
                               userMessage.toLowerCase().contains("function ") ||
                               userMessage.toLowerCase().contains("def ") ||
                               userMessage.toLowerCase().contains("#include") ||
                               userMessage.toLowerCase().contains("package ");
        
        String systemPrompt;
        if (isCodeOptimize) {
            systemPrompt = "分析这段代码的主要功能，生成一个简短的中文标题（10-15字），格式如：'代码优化：Java类设计'、'代码审查：用户模型'等。";
        } else {
            systemPrompt = "将用户消息总结为简短的中文对话标题（10-15字），不要包含引号。";
        }
        
        messages.add(new ChatRequest.Message("system", systemPrompt));
        messages.add(new ChatRequest.Message("user", userMessage));
        
        ChatRequest request = new ChatRequest("gpt-4o-mini", messages); // Use mini model for speed if available, or gpt-4o
        
        RetrofitClient.getInstance().getApiService().sendMessage(request).enqueue(new Callback<ChatResponse>() {
            @Override
            public void onResponse(Call<ChatResponse> call, Response<ChatResponse> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().choices.isEmpty()) {
                    String title = response.body().choices.get(0).message.content.trim();
                    // Remove quotes if present
                    title = title.replace("\"", "").replace("'", "");
                    if (title.length() > 25) title = title.substring(0, 25) + "...";
                    
                    final String finalTitle = title;
                    chatHistoryExecutor.execute(() -> {
                        Conversation conv = conversationDao.getConversationById(conversationId);
                        if (conv != null) {
                            conv.title = finalTitle;
                            conversationDao.update(conv);
                            mainHandler.post(() -> loadConversations());
                        }
                    });
                }
            }
            @Override
            public void onFailure(Call<ChatResponse> call, Throwable t) {
                // Ignore failure, keep default title
            }
        });
    }
}
