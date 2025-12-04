package com.example.beihangagent.util;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import com.example.beihangagent.model.UserProfile;
import com.example.beihangagent.model.ConversationRecord;

/**
 * 个性化分析器 - 分析用户学习模式并生成个性化建议
 */
public class PersonalizationAnalyzer {

    /**
     * 分析用户的学习风格
     * @param records 用户的对话记录
     * @return 学习风格类型
     */
    public static String analyzeLearningStyle(List<ConversationRecord> records) {
        if (records.isEmpty()) return "mixed";

        int conceptCount = 0;
        int codeCount = 0;
        int theoryCount = 0;
        int practicalCount = 0;

        for (ConversationRecord record : records) {
            switch (record.questionType) {
                case "concept":
                    conceptCount++;
                    break;
                case "code":
                    codeCount++;
                    break;
                case "theory":
                    theoryCount++;
                    break;
                case "practical":
                    practicalCount++;
                    break;
            }
        }

        // 确定主要学习风格
        int maxCount = Math.max(Math.max(conceptCount, codeCount), 
                               Math.max(theoryCount, practicalCount));

        if (maxCount == conceptCount && conceptCount > records.size() * 0.4) {
            return "visual";
        } else if (maxCount == theoryCount && theoryCount > records.size() * 0.4) {
            return "theoretical";
        } else if (maxCount == codeCount && codeCount > records.size() * 0.4) {
            return "practical";
        } else {
            return "mixed";
        }
    }

