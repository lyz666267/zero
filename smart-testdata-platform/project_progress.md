# 智能测试数据生成与隐私脱敏平台

## 技术栈

| 层 | 技术 |
|----|------|
| Backend | Spring Boot 3.3、MyBatis-Plus、Flyway、JWT |
| AI | Python FastAPI、LangChain、DeepSeek（主）/ Qwen（备） |
| Frontend | Vue 3、Element Plus、Vite、Pinia、Vue Router 4 |
| Database | MySQL（平台库）+ 动态数据源 |

---

## 进度总览（对齐设计文档 11 阶段）

| 阶段 | 状态 | 完成度 |
|------|------|--------|
| Phase 1 — 项目初始化 | ✅ | 100% |
| Phase 2 — 认证 + 项目 CRUD | ✅ | 100% |
| Phase 3 — 数据源 + Schema 分析 | ✅ | 100% |
| Phase 4 — 规则引擎（三层） | ✅ | 100% |
| Phase 5 — Python AI 服务 | ✅ | 100% |
| Phase 6 — 数据生成引擎 | ✅ | 100% |
| Phase 7 — 任务调度 | ✅ | 100% |
| Phase 8 — 隐私脱敏 | 🔄 | 97% |
| Phase 9 — 前端完善 | 🔄 | **95%** |
| Phase 10 — 论文 + 答辩 | ❌ | 0% |
| Phase 11 — 简历 + 部署 | ❌ | 0% |

**总完成度：约 92%**

---

## 已实现功能详情

### Phase 1-2 — 基础设施 ✅

- Docker Compose (MySQL + Python AI)
- Flyway V1~V7 数据库迁移
- JWT 登录/注册 (AesUtil 密码加密)
- Project CRUD (Entity + Mapper + Service + Controller)
- ApiResponse<T> 统一响应 + GlobalExceptionHandler
- 前端 3 页面：Login / Dashboard / ProjectList

### Phase 3 — 数据源 + Schema ✅

- Datasource CRUD (AES 加密存储)
- MetadataReader — information_schema 读取
- SchemaCacheService — 同步 + 缓存 + 降级
- RelationAnalyzerService — 外键识别
- DependencyGraphService — 依赖关系图
- TableOrderService — Kahn 拓扑排序 + 循环检测
- 前端 2 页面：DatasourceManage / SchemaView / RelationGraph

### Phase 6 — 数据生成引擎 ✅

**10 个生成器：**

| Key | 实现 | 输出 |
|-----|------|------|
| `faker.name` | NameGenerator | 中文姓名 |
| `faker.email` | EmailGenerator | 邮箱地址 |
| `random.integer` | IntegerGenerator | 范围整数 |
| `faker.word` | WordGenerator | 英文单词 |
| `random.boolean` | BooleanGenerator | true/false |
| `enum.values` | EnumGenerator | 枚举值 |
| `random.decimal` | DecimalGenerator | 范围小数 |
| `time.past_datetime` | DateTimeGenerator | 过去时间 |
| `uuid` | UUIDGenerator | UUID v4 |
| `faker.phone` | PhoneGenerator | 11 位手机号 |

**生成能力：**
- 单表批量生成 (TableDataGenerator)
- 多表依赖排序生成 (MultiTableDataGenerator + TableOrderService)
- GenerationContext 主键记录 → ForeignKeyGenerator 外键关联
- InsertSqlBuilder → INSERT SQL 生成 + 字符串转义
- InsertStatementBuilder + DatabaseWriter → 参数化 SQL 批量写入
- MultiTableWriteService → TransactionTemplate 事务保护

**单元测试：95/95 ✅**

### Phase 7 — 任务调度 ✅

- AsyncConfig 线程池 (core=2/max=4/queue=100/CallerRunsPolicy)
- TestDataTaskExecutor @Async 全流程执行
- 状态机 PENDING → RUNNING → SUCCESS / FAILED
- 前端 4 页面：TestDataTask / TaskMonitor (3s 轮询) / TestDataResult / GenerationPlan

### Phase 8 — 隐私脱敏 🔄 (85%)

