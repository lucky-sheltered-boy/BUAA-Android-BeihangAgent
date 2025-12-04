package com.example.beihangagent.util;

public class PersonalizedGuidance {
    
    /**
     * 分析学生代码风格特征
     */
    public static class CodeStyleAnalyzer {
        
        public static String analyzeStyle(String code, String userMessage) {
            StringBuilder styleFeatures = new StringBuilder();
            
            // 命名风格分析
            if (containsCamelCase(code)) {
                styleFeatures.append("使用驼峰命名法，");
            } else if (containsSnakeCase(code)) {
                styleFeatures.append("偏好下划线命名，");
            }
            
            // 代码结构分析
            if (hasLongFunctions(code)) {
                styleFeatures.append("习惯编写较长函数，建议拆分，");
            }
            
            if (hasDeepNesting(code)) {
                styleFeatures.append("代码嵌套较深，可考虑早返回模式，");
            }
            
            // 注释习惯
            double commentDensity = calculateCommentDensity(code);
            if (commentDensity < 0.1) {
                styleFeatures.append("注释较少，建议增加关键逻辑说明，");
            } else if (commentDensity > 0.3) {
                styleFeatures.append("注释详细，保持良好习惯，");
            }
            
            return styleFeatures.length() > 0 ? 
                styleFeatures.substring(0, styleFeatures.length() - 1) : "";
        }
        
        private static boolean containsCamelCase(String code) {
            return code.matches(".*[a-z][A-Z].*");
        }
        
        private static boolean containsSnakeCase(String code) {
            return code.matches(".*[a-z]_[a-z].*");
        }
        
        private static boolean hasLongFunctions(String code) {
            String[] lines = code.split("\n");
            int functionLineCount = 0;
            boolean inFunction = false;
            
            for (String line : lines) {
                if (line.trim().matches(".*(public|private|protected).*\\(.*\\).*\\{.*") ||
                    line.trim().matches(".*def\\s+\\w+\\s*\\(.*")) {
                    inFunction = true;
                    functionLineCount = 1;
                } else if (inFunction && line.trim().equals("}")) {
                    if (functionLineCount > 20) {
                        return true;
                    }
                    inFunction = false;
                } else if (inFunction) {
                    functionLineCount++;
                }
            }
            return false;
        }
        
        private static boolean hasDeepNesting(String code) {
            String[] lines = code.split("\n");
            int maxNesting = 0;
            int currentNesting = 0;
            
            for (String line : lines) {
                long openBraces = line.chars().filter(ch -> ch == '{').count();
                long closeBraces = line.chars().filter(ch -> ch == '}').count();
                currentNesting += (openBraces - closeBraces);
                maxNesting = Math.max(maxNesting, currentNesting);
            }
            
            return maxNesting > 4;
        }
        
        private static double calculateCommentDensity(String code) {
            String[] lines = code.split("\n");
            int totalLines = lines.length;
            int commentLines = 0;
            
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("//") || trimmed.startsWith("/*") || 
                    trimmed.startsWith("*") || trimmed.startsWith("#")) {
                    commentLines++;
                }
            }
            
