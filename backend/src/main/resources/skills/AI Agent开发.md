# AI Agent开发

> AI Agent 面试方向，涵盖 LLM、Agent 架构、RAG、多 Agent 协作

## 基本信息

- **方向名称**: AI Agent开发
- **描述**: AI Agent 面试方向，涵盖 LLM、Agent 架构、RAG、多 Agent 协作
- **版本**: 1.0.0

## 考察范围

- LLM 基础（Transformer、Prompt Engineering）
- Agent 架构（ReAct、Plan-Execute）
- 工具调用（Function Calling、MCP）
- RAG 增强检索生成
- 多 Agent 协作框架
- Memory 与上下文管理
- Agent 评估与安全
- LangChain/LlamaIndex 框架
- 模型微调（SFT、RLHF）
- AI 应用部署与推理优化

## 难度分布

| 等级 | 占比 | 说明 |
|------|------|------|
| 校招 | 40% | LLM 基础概念、Prompt 设计、API 调用 |
| 中级 | 35% | Agent 架构实现、RAG 系统搭建、工具集成 |
| 高级 | 25% | 多 Agent 协作、模型微调、生产部署优化 |

## 参考知识库

- ReAct 模式原理与实现
- Function Calling 协议
- 向量数据库与 Embedding
- RAG 评估方法论
- Prompt 设计模式（CoT、ToT）
- Agent Tool 注册与发现
- 多 Agent 通信协议
- AI 安全（Prompt Injection、Guardrails）

## Prompt 模板

```
你是一个专业的 AI Agent 面试出题专家。

考察范围：LLM 基础、Agent 架构（ReAct、Plan-Execute）、工具调用（Function Calling）、RAG 增强检索、多 Agent 协作、Memory 管理、Agent 评估与安全

参考知识库：
- ReAct 模式原理与实现
- Function Calling 协议
- 向量数据库与 Embedding
- RAG 评估方法论
- Prompt 设计模式（CoT、ToT）
- AI 安全（Prompt Injection、Guardrails）

请生成 {count} 道 {level} 难度的 AI Agent 开发面试题，要求：
1. 题目结合真实 AI Agent 应用场景
2. 难度分布合理，由浅入深
3. 每道题包含具体场景描述和问题
4. 答案预期需要体现对 AI Agent 架构的深度理解
5. 避免与已有题目重复

请以 JSON 数组格式返回，每道题包含：text（题目内容）、difficultyScore（难度系数1-10）、category（知识点分类）。
```
