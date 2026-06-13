package com.interview.modules.interview.model;

/**
 * 面试难度等级
 */
public enum InterviewLevel {
    CAMPUS("校招", "0-1年", 0, 1),
    MID("中级", "1-3年", 1, 3),
    SENIOR("高级", "3年+", 3, 20);

    private final String displayName;
    private final String yearRange;
    private final int minYears;
    private final int maxYears;

    InterviewLevel(String displayName, String yearRange, int minYears, int maxYears) {
        this.displayName = displayName;
        this.yearRange = yearRange;
        this.minYears = minYears;
        this.maxYears = maxYears;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getYearRange() {
        return yearRange;
    }

    public int getMinYears() {
        return minYears;
    }

    public int getMaxYears() {
        return maxYears;
    }

    public static InterviewLevel fromDisplayName(String displayName) {
        for (InterviewLevel level : values()) {
            if (level.displayName.equals(displayName)) {
                return level;
            }
        }
        throw new IllegalArgumentException("无效的面试等级: " + displayName);
    }
}
