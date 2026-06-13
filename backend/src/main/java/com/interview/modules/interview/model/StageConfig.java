package com.interview.modules.interview.model;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 面试阶段时长配置
 * 总时长拖动后各阶段按时比自动分配
 */
public class StageConfig implements Serializable {

    public static final String STAGE_SELF_INTRO = "selfIntro";
    public static final String STAGE_TECH_EXAM = "techExam";
    public static final String STAGE_PROJECT_DEEP = "projectDeep";
    public static final String STAGE_QA_ROUND = "qaRound";

    /** 各阶段默认时长占比 */
    private static final Map<String, Double> DEFAULT_RATIOS = new LinkedHashMap<>();

    static {
        DEFAULT_RATIOS.put(STAGE_SELF_INTRO, 0.15);    // 自我介绍 15%
        DEFAULT_RATIOS.put(STAGE_TECH_EXAM, 0.40);     // 技术考察 40%
        DEFAULT_RATIOS.put(STAGE_PROJECT_DEEP, 0.30);  // 项目深挖 30%
        DEFAULT_RATIOS.put(STAGE_QA_ROUND, 0.15);      // 反问环节 15%
    }

    /** 面试总时长（分钟） */
    private int totalMinutes;

    /** 各阶段时长（分钟） */
    private Map<String, Integer> stageMinutes;

    public StageConfig() {
        this(60); // 默认 60 分钟
    }

    public StageConfig(int totalMinutes) {
        this.totalMinutes = totalMinutes;
        this.stageMinutes = recalcStages(totalMinutes);
    }

    /**
     * 根据总时长重新计算各阶段时长
     */
    public static Map<String, Integer> recalcStages(int totalMinutes) {
        Map<String, Integer> result = new LinkedHashMap<>();
        int allocated = 0;
        int remaining = totalMinutes;

        for (Map.Entry<String, Double> entry : DEFAULT_RATIOS.entrySet()) {
            if (entry.getKey().equals(STAGE_QA_ROUND)) {
                // 最后一个阶段取剩余所有时间，避免舍入误差
                result.put(entry.getKey(), remaining - allocated);
            } else {
                int minutes = (int) Math.round(totalMinutes * entry.getValue());
                result.put(entry.getKey(), Math.max(1, minutes));
            }
            allocated += result.get(entry.getKey());
        }

        return result;
    }

    public int getTotalMinutes() {
        return totalMinutes;
    }

    public void setTotalMinutes(int totalMinutes) {
        this.totalMinutes = totalMinutes;
        this.stageMinutes = recalcStages(totalMinutes);
    }

    public Map<String, Integer> getStageMinutes() {
        return stageMinutes;
    }

    public void setStageMinutes(Map<String, Integer> stageMinutes) {
        this.stageMinutes = stageMinutes;
    }

    public int getStageMinutes(String stage) {
        return stageMinutes.getOrDefault(stage, 0);
    }

    public static Map<String, Double> getDefaultRatios() {
        return new LinkedHashMap<>(DEFAULT_RATIOS);
    }
}
