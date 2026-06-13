package com.interview.modules.interview.service;

import com.interview.modules.interview.skill.SkillRegistry;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Skill 注册表服务封装
 * 对外提供 Skill 管理的简洁 API
 */
@Service
public class SkillRegistryService {

    private final SkillRegistry skillRegistry;

    public SkillRegistryService(SkillRegistry skillRegistry) {
        this.skillRegistry = skillRegistry;
    }

    /**
     * 获取所有面试方向名称
     */
    public List<String> getAllDirectionNames() {
        return skillRegistry.getAllDirectionNames();
    }

    /**
     * 获取所有 Skill 描述信息
     */
    public List<Map<String, String>> getAllSkillDescriptions() {
        return skillRegistry.getAllSkillDescriptions();
    }

    /**
     * 刷新指定 Skill 缓存
     */
    public void refreshSkill(String directionName) {
        skillRegistry.refreshSkill(directionName);
    }

    /**
     * 清除所有 Skill 缓存
     */
    public void clearCache() {
        skillRegistry.clearCache();
    }
}
