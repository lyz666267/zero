# 任务计划 — 智能测试数据生成与隐私脱敏平台

## 项目概述

基于大模型 Agent 的智能测试数据生成与隐私脱敏平台（毕业设计）。

**技术栈：**
- 后端：Spring Boot 3.3 + MyBatis-Plus + Flyway + JWT
- 前端：Vue 3 + Element Plus + Vite
- AI 服务：Python FastAPI + LangChain + DeepSeek (主) / Qwen (备)

## 阶段总览（对齐设计文档 11 阶段）

| 阶段 | 状态 | 说明 |
|------|------|------|
| Phase 1 | ✅ 完成 | 项目骨架 + Docker Compose + Flyway 建表 |
| Phase 2 | ✅ 完成 | JWT 认证 + 项目 CRUD + 前端 Login/Dashboard/ProjectList |
| Phase 3 | ✅ 完成 | 数据源管理 + Schema 分析 + 关系分析器 + Schema 缓存 |
| Phase 4 | 🔄 部分完成 | 规则引擎（第1层字段名规则 ✅｜第2层正则 ❌｜第3层 LLM ❌） |
| Phase 5 | 🔄 部分完成 | Python AI 服务（generate-plan ✅｜schema/analyze ❌｜strategy ❌） |
| Phase 6 | ✅ 完成 | 数据生成引擎（10 个生成器 + 单表/多表 + FK + SQL + DB 批量写入） |
| Phase 7 | ✅ 完成 | 任务调度（异步执行 + 状态机 + 轮询 + TaskMonitor） |
| Phase 8 | 🔄 部分完成 | 隐私脱敏（后端全链路 ✅｜DB 脱敏执行 ❌｜前端 MaskConfig ❌） |
| Phase 9 | 🔄 部分完成 | 前端完善（11 页面 ✅｜MaskConfig ❌｜数据导出 ❌） |
| Phase 10 | ❌ 未开始 | 论文撰写 + 答辩准备 |
| Phase 11 | ❌ 未开始 | 简历包装 + 云部署 |

---

## 详细子阶段

### Phase 1 — 项目初始化 ✅
- [x] Spring Boot 3.3 项目骨架
- [x] Docker Compose (MySQL + Python AI)
- [x] Flyway V1~V2 建表迁移
- [x] MyBatis-Plus + JWT 基础配置

### Phase 2 — 认证 + 项目 CRUD ✅
- [x] JWT 登录/注册 (AesUtil 密码加密)
- [x] Project CRUD (Entity + Mapper + Service + Controller)
- [x] ApiResponse<T> 统一响应包装
- [x] GlobalExceptionHandler 全局异常处理
- [x] 前端：Login.vue + Dashboard.vue + ProjectList.vue
- [x] 前端：Vue Router 4 + Pinia + Axios 拦截器

### Phase 3 — 数据源 + Schema 分析 ✅

**3.1 数据库 Schema 智能分析 ✅**
- [x] MetadataReader — 读取 information_schema (TABLES + COLUMNS)
- [x] Datasource CRUD (AES 加密存储密码)
- [x] 前端：DatasourceManage.vue

**3.2 LLM Agent 规划器 ✅**
- [x] TestdataService — 代理 AI 服务 POST /api/ai/generate-plan
- [x] GeneratePlanRequest/Response DTO
- [x] RestClient 集成 + Jackson 配置

**3.3 数据采样 + Schema 缓存 ✅**
- [x] SchemaSampleService — 字段数据采样
- [x] Flyway V3 — schema_table / schema_column 缓存表
- [x] SchemaCacheService — 同步 + 读取缓存
- [x] 前端：SchemaView.vue

**3.4 关系分析器 ✅**
- [x] RelationAnalyzerService — 读取 KEY_COLUMN_USAGE
- [x] DependencyGraphService — 构建外键依赖图
- [x] TableOrderService — Kahn 拓扑排序 + 循环检测
- [x] 前端：RelationGraph.vue

