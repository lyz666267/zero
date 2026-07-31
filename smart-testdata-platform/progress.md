# 进度日志

## 会话 2026-07-31 — 全面进度审计（对齐设计文档 11 阶段）

### 目标

对照毕业设计文档 [fluffy-puzzling-lynx.md](C:\Users\21776\OneDrive\桌面\fluffy-puzzling-lynx.md) 的 11 个阶段，逐一核对代码实现状态。

### 审计结论

| 阶段 | 状态 | 完成度 | 关键证据 |
|------|------|--------|---------|
| Phase 1 项目初始化 | ✅ | 100% | Docker Compose + Flyway V1~V7 + Spring Boot 3.3 |
| Phase 2 认证+CRUD | ✅ | 100% | JWT + AesUtil + Project CRUD + 3 前端页面 |
| Phase 3 数据源+Schema | ✅ | 100% | MetadataReader + SchemaCache + RelationAnalyzer + TableOrderService |
| Phase 4 规则引擎 | 🔄 | 33% | 第1层 SensitiveFieldDetector ✅，第2层 RegexRule ❌，第3层 LLMRule ❌，前端 MaskConfig ❌ |
| Phase 5 Python AI | 🔄 | 40% | /generate-plan ✅，/schema/analyze ❌ (not_implemented)，/generate-strategy ❌ (not_implemented)，chains/ + tools/ 目录为空，LLMRouter 未自动降级 |
| Phase 6 生成引擎 | ✅ | 100% | 10 生成器 + 单表/多表 + FK + SQL + DB 批量写入 + 95 测试 |
| Phase 7 任务调度 | ✅ | 100% | @Async 执行器 + 状态机 + TaskMonitor 3s 轮询 + 4 前端页面 |
| Phase 8 隐私脱敏 | 🔄 | 60% | 后端全链路 ✅ (Detector→Registry→Executor→Processor→Controller)，DB 脱敏执行 ❌，MaskConfig.vue ❌ |
| Phase 9 前端完善 | 🔄 | 75% | 11 页面 ✅ (12 路由)，MaskConfig.vue ❌，数据导出 ❌ |
| Phase 10 论文答辩 | ❌ | 0% | 无 thesis/paper/ppt 相关文件 |
| Phase 11 简历部署 | ❌ | 0% | 无生产 Docker Compose，无部署脚本 |

### 文件核对清单

**后端 (100+ Java 文件)：**
- ✅ 10 个生成器实现类（generator/impl/）
- ✅ 生成器引擎 + 注册中心 + 上下文 + FK 生成器
- ✅ 单表/多表生成器 + 调度器
- ✅ SQL 构建器 + 数据库写入器（参数化 SQL + 事务保护）
- ✅ 异步执行器 + 线程池配置
- ✅ 任务 Entity + Mapper + Service + Controller
- ✅ 结果 Entity + Mapper + Service + Controller
- ✅ 隐私脱敏全链路 6 个类
- ❌ RegexRule / LLMRule 类不存在
- ❌ 数据库脱敏 UPDATE 执行逻辑不存在

**Python AI 服务 (20+ 文件)：**
- ✅ testdata_agent.py (496 行，FIELD_GENERATOR_MAP 100+ + TYPE_GENERATOR_MAP 25+)
- ✅ llm_service.py (OpenAI SDK → DeepSeek)
- ✅ router.py (LLMRouter → DeepSeek + Qwen)
- ✅ models/generation_plan.py (Pydantic Schema)
- ✅ routes.py 4 端点，/generate-plan 完整
- ❌ routes.py /analyze-schema → not_implemented
- ❌ routes.py /generate-strategy → not_implemented
- ❌ chains/ 目录为空
- ❌ tools/ 目录为空

**前端 (12 路由，11 Vue 文件)：**
- ✅ Login / Dashboard / ProjectList / DatasourceManage
- ✅ TestDataGenerate / TestDataTask / TaskMonitor / TestDataResult
- ✅ SchemaView / RelationGraph / GenerationPlan
- ❌ MaskConfig.vue 不存在
- ❌ 数据导出功能不存在

### 已更新文件

- `task_plan.md` — 重新对齐设计文档 11 阶段，准确标记完成/部分/未开始
- `project_progress.md` — 更新 API 列表 + 测试统计 + 未完成清单

### 技术债务记录

1. task_plan.md 原有 3.6-3, 3.7-1~3.7-4 的进度未反映到阶段总览表中（表现已修正）
2. project_progress.md 落后实际进度 3 个 phase（表现已修正）
3. LLMRouter 定义了主备切换但 testdata_agent 直接用 llm_service，路由未集成
4. Phase 4 规则引擎与 Phase 8 隐私脱敏的第 1 层（Detector）有概念重叠，设计文档建议三层：FieldNameRule → RegexRule → LLMRule

---