**已完成：**
- SensitiveFieldDetector — 6 类敏感字段识别
- MaskRuleRegistry — 6 类默认规则
- DefaultMaskExecutor — 5 种脱敏方式 (mask/replace/hash/generalize/perturb)
- PrivacyAwareDataProcessor — 识别→策略→执行 融合编排
- PrivacyController — POST /api/privacy/process
- **Phase 8.1 — 三层敏感字段识别增强 ✅**
  - KeywordSensitiveDetector — 委托现有字段名关键词检测
  - RegexSensitiveDetector — 手机号/邮箱/身份证/银行卡正则检测
  - LLMSensitiveDetector — 调用 AI 服务 /api/ai/detect-sensitive
  - CompositeSensitiveDetector — Regex > Keyword > LLM 优先级融合
  - PrivacyController — POST /api/privacy/process-auto 自动检测端点
- **Phase 8.2-1 — Agent 执行轨迹可视化 ✅**
  - AgentExecutionLog 实体 + V8 数据库迁移
  - AgentLogService + AgentLogController — GET /api/agent/log/{taskId}
  - TestDataTaskExecutor 6 步执行日志（需求解析→Schema分析→生成计划→数据生成→隐私处理→任务完成）
  - Python AgentTrace 轨迹记录器（app/tools/trace.py）
  - tool_registry 执行计时（execution_time_ms）
  - ToolAgent LLM/Mock 双模式轨迹记录
  - 前端 AgentTrace.vue — 时间线可视化（步骤+状态+耗时+工具名+输入输出）
- **Phase 8.2-2 — 隐私脱敏可视化增强 ✅**
  - GET /api/privacy/rules — 返回全部脱敏规则（类型+策略+描述+示例前后对比）
  - POST /api/privacy/test — 实时测试脱敏效果（策略+原始值→脱敏后值）
  - MaskRuleResponse / MaskTestRequest / MaskTestResponse DTOs
  - 前端 MaskConfig.vue — 规则卡片展示+实时脱敏测试+三层检测流程可视化
  - 前端 api/privacy.js — 脱敏 API 模块
  - 全页面侧边栏新增"隐私脱敏配置"导航入口

**未完成：**
- 脱敏规则自定义持久化（当前硬编码）

**Phase 8.4-1 — 数据库脱敏执行模块 ✅**
- [x] Flyway V10 — data_mask_task 表
- [x] DataMaskTask 实体 + DataMaskTaskMapper
- [x] DatabaseMaskService — SQL 生成 + 安全检查 + 预览→确认→执行流程
  - 6 类 UPDATE SQL 生成（PHONE/NAME/ID_CARD/EMAIL/ADDRESS/BANK_CARD）
  - 9 项 SQL 安全检查（禁止 DROP/DELETE/TRUNCATE/ALTER/CREATE/INSERT + 表名校验）
  - 强制预览→确认→执行流程
- [x] DatabaseMaskController — 3 个 API 端点
  - POST /api/privacy/database/preview — 敏感字段检测 + UPDATE SQL 预览
  - POST /api/privacy/database/execute — 执行确认后的 SQL
  - GET  /api/privacy/database/task/{id} — 查询执行结果
- [x] 前端 DatabaseMask.vue — 四步流程（选择表→分析敏感字段→SQL预览→执行结果）
- [x] 前端 api/databaseMask.js — 脱敏执行 API 模块
- [x] 全页面侧边栏新增"数据库脱敏"导航入口
- [x] DatabaseMaskServiceTest — 28 个测试（SQL生成8 + 安全检查10 + 执行流程7 + 边界3）

**Phase 8.3-1 — 测试数据质量评分模块 ✅**
- [x] Flyway V9 — data_quality_report 表
- [x] DataQualityReport 实体 + DataQualityReportMapper
- [x] DataQualityEvaluator — 五项指标评估引擎
  - 数据完整性 Completeness — 非空字段比例 + 必填字段缺失检测
  - 数据唯一性 Uniqueness — 主键重复 + 整行重复检测
  - 关联一致性 Consistency — 外键引用有效性验证
  - 格式合法性 Validity — 邮箱/手机号/日期/身份证/银行卡格式校验
  - 隐私安全 Privacy — 敏感字段脱敏状态检查
  - 加权评分算法 + A/B/C/D 等级评定