    /**
     * 分析用户的知识领域偏好
     * @param records 对话记录
     * @return JSON格式的知识领域统计
     */
    public static String analyzeKnowledgeAreas(List<ConversationRecord> records) {
        Map<String, Integer> areaCount = new HashMap<>();
        
        for (ConversationRecord record : records) {
            String area = record.knowledgeArea;
            areaCount.put(area, areaCount.getOrDefault(area, 0) + 1);
        }

        JSONObject result = new JSONObject();
        try {
            for (Map.Entry<String, Integer> entry : areaCount.entrySet()) {
                // 计算该领域的熟练度（基于提问次数和难度）
                int proficiency = Math.min(10, entry.getValue());
                result.put(entry.getKey(), proficiency);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return result.toString();
    }

    /**
     * 分析用户的难度偏好
     * @param records 对话记录
     * @return 平均难度等级
     */
    public static int analyzeDifficultyPreference(List<ConversationRecord> records) {
        if (records.isEmpty()) return 3;

        int totalDifficulty = 0;
        for (ConversationRecord record : records) {
            totalDifficulty += record.difficultyLevel;
        }

        return Math.round((float) totalDifficulty / records.size());
    }

    /**
     * 分析常见主题
     * @param records 对话记录
     * @return JSON格式的高频主题列表
     */
    public static String analyzeFrequentTopics(List<ConversationRecord> records) {
        Map<String, Integer> topicCount = new HashMap<>();
        
        for (ConversationRecord record : records) {
            // 组合问题类型和知识领域作为主题
            String topic = record.knowledgeArea + "-" + record.questionType;
            topicCount.put(topic, topicCount.getOrDefault(topic, 0) + 1);
        }

        // 选择前5个最频繁的主题
        List<String> frequentTopics = new ArrayList<>();
        topicCount.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(5)
                .forEach(entry -> frequentTopics.add(entry.getKey()));

        JSONArray result = new JSONArray();
        for (String topic : frequentTopics) {
            result.put(topic);
        }

        return result.toString();
    }

    /**
     * 分析用户的沟通风格偏好
     * @param records 对话记录
     * @return 沟通风格类型
     */
    public static String analyzeCommunicationStyle(List<ConversationRecord> records) {
        if (records.isEmpty()) return "formal";

        int totalLength = 0;
        int detailQuestions = 0;
        
        for (ConversationRecord record : records) {
            totalLength += record.question.length();
            if (record.question.length() > 50) {
                detailQuestions++;
            }
        }

        double avgLength = (double) totalLength / records.size();
        double detailRatio = (double) detailQuestions / records.size();

        if (avgLength > 80 && detailRatio > 0.6) {
            return "detailed";
        } else if (avgLength < 30) {
            return "concise";
        } else if (detailRatio > 0.4) {
            return "formal";
        } else {
            return "casual";
        }
    }

    /**
     * 生成个性化的AI提示词增强
     * @param profile 用户档案
     * @param currentQuestion 当前问题
     * @return 个性化的提示词
     */
    public static String generatePersonalizedPrompt(UserProfile profile, String currentQuestion) {
        StringBuilder prompt = new StringBuilder();
        
        // 基础角色定义
        prompt.append("你是一个北航智能学习助手，需要根据学生的个人特征提供个性化回答。");
        
        // 用户经验等级
        prompt.append("学生经验等级：").append(profile.getExperienceLevel()).append("。");
        
        // 学习风格适应
        switch (profile.learningStyle) {
            case "visual":
                prompt.append("学生偏好概念性学习，请多使用图表、示例和直观解释。");
                break;
            case "theoretical":
                prompt.append("学生偏好理论学习，请提供深入的原理解释和理论背景。");
                break;
            case "practical":
                prompt.append("学生偏好实践学习，请多提供代码示例和实际应用场景。");
                break;
            default:
                prompt.append("学生学习风格多样，请综合使用理论、示例和实践相结合的方式。");
                break;
        }
        
        // 沟通风格适应
        switch (profile.communicationStyle) {
            case "detailed":
                prompt.append("学生喜欢详细解释，请提供充分的背景信息和步骤说明。");
                break;
            case "concise":
                prompt.append("学生喜欢简洁回答，请直接切入要点，避免冗余信息。");
                break;
            case "casual":
                prompt.append("学生喜欢轻松的交流方式，请使用友好、平易近人的语调。");
                break;
            default:
                prompt.append("请使用正式但友好的语调回答。");
                break;
        }
        
        // 难度等级适应
        if (profile.difficultyPreference <= 2) {
            prompt.append("学生偏好基础内容，请从基本概念开始，避免过于复杂的细节。");
        } else if (profile.difficultyPreference >= 4) {
            prompt.append("学生能够处理高难度内容，可以涉及深入的技术细节和高级概念。");
        } else {
            prompt.append("学生适合中等难度内容，请平衡基础概念和进阶知识。");
        }
        
        // 知识领域偏好
        try {
            JSONObject knowledgeAreas = new JSONObject(profile.knowledgeAreas);
            if (knowledgeAreas.length() > 0) {
                String dominantArea = "";
                int maxScore = 0;
                
                var keys = knowledgeAreas.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    int score = knowledgeAreas.getInt(key);
                    if (score > maxScore) {
                        maxScore = score;
                        dominantArea = key;
                    }
                }
                
                if (!dominantArea.isEmpty() && maxScore > 3) {
                    prompt.append("学生在").append(dominantArea).append("领域较为熟悉，可以适当关联相关知识。");
                }
            }
        } catch (JSONException e) {
            // 忽略解析错误
        }
        
        return prompt.toString();
    }

    /**
     * 更新用户档案
     * @param profile 当前用户档案
     * @param records 最新的对话记录
     * @return 更新后的用户档案
     */
    public static UserProfile updateProfile(UserProfile profile, List<ConversationRecord> records) {
        profile.learningStyle = analyzeLearningStyle(records);
        profile.knowledgeAreas = analyzeKnowledgeAreas(records);
        profile.difficultyPreference = analyzeDifficultyPreference(records);
        profile.frequentTopics = analyzeFrequentTopics(records);
        profile.communicationStyle = analyzeCommunicationStyle(records);
        profile.incrementConversations();
        
        return profile;
    }
}