## 会话 2026-07-29 — Phase 3.3-2 完成

### 目标

实现 Schema 缓存功能：将 information_schema 数据同步到本地 schema_table / schema_column 表，避免重复扫描。改造数据采样服务优先使用缓存。

### 已完成

| # | 任务 | 文件 |
|---|------|------|
| 1 | Flyway V3 迁移 | `src/main/resources/db/migration/V3__enhance_schema_cache.sql` |
| 2 | SchemaTable Entity | `src/main/java/com/platform/entity/schema/SchemaTable.java` |
| 3 | SchemaColumn Entity | `src/main/java/com/platform/entity/schema/SchemaColumn.java` |
| 4 | SchemaTableMapper | `src/main/java/com/platform/mapper/schema/SchemaTableMapper.java` |
| 5 | SchemaColumnMapper | `src/main/java/com/platform/mapper/schema/SchemaColumnMapper.java` |
| 6 | SchemaCacheSyncRequest DTO | `src/main/java/com/platform/dto/SchemaCacheSyncRequest.java` |
| 7 | SchemaCacheSyncResponse DTO | `src/main/java/com/platform/dto/SchemaCacheSyncResponse.java` |
| 8 | CachedSchemaResponse DTO | `src/main/java/com/platform/dto/CachedSchemaResponse.java` |
| 9 | SchemaCacheService | `src/main/java/com/platform/schema/SchemaCacheService.java` |
| 10 | SchemaController (3 端点) | `src/main/java/com/platform/controller/SchemaController.java` |
| 11 | SchemaSampleService 缓存改造 | `src/main/java/com/platform/schema/SchemaSampleService.java` |

### 测试结果

| 端点 | 结果 |
|------|------|
| `POST /api/schema/cache/sync` | ✅ 同步 10 表 93 列 |
| `GET /api/schema/cache/{id}` | ✅ 完整表+列结构 |
| `POST /api/schema/sample` (缓存命中) | ✅ 列名来自缓存，采样成功 |
| `POST /api/schema/sample` (表不在缓存) | ✅ 优雅降级，WARN 日志 |

### 遇到的问题

| 问题 | 解决 |
|------|------|
| Maven 3.5.4 不兼容 compiler-plugin 3.13.0 | 覆盖为 3.8.1 + `<compilerArgs>-parameters</compilerArgs>` |
| SnakeYAML android 版本冲突 | 排除 javafaker 的 snakeyaml 传递依赖 |
| `-parameters` 未保留参数名 | 使用 `<compilerArgs><arg>-parameters</arg>` 而非 `<parameters>true` |

---

## 会话 2026-07-29 — Phase 3.4 完成

### 目标

实现数据库关系分析器：读取 information_schema 外键关系 → 构建依赖图 → 拓扑排序确定生成顺序 → 检测循环依赖。

### 已完成

| # | 任务 | 文件 |
|---|------|------|
| 1 | 关系分析器 Service | `schema/relation/RelationAnalyzerService.java` |
| 2 | 依赖图 Service | `schema/relation/DependencyGraphService.java` |
| 3 | 拓扑排序 Service | `schema/relation/TableOrderService.java` |
| 4 | 综合响应 DTO | `dto/RelationAnalysisResponse.java` |
| 5 | SchemaController 新增端点 | `controller/SchemaController.java` (+1 endpoint) |

### 新增 API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/schema/relation/{datasourceId}` | 外键关系 + 依赖图 + 生成顺序 |

### 测试结果

| 测试 | 结果 |
|------|------|
| employee.department_id → department.id | ✅ 正确识别 FK |
| 依赖图 nodes + edges | ✅ 正确 |
| 拓扑排序 department → employee | ✅ 被依赖表在前 |
| 无外键数据库 | ✅ 空 relations/graph/order |
| 循环依赖 A→B, B→A | ✅ BusinessException "存在循环依赖..." |

### 技术要点

- **Kahn 算法**：入度为 0 的节点无依赖 → 先生成；移除已排序节点 → 减少下游入度
- **循环检测**：排序后 size ≠ 总表数 → 存在环 → 抛出 BusinessException
- **安全保障**：只读 `information_schema.KEY_COLUMN_USAGE`，不拼接用户输入

---

## 会话 2026-07-29 — Phase 3.5-1 完成

### 目标

实现测试数据生成执行引擎基础框架：Generator 接口、注册机制、引擎调度、5 个基础生成器、测试接口。

### 已完成

| # | 任务 | 文件 |
|---|------|------|
| 1 | Generator 接口 | `generator/Generator.java` |
| 2 | 5 个生成器实现 | `generator/impl/NameGenerator.java` 等 |
| 3 | 生成器注册中心 | `generator/GeneratorRegistry.java` |
| 4 | 生成器引擎 | `generator/GeneratorEngine.java` |
| 5 | 测试接口 DTO | `dto/GeneratorTestRequest.java` + `GeneratorTestResponse.java` |
| 6 | 测试端点 | `controller/TestdataController.java` (+1 endpoint) |
| 7 | SecurityConfig 白名单 | `config/SecurityConfig.java` (modify) |
| 8 | 单元测试 | `test/.../generator/GeneratorTest.java` (9 cases) |

