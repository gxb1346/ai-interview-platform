package com.interview.modules.interview.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StageConfig 阶段流转逻辑测试
 */
class StageConfigTest {

    @Test
    void shouldReturnCorrectStageOrder() {
        assertEquals(4, StageConfig.STAGE_ORDER.size());
        assertEquals("selfIntro", StageConfig.STAGE_ORDER.get(0));
        assertEquals("techExam", StageConfig.STAGE_ORDER.get(1));
        assertEquals("projectDeep", StageConfig.STAGE_ORDER.get(2));
        assertEquals("qaRound", StageConfig.STAGE_ORDER.get(3));
    }

    @Test
    void shouldReturnNextStage() {
        assertEquals("techExam", StageConfig.getNextStage("selfIntro"));
        assertEquals("projectDeep", StageConfig.getNextStage("techExam"));
        assertEquals("qaRound", StageConfig.getNextStage("projectDeep"));
        assertNull(StageConfig.getNextStage("qaRound"), "最后一个阶段应返回 null");
    }

    @Test
    void shouldReturnNullForUnknownStage() {
        assertNull(StageConfig.getNextStage("unknown"));
        assertNull(StageConfig.getNextStage(""));
        assertNull(StageConfig.getNextStage(null));
    }

    @Test
    void shouldIdentifyLastStage() {
        assertTrue(StageConfig.isLastStage("qaRound"));
        assertFalse(StageConfig.isLastStage("selfIntro"));
        assertFalse(StageConfig.isLastStage("techExam"));
        assertFalse(StageConfig.isLastStage("projectDeep"));
    }

    @Test
    void shouldCalculateStageMinutesFromTotalDuration() {
        // 60分钟总时长
        StageConfig config = new StageConfig(60);
        var minutes = config.getStageMinutes();

        assertNotNull(minutes);
        assertEquals(4, minutes.size());
        // 15% = 9, 40% = 24, 30% = 18, 最后阶段取剩余
        assertTrue(minutes.get("selfIntro") > 0);
        assertTrue(minutes.get("techExam") > 0);
        assertTrue(minutes.get("projectDeep") > 0);
        assertTrue(minutes.get("qaRound") > 0);
        assertEquals(60, minutes.values().stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    void shouldHandleMinimalTotalDuration() {
        StageConfig config = new StageConfig(4); // 4分钟
        var minutes = config.getStageMinutes();
        assertEquals(4, minutes.values().stream().mapToInt(Integer::intValue).sum());
        // 极短时长下，前几个阶段至少1分钟，最后一阶段可能为0（舍入后剩余）
        minutes.values().forEach(m -> assertTrue(m >= 0, "每个阶段不能为负数"));
    }

    @Test
    void shouldGetSpecificStageMinutes() {
        StageConfig config = new StageConfig(60);
        assertTrue(config.getStageMinutes("selfIntro") > 0);
        assertEquals(0, config.getStageMinutes("nonExistent"));
    }

    @Test
    void shouldUpdateMinutesWhenTotalChanges() {
        StageConfig config = new StageConfig(60);
        config.setTotalMinutes(120);
        assertEquals(120, config.getTotalMinutes());
        assertEquals(120, config.getStageMinutes().values().stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    void shouldHaveDefaultRatiosSumToOne() {
        var ratios = StageConfig.getDefaultRatios();
        double sum = ratios.values().stream().mapToDouble(Double::doubleValue).sum();
        assertEquals(1.0, sum, 0.001);
    }

    @Test
    void shouldHaveCorrectChineseLabels() {
        assertEquals("自我介绍", StageConfig.STAGE_LABELS.get("selfIntro"));
        assertEquals("技术考察", StageConfig.STAGE_LABELS.get("techExam"));
        assertEquals("项目深挖", StageConfig.STAGE_LABELS.get("projectDeep"));
        assertEquals("反问环节", StageConfig.STAGE_LABELS.get("qaRound"));
    }
}