### Phase 4 — 规则引擎（三层架构）🔄

**第 1 层：字段名关键词匹配 ✅**
- [x] SensitiveFieldDetector — 6 类关键词 → 敏感类型识别
- [x] SensitiveFieldType 枚举 (PHONE/EMAIL/ID_CARD/NAME/ADDRESS/BANK_CARD)
- [ ] ~~独立 RegexRule 类~~ ❌ 未实现

**第 2 层：正则表达式检测 ❌**
- [ ] 身份证格式正则 `\d{17}[\dXx]`
- [ ] 手机号格式正则 `1[3-9]\d{9}`
- [ ] 邮箱格式正则
- [ ] 银行卡格式正则

**第 3 层：LLM 语义判断 ❌**
- [ ] LLM 辅助识别非常规敏感字段
- [ ] 语义上下文分析

**前端 ❌**
- [ ] MaskConfig.vue — 脱敏规则配置页

### Phase 5 — Python AI 服务 🔄

**已完成 ✅**
- [x] POST /api/ai/generate-plan — 完整可用（LLM + Mock 双模式）
- [x] TestDataAgent — 字段名→生成器映射（100+ 关键词）
- [x] TestDataAgent — MySQL 类型→生成器映射（25+ 类型）
- [x] LLMService — OpenAI SDK 封装 DeepSeek
- [x] LLMRouter — LangChain 主备路由（DeepSeek + Qwen）
- [x] Pydantic Schema — generation_plan.py

**未完成 ❌**
- [ ] POST /api/ai/schema/analyze — 占位符 `not_implemented`
- [ ] POST /api/ai/generate-strategy — 占位符 `not_implemented`
- [ ] Chains 编排 — chains/ 目录为空
- [ ] Agent 工具调用 — tools/ 目录为空
- [ ] LLMRouter 自动降级 — 未做超时/限流自动切换

### Phase 6 — 数据生成引擎 ✅

**6.1 生成器基础框架 ✅**
- [x] Generator 接口 + GeneratorRegistry + GeneratorEngine
- [x] 10 个生成器：Name/Email/Integer/Word/Boolean/Enum/Decimal/DateTime/UUID/Phone

**6.2 单表生成 ✅**
- [x] TableDataGenerator — 批量生成 + LinkedHashMap
- [x] POST /api/testdata/generator/table

**6.3 多表生成 ✅**
- [x] GenerationContext — ConcurrentHashMap 记录主键
- [x] ForeignKeyGenerator — 从上下文随机选取外键值
- [x] MultiTableDataGenerator — 结合 TableOrderService 按依赖顺序生成
- [x] POST /api/testdata/generator/multi-table

**6.4 SQL 生成 + 数据库写入 ✅**
- [x] InsertSqlBuilder — INSERT SQL 生成 + 字符串转义
- [x] InsertStatementBuilder — 参数化 SQL (? 占位符)
- [x] DatabaseWriter — JdbcTemplate.batchUpdate
- [x] MultiTableWriteService — TransactionTemplate 事务保护
- [x] POST /api/testdata/sql/build + POST /api/testdata/write

**单元测试：95/95 ✅**

### Phase 7 — 任务调度 ✅

**7.1 任务管理 ✅**
- [x] Flyway V4~V5 — testdata_task 表
- [x] TestDataTask Entity + Mapper + Service + Controller
- [x] POST /api/testdata/task + GET /api/testdata/task/{id}

**7.2 异步执行 ✅**
- [x] AsyncConfig — testdata-task- 线程池 (core=2/max=4/queue=100)
- [x] TestDataTaskExecutor — @Async 后台执行全流程
- [x] 状态机：PENDING → RUNNING → SUCCESS / FAILED
- [x] 前端：TestDataTask.vue + TaskMonitor.vue（3s 轮询 + 进度条）

**7.3 结果查看 ✅**
- [x] TestDataResultService + TestDataResultController
- [x] GET /api/testdata/task/{id}/result
- [x] GET /api/testdata/task/{id}/plan
- [x] 前端：TestDataResult.vue + GenerationPlan.vue

