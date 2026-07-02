package com.interview.modules.interview.service;

import com.interview.modules.interview.skill.InterviewSkill;
import com.interview.modules.interview.skill.SkillRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SkillRegistryServiceTest {

    @Mock
    private SkillRegistry skillRegistry;

    @InjectMocks
    private SkillRegistryService service;

    @Test
    void shouldGetAllDirectionNames() {
        when(skillRegistry.getAllDirectionNames()).thenReturn(List.of("Java后端开发", "前端工程"));

        List<String> result = service.getAllDirectionNames();

        assertEquals(2, result.size());
        assertTrue(result.contains("Java后端开发"));
    }

    @Test
    void shouldGetAllSkillDescriptions() {
        when(skillRegistry.getAllSkillDescriptions()).thenReturn(List.of(
                Map.of("name", "Java后端开发", "description", "Java后端面试题", "version", "1.0", "scopeCount", "5")
        ));

        List<Map<String, String>> result = service.getAllSkillDescriptions();

        assertEquals(1, result.size());
        assertEquals("Java后端开发", result.get(0).get("name"));
    }

    @Test
    void shouldRefreshSkill() {
        doNothing().when(skillRegistry).refreshSkill("Java后端开发");

        service.refreshSkill("Java后端开发");

        verify(skillRegistry).refreshSkill("Java后端开发");
    }

    @Test
    void shouldClearCache() {
        doNothing().when(skillRegistry).clearCache();

        service.clearCache();

        verify(skillRegistry).clearCache();
    }
}