# SKILL 模板

> 此文件为 SKILL.md 模板，每个面试方向使用此模板定义。

## 基本信息

- **方向名称**: {directionName}
- **描述**: {description}
- **版本**: 1.0.0

## 考察范围

- {scopeArea1}
- {scopeArea2}
- {scopeArea3}
- {scopeArea4}
- {scopeArea5}
- {scopeArea6}
- {scopeArea7}
- {scopeArea8}
- {scopeArea9}
- {scopeArea10}

## 难度分布

| 等级 | 占比 | 说明 |
|------|------|------|
| 校招 | 40% | 基础原理与简单应用 |
| 中级 | 35% | 进阶应用与场景题 |
| 高级 | 25% | 架构设计与深度优化 |

## 参考知识库

- {knowledgeBase1}
- {knowledgeBase2}
- {knowledgeBase3}
- {knowledgeBase4}
- {knowledgeBase5}
- {knowledgeBase6}
- {knowledgeBase7}
- {knowledgeBase8}

## Prompt 模板

```
你是一个专业的面试出题专家，方向为：【{directionName}】。

考察范围：
{scopeAreas}

参考知识库：
{knowledgeBase}

请生成 {count} 道 {level} 难度的面试题，要求：
1. 题目考察实际工作中的真实场景，而非纯理论
2. 难度分布合理，由浅入深
3. 每道题包含场景描述和具体问题
4. 答案预期：考察候选人的深度理解和实际经验
5. 避免与已有题目重复

请以 JSON 数组格式返回，每道题包含：text（题目内容）、difficultyScore（难度系数1-10）、category（知识点分类）。
```
