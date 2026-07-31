# 任务计划 — 智能测试数据生成与隐私脱敏平台

> **📋 优化执行计划：** 详见 [optimization_plan.md](optimization_plan.md) — 从"毕业设计完成版"到"测试开发面试展示版"的 37 项优化任务

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
| Phase 4 | ✅ 完成 | 规则引擎（三层架构：Keyword + Regex + LLM） |
| Phase 5 | ✅ 完成 | Python AI 服务（generate-plan ✅｜analyze-schema ✅｜LLM Router ✅｜StrategyAgent ✅｜Chain ✅｜Tool Calling ✅） |
| Phase 6 | ✅ 完成 | 数据生成引擎（10 个生成器 + 单表/多表 + FK + SQL + DB 批量写入） |
| Phase 7 | ✅ 完成 | 任务调度（异步执行 + 状态机 + 轮询 + TaskMonitor） |
| Phase 8 | 🔄 部分完成 | 隐私脱敏（后端全链路 ✅｜三层检测 ✅｜Agent 轨迹 ✅｜质量评分 ✅｜DB 脱敏执行 ✅｜规则持久化 ❌） |
| Phase 9 | 🔄 部分完成 | 前端完善（16 页面 ✅｜端到端集成 ✅） |
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

### Phase 4 — 规则引擎（三层架构）✅

**第 1 层：字段名关键词匹配 ✅**
- [x] SensitiveFieldDetector — 6 类关键词 → 敏感类型识别
- [x] SensitiveFieldType 枚举 (PHONE/EMAIL/ID_CARD/NAME/ADDRESS/BANK_CARD)

**第 2 层：正则表达式检测 ✅ (Phase 8.1)**
- [x] 手机号格式正则 `1[3-9]\d{9}`
- [x] 邮箱格式正则
- [x] 身份证格式正则 `\d{17}[\dXx]`
- [x] 银行卡格式正则 `\d{16,19}`
- [x] 数字类型跳过（防止 ID 误判）

**第 3 层：LLM 语义判断 ✅ (Phase 8.1)**
- [x] LLM 辅助识别非常规敏感字段
- [x] POST /api/ai/detect-sensitive 端点
- [x] 语义上下文分析（字段名 + 注释 + 样本值）

**三层融合编排 ✅ (Phase 8.1)**
- [x] CompositeSensitiveDetector — 优先级融合：Regex > Keyword > LLM
- [x] KeywordSensitiveDetector — 委托现有 SensitiveFieldDetector
- [x] RegexSensitiveDetector — 数据值正则匹配
- [x] LLMSensitiveDetector — 调用 AI 服务 LLM Router
- [x] PrivacyController — POST /api/privacy/process-auto 自动检测端点

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

