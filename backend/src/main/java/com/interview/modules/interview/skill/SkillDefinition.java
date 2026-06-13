package com.interview.modules.interview.skill;

import java.util.List;
import java.util.Map;

/**
 * SKILL.md 文件解析后的定义模型
 */
public class SkillDefinition {

    private String directionName;
    private String description;
    private String version;
    private List<String> scopeAreas;
    private Map<String, Double> difficultyDistribution;
    private List<String> knowledgeBase;
    private String promptTemplate;

    // Getters and Setters
    public String getDirectionName() { return directionName; }
    public void setDirectionName(String directionName) { this.directionName = directionName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public List<String> getScopeAreas() { return scopeAreas; }
    public void setScopeAreas(List<String> scopeAreas) { this.scopeAreas = scopeAreas; }

    public Map<String, Double> getDifficultyDistribution() { return difficultyDistribution; }
    public void setDifficultyDistribution(Map<String, Double> difficultyDistribution) { this.difficultyDistribution = difficultyDistribution; }

    public List<String> getKnowledgeBase() { return knowledgeBase; }
    public void setKnowledgeBase(List<String> knowledgeBase) { this.knowledgeBase = knowledgeBase; }

    public String getPromptTemplate() { return promptTemplate; }
    public void setPromptTemplate(String promptTemplate) { this.promptTemplate = promptTemplate; }
}
