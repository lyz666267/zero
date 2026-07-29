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
| **Phase 3.4** | **✅ 完成** | **关系分析器（外键依赖关系图）** |
| Phase 4 | ⏳ 待开始 | 规则引擎 |
| Phase 5 | ⏳ 待开始 | 数据生成引擎 |
| Phase 6 | ⏳ 待开始 | 隐私脱敏模块 |
| Phase 7 | ⏳ 待开始 | 前端完整页面 |
| Phase 8 | ⏳ 待开始 | 端到端集成测试 |
| Phase 9 | ⏳ 待开始 | 性能优化 |
| Phase 10 | ⏳ 待开始 | 部署 |
| Phase 11 | ⏳ 待开始 | 论文撰写 |

## 当前阶段：Phase 3.4 ✅

**关系分析器** — 读取外键关系、构建依赖图、拓扑排序确定生成顺序。

### 新增模块

| 文件 | 说明 |
|------|------|
| `schema/relation/RelationAnalyzerService.java` | 读取 `information_schema.KEY_COLUMN_USAGE` 获取 FK |
| `schema/relation/DependencyGraphService.java` | 构建依赖图（节点 + 有向边） |
| `schema/relation/TableOrderService.java` | Kahn 拓扑排序 + 循环依赖检测 |
| `dto/RelationAnalysisResponse.java` | 综合响应 DTO（relations + graph + order） |

### 新增 API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/schema/relation/{datasourceId}` | 关系分析（FK + 依赖图 + 生成顺序） |

### 响应结构

```json
{
  "relations": [{ "table": "employee", "column": "department_id",
                   "referencedTable": "department", "referencedColumn": "id" }],
  "graph": { "nodes": ["employee", "department"],
             "edges": [{ "from": "employee", "to": "department" }] },
  "generationOrder": ["department", "employee"]
}
```

### 测试结果

| 测试 | 结果 |
|------|------|
| employee.department_id → department.id | ✅ 正确识别 |
| 依赖图节点+边 | ✅ 正确 |
| 生成顺序 department → employee | ✅ 被依赖表在前 |
| 无外键数据库 | ✅ 空 relations/graph/order |
| 循环依赖 (A→B, B→A) | ✅ BusinessException "存在循环依赖..." |
