package com.interview.modules.interview.skill;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Skill 定义数据类
 * 封装从 SKILL.md 和 skill.meta.yml 加载的所有数据
 */
@Data
@Builder
public class SkillDefinition {
    /** 中文方向名称 */
    private String directionName;
    /** 目录名 */
    private String dirName;
    /** 英文名称 */
    private String name;
    /** 描述 */
    private String description;
    /** 显示名称 */
    private String displayName;
    /** 图标 */
    private String icon;
    /** 渐变样式 */
    private String gradient;
    /** 图标背景样式 */
    private String iconBg;
    /** 图标颜色样式 */
    private String iconColor;
    /** SKILL.md 正文（系统提示词） */
    private String systemPrompt;
    /** 考察范围 */
    private List<String> scopeAreas;
    /** 知识库内容 */
    private String knowledgeBase;
    /** 版本号 */
    private String version;
}