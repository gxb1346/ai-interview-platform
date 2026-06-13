# Java后端开发

> Java 后端全栈面试方向，涵盖 Java 核心、Spring 生态、微服务架构与分布式系统

## 基本信息

- **方向名称**: Java后端开发
- **描述**: Java 后端全栈面试方向，涵盖 Java 核心、Spring 生态、微服务架构与分布式系统
- **版本**: 1.0.0

## 考察范围

- Java核心（JVM、并发、集合）
- Spring Boot/Cloud 生态
- 微服务架构（服务治理、熔断、限流）
- 数据库（MySQL、PostgreSQL）
- Redis 与缓存设计
- 消息队列（Kafka、RocketMQ）
- 分布式理论（CAP、BASE、一致性算法）
- 高可用架构设计
- 性能调优与监控
- CI/CD与DevOps

## 难度分布

| 等级 | 占比 | 说明 |
|------|------|------|
| 校招 | 40% | Java 基础、JVM 内存模型、Spring 基础、SQL 基础 |
| 中级 | 35% | Spring 源码、微服务治理、Redis 深度、Kafka 实践 |
| 高级 | 25% | 分布式事务、高并发架构、性能优化、系统设计 |

## 参考知识库

- JVM 内存模型与垃圾回收调优
- ConcurrentHashMap 实现原理
- Spring 事务传播机制
- MySQL 索引优化与慢查询分析
- Redis 持久化与主从复制
- Kafka 消息可靠性保证
- 分布式一致性（Raft、Paxos）
- 系统性能指标（QPS、TP99、RT）

## Prompt 模板

```
你是一个专业的 Java 后端面试出题专家。

考察范围：Java核心（JVM、并发、集合）、Spring Boot/Cloud 生态、微服务架构、数据库、Redis、消息队列、分布式理论、高可用架构、性能调优

参考知识库：
- JVM 内存模型与垃圾回收调优
- ConcurrentHashMap 实现原理
- Spring 事务传播机制
- MySQL 索引优化与慢查询分析
- Redis 持久化与主从复制
- Kafka 消息可靠性保证
- 分布式一致性（Raft、Paxos）

请生成 {count} 道 {level} 难度的 Java 后端面试题，要求：
1. 题目结合实际工作场景，考察真实编码能力和系统理解
2. 难度分布合理，由浅入深
3. 每道题包含具体场景描述和问题
4. 答案预期需要体现候选人的深度理解和实战经验
5. 避免与已有题目重复

请以 JSON 数组格式返回，每道题包含：text（题目内容）、difficultyScore（难度系数1-10）、category（知识点分类）。
```