**Phase 5.1 — Schema 理解 Agent ✅**
- [x] POST /api/ai/analyze-schema — LLM + Mock 双模式
- [x] POST /api/ai/schema/analyze — 别名端点
- [x] SchemaAgent — 字段语义标签识别（14 类）
- [x] SchemaAgent — 敏感字段检测（6 类 PII）
- [x] SchemaAgent — 外键关系推断（xxx_id 模式匹配）
- [x] SchemaAgent — 生成器推荐（语义标签 + 类型匹配）
- [x] SchemaAnalyzeService — Spring Boot 代理 AI 服务
- [x] SchemaAnalyzeController — POST /api/schema/analyze
- [x] SecurityConfig 白名单 — /api/schema/** 开放

**Phase 5.2 — LLM Router 自动故障切换 ✅**
- [x] `app/llm/base.py` — LLMProvider 统一接口 + LLMProviderError + RouterExhaustedError
- [x] `app/llm/deepseek_provider.py` — DeepSeekProvider 封装（OpenAI SDK）
- [x] `app/llm/qwen_provider.py` — QwenProvider 封装（OpenAI 兼容接口）
- [x] `app/llm/router.py` — LLMRouter 主备自动切换（超时/429/5xx/连接错误）
- [x] `app/llm/__init__.py` — 包导出 + 全局 `llm_router` 单例
- [x] SchemaAgent 改用 LLMRouter（替换直接调用 llm_service）
- [x] 25 个 Python 单元测试全部通过（3 大场景全覆盖）
- [x] 故障切换链：DeepSeek → Qwen → RouterExhaustedError → Mock 降级

**Phase 5.3 — Strategy Agent + Chain 编排 ✅**
- [x] `app/prompts/strategy_prompt.py` — StrategyAgent System Prompt（策略生成指导）
- [x] `app/agents/strategy_agent.py` — StrategyAgent（LLM + Mock 双模式）
- [x] `app/chains/generation_chain.py` — GenerationChain（SchemaAgent → StrategyAgent）
- [x] `app/chains/__init__.py` — Chains 模块导出
- [x] POST /api/ai/generate-plan — 升级为 GenerationChain 流水线
- [x] POST /api/ai/generate-strategy — 实现（接收已分析结果 + 需求）
- [x] TestDataAgent 重构 — 改用 LLMRouter（替换直接调用 llm_service）
- [x] 27 个 Python 单元测试全部通过（StrategyAgent + GenerationChain）
- [x] 全部 52 个 Python 测试通过（25 existing + 27 new）

**Phase 5.4 — Agent Tool Calling 工具调用 ✅**
- [x] `app/tools/base.py` — Tool 统一接口（name/description/parameters_schema/execute）
- [x] `app/tools/schema_tool.py` — SchemaTool：获取数据库 Schema 信息
- [x] `app/tools/sample_tool.py` — SampleTool：获取字段真实样本数据
- [x] `app/tools/relation_tool.py` — RelationTool：获取外键关系
- [x] `app/tools/tool_registry.py` — ToolRegistry：统一注册/查找/执行/LLM 描述生成
- [x] `app/agents/tool_agent.py` — ToolAgent：ReAct 风格 "思考→行动→观察" 循环
- [x] POST /api/ai/tool-agent — 新增 API 端点
- [x] 54 个新测试全部通过（Tools 17 + Registry 9 + Mock 7 + LLM 5 + Helpers 8 + Interface 3 + Integration 3）
- [x] 全部 106 个 Python 测试通过（52 existing + 54 new）

**未完成 ❌**
- [ ] —（Phase 5 全部子阶段已完成）

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
- [x] **Phase 8.1 — 三层敏感字段识别增强 ✅**
  - [x] KeywordSensitiveDetector — 委托现有 SensitiveFieldDetector
  - [x] RegexSensitiveDetector — 手机号/邮箱/身份证/银行卡正则
  - [x] LLMSensitiveDetector — 调用 AI 服务 /api/ai/detect-sensitive
  - [x] CompositeSensitiveDetector — Regex > Keyword > LLM 融合
  - [x] PrivacyController — POST /api/privacy/process-auto
  - [x] Python — POST /api/ai/detect-sensitive + LLMRouter
- [x] 所有 159 个单元测试通过（原 95 + 新增 64）

**Phase 8.2-1 — Agent 执行轨迹可视化 ✅**
- [x] AgentExecutionLog 实体 + Flyway V8 迁移 — `agent_execution_log` 表
- [x] AgentExecutionLogMapper + AgentLogService + AgentLogController
- [x] GET /api/agent/log/{taskId} — 查询 Agent 执行步骤
- [x] TestDataTaskExecutor 6 步日志（PARSE→ANALYZE→PLAN→GENERATE→PRIVACY→COMPLETE）
- [x] Python `app/tools/trace.py` — AgentTrace 轨迹记录器
- [x] tool_registry.execute() 执行计时（execution_time_ms）
- [x] ToolAgent LLM/Mock 双模式 AgentTrace 集成
- [x] POST /api/ai/tool-agent 响应新增 `trace` 字段
- [x] 前端 AgentTrace.vue — 时间线可视化（步骤+状态+耗时+输入输出详情）
- [x] 前端路由 `/agent-trace` + API 模块 `agent.js`

**Phase 8.2-2 — 隐私脱敏可视化增强 ✅**
- [x] GET /api/privacy/rules — 返回全部脱敏规则（类型+策略+描述+示例前后对比）
- [x] POST /api/privacy/test — 实时测试脱敏效果
- [x] MaskRuleResponse / MaskTestRequest / MaskTestResponse DTOs
- [x] 前端 MaskConfig.vue — 规则卡片展示+实时脱敏测试+三层检测流程可视化
- [x] 前端 api/privacy.js — 脱敏 API 模块
- [x] 全页面侧边栏新增"隐私脱敏配置"导航入口

**未完成 ❌**
- [ ] 脱敏规则自定义持久化 — 用户可修改规则（当前硬编码到 MaskRuleRegistry）

**Phase 8.4-1 — 数据库脱敏执行模块 ✅**
- [x] Flyway V10 — data_mask_task 表（datasource_id/table_name/status/sql_preview/execute_result/affected_rows）
- [x] DataMaskTask 实体 + DataMaskTaskMapper
- [x] DatabaseMaskService — SQL 生成 + 安全检查 + 预览→确认→执行流程
  - [x] 6 类 MySQL UPDATE SQL 生成（PHONE/NAME/ID_CARD/EMAIL/ADDRESS/BANK_CARD）
  - [x] 9 项 SQL 安全检查（禁止 DROP/DELETE/TRUNCATE/ALTER/CREATE/INSERT + 表名校验 + 非UPDATE检测）
  - [x] 强制预览→确认→执行流程，禁止直接执行
- [x] DatabaseMaskController — 3 个 API 端点
  - [x] POST /api/privacy/database/preview — 敏感字段检测 + UPDATE SQL 预览
  - [x] POST /api/privacy/database/execute — 执行确认后的 SQL
  - [x] GET  /api/privacy/database/task/{id} — 查询执行结果
- [x] SecurityConfig — /api/privacy/database/** 已由 /api/privacy/** 覆盖
- [x] 前端 DatabaseMask.vue — 四步流程（选择表→分析敏感字段→SQL预览→执行结果）
- [x] 前端 api/databaseMask.js — 脱敏执行 API 模块
- [x] 前端路由 /database-mask + 15 页面侧边栏"数据库脱敏"导航
- [x] DatabaseMaskServiceTest — 28 个测试（SQL生成8 + 安全检查10 + 执行流程7 + 边界3）

### Phase 9 — 前端完善 🔄
- [x] Flyway V9 — data_quality_report 表（task_id/综合评分/等级/5项指标得分/问题明细JSON）
- [x] DataQualityReport 实体 + DataQualityReportMapper
- [x] DataQualityEvaluator — 五项指标评估服务
  - [x] 完整性（Completeness, 25%）— 非空比例 + 必填字段缺失检测
  - [x] 唯一性（Uniqueness, 20%）— 主键重复 + 整行重复检测
  - [x] 关联一致性（Consistency, 25%）— 外键引用有效性验证
  - [x] 格式合法性（Validity, 15%）— 邮箱/手机号/日期/身份证/银行卡格式正则校验
  - [x] 隐私安全（Privacy, 15%）— 敏感字段脱敏状态检查
  - [x] 加权评分算法：completeness×0.25 + uniqueness×0.20 + consistency×0.25 + validity×0.15 + privacy×0.15
  - [x] A/B/C/D 等级评定：90+ 优秀 / 80+ 良好 / 60+ 合格 / <60 不合格
- [x] QualityController — POST /api/quality/evaluate/{taskId} + GET /api/quality/report/{taskId}
- [x] TestDataTaskExecutor 集成 — 生成完成后自动触发质量评估（Step 5.5）
- [x] SecurityConfig — /api/quality/** 白名单
- [x] 前端 DataQuality.vue — 综合评分总览+五维雷达图(ECharts)+指标进度条+问题列表+改进建议
- [x] 前端 api/quality.js — 质量评估 API 模块
- [x] 前端路由 /data-quality + 14 页面侧边栏"数据质量评分"导航
- [x] DataQualityEvaluatorTest — 18 个测试（完整性/唯一性/一致性/合法性/隐私/综合评分/等级/报告CRUD/空数据）

### Phase 9 — 前端完善 🔄

**已完成 ✅ — 16 条路由，16 个页面**
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
| MaskConfig.vue | /privacy | 隐私脱敏配置 |
| DataQuality.vue | /data-quality | 数据质量评分 ✅ |
| DatabaseMask.vue | /database-mask | 数据库脱敏 ✅ |
| DataExport.vue | /data-export | 数据导出 ✅ |

**未完成 ❌**
- [ ] 全流程端到端集成（创建任务 → 监控 → 查看结果 → 脱敏）

**Phase 9.1 — 测试数据导出模块 ✅**
- [x] ExportService — CSV / SQL INSERT / JSON 三种格式导出
  - CSV：带文件头注释 + 表头行 + 逗号分隔 + 字符串引号转义（复用 TestDataResultService.findDataByTaskId）
  - SQL：批量 INSERT INTO ... VALUES 语句（复用 InsertSqlBuilder.build）
  - JSON：美化输出的按表分组嵌套结构（Jackson ObjectMapper）
  - listExportableTasks — 列出 SUCCESS 状态的可导出任务
- [x] ExportController — 3 个 API 端点
  - GET  /api/export/tasks — 列出可导出任务
  - GET  /api/export/task/{taskId}?format=CSV|SQL|JSON — 浏览器友好下载
  - POST /api/export/task/{taskId}?format=CSV|SQL|JSON — 文件流下载
- [x] SecurityConfig — /api/export/** 白名单
- [x] 前端 DataExport.vue — 四步流程（选择任务→数据预览→选择格式→导出下载）
- [x] 前端 api/export.js — 导出 API 模块（listExportableTasks / exportTaskData / previewExportData）
- [x] 前端路由 /data-export + 15 页面侧边栏"数据导出"导航
- [x] ExportServiceTest — 28 个测试（CSV转义7 + 格式校验6 + 任务校验2 + 文件名5 + JSON导出2 + CSV导出3 + SQL导出3 + 任务列表1）

**Phase 9.2 — 全流程端到端集成测试 ✅**
- [x] pom.xml — 新增 TestContainers 1.20.0 + MySQL + JUnit Jupiter 依赖
- [x] FullWorkflowIntegrationTest — 5 个 @Test，TestContainers MySQL 完整 12 步业务流程
  - Test 1: 全流程端到端（创建任务→Schema分析→AI计划→数据生成→DB写入→结果保存→敏感字段检测→隐私脱敏→质量评估→Agent轨迹→CSV/SQL/JSON导出）
  - Test 2: 生成数量验证（user=5行, order=10行, 全字段非空）
  - Test 3: 质量评分验证（五项指标 + 等级评定 + 权重验证）
  - Test 4: 导出完整性验证（CSV/SQL/JSON 三种格式 + JSON 合法性）
  - Test 5: 可导出任务列表（SUCCESS 状态过滤）
- [x] 测试数据库 — TestContainers MySQL 8.0.33（自动启动 + Flyway 迁移 + 动态建表）
- [x] 测试表 — user（id/name/phone/email）+ order（id/user_id/amount, FK→user.id）
- [x] Mock AI 服务 — @MockBean TestdataService（仅 Mock AI 调用，其余全部真实组件）
- [x] 编译验证 — `mvn test-compile` ✅ BUILD SUCCESS

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

## 当前阶段：Phase 10 — 论文 + 答辩准备

**建议优先级：**
1. Phase 10 — 毕业论文撰写 + 答辩 PPT 制作
2. Phase 8 脱敏规则自定义持久化 — 低优先级优化
3. Phase 11 — 云部署 + 简历包装