            return totalLines > 0 ? (double) commentLines / totalLines : 0;
        }
    }
    
    /**
     * 错误类型智能识别
     */
    public static class ErrorAnalyzer {
        
        public static String analyzeErrorPattern(String userMessage, String code) {
            StringBuilder errorPatterns = new StringBuilder();
            String lowerMessage = userMessage.toLowerCase();
            String lowerCode = code != null ? code.toLowerCase() : "";
            
            // 语法错误模式
            if (lowerMessage.contains("语法错误") || lowerMessage.contains("syntax error")) {
                errorPatterns.append("语法错误频发，需强化基础语法，");
            }
            
            // 逻辑错误模式
            if (lowerMessage.contains("逻辑") || lowerMessage.contains("结果不对") || 
                lowerMessage.contains("输出错误")) {
                errorPatterns.append("逻辑思维需要梳理，建议画流程图，");
            }
            
            // 空指针/空引用
            if (lowerMessage.contains("nullpointer") || lowerMessage.contains("空指针") ||
                lowerMessage.contains("null") || lowerCode.contains("null")) {
                errorPatterns.append("空值处理不当，需加强防御性编程，");
            }
            
            // 数组越界
            if (lowerMessage.contains("越界") || lowerMessage.contains("index") ||
                lowerMessage.contains("bounds")) {
                errorPatterns.append("数组边界控制问题，需注意索引范围，");
            }
            
            // 循环问题
            if (lowerMessage.contains("无限循环") || lowerMessage.contains("死循环") ||
                lowerMessage.contains("infinite loop")) {
                errorPatterns.append("循环终止条件设计不当，");
            }
            
            // 类型转换
            if (lowerMessage.contains("类型") || lowerMessage.contains("cast") ||
                lowerMessage.contains("conversion")) {
                errorPatterns.append("数据类型理解需加强，");
            }
            
            return errorPatterns.length() > 0 ? 
                errorPatterns.substring(0, errorPatterns.length() - 1) : "";
        }
    }
    
    /**
     * 个性化建议生成器
     */
    public static class GuidanceGenerator {
        
        public static String generatePersonalizedGuidance(String studentName, 
                                                        String codeStyle, 
                                                        String errorPattern, 
                                                        String currentQuestion) {
            StringBuilder guidance = new StringBuilder();
            
            // 个性化称呼
            String name = (studentName != null && !studentName.trim().isEmpty()) ? 
                         studentName : "同学";
            
            guidance.append(name).append("，根据你的编程习惯和遇到的问题，我为你制定了以下学习建议：\n\n");
            
            // 代码风格指导
            if (codeStyle != null && !codeStyle.trim().isEmpty()) {
                guidance.append("📝 **代码风格优化**：\n");
                guidance.append("你的").append(codeStyle).append("。");
                guidance.append(getStyleImprovement(codeStyle)).append("\n\n");
            }
            
            // 错误模式指导
            if (errorPattern != null && !errorPattern.trim().isEmpty()) {
                guidance.append("🐛 **错误模式分析**：\n");
                guidance.append("检测到你").append(errorPattern).append("。");
                guidance.append(getErrorImprovement(errorPattern)).append("\n\n");
            }
            
            // 学习路径建议
            guidance.append("📚 **学习路径建议**：\n");
            guidance.append(getLearningPath(codeStyle, errorPattern, currentQuestion));
            
            // 实践练习
            guidance.append("\n\n💪 **实践练习**：\n");
            guidance.append(getPracticeExercises(codeStyle, errorPattern));
            
            return guidance.toString();
        }
        
        private static String getStyleImprovement(String style) {
            if (style.contains("较长函数")) {
                return "建议采用单一职责原则，将长函数拆分为多个短小精悍的函数。";
            }
            if (style.contains("嵌套较深")) {
                return "可以使用早返回（Early Return）模式减少嵌套层级。";
            }
            if (style.contains("注释较少")) {
                return "增加必要的注释，特别是复杂算法和业务逻辑部分。";
            }
            return "继续保持良好的编码习惯。";
        }
        
        private static String getErrorImprovement(String errorPattern) {
            if (errorPattern.contains("空值处理")) {
                return "建议学习Optional类的使用，或在使用对象前先进行null检查。";
            }
            if (errorPattern.contains("数组边界")) {
                return "使用for-each循环或在访问数组时先检查length属性。";
            }
            if (errorPattern.contains("循环终止")) {
                return "仔细检查循环变量的更新逻辑，确保能够达到终止条件。";
            }
            return "多进行调试练习，培养问题定位能力。";
        }
        
        private static String getLearningPath(String style, String errorPattern, String question) {
            StringBuilder path = new StringBuilder();
            
            if (question.toLowerCase().contains("java")) {
                path.append("1. 深入学习Java基础语法\n");
                path.append("2. 掌握面向对象编程思想\n");
                path.append("3. 学习常用设计模式\n");
            } else if (question.toLowerCase().contains("python")) {
                path.append("1. 熟练掌握Python语法特性\n");
                path.append("2. 学习Pythonic编程风格\n");
                path.append("3. 掌握常用库的使用\n");
            } else {
                path.append("1. 巩固编程语言基础\n");
                path.append("2. 提高代码质量意识\n");
                path.append("3. 培养调试技能\n");
            }
            
            return path.toString();
        }
        
        private static String getPracticeExercises(String style, String errorPattern) {
            StringBuilder exercises = new StringBuilder();
            
            if (errorPattern != null && errorPattern.contains("空值")) {
                exercises.append("• 练习编写防御性代码，处理各种边界情况\n");
            }
            if (style != null && style.contains("较长函数")) {
                exercises.append("• 重构一段现有代码，将其拆分为多个函数\n");
            }
            
            exercises.append("• 每日代码review，总结常见问题\n");
            exercises.append("• 阅读优秀开源项目代码，学习最佳实践");
            
            return exercises.toString();
        }
    }
}