### 新增 API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/testdata/generator/test` | 测试单个生成器 {"generator":"faker.email"} |

### API 测试结果

| 输入 | 输出 | 结果 |
|------|------|------|
| `faker.email` | `"正豪.白@gmail.com"` | ✅ |
| `faker.name` | `"薛越泽"` | ✅ |
| `random.integer` | `9` | ✅ |
| `random.boolean` | `true` | ✅ |
| `faker.word` | `"cupiditate"` | ✅ |
| `faker.unknown` | 400 + 已注册列表 | ✅ |

### 单元测试

| 测试 | 结果 |
|------|------|
| faker.email → 含 @ | ✅ |
| faker.name → 非空 ≥2 字符 | ✅ |
| random.integer 默认 1~100 | ✅ |
| random.integer 指定范围 10~20 | ✅ |
| random.integer 边界 min=max=42 | ✅ |
| random.boolean → true+false | ✅ |
| 未注册生成器 → BusinessException | ✅ |
| generator 为空 → BusinessException | ✅ |
| 注册中心包含 5 个 key | ✅ |

### 架构说明

```
POST /api/testdata/generator/test
  │
  └── GeneratorEngine.execute(fieldPlan)
        │
        └── GeneratorRegistry.get("faker.email")
              │
              └── EmailGenerator.generate(fieldPlan)
                    │
                    └── JavaFaker → "test@qq.com"

已注册生成器:
  faker.name     → NameGenerator
  faker.email    → EmailGenerator
  random.integer → IntegerGenerator (支持 Range{min,max})
  faker.word     → WordGenerator
  random.boolean → BooleanGenerator
```

---

## 会话 2026-07-29 — Phase 3.5-2 完成

### 目标

扩展 GeneratorEngine，新增 5 个生成器：枚举值、小数、过去时间、UUID、手机号。

### 已完成

| # | 任务 | 文件 |
|---|------|------|
| 1 | EnumGenerator | `generator/impl/EnumGenerator.java` |
| 2 | DecimalGenerator | `generator/impl/DecimalGenerator.java` |
| 3 | DateTimeGenerator | `generator/impl/DateTimeGenerator.java` |
| 4 | UUIDGenerator | `generator/impl/UUIDGenerator.java` |
| 5 | PhoneGenerator | `generator/impl/PhoneGenerator.java` |
| 6 | 注册中心更新 | `generator/GeneratorRegistry.java` (modify) |
| 7 | 单元测试扩展 | `test/.../generator/GeneratorTest.java` (15 cases) |

### API 测试结果

| 输入 | 输出 | 结果 |
|------|------|------|
| `random.decimal` | `15.98` | ✅ |
| `time.past_datetime` | `2026-07-24 02:41:14` | ✅ 过去时间 |
| `uuid` | `26181ff7-2d6e-43c9-8162-8b8fc263917d` | ✅ |
| `faker.phone` | `17184943254` | ✅ 11 位 |

### 单元测试新增

| 测试 | 结果 |
|------|------|
| enum.values → 返回值在枚举列表内 | ✅ |
| random.decimal 默认 0~10000 | ✅ |
| random.decimal 指定范围 | ✅ |
| time.past_datetime 格式正确+过去时间 | ✅ |
| uuid 36 字符+可解析 | ✅ |
| faker.phone 11 位数字 | ✅ |

### 架构说明

```
已注册生成器（共 10 个）:

faker.name          → NameGenerator         (基础)
faker.email         → EmailGenerator        (基础)
random.integer      → IntegerGenerator      (基础)
faker.word          → WordGenerator         (基础)
random.boolean      → BooleanGenerator      (基础)
enum.values         → EnumGenerator         (新增)
random.decimal      → DecimalGenerator      (新增)
time.past_datetime  → DateTimeGenerator     (新增)
uuid                → UUIDGenerator         (新增)
faker.phone         → PhoneGenerator        (新增)
```

---

## 会话 2026-07-29 — Phase 3.5-3 完成

### 目标

实现单表测试数据生成器：根据 TablePlan（表名、行数、字段计划）自动批量生成测试数据。

### 已完成

| # | 任务 | 文件 |
|---|------|------|
| 1 | TableGenerateRequest DTO | `dto/TableGenerateRequest.java` |
| 2 | TableGenerateResponse DTO | `dto/TableGenerateResponse.java` |
| 3 | TableDataGenerator 服务 | `generator/table/TableDataGenerator.java` |
| 4 | 表生成端点 | `controller/TestdataController.java` (+1 endpoint) |
| 5 | 单元测试扩展 | `test/.../generator/GeneratorTest.java` (19 cases) |

