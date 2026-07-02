package com.interview.modules.interview.service;

import com.interview.modules.interview.model.InterviewSession;
import com.interview.modules.interview.repository.InterviewSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JDParseServiceTest {

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec responseSpec;

    @Mock
    private SkillRegistryService skillRegistryService;

    @Mock
    private InterviewSessionRepository sessionRepository;

    private JDParseService service;

    @BeforeEach
    void setUp() {
        when(chatClientBuilder.defaultSystem(anyString())).thenReturn(chatClientBuilder);
        when(chatClientBuilder.build()).thenReturn(chatClient);

        service = new JDParseService(chatClientBuilder, skillRegistryService, sessionRepository);
    }

    // ==================== 基本解析 ====================

    @Test
    void shouldParseJD() {
        String aiResponse = """
                {
                    "matchedDirection": "Java后端开发",
                    "skills": ["Spring Boot", "MyBatis", "Redis", "Docker", "K8s"],
                    "experienceRequired": 3,
                    "techStack": ["Java", "Spring", "Docker"]
                }""";

        when(skillRegistryService.getAllDirectionNames()).thenReturn(List.of("Java后端开发", "AI Agent开发", "前端开发"));
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn(aiResponse);

        JDParseService.JDParseResult result = service.parseJD("招聘Java开发工程师，3年经验");

        assertEquals("Java后端开发", result.getMatchedDirection());
        assertEquals(5, result.getSkills().size());
        assertTrue(result.getSkills().contains("Spring Boot"));
        assertEquals(3, result.getExperienceRequired());
        assertEquals(3, result.getTechStack().size());
    }

    // ==================== 解析失败降级 ====================

    @Test
    void shouldReturnFallbackOnParseFailure() {
        when(skillRegistryService.getAllDirectionNames()).thenReturn(List.of("Java后端开发"));
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenThrow(new RuntimeException("AI service error"));

        JDParseService.JDParseResult result = service.parseJD("任意JD文本");

        assertEquals("Java后端开发", result.getMatchedDirection());
        assertEquals(1, result.getSkills().size());
        assertEquals("通用技能", result.getSkills().get(0));
        assertEquals(3, result.getExperienceRequired());
    }

    @Test
    void shouldReturnFallbackOnInvalidJson() {
        when(skillRegistryService.getAllDirectionNames()).thenReturn(List.of("Java后端开发"));
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("这不是JSON");

        JDParseService.JDParseResult result = service.parseJD("JD文本");

        assertEquals("Java后端开发", result.getMatchedDirection());
        assertEquals(3, result.getExperienceRequired());
    }

    // ==================== Markdown JSON 包裹 ====================

    @Test
    void shouldStripMarkdownJsonBlock() {
        String aiResponse = """
                ```json
                {
                    "matchedDirection": "AI Agent开发",
                    "skills": ["Python", "LangChain", "Docker"],
                    "experienceRequired": 2,
                    "techStack": ["Python", "LangChain"]
                }
                ```""";

        when(skillRegistryService.getAllDirectionNames()).thenReturn(List.of("AI Agent开发", "Java后端开发"));
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn(aiResponse);

        JDParseService.JDParseResult result = service.parseJD("AI Agent岗位JD");

        assertEquals("AI Agent开发", result.getMatchedDirection());
        assertEquals("Python", result.getSkills().get(0));
    }

    // ==================== 基于 JD 创建会话 ====================

    @Test
    void shouldCreateSessionFromJD() {
        String aiResponse = """
                {
                    "matchedDirection": "Java后端开发",
                    "skills": ["Spring Boot", "MyBatis"],
                    "experienceRequired": 5,
                    "techStack": ["Java", "Spring"]
                }""";

        when(skillRegistryService.getAllDirectionNames()).thenReturn(List.of("Java后端开发"));
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn(aiResponse);

        InterviewSession session = new InterviewSession();
        session.setCandidateName("李四");
        String jdText = "招聘高级Java开发，5年经验";

        InterviewSession result = service.createSessionFromJD(session, jdText);

        assertEquals("Java后端开发", result.getDirection());
        assertEquals(jdText, result.getCustomJD());
        assertNotNull(result.getSessionId());
        assertEquals("PREPARING", result.getStatus());
        verify(sessionRepository).save(any(InterviewSession.class));
    }
}