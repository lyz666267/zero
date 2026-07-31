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
| Phase 4 — 规则引擎（三层） | 🔄 | 33% |
| Phase 5 — Python AI 服务 | 🔄 | 40% |
| Phase 6 — 数据生成引擎 | ✅ | 100% |
| Phase 7 — 任务调度 | ✅ | 100% |
| Phase 8 — 隐私脱敏 | 🔄 | 60% |
| Phase 9 — 前端完善 | 🔄 | 75% |
| Phase 10 — 论文 + 答辩 | ❌ | 0% |
| Phase 11 — 简历 + 部署 | ❌ | 0% |

**总完成度：约 65%**

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

### Phase 8 — 隐私脱敏 🔄 (60%)

**已完成：**
- SensitiveFieldDetector — 6 类敏感字段识别
- MaskRuleRegistry — 6 类默认规则
- DefaultMaskExecutor — 5 种脱敏方式 (mask/replace/hash/generalize/perturb)
- PrivacyAwareDataProcessor — 识别→策略→执行 融合编排
- PrivacyController — POST /api/privacy/process

**未完成：**
- 数据库脱敏执行（UPDATE SQL 生成 + 执行）
- 前端 MaskConfig.vue

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

**共 26 个后端 API 端点**

### Python AI 服务

| 方法 | 路径 | 状态 |
|------|------|------|
| POST | `/api/ai/generate-plan` | ✅ LLM + Mock 双模式 |
| POST | `/api/ai/schema/analyze` | ❌ not_implemented |
| POST | `/api/ai/generate-strategy` | ❌ not_implemented |

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
| **MaskConfig.vue** | — | ❌ 未创建 |

---

## 待完成清单（按优先级）

### 高优先级（答辩核心功能）
1. **Phase 4 第 2 层** — 正则表达式检测规则
2. **Phase 8** — 数据库脱敏执行（UPDATE SQL 生成）
3. **Phase 9** — MaskConfig.vue 脱敏配置页

### 中优先级（演示体验）
4. **Phase 4 第 3 层** — LLM 语义辅助规则
5. **Phase 9** — 数据导出 CSV/SQL
6. **Phase 5** — AI 服务完善（Schema 分析 + 策略生成）

### 低优先级（非核心）
7. **Phase 10** — 论文撰写（需等代码完成）
8. **Phase 11** — Docker Compose 生产配置 + 部署

---

## 测试统计

```
后端单元测试: 95/95 ✅
  - 1   AppTest (Spring 容器)
  - 34  GeneratorTest (生成器 + 表生成 + 多表 + SQL)
  - 6   DatabaseWriterTest (H2 内存数据库)
  - 12  SensitiveFieldDetectorTest
  - 8   MaskRuleRegistryTest
  - 17  MaskExecutorTest
  - 13  PrivacyAwareDataProcessorTest
  - 4   TestDataTaskExecutorTest

Python AI: 无自动化测试（手工测试通过）
前端: 无自动化测试（手工测试通过）
```