### 新增 API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/testdata/generator/table` | 单表测试数据批量生成 |

### API 测试结果

| 测试 | 请求 | 结果 |
|------|------|------|
| 多字段生成 | 3 字段 × 3 行 | ✅ username + email + age 全部正确 |
| 大批量生成 | count=100 | ✅ 100 行 UUID + boolean |
| 未知生成器 | faker.unknown | ✅ 400 + 已注册列表 |

### 单元测试新增

| 测试 | 结果 |
|------|------|
| 单字段表生成 5 行 | ✅ |
| 多字段表生成 3 行（含 range）| ✅ |
| count=100 返回 100 条 | ✅ |
| 未知 generator → BusinessException | ✅ |

### 技术要点

- **纯内存生成**：不连接数据库，循环调用 `GeneratorEngine.execute(fieldPlan)`
- **复用已有架构**：`TableDataGenerator` 注入 `GeneratorEngine`，不修改任何已有生成器
- **保持字段顺序**：使用 `LinkedHashMap` 确保输出字段顺序与输入一致
- **异常透传**：未知 generator 时 `GeneratorEngine` 抛出 `BusinessException`，Controller 层统一处理

---

## 会话 2026-07-29 — Phase 3.5-4-1 完成

### 目标

实现多表生成上下文（GenerationContext）：保存已生成表的主键值，为外键关联生成做准备。

### 已完成

| # | 任务 | 文件 |
|---|------|------|
| 1 | GenerationContext | `generator/context/GenerationContext.java` |
| 2 | GeneratedRecord | `generator/context/GeneratedRecord.java` |
| 3 | TableDataGenerator 改造 | `generator/table/TableDataGenerator.java` (modify) |
| 4 | 单元测试扩展 | `test/.../generator/GeneratorTest.java` (23 cases) |

### 测试结果

| 测试 | 结果 |
|------|------|
| Unit: 23/23 | ✅ 全部通过 |
| context 自动记录主键 | ✅ |
| 无主键标记不记录 | ✅ |
| getIds 返回不可变快照 | ✅ |
| 未生成表返回空列表 | ✅ |
| API: department 表含 primaryKey | ✅ |

### 架构说明

```
GenerationContext (ConcurrentHashMap)
  └── "department" → [1, 2, 3]
  └── "category"   → [10, 11]

TableDataGenerator.generate(tablePlan, context)
  → 自动检测 primaryKey=true 的字段
  → 每行生成后写入 context.addGeneratedId()
```

---

## 会话 2026-07-29 — Phase 3.5-4-2 完成

### 目标

实现外键字段自动生成：根据 FieldPlan 中的 foreignKey 信息，从 GenerationContext 中随机选取关联表已生成的主键值。

### 已完成

| # | 任务 | 文件 |
|---|------|------|
| 1 | ForeignKeyInfo DTO | `dto/ForeignKeyInfo.java` |
| 2 | FieldPlan 新增 foreignKey | `dto/GeneratePlanResponse.java` (modify) |
| 3 | ForeignKeyGenerator | `generator/relation/ForeignKeyGenerator.java` |
| 4 | GeneratorEngine 改造 | `generator/GeneratorEngine.java` (modify) |
| 5 | TableDataGenerator 传递 context | `generator/table/TableDataGenerator.java` (modify) |
| 6 | 单元测试扩展 | `test/.../generator/GeneratorTest.java` (25 cases) |

### 测试结果

| 测试 | 结果 |
|------|------|
| Unit: 25/25 | ✅ 全部通过 |
| FK: department_id 随机选自 department.id | ✅ |
| FK: 关联表无数据 → BusinessException | ✅ |
| API: foreignKey 反序列化正确 | ✅ |

### 架构说明

```
多表生成流程:

1. 生成 department:
   TablePlan → TableDataGenerator.generate(plan, ctx)
     → ctx: { "department" → [1, 2, 3] }

2. 生成 employee (含外键 department_id):
   GeneratorEngine.execute(fieldPlan, ctx)
     → foreignKey != null && ctx != null
     → ForeignKeyGenerator.generate({table:"department", column:"id"}, ctx)
     → ctx.getIds("department") → 随机返回 1/2/3
```

---

## 会话 2026-07-30 — Phase 3.5-4-3 完成

### 目标

实现多表测试数据生成调度器：结合 TableOrderService 的依赖排序，自动按正确顺序生成多个关联表数据。

### 已完成

| # | 任务 | 文件 |
|---|------|------|
| 1 | MultiTableGenerateRequest DTO | `dto/MultiTableGenerateRequest.java` |
| 2 | MultiTableGenerateResponse DTO | `dto/MultiTableGenerateResponse.java` |
| 3 | MultiTableDataGenerator 调度器 | `generator/task/MultiTableDataGenerator.java` |
| 4 | 多表生成端点 | `controller/TestdataController.java` (+1 endpoint) |
| 5 | 单元测试扩展 | `test/.../generator/GeneratorTest.java` (28 cases) |

