package com.interview.modules.interview.model;

import java.util.Arrays;
import java.util.List;

/**
 * 面试方向枚举
 */
public enum InterviewDirection {
    JAVA_BACKEND("Java后端开发", "Java后端开发面试方向"),
    ALIBABA_SPECIAL("阿里后端", "阿里后端专项面试方向"),
    BYTEDANCE_SPECIAL("字节后端", "字节后端专项面试方向"),
    TENCENT_SPECIAL("腾讯后端", "腾讯后端专项面试方向"),
    FRONTEND("前端工程", "前端工程面试方向"),
    PYTHON_BACKEND("Python后端开发", "Python后端开发面试方向"),
    ALGORITHM("算法与数据结构", "算法与数据结构面试方向"),
    SYSTEM_DESIGN("系统设计", "系统架构设计面试方向"),
    TEST_DEV("测试开发", "测试开发面试方向"),
    AI_AGENT("AI Agent开发", "AI Agent开发面试方向");

    private final String displayName;
    private final String description;

    InterviewDirection(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public static InterviewDirection fromDisplayName(String displayName) {
        for (InterviewDirection dir : values()) {
            if (dir.displayName.equals(displayName)) {
                return dir;
            }
        }
        throw new IllegalArgumentException("无效的面试方向: " + displayName);
    }

    public static List<String> getDisplayNames() {
        return java.util.Arrays.stream(values())
                .map(InterviewDirection::getDisplayName)
                .toList();
    }
}
