package com.interview.modules.interview.service;

import com.interview.modules.interview.model.InterviewQuestion;
import com.interview.modules.interview.repository.InterviewSessionRepository;
import com.interview.modules.interview.skill.InterviewSkill;
import com.interview.modules.interview.skill.SkillRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.client.ChatClient;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QuestionGeneratorServiceTest {

    @Mock
    private SkillRegistry skillRegistry;

    @Mock
    private InterviewSkill interviewSkill;

    @Mock
    private InterviewSessionRepository sessionRepository;

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private ChatClient resumeChatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec responseSpec;

    private QuestionGeneratorService service;

    @BeforeEach
    void setUp() {
        when(chatClientBuilder.defaultSystem(anyString())).thenReturn(chatClientBuilder);
        when(chatClientBuilder.build()).thenReturn(resumeChatClient);

        service = new QuestionGeneratorService(skillRegistry, sessionRepository, chatClientBuilder);
    }

    private List<InterviewQuestion> createSkillQuestions(int count) {
        List<InterviewQuestion> questions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            questions.add(new InterviewQuestion("SKILL-" + i, "方向题" + i, "SKILL", "Java后端开发", "校招"));
        }
        return questions;
    }

    private List<InterviewQuestion> createResumeQuestions(int count) {
        List<InterviewQuestion> questions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            questions.add(new InterviewQuestion("RESUME-" + i, "简历题" + i, "RESUME_DEEP_DIVE", "Java后端开发", "校招"));
        }
        return questions;
    }

    // ==================== 自我介绍环节：100% Skill 题 ====================

    @Test
    void shouldOnlyGenerateSkillQuestionsForSelfIntro() {
        when(sessionRepository.getAskedQuestionIds("cand1", "Java后端开发")).thenReturn(List.of());
        when(skillRegistry.getSkill("Java后端开发")).thenReturn(interviewSkill);
        when(interviewSkill.generateQuestions(2, "校招", "selfIntro", List.of()))
                .thenReturn(createSkillQuestions(2));

        List<InterviewQuestion> result = service.generateQuestions(
                "有简历", "Java后端开发", "校招", "selfIntro", "cand1", 2);

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(q -> "SKILL".equals(q.getSource())));
        verify(resumeChatClient, never()).prompt();
    }

    // ==================== 反问环节：100% Skill 题 ====================

    @Test
    void shouldOnlyGenerateSkillQuestionsForQaRound() {
        when(sessionRepository.getAskedQuestionIds("cand1", "Java后端开发")).thenReturn(List.of());
        when(skillRegistry.getSkill("Java后端开发")).thenReturn(interviewSkill);
        when(interviewSkill.generateQuestions(3, "校招", "qaRound", List.of()))
                .thenReturn(createSkillQuestions(3));

        List<InterviewQuestion> result = service.generateQuestions(
                "有简历", "Java后端开发", "校招", "qaRound", "cand1", 3);

        assertEquals(3, result.size());
        assertTrue(result.stream().allMatch(q -> "SKILL".equals(q.getSource())));
    }

    // ==================== 无简历：100% Skill 题 ====================

    @Test
    void shouldOnlyGenerateSkillQuestionsWhenNoResume() {
        when(sessionRepository.getAskedQuestionIds("cand1", "Java后端开发")).thenReturn(List.of());
        when(skillRegistry.getSkill("Java后端开发")).thenReturn(interviewSkill);
        when(interviewSkill.generateQuestions(5, "校招", "techExam", List.of()))
                .thenReturn(createSkillQuestions(5));

        List<InterviewQuestion> result = service.generateQuestions(
                null, "Java后端开发", "校招", "techExam", "cand1", 5);

        assertEquals(5, result.size());
        assertTrue(result.stream().allMatch(q -> "SKILL".equals(q.getSource())));
    }

    @Test
    void shouldOnlyGenerateSkillQuestionsWhenResumeIsBlank() {
        when(sessionRepository.getAskedQuestionIds("cand1", "Java后端开发")).thenReturn(List.of());
        when(skillRegistry.getSkill("Java后端开发")).thenReturn(interviewSkill);
        when(interviewSkill.generateQuestions(4, "校招", "techExam", List.of()))
                .thenReturn(createSkillQuestions(4));

        List<InterviewQuestion> result = service.generateQuestions(
                "   ", "Java后端开发", "校招", "techExam", "cand1", 4);

        assertEquals(4, result.size());
        assertTrue(result.stream().allMatch(q -> "SKILL".equals(q.getSource())));
    }

    // ==================== 有简历 + 技术考察：60% 简历 + 40% Skill ====================

    @Test
    void shouldGenerateResumeAndSkillQuestionsForTechExam() {
        when(sessionRepository.getAskedQuestionIds("cand1", "Java后端开发")).thenReturn(List.of());
        when(skillRegistry.getSkill("Java后端开发")).thenReturn(interviewSkill);
        // 5 题：40% = 2 道 Skill，60% = 3 道简历
        when(interviewSkill.generateQuestions(2, "校招", "techExam", List.of()))
                .thenReturn(createSkillQuestions(2));

        String aiResponse = """
                [
                    {"text": "简历题1", "difficultyScore": 5, "category": "项目深挖"},
                    {"text": "简历题2", "difficultyScore": 6, "category": "项目深挖"},
                    {"text": "简历题3", "difficultyScore": 4, "category": "项目深挖"}
                ]""";
        when(resumeChatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn(aiResponse);

        List<InterviewQuestion> result = service.generateQuestions(
                "有简历", "Java后端开发", "校招", "techExam", "cand1", 5);

        assertEquals(5, result.size());
        // 验证交错合并：第一位是简历题，第二位是 Skill 题
        assertEquals("RESUME_DEEP_DIVE", result.get(0).getSource());
        assertEquals("SKILL", result.get(1).getSource());
    }

    // ==================== 简历深挖失败降级 ====================

    @Test
    void shouldUseFallbackWhenResumeAiFails() {
        when(sessionRepository.getAskedQuestionIds("cand1", "Java后端开发")).thenReturn(List.of());
        when(skillRegistry.getSkill("Java后端开发")).thenReturn(interviewSkill);
        when(interviewSkill.generateQuestions(anyInt(), anyString(), anyString(), anyList()))
                .thenReturn(createSkillQuestions(2));

        when(resumeChatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenThrow(new RuntimeException("AI服务不可用"));

        List<InterviewQuestion> result = service.generateQuestions(
                "有简历", "Java后端开发", "校招", "projectDeep", "cand1", 3);

        // 2 道 Skill + 降级简历题
        assertFalse(result.isEmpty());
        assertTrue(result.size() >= 2, "至少应该有 Skill 题和降级简历题");
    }

    // ==================== 历史去重 ====================

    @Test
    void shouldPassExcludeIdsForDeduplication() {
        List<String> excludeIds = List.of("Q1", "Q2", "Q3");
        when(sessionRepository.getAskedQuestionIds("cand1", "AI Agent开发")).thenReturn(excludeIds);
        when(skillRegistry.getSkill("AI Agent开发")).thenReturn(interviewSkill);
        when(interviewSkill.generateQuestions(3, "校招", "selfIntro", excludeIds))
                .thenReturn(createSkillQuestions(3));

        List<InterviewQuestion> result = service.generateQuestions(
                null, "AI Agent开发", "校招", "selfIntro", "cand1", 3);

        assertEquals(3, result.size());
        verify(interviewSkill).generateQuestions(3, "校招", "selfIntro", excludeIds);
    }

    // ==================== 交错合并顺序 ====================

    @Test
    void shouldInterleaveResumeAndSkillQuestions() {
        when(sessionRepository.getAskedQuestionIds("cand1", "Java后端开发")).thenReturn(List.of());
        when(skillRegistry.getSkill("Java后端开发")).thenReturn(interviewSkill);
        when(interviewSkill.generateQuestions(2, "校招", "projectDeep", List.of()))
                .thenReturn(createSkillQuestions(2));

        String aiResponse = """
                [
                    {"text": "简历题1", "difficultyScore": 5, "category": "项目深挖"},
                    {"text": "简历题2", "difficultyScore": 6, "category": "项目深挖"},
                    {"text": "简历题3", "difficultyScore": 4, "category": "项目深挖"}
                ]""";
        when(resumeChatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn(aiResponse);

        List<InterviewQuestion> result = service.generateQuestions(
                "有简历", "Java后端开发", "校招", "projectDeep", "cand1", 5);

        // 交错顺序：R, S, R, S, R
        assertEquals("RESUME_DEEP_DIVE", result.get(0).getSource());
        assertEquals("SKILL", result.get(1).getSource());
        assertEquals("RESUME_DEEP_DIVE", result.get(2).getSource());
        assertEquals("SKILL", result.get(3).getSource());
        assertEquals("RESUME_DEEP_DIVE", result.get(4).getSource());
    }
}