### 新增 API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/testdata/generator/multi-table` | 多表按依赖顺序批量生成 |

### 测试结果

| 测试 | 结果 |
|------|------|
| Unit: 28/28 | ✅ 全部通过 |
| 2 层依赖 department→employee 排序 | ✅ department 先于 employee |
| 3 层依赖 department→employee→task | ✅ 三层正确排序 |
| 循环依赖 A→B, B→A | ✅ BusinessException |

### 架构说明

```
MultiTableDataGenerator.generate(tablePlans)
  │
  ├── extractRelations()
  │     FieldPlan.foreignKey → RelationItem
  │
  ├── TableOrderService.topologicalSort()
  │     Kahn 算法 → ["department", "employee", "task"]
  │
  ├── 构建最终顺序：排序表 + 无依赖表（原始顺序）
  │
  └── for each table:
        TableDataGenerator.generate(tablePlan, context)
          ├── PK → context.addGeneratedId()
          └── FK → ForeignKeyGenerator（通过 GeneratorEngine 透明调用）
```

### 技术要点

- **复用不复制**：完全复用 TableOrderService、TableDataGenerator、GenerationContext、ForeignKeyGenerator
- **FK 关系提取**：从 FieldPlan.foreignKey 自动构建 RelationItem，无需数据库
- **无 FK 表处理**：不在拓扑排序结果中的表（无外键依赖）按原始顺序追加到最后
- **循环依赖检测**：由 TableOrderService.topologicalSort 的 Kahn 算法自动检测，排序结果 size ≠ 总表数 → 抛出 BusinessException

---

## 会话 2026-07-30 — Phase 3.6-1 完成

### 目标

实现 SQL Insert 生成器：将生成的测试数据转换为可执行的 INSERT SQL，支持类型识别、字符串转义、批量生成。

### 已完成

| # | 任务 | 文件 |
|---|------|------|
| 1 | SqlGenerateRequest DTO | `dto/SqlGenerateRequest.java` |
| 2 | SqlGenerateResponse DTO | `dto/SqlGenerateResponse.java` |
| 3 | InsertSqlBuilder 服务 | `sql/InsertSqlBuilder.java` |
| 4 | SQL 构建端点 | `controller/TestdataController.java` (+1 endpoint) |
| 5 | SecurityConfig 白名单 | `config/SecurityConfig.java` (modify) |
| 6 | 单元测试扩展 | `test/.../generator/GeneratorTest.java` (32 cases) |

### 新增 API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/testdata/sql/build` | 将数据转换为 INSERT SQL |

### 测试结果

| 测试 | 结果 |
|------|------|
| Unit: 33/33 (1 AppTest + 32 GeneratorTest) | ✅ 全部通过 |
| 单条数据生成 INSERT | ✅ 列名 + 值格式正确 |
| 100 条批量一个 INSERT | ✅ 1 条 INSERT，100 行值 |
| 字符串特殊字符转义 ' → '' | ✅ |
| null 值输出 NULL | ✅ |

### 技术要点

- **类型识别**：String → 加引号转义；Integer/Long/BigDecimal → 直接数字；Boolean → 1/0；Date → 格式化字符串；null → NULL
- **SQL 转义**：单引号 `'` → `''`（MySQL/标准 SQL 规范），不使用反斜杠
- **批量生成**：多条数据合并为一条 INSERT ... VALUES (...), (...), ...; 语句
- **列顺序稳定**：使用 LinkedHashSet 收集列名，保持首次出现顺序

---

## 会话 2026-07-30 — Phase 3.6-2 完成

### 目标

实现数据库批量写入执行器：通过 JdbcTemplate 将生成的数据批量写入目标数据库，多表写入在同一事务中保护。

### 已完成

| # | 任务 | 文件 |
|---|------|------|
| 1 | InsertStatementBuilder | `generator/persistence/InsertStatementBuilder.java` |
| 2 | DatabaseWriter | `generator/persistence/DatabaseWriter.java` |
| 3 | MultiTableWriteService | `generator/persistence/MultiTableWriteService.java` |
| 4 | WriteResult DTO | `dto/WriteResult.java` |
| 5 | DatabaseWriteRequest DTO | `dto/DatabaseWriteRequest.java` |
| 6 | DatabaseWriteResponse DTO | `dto/DatabaseWriteResponse.java` |
| 7 | 写入端点 | `controller/TestdataController.java` (+1 endpoint) |
| 8 | SecurityConfig 白名单 | `config/SecurityConfig.java` (modify) |
| 9 | DB 写入测试 | `persistence/DatabaseWriterTest.java` (6 cases) |
| 10 | 参数化 SQL 测试 | `GeneratorTest.java` (+2 cases) |