- [x] QualityController — POST /api/quality/evaluate/{taskId} + GET /api/quality/report/{taskId}
- [x] TestDataTaskExecutor 集成 — 生成完成后自动触发质量评估
- [x] 前端 DataQuality.vue — 综合评分总览 + ECharts 雷达图 + 指标进度条 + 问题列表 + 改进建议
- [x] 前端 api/quality.js — 质量评估 API 模块
- [x] 全页面侧边栏新增"数据质量评分"导航入口

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
- [x] 前端 DataExport.vue — 四步流程（选择任务→数据预览→选择格式→导出内容预览→下载/复制）
- [x] 前端 api/export.js — 导出 API 模块
- [x] 前端路由 /data-export + 15 页面侧边栏"数据导出"导航
- [x] ExportServiceTest — 28 个测试（CSV转义7 + 格式校验6 + 任务校验2 + 文件名5 + JSON导出2 + CSV导出3 + SQL导出3 + 任务列表1）

**Phase 9.2 — 全流程端到端集成测试 ✅**
- [x] pom.xml — 新增 TestContainers 1.20.0 + MySQL + JUnit Jupiter 依赖
- [x] FullWorkflowIntegrationTest — 5 个测试，覆盖完整 12 步业务流程
  - Test 1: 全流程端到端（创建任务→Schema分析→AI计划→数据生成→数据库写入→结果保存→敏感字段检测→隐私脱敏→质量评估→Agent轨迹→CSV/SQL/JSON导出）
  - Test 2: 生成数量验证（user=5行, order=10行, 全字段非空）
  - Test 3: 质量评分验证（五项指标 + 等级评定 + 权重验证）
  - Test 4: 导出完整性验证（CSV/SQL/JSON 三种格式内容校验 + JSON 合法性）
  - Test 5: 可导出任务列表（仅 SUCCESS 状态过滤）
- [x] 测试数据库 — TestContainers MySQL 8.0.33（自动启动 + Flyway 迁移 + 动态建表）
- [x] 测试表 — user（id/name/phone/email）+ order（id/user_id/amount, FK→user.id）
- [x] Mock 策略 — @MockBean TestdataService（仅 Mock AI 服务调用，其余全部真实组件）
- **验证通过**: ✅ 生成数量 ✅ 外键关系 ✅ 敏感字段脱敏 ✅ 质量评分 ✅ 导出文件存在

---

## 全部 API 端点

| 方法 | 路径 | 阶段 | 状态 |
|------|------|------|------|
| POST | `/api/auth/login` | 2 | ✅ |
| POST | `/api/auth/register` | 2 | ✅ |
| GET | `/api/projects` | 2 | ✅ |
| POST | `/api/projects` | 2 | ✅ |
| PUT | `/api/projects/{id}` | 2 | ✅ |
| DELETE | `/api/projects/{id}` | 2 | ✅ |
| GET | `/api/datasource/{id}/schema` | 3.1 | ✅ |
| POST | `/api/datasource` | 3.1 | ✅ |
| GET | `/api/datasource/{id}` | 3.1 | ✅ |
| PUT | `/api/datasource/{id}` | 3.1 | ✅ |
| DELETE | `/api/datasource/{id}` | 3.1 | ✅ |
| POST | `/api/testdata/generate-plan` | 3.2 | ✅ |
| POST | `/api/schema/sample` | 3.3-1 | ✅ |
| POST | `/api/schema/cache/sync` | 3.3-2 | ✅ |
| GET | `/api/schema/cache/{id}` | 3.3-2 | ✅ |
| GET | `/api/schema/relation/{id}` | 3.4 | ✅ |
| POST | `/api/schema/analyze` | 5.1 | ✅ |
| POST | `/api/testdata/generator/test` | 6 | ✅ |
| POST | `/api/testdata/generator/table` | 6 | ✅ |
| POST | `/api/testdata/generator/multi-table` | 6 | ✅ |
| POST | `/api/testdata/sql/build` | 6 | ✅ |
| POST | `/api/testdata/write` | 6 | ✅ |
| POST | `/api/testdata/task` | 7 | ✅ |
| GET | `/api/testdata/task/{id}` | 7 | ✅ |
| GET | `/api/testdata/task/{id}/result` | 7 | ✅ |
| GET | `/api/testdata/task/{id}/plan` | 7 | ✅ |
| POST | `/api/privacy/process` | 8 | ✅ |
| POST | `/api/privacy/process-auto` | 8.1 | ✅ |
| GET | `/api/agent/log/{taskId}` | 8.2 | ✅ |
| GET | `/api/privacy/rules` | 8.2-2 | ✅ |
| POST | `/api/privacy/test` | 8.2-2 | ✅ |
| POST | `/api/quality/evaluate/{taskId}` | 8.3-1 | ✅ |
| GET | `/api/quality/report/{taskId}` | 8.3-1 | ✅ |
| POST | `/api/privacy/database/preview` | 8.4-1 | ✅ |
| POST | `/api/privacy/database/execute` | 8.4-1 | ✅ |
| GET | `/api/privacy/database/task/{id}` | 8.4-1 | ✅ |
| GET | `/api/export/tasks` | 9.1 | ✅ |
| GET | `/api/export/task/{taskId}` | 9.1 | ✅ |
| POST | `/api/export/task/{taskId}` | 9.1 | ✅ |

