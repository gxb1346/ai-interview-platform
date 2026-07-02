package com.interview.modules.interview.skill;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Skill 注册中心
 * 基于 Progressive Disclosure 机制实现按需加载
 */
@Service
@Slf4j
public class SkillRegistry {

    private final Map<String, InterviewSkill> skillCache = new ConcurrentHashMap<>();
    private final ChatClient skillChatClient;

    /**
     * 为 Skill 出题使用独立的 ChatClient，与简历深挖物理隔离
     */
    public SkillRegistry(ChatClient.Builder chatClientBuilder) {
        this.skillChatClient = chatClientBuilder
                .defaultSystem("你是一个技术面试出题专家，负责根据不同面试方向和难度等级生成高质量的面试题目。")
                .build();
    }

    /**
     * 按需获取 Skill（Progressive Disclosure）
     * 只有被使用时才加载到内存
     */
    public InterviewSkill getSkill(String directionName) {
        return skillCache.computeIfAbsent(directionName, this::loadSkill);
    }

    /**
     * 获取所有已注册的方向名称
     */
    public List<String> getAllDirectionNames() {
        return List.of(
                "Java后端开发", "阿里后端", "字节后端", "腾讯后端",
                "前端工程", "Python后端开发", "算法与数据结构",
                "系统设计", "测试开发", "AI Agent开发"
        );
    }

    /**
     * 获取所有已注册的 Skill 描述
     */
    public List<Map<String, String>> getAllSkillDescriptions() {
        List<Map<String, String>> result = new ArrayList<>();
        for (String name : getAllDirectionNames()) {
            InterviewSkill skill = getSkill(name);
            result.add(Map.of(
                    "name", skill.getDirectionName(),
                    "description", skill.getDescription(),
                    "version", skill.getVersion(),
                    "scopeCount", String.valueOf(skill.getScopeAreas().size())
            ));
        }
        return result;
    }

    /**
     * 获取 Skill 的显示元数据（图标、颜色等）
     * 用于前端渲染 Skill 选择器
     */
    public Map<String, Object> getSkillDisplayMeta(String directionName) {
        InterviewSkill skill = getSkill(directionName);
        if (skill instanceof FileBasedInterviewSkill fileSkill) {
            SkillDefinition def = fileSkill.getDefinition();
            return Map.of(
                    "directionName", def.getDirectionName(),
                    "displayName", def.getDisplayName() != null ? def.getDisplayName() : def.getDirectionName(),
                    "icon", def.getIcon() != null ? def.getIcon() : "📋",
                    "gradient", def.getGradient() != null ? def.getGradient() : "from-blue-500 to-indigo-500",
                    "iconBg", def.getIconBg() != null ? def.getIconBg() : "bg-blue-100",
                    "iconColor", def.getIconColor() != null ? def.getIconColor() : "text-blue-600",
                    "description", skill.getDescription(),
                    "version", skill.getVersion(),
                    "scopeCount", skill.getScopeAreas().size()
            );
        }
        return Map.of(
                "directionName", directionName,
                "displayName", directionName,
                "icon", "📋",
                "description", skill.getDescription(),
                "version", skill.getVersion()
        );
    }

    /**
     * 加载 Skill 实现
     * 优先从 resources/skills/{dirName}/SKILL.md 和 skill.meta.yml 加载
     * 如果文件不存在，回退到内置的 DefaultInterviewSkill
     */
    private InterviewSkill loadSkill(String directionName) {
        // 1. 尝试从文件加载
        SkillDefinition definition = SkillFileLoader.load(directionName);
        if (definition != null) {
            log.info("从文件加载 Skill: {} (目录: {})", directionName, definition.getDirName());
            return new FileBasedInterviewSkill(definition, skillChatClient);
        }

        // 2. 回退到内置默认实现
        log.info("文件未找到，使用内置默认 Skill: {}", directionName);
        return new DefaultInterviewSkill(directionName, skillChatClient);
    }

    /**
     * 获取 Skill 专用的 ChatClient（与主 ChatClient 隔离）
     */
    public ChatClient getSkillChatClient() {
        return skillChatClient;
    }

    /**
     * 刷新指定 Skill 的缓存（支持覆盖）
     */
    public void refreshSkill(String directionName) {
        skillCache.remove(directionName);
    }

    /**
     * 清除所有 Skill 缓存
     */
    public void clearCache() {
        skillCache.clear();
    }
}