### 新增 API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/testdata/write` | 事务保护批量写入数据库 |

### 测试结果

| 测试 | 结果 |
|------|------|
| Unit: 41/41 (1 App + 34 Generator + 6 DB Writer) | ✅ 全部通过 |
| 单表写入 10 条 + COUNT 验证 | ✅ |
| 多表 department → employee 顺序写入 | ✅ FK 正确 |
| 不存在的表 → 异常 | ✅ |
| insertCount = 数据库实际行数 | ✅ |
| 特殊字符参数化写入安全 | ✅ |
| 参数化 SQL 构建（? 占位符） | ✅ |

### 技术要点

- **安全防注入**：参数化 SQL（? 占位符）+ JdbcTemplate.batchUpdate，不拼接用户值
- **事务保护**：MultiTableWriteService 使用 TransactionTemplate + DataSourceTransactionManager，任意表失败自动回滚
- **动态数据源**：从 Datasource 实体构建 DriverManagerDataSource，通过 AesUtil 解密密码
- **复用架构**：注入 DatasourceService 获取数据源配置，与现有 schema 服务保持一致的 JDBC URL 构建逻辑
- **H2 测试**：使用 H2 内存数据库（MODE=MySQL）隔离测试，不影响生产数据

---

## 会话 2026-07-30 — Phase 3.6-3-1 完成

### 目标

实现测试数据生成任务管理：创建 `testdata_task` 表，提供任务创建和状态查询接口。

### 已完成

| # | 任务 | 文件 |
|---|------|------|
| 1 | Flyway V4 迁移 — testdata_task 表 | `db/migration/V4__add_testdata_task.sql` |
| 2 | TestDataTask Entity | `entity/TestDataTask.java` |
| 3 | TestDataTaskMapper | `mapper/TestDataTaskMapper.java` |
| 4 | CreateTaskRequest DTO | `dto/CreateTaskRequest.java` |
| 5 | TaskResponse DTO | `dto/TaskResponse.java` |
| 6 | TestDataTaskService | `service/TestDataTaskService.java` |
| 7 | TestDataTaskController | `controller/TestDataTaskController.java` |
| 8 | SecurityConfig 白名单 | `config/SecurityConfig.java` (modify) |
| 9 | MetaObjectHandler 新增 createTime | `config/MetaObjectHandlerConfig.java` (modify) |

### 新增 API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/testdata/task` | 创建生成任务 |
| GET | `/api/testdata/task/{id}` | 查询任务状态 |

### 任务状态机

```
PENDING → RUNNING → SUCCESS
                  → FAILED
```

### 技术要点

- **MyBatis-Plus 架构**：Entity + BaseMapper + MetaObjectHandler 自动填充
- **Flyway 管理**：V4 迁移脚本，id/task_name/status/total_count/success_count/fail_count/error_message/create_time/finish_time
- **事务控制**：Service 层 insert 操作在 Spring 事务上下文中

---

## 会话 2026-07-30 — Phase 3.6-3-2 完成

### 目标

实现测试数据生成异步执行器：任务创建后立即触发后台异步执行，HTTP 请求不阻塞。

### 已完成

| # | 任务 | 文件 |
|---|------|------|
| 1 | Flyway V5 迁移 — task 表加 datasource_id | `db/migration/V5__add_datasource_to_task.sql` |
| 2 | AsyncConfig — 线程池配置 | `config/AsyncConfig.java` |
| 3 | TestDataTaskExecutor 异步执行器 | `generator/task/TestDataTaskExecutor.java` |
| 4 | TestDataTaskService 异步接入 | `service/TestDataTaskService.java` (modify) |
| 5 | Entity/DTO 加 datasourceId | `entity/TestDataTask.java` + `dto/*.java` (modify) |
| 6 | 异步执行测试 | `test/.../task/TestDataTaskExecutorTest.java` (4 cases) |

### 异步执行流程

```
POST /api/testdata/task → 保存 PENDING → 立即返回
  └── @Async 线程池:
        ├── RUNNING
        ├── SchemaCacheService.sync(datasourceId)
        ├── TestdataService.generatePlan(schema, requirement) → AI 生成 TablePlan
        ├── MultiTableDataGenerator.generate(tablePlans) → 按依赖顺序生成
        ├── MultiTableWriteService.writeAll(datasourceId, tableData) → 事务写入
        ├── SUCCESS + successCount + finishTime
        └── FAILED + errorMessage + finishTime
```

### 测试结果

| 测试 | 结果 |
|------|------|
| Unit: 45/45 (1 App + 34 Generator + 6 DB Writer + 4 Task Executor) | ✅ 全部通过 |
| 创建任务立即返回 PENDING | ✅ |
| 异步执行后状态变更（轮询 15s） | ✅ |
| 失败任务保存 errorMessage | ✅ |
| 不存在的任务 → 404 | ✅ |