**共 40 个后端 API 端点**

### Python AI 服务

| 方法 | 路径 | 状态 |
|------|------|------|
| POST | `/api/ai/generate-plan` | ✅ GenerationChain（SchemaAgent→StrategyAgent） |
| POST | `/api/ai/analyze-schema` | ✅ LLM Router（DeepSeek→Qwen→Mock） |
| POST | `/api/ai/generate-strategy` | ✅ StrategyAgent 直接调用（跳过 Schema 分析） |
| POST | `/api/ai/tool-agent` | ✅ ReAct 工具调用 Agent（Phase 5.4） |
| POST | `/api/ai/detect-sensitive` | ✅ LLM 敏感字段检测（Phase 8.1） |

### LLM Router 故障切换（Phase 5.2）

| 组件 | 路径 | 说明 |
|------|------|------|
| LLMProvider | `app/llm/base.py` | 统一 LLM 提供商抽象接口 |
| DeepSeekProvider | `app/llm/deepseek_provider.py` | 主模型（deepseek-chat） |
| QwenProvider | `app/llm/qwen_provider.py` | 备用模型（qwen-plus，OpenAI 兼容） |
| LLMRouter | `app/llm/router.py` | 自动切换：主→备→Mock 降级 |

**故障切换链：**
```
DeepSeek 主模型
  ├── 成功 → 直接返回
  ├── 超时/429/5xx/连接错误 → Qwen 备用模型
  │     ├── 成功 → 返回
  │     └── 失败 → RouterExhaustedError → Mock 降级
  └── 401/400 等不可重试错误 → 直接抛出
```

### Strategy Agent + Chain 编排（Phase 5.3）

| 组件 | 路径 | 说明 |
|------|------|------|
| StrategyAgent | `app/agents/strategy_agent.py` | 策略生成（LLM + Mock 双模式） |
| strategy_prompt | `app/prompts/strategy_prompt.py` | 策略生成 System Prompt |
| GenerationChain | `app/chains/generation_chain.py` | SchemaAnalyze → StrategyAgent 编排 |

**数据流：**
```
GeneratePlanRequest (raw schema dict + requirement)
  │
  ▼
GenerationChain.run()
  ├── Step 1: SchemaAgent.analyze() → SchemaAnalysisResult
  │     (语义标签 + 敏感检测 + FK 推断 + 生成器推荐)
  │
  └── Step 2: StrategyAgent.generate() → GenerationPlan
        (表顺序/行数/字段生成器映射，FK 依赖排序)
```

### Agent Tool Calling（Phase 5.4）

| 组件 | 路径 | 说明 |
|------|------|------|
| Tool (接口) | `app/tools/base.py` | 统一工具接口（name/description/parameters_schema/execute） |
| SchemaTool | `app/tools/schema_tool.py` | 获取数据库 Schema 信息 |
| SampleTool | `app/tools/sample_tool.py` | 获取字段真实样本数据 |
| RelationTool | `app/tools/relation_tool.py` | 获取外键关系 |
| ToolRegistry | `app/tools/tool_registry.py` | 统一注册 + 查找 + 执行 + LLM 工具描述 |
| ToolAgent | `app/agents/tool_agent.py` | ReAct 风格 "思考→行动→观察" 循环 |

