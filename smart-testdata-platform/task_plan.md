# 任务计划 — 智能测试数据生成与隐私脱敏平台

## 项目概述

基于大模型 Agent 的智能测试数据生成与隐私脱敏平台（毕业设计）。

**技术栈：**
- 后端：Spring Boot 3.3 + MyBatis-Plus + Flyway + JWT
- 前端：Vue 3
- AI 服务：FastAPI

## 阶段

| 阶段 | 状态 | 说明 |
|------|------|------|
| Phase 1 | ✅ 完成 | 项目骨架 + Docker + Flyway 建表 |
| Phase 2 | ✅ 完成 | JWT 认证 + 项目 CRUD + 前端页面 |
| Phase 3.1 | ✅ 完成 | 数据库 Schema 智能分析模块 |
| Phase 3.2 | ✅ 完成 | LLM Agent 测试数据生成规划器 |
| Phase 3.3-1 | ✅ 完成 | 数据库字段数据采样 |
| **Phase 3.3-2** | **✅ 完成** | **Schema 缓存** |
| Phase 3.4 | ⏳ 待开始 | 关系分析器（外键依赖关系图） |
| Phase 4 | ⏳ 待开始 | 规则引擎 |
| Phase 5 | ⏳ 待开始 | 数据生成引擎 |
| Phase 6 | ⏳ 待开始 | 隐私脱敏模块 |
| Phase 7 | ⏳ 待开始 | 前端完整页面 |
| Phase 8 | ⏳ 待开始 | 端到端集成测试 |
| Phase 9 | ⏳ 待开始 | 性能优化 |
| Phase 10 | ⏳ 待开始 | 部署 |
| Phase 11 | ⏳ 待开始 | 论文撰写 |

## 当前阶段：Phase 3.3-2 ✅

**Schema 缓存** — 将 information_schema 数据同步到本地表。

### 新增 API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/schema/cache/sync` | 同步 Schema 到缓存表 |
| GET | `/api/schema/cache/{datasourceId}` | 查询缓存的 Schema |
| POST | `/api/schema/sample` | 数据采样（优先读缓存） |

### 缓存优先策略

```
sampleTable()
  ├── hasCache(datasourceId)?
  │   ├── YES → getCachedColumnNames() → skip JDBC meta query
  │   └── NO  → getValidColumnNames(conn, dbName, tableName) → direct JDBC + WARN log
  └── sample each column → return results
```