### 技术要点

- **非阻塞 HTTP**：`taskMapper.insert()` 后立即返回，不等待 AI 调用和 DB 写入
- **线程池隔离**：`testdata-task-` 前缀线程池 core=2/max=4/queue=100，CallerRunsPolicy
- **复用不复制**：直接注入 TestdataService、MultiTableDataGenerator、MultiTableWriteService
- **错误保存**：异常信息截断至 1000 字符存入 error_message

---

## 会话 2026-07-30 — Phase 3.7-1 完成

### 目标

实现敏感字段识别器：根据数据库字段名识别可能包含个人隐私数据的字段。

### 已完成

| # | 任务 | 文件 |
|---|------|------|
| 1 | SensitiveFieldType 枚举 | `privacy/SensitiveFieldType.java` |
| 2 | SensitiveField DTO | `dto/SensitiveField.java` |
| 3 | SensitiveFieldDetector 识别器 | `privacy/SensitiveFieldDetector.java` |
| 4 | 单元测试 | `test/.../privacy/SensitiveFieldDetectorTest.java` (12 cases) |

### 识别规则

| 关键词 | 敏感类型 | 精确匹配 | 包含匹配 |
|--------|---------|---------|---------|
| phone / mobile / tel | PHONE | 0.95 | 0.80 |
| email / mail | EMAIL | 0.95 | 0.80 |
| idcard / id_card / card_no | ID_CARD | 0.95 | 0.80 |
| name / username | NAME | 0.95 | 0.80 |
| address / addr / location | ADDRESS | 0.95 | 0.80 |
| bank / card | BANK_CARD | 0.95 | 0.80 |

### 测试结果

| 测试 | 结果 |
|------|------|
| Unit: 57/57 全部通过 | ✅ |
| PHONE 识别 (含大小写变体) | ✅ |
| EMAIL 识别 | ✅ |
| ID_CARD 识别 (card_no 优先于 card) | ✅ |
| NAME 识别 | ✅ |
| ADDRESS 识别 | ✅ |
| BANK_CARD 识别 | ✅ |
| 未知字段忽略 (age/status/created_at) | ✅ |
| 空列表 / null 安全 | ✅ |

### 技术要点

- **大小写不敏感**：匹配前统一 toLowerCase()
- **下划线支持**：contains() 子串匹配，snake_case 自然支持
- **优先级**：ID_CARD 优先于 BANK_CARD，确保 card_no → ID_CARD
- **只做识别不做修改**：纯读操作，识别与执行分离

---

## 会话 2026-07-30 — Phase 3.7-2 完成

### 目标

实现脱敏规则引擎：根据 SensitiveFieldType 自动映射对应脱敏策略。

### 已完成

| # | 任务 | 文件 |
|---|------|------|
| 1 | MaskStrategy 枚举 | `privacy/mask/MaskStrategy.java` |
| 2 | MaskRule Record | `privacy/mask/MaskRule.java` |
| 3 | MaskRuleRegistry 注册表 | `privacy/mask/MaskRuleRegistry.java` |
| 4 | 单元测试 | `test/.../privacy/mask/MaskRuleRegistryTest.java` (8 cases) |

### 默认映射

| SensitiveFieldType | MaskStrategy |
|-------------------|-------------|
| PHONE | PHONE_MASK (保留前3后4) |
| EMAIL | EMAIL_MASK (保留首字符+域名) |
| ID_CARD | ID_CARD_MASK (保留前6后4) |
| NAME | NAME_MASK (保留首字) |
| ADDRESS | ADDRESS_MASK (保留前6字符) |
| BANK_CARD | BANK_CARD_MASK (保留后4位) |
| UNKNOWN | (无规则) |

### 测试结果

| 测试 | 结果 |
|------|------|
| Unit: 65/65 全部通过 | ✅ |
| PHONE → PHONE_MASK | ✅ |
| EMAIL → EMAIL_MASK | ✅ |
| ID_CARD → ID_CARD_MASK | ✅ |
| UNKNOWN → Optional.empty() | ✅ |
| 6 种类型映射完整 | ✅ |

### 技术要点

- **@PostConstruct 初始化**：规则在容器启动时注册，日志记录
- **只做映射不做执行**：策略选择与值转换分离
- **record 类型**：MaskRule 使用 Java record 确保不可变性

---

## 会话 2026-07-30 — Phase 3.7-3 完成

### 目标

实现脱敏执行器：根据 MaskStrategy 对实际字段值进行脱敏处理。

### 已完成

| # | 任务 | 文件 |
|---|------|------|
| 1 | MaskExecutor 接口 | `privacy/executor/MaskExecutor.java` |
| 2 | DefaultMaskExecutor 实现 | `privacy/executor/DefaultMaskExecutor.java` |
| 3 | 单元测试 | `test/.../privacy/executor/MaskExecutorTest.java` (17 cases) |