### Phase 8 — 隐私脱敏 🔄

**已完成 ✅**
- [x] SensitiveFieldDetector — 字段名关键词识别（Phase 3.7-1）
- [x] MaskRuleRegistry — 6 类默认规则映射（Phase 3.7-2）
- [x] DefaultMaskExecutor — 5 种脱敏方式（Phase 3.7-3）
- [x] PrivacyAwareDataProcessor — 融合编排（Phase 3.7-4）
- [x] PrivacyController — POST /api/privacy/process
- [x] 95 个单元测试全部通过

**未完成 ❌**
- [ ] 数据库脱敏执行 — 对目标数据库生成 UPDATE SQL + 执行
- [ ] 前端 MaskConfig.vue — 脱敏规则配置页
- [ ] 脱敏规则自定义 — 用户可修改规则（当前硬编码）

### Phase 9 — 前端完善 🔄

**已完成 ✅ — 12 条路由，11 个页面**
| 页面 | 路由 | 说明 |
|------|------|------|
| Login.vue | /login | 登录页 |
| Dashboard.vue | /dashboard | 工作台 |
| ProjectList.vue | /projects | 项目管理 |
| DatasourceManage.vue | /datasources | 数据源管理 |
| TestDataGenerate.vue | /testdata | 测试数据生成 |
| TestDataTask.vue | /testdata/task | 创建生成任务 |
| TaskMonitor.vue | /task-monitor | 任务监控（进度条+轮询） |
| TestDataResult.vue | /testdata/result | 生成结果查看 |
| SchemaView.vue | /schema/view | Schema 结构 |
| RelationGraph.vue | /schema/relation | 数据库关系图 |
| GenerationPlan.vue | /testdata/plan | AI 生成计划 |

**未完成 ❌**
- [ ] MaskConfig.vue — 脱敏规则配置页
- [ ] 数据导出 — CSV / SQL 文件导出功能
- [ ] 全流程端到端集成（创建任务 → 监控 → 查看结果 → 脱敏）

### Phase 10 — 论文 + 答辩 ❌
- [ ] 毕业设计文档
- [ ] 开题报告
- [ ] 毕业论文正文
- [ ] 答辩 PPT

### Phase 11 — 简历 + 部署 ❌
- [ ] Docker Compose 生产环境配置
- [ ] GitHub README 完善
- [ ] 云服务器部署
- [ ] 简历项目经历

---

## 设计文档对齐说明

任务计划已按 [fluffy-puzzling-lynx.md](C:\Users\21776\OneDrive\桌面\fluffy-puzzling-lynx.md) 11 阶段重新对齐。
task_plan 历史子阶段编号（3.1~3.7-4）已全部对应到设计文档阶段：

| 历史子阶段 | 对应设计文档阶段 | 内容 |
|-----------|----------------|------|
| 3.1~3.4 | Phase 3 | 数据源 + Schema + 缓存 + 关系分析 |
| 3.5-1~3.6-2 | Phase 6 | 数据生成引擎（含 SQL + DB 写入） |
| 3.6-3-1~3.6-3-2 | Phase 7 | 任务调度（含异步执行） |
| 3.7-1~3.7-4 | Phase 8 (部分) | 隐私脱敏后端全链路 |
| — | Phase 4 (部分) | 规则引擎第 1 层（字段名检测 = 3.7-1 的 Detector） |

---

## 当前阶段：Phase 4/5/8/9 — 补齐未完成功能

**建议优先级：**
1. Phase 4 第 2 层（正则检测）— 工作量小，补齐"三层架构"
2. Phase 8 数据库脱敏执行 + MaskConfig.vue — 打通完整闭环
3. Phase 9 数据导出 — 提升演示体验
4. Phase 5 AI 服务完善 — 低优先级（Mock 模式已可用）
5. Phase 10/11 — 论文 + 部署