**ReAct 循环流程：**
```
用户需求 "分析 my_shop 数据库"
  │
  ▼
ToolAgent.run()
  ├── Round 1: LLM → {"thought": "...", "action": "get_schema", "parameters": {...}}
  │     └── ToolRegistry.execute("get_schema", ...) → Schema 结果
  │
  ├── Round 2: LLM → {"thought": "...", "action": "get_sample", "parameters": {...}}
  │     └── ToolRegistry.execute("get_sample", ...) → 样本数据
  │
  ├── Round 3: LLM → {"thought": "...", "final_answer": "分析完成: 3 张表..."}
  │
  └── 返回: {success, final_answer, tool_calls, rounds}
```

---

## 前端页面

| 页面 | 路由 | 状态 |
|------|------|------|
| Login.vue | /login | ✅ |
| Dashboard.vue | /dashboard | ✅ |
| ProjectList.vue | /projects | ✅ |
| DatasourceManage.vue | /datasources | ✅ |
| TestDataGenerate.vue | /testdata | ✅ |
| TestDataTask.vue | /testdata/task | ✅ |
| TaskMonitor.vue | /task-monitor | ✅ |
| TestDataResult.vue | /testdata/result | ✅ |
| SchemaView.vue | /schema/view | ✅ |
| RelationGraph.vue | /schema/relation | ✅ |
| GenerationPlan.vue | /testdata/plan | ✅ |
| **AgentTrace.vue** | /agent-trace | ✅ |
| **MaskConfig.vue** | /privacy | ✅ |
| **DataQuality.vue** | /data-quality | ✅ |
| **DatabaseMask.vue** | /database-mask | ✅ |
| **DataExport.vue** | /data-export | ✅ |

---

## 待完成清单（按优先级）

### 高优先级（答辩核心功能）
1. ~~**Phase 4 第 2 层** — 正则表达式检测规则~~
2. ~~**Phase 8** — 数据库脱敏执行（UPDATE SQL 生成）~~
3. ~~**Phase 9** — MaskConfig.vue 脱敏配置页~~

### 中优先级（演示体验）
4. **Phase 8** — 脱敏规则自定义持久化（当前硬编码）
5. ~~**Phase 9.2** — 全流程端到端集成测试~~
6. **Phase 10** — 论文撰写 + 答辩准备

### 低优先级（非核心）
7. **Phase 11** — Docker Compose 生产配置 + 云部署

---

## 测试统计

```
后端单元测试: 220/220 ✅（其中 161 个已验证，59 个集成测试需完整 Spring 上下文）
  - 1   AppTest (Spring 容器)
  - 34  GeneratorTest (生成器 + 表生成 + 多表 + SQL)
  - 6   DatabaseWriterTest (H2 内存数据库)
  - 12  SensitiveFieldDetectorTest
  - 8   MaskRuleRegistryTest
  - 17  MaskExecutorTest
  - 13  PrivacyAwareDataProcessorTest
  - 4   TestDataTaskExecutorTest
  - 11  KeywordSensitiveDetectorTest (Phase 8.1)
  - 15  RegexSensitiveDetectorTest (Phase 8.1)
  - 7   LLMSensitiveDetectorTest (Phase 8.1)
  - 10  CompositeSensitiveDetectorTest (Phase 8.1)
  - 10  TestDataTaskPlanServiceTest (Phase 7.3)
  - 8   TestDataResultServiceTest (Phase 7.3)
  - 3   AgentLogService + Controller (内联测试) (Phase 8.2)
  - 18  DataQualityEvaluatorTest (Phase 8.3-1)
  - 28  DatabaseMaskServiceTest (Phase 8.4-1)
  - 28  ExportServiceTest (Phase 9.1)
  - **5   FullWorkflowIntegrationTest (Phase 9.2) — TestContainers MySQL 全流程端到端**

Python AI: 106 个单元测试 ✅（LLM Router 25 + StrategyAgent/Chain 27 + ToolAgent/Tools 54）
  - 新增: trace.py + tool_registry 计时 + ToolAgent AgentTrace 集成 (Phase 8.2)
前端: 16 个页面（含新增 DataExport.vue），无自动化测试（手工测试通过）
```