### 脱敏规则

| 策略 | 规则 | 示例 |
|------|------|------|
| PHONE_MASK | 保留前3后4，中间 **** | 13812345678 → 138****5678 |
| EMAIL_MASK | 保留前3字符 + *** + @域名 | zhangsan@gmail.com → zha***@gmail.com |
| ID_CARD_MASK | 保留前6后4，中间 ******** | 110101199001011234 → 110101********1234 |
| NAME_MASK | 保留首字，其余 * | 张三 → 张* |
| ADDRESS_MASK | 保留前6字符，其余 * | 北京市朝阳区建国路100号 → 北京市朝阳区******* |
| BANK_CARD_MASK | 保留后4位，前面 **** | 6222021234567890123 → ****0123 |

### 测试结果

| 测试 | 结果 |
|------|------|
| Unit: 82/82 全部通过 | ✅ |
| 手机号脱敏 ×2 | ✅ |
| 邮箱脱敏 ×3 (含无@回退) | ✅ |
| 身份证脱敏 ×2 | ✅ |
| 姓名脱敏 ×3 (含单字) | ✅ |
| 地址脱敏 ×2 | ✅ |
| 银行卡脱敏 ×2 | ✅ |
| null / 空字符串安全 | ✅ |

### 技术要点

- **边界安全**：null → null，空串 → 空串，短值尽力保留不抛异常
- **Switch 表达式**：Java 17+ switch 分支，编译时完整性检查
- **只做值转换**：执行器不涉及识别或策略选择，单一职责

---

## 会话 2026-07-30 — Phase 3.7-4 完成

### 目标

实现生成数据与脱敏融合：将已有测试数据生成流程接入隐私脱敏能力，提供 REST API。

### 已完成

| # | 任务 | 文件 |
|---|------|------|
| 1 | PrivacyProcessRequest DTO | `dto/PrivacyProcessRequest.java` |
| 2 | PrivacyProcessResponse DTO | `dto/PrivacyProcessResponse.java` |
| 3 | PrivacyAwareDataProcessor | `privacy/service/PrivacyAwareDataProcessor.java` |
| 4 | PrivacyController REST 端点 | `controller/PrivacyController.java` |
| 5 | SecurityConfig 白名单 | `config/SecurityConfig.java` (modify) |
| 6 | 单元测试 | `test/.../privacy/service/PrivacyAwareDataProcessorTest.java` (13 cases) |

### 新增 API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/privacy/process` | 对数据行执行脱敏处理 |

### API 示例

```json
POST /api/privacy/process
{
  "data": [{"name":"张三", "phone":"13812345678", "email":"test@gmail.com"}],
  "sensitiveFields": [
    {"columnName":"phone", "type":"PHONE"},
    {"columnName":"email", "type":"EMAIL"}
  ]
}
→ {"success":true, "data":[{"name":"张三", "phone":"138****5678", "email":"tes***@gmail.com"}]}
```

### 测试结果

| 测试 | 结果 |
|------|------|
| Unit: 95/95 全部通过 | ✅ |
| 手机号脱敏 | ✅ |
| 邮箱脱敏 | ✅ |
| 非敏感字段原值保留 | ✅ |
| 仅标记字段脱敏 | ✅ |
| 多行数据逐行处理 | ✅ |
| null 值安全 | ✅ |
| 空数据 / null 安全 | ✅ |
| UNKNOWN / 无效类型跳过 | ✅ |
| 入参不可变（防御性复制） | ✅ |
| 6 种策略完整集成 | ✅ |

### Phase 3.7 完整架构

```
SchemaColumn 列表
      │
      ▼
SensitiveFieldDetector  ──→  List<SensitiveField>          ← 3.7-1 识别
      │                         (columnName, type, confidence)
      ▼
MaskRuleRegistry        ──→  MaskRule                       ← 3.7-2 策略映射
      │                         (type, strategy, description)
      ▼
DefaultMaskExecutor     ──→  String (脱敏后值)               ← 3.7-3 值转换
      │
      ▼
PrivacyAwareDataProcessor ──→ List<Map> (脱敏后数据)        ← 3.7-4 融合编排
      │
      ▼
POST /api/privacy/process                                    ← REST API
```

### 技术要点

- **三层分离**：识别（Detector）、策略映射（Registry）、值转换（Executor）完全解耦
- **复用不复制**：Processor 直接注入 MaskRuleRegistry + MaskExecutor，零代码重复
- **防御性复制**：新建 LinkedHashMap 存放脱敏结果，不修改入参
- **类型安全**：请求中的 type 字符串通过 SensitiveFieldType.valueOf() 解析，UNKNOWN/无效类型自动跳过
