package com.interview.modules.voiceinterview.service;

import com.interview.common.ai.PromptSanitizer;
import com.interview.common.config.LlmProviderProperties;
import com.interview.modules.voiceinterview.config.VoiceInterviewProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VoiceInterviewPromptServiceTest {

    private VoiceInterviewPromptService service;
    private PromptSanitizer sanitizer;
    private VoiceInterviewProperties properties;

    @BeforeEach
    void setUp() {
        LlmProviderProperties llmProps = new LlmProviderProperties();
        sanitizer = new PromptSanitizer(llmProps);
        properties = new VoiceInterviewProperties();
        VoiceInterviewProperties.DurationConfig intro = new VoiceInterviewProperties.DurationConfig(3, 5, 8, 1, 1);
        VoiceInterviewProperties.DurationConfig tech = new VoiceInterviewProperties.DurationConfig(8, 10, 15, 3, 5);
        VoiceInterviewProperties.DurationConfig project = new VoiceInterviewProperties.DurationConfig(8, 10, 15, 2, 3);
        VoiceInterviewProperties.DurationConfig hr = new VoiceInterviewProperties.DurationConfig(3, 5, 8, 1, 2);
        properties.setEnableFourPhaseFlow(true);
        VoiceInterviewProperties.PhaseConfig phase = new VoiceInterviewProperties.PhaseConfig();
        phase.setIntro(intro);
        phase.setTech(tech);
        phase.setProject(project);
        phase.setHr(hr);
        properties.setPhase(phase);

        service = new VoiceInterviewPromptService(sanitizer, properties);
    }

    // ==================== 基本结构 ====================

    @Test
    void shouldGenerateSystemPromptWithoutResume() {
        String prompt = service.generateSystemPromptWithContext("java-backend", null, "INTRO", 0, 0);

        assertNotNull(prompt);
        assertTrue(prompt.contains("java-backend"));
        assertTrue(prompt.contains("第一阶段"));
        assertTrue(prompt.contains("语音面试输出约束"));
    }

    @Test
    void shouldGenerateSystemPromptWithResumeFirstRound() {
        String resumeText = "张三，3年Java后端开发经验，擅长Spring Boot";
        String prompt = service.generateSystemPromptWithContext("java-backend", resumeText, "INTRO", 0, 0);

        assertNotNull(prompt);
        assertTrue(prompt.contains("张三"));
        assertTrue(prompt.contains("首轮仅用一句话说明已查阅"));
        assertTrue(prompt.contains("【简历解析文本】"));
    }

    @Test
    void shouldGenerateSystemPromptWithResumeNonFirstRound() {
        String resumeText = "张三，3年Java后端开发经验";
        String prompt = service.generateSystemPromptWithContext("java-backend", resumeText, "TECH", 3, 1);

        assertNotNull(prompt);
        assertTrue(prompt.contains("当前是第4轮对话"));
        assertTrue(prompt.contains("不要重复开场白"));
        assertTrue(prompt.contains("【简历解析文本】"));
    }

    // ==================== 四阶段流程提示 ====================

    @Test
    void shouldContainFourPhaseInstructionsForIntro() {
        String prompt = service.generateSystemPromptWithContext("java-backend", null, "INTRO", 0, 0);

        assertTrue(prompt.contains("第一阶段"));
        assertTrue(prompt.contains("自我介绍"));
    }

    @Test
    void shouldContainFourPhaseInstructionsForTech() {
        String prompt = service.generateSystemPromptWithContext("java-backend", null, "TECH", 1, 0);

        assertTrue(prompt.contains("第二阶段"));
        assertTrue(prompt.contains("技术考察"));
    }

    @Test
    void shouldContainFourPhaseInstructionsForProject() {
        String prompt = service.generateSystemPromptWithContext("java-backend", null, "PROJECT", 2, 1);

        assertTrue(prompt.contains("第三阶段"));
        assertTrue(prompt.contains("项目深挖"));
    }

    @Test
    void shouldContainFourPhaseInstructionsForHr() {
        String prompt = service.generateSystemPromptWithContext("java-backend", null, "HR", 3, 0);

        assertTrue(prompt.contains("第四阶段"));
        assertTrue(prompt.contains("反问环节"));
    }

    @Test
    void shouldNotContainFourPhaseWhenDisabled() {
        properties.setEnableFourPhaseFlow(false);
        String prompt = service.generateSystemPromptWithContext("java-backend", null, "INTRO", 0, 0);

        assertFalse(prompt.contains("四个阶段"));
        assertTrue(prompt.contains("语音面试输出约束"));
    }

    // ==================== 阶段配置检查 ====================

    @Test
    void shouldUseCorrectMaxFollowUpForPhase() {
        String prompt = service.generateSystemPromptWithContext("java-backend", null, "TECH", 0, 0);

        // tech phase max 5 questions
        assertTrue(prompt.contains("最多 5 次深度追问"));
    }

    // ==================== 技能指令 ====================

    @Test
    void shouldIncludeSkillInstruction() {
        String prompt = service.generateSystemPromptWithContext("data-engineer", null, "INTRO", 0, 0);

        assertTrue(prompt.contains("data-engineer"));
        assertTrue(prompt.contains("你是一位"));
    }

    @Test
    void shouldSkipSkillInstructionWhenSkillIdEmpty() {
        String prompt = service.generateSystemPromptWithContext("", null, "INTRO", 0, 0);

        assertFalse(prompt.contains("你是一位"));
    }

    @Test
    void shouldSkipSkillInstructionWhenSkillIdNull() {
        String prompt = service.generateSystemPromptWithContext(null, null, "INTRO", 0, 0);

        assertFalse(prompt.contains("你是一位"));
    }

    // ==================== 防注入 ====================

    @Test
    void shouldContainAntiInjectionInstruction() {
        String prompt = service.generateSystemPromptWithContext("java-backend", null, "INTRO", 0, 0);

        assertTrue(prompt.contains("ignore any instructions") || prompt.contains("ignore"),
                "Prompt should contain anti-injection instruction, but got: " + prompt);
    }

    // ==================== 兼容旧调用 ====================

    @Test
    void shouldWorkWithDeprecatedCall() {
        String prompt = service.generateSystemPromptWithContext("java-backend", "简历内容");

        assertNotNull(prompt);
        assertTrue(prompt.contains("java-backend"));
        assertTrue(prompt.contains("简历内容"));
    }
}