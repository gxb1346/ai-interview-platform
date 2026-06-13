package com.interview.modules.interview.skill;

import com.interview.modules.interview.model.InterviewQuestion;

import java.util.List;
import java.util.Map;

/**
 * 面试 Skill 接口
 * 每个面试方向实现一个 Skill，定义考察范围、难度分布和参考知识库
 */
public interface InterviewSkill {

    /**
     * 获取面试方向名称
     */
    String getDirectionName();

    /**
     * 获取技能描述
     */
    String getDescription();

    /**
     * 获取考察范围列表
     */
    List<String> getScopeAreas();

    /**
     * 获取难度分布：<难度等级, 题目占比>
     */
    Map<String, Double> getDifficultyDistribution();

    /**
     * 获取参考知识库
     */
    List<String> getKnowledgeBase();

    /**
     * 生成面试题目
     *
     * @param count     题目数量
     * @param level     难度等级
     * @param stage     面试阶段
     * @param excludeIds 需要排除的题目 ID（历史去重）
     * @return 生成的题目列表
     */
    List<InterviewQuestion> generateQuestions(int count, String level,
                                              String stage, List<String> excludeIds);

    /**
     * 获取基础 Prompt 模板
     */
    String getPromptTemplate();

    /**
     * 获取该 Skill 的版本号
     */
    String getVersion();
}
