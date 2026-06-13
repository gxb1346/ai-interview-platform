# Python后端开发

> Python 后端开发面试方向，涵盖 FastAPI/Django、异步编程与数据科学

## 基本信息

- **方向名称**: Python后端开发
- **描述**: Python 后端开发面试方向，涵盖 FastAPI/Django、异步编程与数据科学
- **版本**: 1.0.0

## 考察范围

- Python 语言核心特性
- Django/FastAPI/Flask 框架
- 异步编程（asyncio、协程）
- ORM 与数据库交互
- Celery 任务队列
- RESTful/GraphQL API 设计
- 数据爬虫与清洗
- 微服务与容器化
- 科学计算与数据分析
- 部署与运维（Gunicorn、Nginx）

## 难度分布

| 等级 | 占比 | 说明 |
|------|------|------|
| 校招 | 40% | Python 基础、框架入门、基本 Web 开发 |
| 中级 | 35% | 异步编程、ORM 深度、Celery 实践 |
| 高级 | 25% | 全链路性能优化、大规模数据处理、微服务 |

## 参考知识库

- GIL 锁对并发的影响
- asyncio 事件循环机制
- Django ORM 查询优化
- FastAPI 依赖注入系统
- Celery 任务调度与结果存储
- GraphQL vs REST 选型
- Python 内存管理与性能分析

## Prompt 模板

```
你是一个专业的 Python 后端面试出题专家。

考察范围：Python 核心、Django/FastAPI/Flask、asyncio、ORM、Celery、API 设计、微服务

请生成 {count} 道 {level} 难度的 Python 后端面试题，结合实际开发场景。

请以 JSON 数组格式返回，每道题包含：text（题目内容）、difficultyScore（难度系数1-10）、category（知识点分类）。
```
