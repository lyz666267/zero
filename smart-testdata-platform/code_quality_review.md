# 智能测试数据生成平台 — 企业级代码质量审查报告

> **审查标准：** 企业代码 Review 标准（SOLID / 单一职责 / 异常处理 / 日志追踪）  
> **审查日期：** 2026-08-03  
> **审查范围：** 后端 Java 148 文件 + Python AI 31 文件 + 前端 Vue 36 文件  
> **发现问题总数：** 77 个（🔴严重 15 / 🟠重要 29 / 🟡建议 23 / 🔵风格 9）

---

## 统计概览

| 区域 | 🔴严重 | 🟠重要 | 🟡建议 | 🔵风格 | 合计 |
|------|--------|--------|--------|--------|------|
| 后端 Java | 6 | 8 | 6 | 3 | **23** |
| Python AI | 6 | 10 | 8 | 3 | **28** |
| 前端 Vue | 3 | 11 | 9 | 3 | **26** |
| **总计** | **15** | **29** | **23** | **9** | **77** |

| 分类 | 数量 | 占比 |
|------|------|------|
| 重复代码 | 15 | 19.5% |
| 异常处理 | 12 | 15.6% |
| 类/组件职责 | 10 | 13.0% |
| 方法过长 | 8 | 10.4% |
| 命名问题 | 7 | 9.1% |
| Spring/FastAPI/Vue 实践 | 7 | 9.1% |
| 日志 | 5 | 6.5% |
| 过度设计 | 5 | 6.5% |
| Agent 设计 | 4 | 5.2% |
| 代码组织 | 3 | 3.9% |
| SOLID | 1 | 1.3% |

---

# 第一部分：后端 Java（23 个问题）

---

## 🔴 严重问题（6 个）

### 问题 J1: `buildJdbcUrl` 在 5 个类中逐字重复
- **文件:** `service/DatasourceService.java`, `service/DatabaseMaskService.java`, `schema/SchemaCacheService.java`, `schema/SchemaSampleService.java`, `generator/persistence/MultiTableWriteService.java`
- **行号:** 约 159-167 / 380-388 / 378-387 / 200-208 / 126-135
- **严重度:** 🔴严重
- **分类:** 重复代码
- **问题:** 同一个 `buildJdbcUrl(Datasource ds)` 方法（30+ 行 JDBC URL 构建）被复制粘贴到 5 个类中。任何 URL 格式变更（如添加 `useSSL`、时区参数、支持新数据库类型）需要同步修改 5 处。
- **建议:** 提取 `JdbcUrlBuilder` 工具组件（`@Component`），提供 `String build(Datasource ds)` 方法，注入到所有消费者中。

### 问题 J2: `DataQualityEvaluator.evaluatePrivacy()` 重新实现了敏感字段检测逻辑
- **文件:** `service/DataQualityEvaluator.java`
- **行号:** 约 461-470
- **严重度:** 🔴严重
- **分类:** 重复代码
- **问题:** `evaluatePrivacy()` 硬编码了敏感字段关键词匹配（"phone", "email", "idcard" 等），而 `SensitiveFieldDetector.RULES` 中已有完全相同的检测逻辑。质量评估器的隐私评分可能与实际检测器结果不一致。
- **建议:** 删除硬编码的关键词匹配，注入 `SensitiveFieldDetector` 并调用 `sensitiveFieldDetector.detect(columns)` 获取实际敏感列。

### 问题 J3: SecurityConfig 所有业务 API 均为 `permitAll()`
- **文件:** `config/SecurityConfig.java`
- **行号:** 约 27-39
- **严重度:** 🔴严重
- **分类:** Spring 实践
- **问题:** 除 `/api/auth/**` 外的所有端点（datasource、project、schema、export、privacy、quality、testdata）均标记为 `.permitAll()`。JWT 过滤器存在但被完全绕过，任何人均可未认证调用所有业务 API。
- **建议:** 仅保留 `/api/auth/**` 和健康检查为 `permitAll()`，其余全部要求 `authenticated()`。

### 问题 J4: `TestDataTaskExecutor.executeTask()` 方法 160+ 行
- **文件:** `generator/task/TestDataTaskExecutor.java`
- **行号:** 约 70-231
- **严重度:** 🔴严重
- **分类:** 方法过长
- **问题:** 单方法涵盖：状态更新、Schema 构建、AI 计划生成、计划持久化、数据生成、数据库写入、结果持久化、质量评估、Agent 日志。深层嵌套 try-catch，编排与实现混杂。
- **建议:** 拆分为：`loadAndValidateTask()`, `buildSchemaMap()`, `callAiService()`, `generateAndWriteData()`, `saveResults()`, `runQualityEvaluation()`。每个方法 20-30 行。

### 问题 J5: `DataQualityEvaluator` 违反单一职责原则（675 行）
- **文件:** `service/DataQualityEvaluator.java`
- **行号:** 全文（675 行）
- **严重度:** 🔴严重
- **分类:** 类职责过多
- **问题:** 该类同时负责 5 种不同的评估算法、Schema 解析、报告持久化/检索、JSON 序列化、格式校验的正则匹配，并重复实现了敏感字段检测。几乎无法对单个指标进行单元测试。
- **建议:** 使用策略模式：定义 `QualityMetric` 接口 `double evaluate(data, schema, issues)`，分别实现 `CompletenessMetric`, `UniquenessMetric`, `ConsistencyMetric`, `ValidityMetric`, `PrivacyMetric`。`DataQualityEvaluator` 变为编排器，自动注入 `List<QualityMetric>` 并汇总。

### 问题 J6: `collectColumns` 逻辑在 3 个类中重复
- **文件:** `generator/persistence/DatabaseWriter.java`, `sql/InsertSqlBuilder.java`, `service/ExportService.java`
- **行号:** 约 88-96 / 49-59 / 169-179
- **严重度:** 🔴严重（与 J1 同等）
- **分类:** 重复代码
- **问题:** "从 Map 列表中收集所有列名，用 LinkedHashSet 去重，返回 ArrayList" 的逻辑在三个类中实现完全相同。
- **建议:** 提取为 `ColumnCollector.collect(List<Map<String, Object>> rows)` 静态工具方法。

---

## 🟠 重要问题（8 个）

### 问题 J7: `TestdataController` 注入 6 个依赖，处理 6 组端点
- **文件:** `controller/TestdataController.java`
- **行号:** 约 29-34
- **严重度:** 🟠重要
- **分类:** 类职责过多
- **问题:** 单个 Controller 处理：plan 生成、生成器测试、单表生成、多表生成、SQL 构建、数据库写入 6 组独立端点。违反单一职责原则，难以测试。
- **建议:** 拆分为 `GenerationController`（plan + 表生成）、`SqlBuilderController`（SQL 构建）、`DatabaseWriteController`（DB 写入），每个最多 2-3 个依赖。

### 问题 J8: `AgentLogService.logStep()` 静默吞掉所有异常
- **文件:** `service/AgentLogService.java`
- **行号:** 约 70-74
- **严重度:** 🟠重要
- **分类:** 异常处理
- **问题:** `catch (Exception e)` 捕获所有异常后仅 `log.warn("...{}", e.getMessage())`，不打印堆栈。数据库连接失败、序列化错误、约束冲突、NPE 全部被同一方式吞掉，调用方完全不知情。
- **建议:** 至少改为 `log.warn("...", e)` 打印完整堆栈。区分 `JsonProcessingException` 和 `DataAccessException` 做不同处理。

### 问题 J9: `evaluateValidity()` 不必要地遍历数据两次
- **文件:** `service/DataQualityEvaluator.java`
- **行号:** 约 379-436
- **严重度:** 🟠重要
- **分类:** 方法过长 / 性能
- **问题:** 第一次遍历统计 valid/total 计数（387-408 行），第二次遍历记录逐条错误（410-433 行）。10,000 行 × 20 列 = 400,000 次循环而非 200,000 次。
- **建议:** 合并为单次遍历，在一次循环中同时统计分数和构建问题列表。

### 问题 J10: `GlobalExceptionHandler` 隐藏内部错误详情
- **文件:** `exception/GlobalExceptionHandler.java`
- **行号:** 约 44-49
- **严重度:** 🟠重要
- **分类:** 异常处理
- **问题:** 通用 `handleException()` 兜底返回固定 "服务器内部错误" 消息，客户端无法获取任何 traceId 或错误关联信息。调试 API 调用极其困难。
- **建议:** 在响应中包含 `traceId`（使用 MDC），让客户端能引述该 ID 排查。或 dev 环境返回详细错误信息。

### 问题 J11: `TestDataTaskExecutor.buildSchemaMap()` 不安全的嵌套字段访问
- **文件:** `generator/task/TestDataTaskExecutor.java`
- **行号:** 约 278-310
- **严重度:** 🟠重要
- **分类:** SOLID / 类职责
- **问题:** 手动将 `CachedTableInfo`/`CachedColumnInfo` 转换为 `Map<String, Object>` 结构，绕过类型系统。`.getPrimaryKey()`, `.getNullable()` 等方法调用无 null 检查。
- **建议:** 在 `CachedColumnInfo`/`CachedTableInfo` 上实现 `toMap()` 方法，将该转换逻辑放入 DTO 层本身。

### 问题 J12: `GeneratorRegistry` 使用硬编码 switch 做 bean 名称映射
- **文件:** `generator/GeneratorRegistry.java`
- **行号:** 约 73-87
- **严重度:** 🟠重要
- **分类:** SOLID（开闭原则）
- **问题:** 新增 Generator 实现需要修改 `beanNameToGeneratorKey()` 的 switch 语句。`Generator` 接口缺少 `getName()` 方法。
- **建议:** 在 `Generator` 接口添加 `String getName()`，每个实现返回自己的 key（如 `"faker.email"`）。注册中心调用 `generator.getName()`。

### 问题 J13: `SchemaCacheService.insertColumnCache()` 逐条单条插入
- **文件:** `schema/SchemaCacheService.java`
- **行号:** 约 210-230
- **严重度:** 🟠重要
- **分类:** Spring 实践 / 性能
- **问题:** 50 表 × 20 列 = 1,000 次独立数据库往返。MyBatis-Plus 的 `saveBatch()` 可直接批量插入。
- **建议:** 使用 `saveBatch()` 或 MyBatis XML `<foreach>` 合并为单条 INSERT。

### 问题 J14: 数据类型标签映射在 Controller 和 Service 中重复定义
- **文件:** `controller/PrivacyController.java`, `service/DatabaseMaskService.java`
- **行号:** 约 41-48 / 58-65
- **严重度:** 🟠重要
- **分类:** 重复代码
- **问题:** `TYPE_LABELS` 映射（`SensitiveFieldType` → 中文名）在两个文件中完全一致。新增敏感类型需要两处同步更新。
- **建议:** 将 label 移入 `SensitiveFieldType` 枚举本身作为 `label` 字段，如 `PHONE("手机号"), EMAIL("邮箱")`。

---

## 🟡 建议问题（6 个）

### 问题 J15: `DatasourceService` 中两个几乎相同的 JDBC URL 构建方法
- **文件:** `service/DatasourceService.java`
- **行号:** 约 146-167
- **严重度:** 🟡建议
- **分类:** 重复代码
- **问题:** `buildJdbcUrlFromRequest()` 和 `buildJdbcUrl()` 仅参数来源不同（`DatasourceRequest` vs `Datasource`），JDBC URL 格式字符串完全相同。
- **建议:** 统一为 `buildJdbcUrl(host, port, dbName, dbType)` 私有方法，两个调用者各自提取字段后调用。

### 问题 J16: `AesUtil` 抛出 RuntimeException 丢失根因分类
- **文件:** `util/AesUtil.java`
- **行号:** 约 38-40, 52-54
- **严重度:** 🟡建议
- **分类:** 异常处理
- **问题:** `catch (Exception)` 过于宽泛，NPE、参数错误等也会被包装为 "AES encryption/decryption failed"。
- **建议:** 捕获明确的异常类型：`NoSuchAlgorithmException`, `InvalidKeyException`, `BadPaddingException`, `IllegalBlockSizeException`。

### 问题 J17: 多数 Controller 零业务操作日志
- **文件:** 多个 Controller
- **行号:** 各处
- **严重度:** 🟡建议
- **分类:** 日志
- **问题:** AuthController、DatasourceController、ProjectController、SchemaController、ExportController 等完全没有 `log.info()` 调用，无法追溯用户操作。
- **建议:** 在关键操作（创建/删除数据源、创建/删除项目、任务创建、数据导出）添加 `log.info("Datasource created: userId={}, dsId={}", ...)`。

### 问题 J18: 硬编码 `jdbc:mysql://` 阻止新增数据库类型
- **文件:** 与 J1 相同的 5 个文件
- **行号:** 参见 J1
- **严重度:** 🟡建议
- **分类:** SOLID（开闭原则）
- **问题:** 所有 `buildJdbcUrl` 只处理 `mysql`，default 分支抛出 `BusinessException`。新增 PostgreSQL 需要修改所有副本。
- **建议:** 提取为统一工具类后，实现策略模式：`JdbcUrlBuilder` 接口 + `MysqlJdbcUrlBuilder` / `PostgresqlJdbcUrlBuilder` 实现。

### 问题 J19: `PrivacyController` 使用 `Map.of()` 限制最大 10 条目
- **文件:** `controller/PrivacyController.java`
- **行号:** 约 41-58
- **严重度:** 🟡建议
- **分类:** 可维护性
- **问题:** `Map.of()` 最多 10 个键值对。当前 6 个条目安全，但添加到 11 个时会在运行时崩溃。
- **建议:** 使用 `Map.ofEntries(Map.entry(...), ...)` 支持任意条目数。更好的是将这些映射移入 `SensitiveFieldType` 枚举。

### 问题 J20: `DatasourceService` 两个 URL 构建方法仅参数来源不同
- **文件:** `service/DatasourceService.java`
- **行号:** 约 146-167
- **严重度:** 🟡建议
- **分类:** 重复代码
- **问题:** 与 J15 指向相同但角度不同——方法签名暴露了不必要的外部依赖（`DatasourceRequest` 对象作为 URL 构建参数）。
- **建议:** 统一为参数化的私有方法，降低耦合。

---

## 🔵 风格问题（3 个）

### 问题 J21: 命名不一致 — `Testdata` vs `TestData`
- **文件:** `controller/TestdataController.java`, `service/TestdataService.java`, `service/TestDataTaskService.java`
- **行号:** 类名
- **严重度:** 🔵风格
- **分类:** 命名
- **问题:** `TestdataController`/`TestdataService` 使用小写 'd'，而 `TestDataTask`、`TestDataResult`、`TestDataTaskService` 使用大写 'D'。明显是早期命名未统一重构的结果。
- **建议:** 将所有 "Testdata" 重命名为 "TestData"。

### 问题 J22: 实体名 `Datasource` 应为 `DataSource`
- **文件:** `entity/Datasource.java` 及所有相关文件
- **行号:** 类声明
- **严重度:** 🔵风格
- **分类:** 命名
- **问题:** Java 命名约定中缩写词按单词处理，应写作 `DataSource`。该不一致传播到 `DatasourceMapper`、`DatasourceService`、`DatasourceRequest` 等。
- **建议:** 全部重命名为 `DataSource` 系列，通过 `@TableName("datasource")` 保留表名不变。

### 问题 J23: `ExportService` 重复导入 `TestDataTask`
- **文件:** `service/ExportService.java`
- **行号:** 第 7 行和第 9 行
- **严重度:** 🔵风格
- **分类:** 代码整洁
- **问题:** 同一类被 import 两次，说明未使用 IDE 的 organize imports。
- **建议:** 删除重复的 import 语句。

---

# 第二部分：Python AI 服务（28 个问题）

---

## 🔴 严重问题（6 个）

### 问题 P1: 两个完全独立的 `LLMRouter` 实现
- **文件:** `app/llm/router.py`（第 1-165 行）和 `app/models/router.py`（第 1-43 行）
- **严重度:** 🔴严重
- **分类:** 重复代码
- **问题:** `app/llm/router.py` 使用原生 openai SDK 实现，被实际业务代码使用。`app/models/router.py` 使用 LangChain `ChatOpenAI` 封装，未被任何生产代码引用。功能重复但行为不一致（LangChain 版无故障切换逻辑）。
- **建议:** 删除 `app/models/router.py`。如不再需要 LangChain 依赖，从 `requirements.txt` 中移除。

### 问题 P2: 多个 Agent 中 `except Exception` 静默吞掉所有异常
- **文件:** `app/agents/schema_agent.py`（第 177-179, 234-236 行）, `app/agents/strategy_agent.py`（第 74-76, 131-133 行）, `app/agents/testdata_agent.py`（第 251-253, 308-312 行）
- **严重度:** 🔴严重
- **分类:** 异常处理
- **问题:** `except Exception as e` 捕获所有异常直接降级 Mock，不区分 `MemoryError`、`SystemExit`、Pydantic `ValidationError`、`KeyboardInterrupt` 等不可恢复错误。真正的 bug 被隐藏。
- **建议:** 替换为具体异常类型：
  ```python
  except (LLMProviderError, RouterExhaustedError, json.JSONDecodeError) as e:
      # 可恢复的 LLM 错误，降级 Mock
  except asyncio.TimeoutError as e:
      # 超时可降级
  # 其他异常应传播，不应被静默捕获
  ```

### 问题 P3: `detect_sensitive` 端点吞掉所有异常并返回 `success=True`
- **文件:** `app/api/routes.py`
- **行号:** 约 322-324
- **严重度:** 🔴严重
- **分类:** 异常处理
- **问题:** `except Exception as e` 捕获所有异常后返回 `DetectSensitiveResponse(success=True, fields=[], mock=True)`。内存错误、JSON 解析错误、数据库连接错误等都会让客户端收到 `success=True` 和空字段列表，无法区分"没有敏感字段"和"系统崩溃"。
- **建议:** 区分可处理的 LLM 错误和不可处理的系统错误。后者返回 `success=False` 并附带有意义的错误消息。

### 问题 P4: `GeneratePlanRequest` 禁用 Pydantic 命名空间保护
- **文件:** `app/schemas/generation_plan.py`
- **行号:** 约 44
- **严重度:** 🔴严重
- **分类:** FastAPI 实践 / 命名
- **问题:** `model_config = {"protected_namespaces": ()}` 关闭了所有命名空间冲突检查，仅因字段名 `schema` 与 `BaseModel` 内部属性冲突。这是一个安全风险。
- **建议:** 将字段名 `schema` 改为 `schema_data` 或 `db_schema`，移除 `protected_namespaces = ()`。

### 问题 P5: ReAct 循环缺少单步超时和重复动作检测
- **文件:** `app/agents/tool_agent.py`
- **行号:** 约 162-255
- **严重度:** 🔴严重
- **分类:** Agent 设计
- **问题:** 
  1. 单次 LLM 调用和工具执行无 timeout 保护，一轮卡住整个请求失败
  2. 无重复动作检测——LLM 可能反复调用同一工具获取相同结果而不自知
  3. 仅靠 `MAX_ROUNDS=5` 硬上限不够
- **建议:** 添加 `asyncio.wait_for(timeout=30)` 包装。维护工具调用历史 set，同一工具+参数调用超过 2 次时注入提示引导 LLM 改变策略。

### 问题 P6: `ToolAgent` 的 `RouterExhaustedError` 处理与 Mock 降级逻辑不一致
- **文件:** `app/agents/tool_agent.py`
- **行号:** 约 169-178
- **严重度:** 🔴严重
- **分类:** Agent 设计 / 异常处理
- **问题:** `_run_with_llm` 中 `RouterExhaustedError` 直接返回 `success=False` 不降级，但 `run()` 方法中 `_run_with_llm` 的调用又被 `except Exception` 捕获并降级 Mock。两处逻辑不一致。如果 LLM 在前面轮次成功但在后面轮次 RouterExhausted，返回的是 `success=False` 而非 Mock 降级结果。
- **建议:** 在 `_run_with_llm` 内部统一：`RouterExhaustedError` 时回退到 `_run_mock` 补全剩余工具调用。

---

## 🟠 重要问题（10 个）

### 问题 P7: `_is_retriable` 在 DeepSeekProvider 和 QwenProvider 中完全重复
- **文件:** `app/llm/deepseek_provider.py`（第 90-111 行）, `app/llm/qwen_provider.py`（第 94-111 行）
- **严重度:** 🟠重要
- **分类:** 重复代码
- **问题:** 两个 Provider 的 `_is_retriable` 静态方法（22 行）逐字相同，包含相同的异常类型集合和 HTTP 状态码判断。
- **建议:** 提升到 `LLMProvider` 基类 `app/llm/base.py` 中作为类方法。

### 问题 P8: `FIELD_GENERATOR_MAP` / `SEMANTIC_LABEL_MAP` / `label_to_gen` 三个映射表高度重复
- **文件:** `app/agents/testdata_agent.py`（第 28-115 行）, `app/agents/schema_agent.py`（第 30-87 行）, `app/agents/strategy_agent.py`（第 361-375 行）
- **严重度:** 🟠重要
- **分类:** 重复代码
- **问题:** 三个 Agent 各自维护字段名→语义/生成器映射表，覆盖几乎相同的字段名集合。修改一个字段映射需改三处。
- **建议:** 创建 `app/shared/field_mappings.py`，集中定义所有映射关系，所有 Agent 从此导入。

### 问题 P9: `_extract_count` 和 `_extract_task_name` 在两个 Agent 中重复
- **文件:** `app/agents/testdata_agent.py`（第 443-467 行）, `app/agents/strategy_agent.py`（第 396-418 行）
- **严重度:** 🟠重要
- **分类:** 重复代码
- **问题:** 两个方法在两个 Agent 中几乎完全相同，仅 `_extract_count` 默认值不同。
- **建议:** 提取到 `app/shared/text_utils.py`，通过参数控制默认行为。

### 问题 P10: `_parse_plan` 在 TestDataAgent 和 StrategyAgent 中完全重复
- **文件:** `app/agents/testdata_agent.py`（第 469-496 行）, `app/agents/strategy_agent.py`（第 420-447 行）
- **严重度:** 🟠重要
- **分类:** 重复代码
- **问题:** LLM 返回 dict → `GenerationPlan` Pydantic 模型的解析逻辑逐行相同。
- **建议:** 移到 `GenerationPlan` 模型自身，添加 `@classmethod from_dict()`。

### 问题 P11: `SchemaAgent._analyze_mock` 方法 112 行
- **文件:** `app/agents/schema_agent.py`
- **行号:** 约 238-349
- **严重度:** 🟠重要
- **分类:** 方法过长
- **问题:** 该方法承担 12 个步骤：遍历表→遍历列→收集主键→语义标签匹配→敏感检测→外键推断→生成器推荐→构建 AnalyzedColumn→表级建议→去重→构建 AnalyzedTable→构建结果。圈复杂度极高。
- **建议:** 拆分为 `_analyze_table_mock()`, `_analyze_column_mock()`, `_build_table_recommendations()`。

### 问题 P12: `routes.py` 的 `detect_sensitive` 单个路由函数承担 5 个职责
- **文件:** `app/api/routes.py`
- **行号:** 约 255-345（91 行）
- **严重度:** 🟠重要
- **分类:** 方法过长 / 类职责
- **问题:** 一个路由函数含有：Prompt 定义（43 行）、LLM 调用、JSON 解析、异常处理、响应构建。违反单一职责。
- **建议:** LLM 调用逻辑提取到 `app/agents/privacy_agent.py`，Prompt 移到 `app/prompts/privacy_prompt.py`，路由仅做参数校验和委托。

### 问题 P13: `SchemaAgent` 承担 5 种独立职责（442 行）
- **文件:** `app/agents/schema_agent.py`
- **行号:** 约 142-583
- **严重度:** 🟠重要
- **分类:** 类职责过多
- **问题:** 同时负责：Schema 文本描述构建、语义标签匹配、外键推断、生成器推荐、LLM 结果解析。文本序列化 + 规则匹配 + 外键推断 + 生成器选择 + JSON 解析混在一个类中。
- **建议:** 将规则引擎部分（`_match_semantic_label`, `_infer_foreign_key`, `_suggest_generator`）抽取到独立模块 `app/shared/rule_engine.py`。

### 问题 P14: 缺少统一的 FastAPI 异常处理中间件
- **文件:** `app/main.py`, `app/api/routes.py`
- **行号:** 遍布
- **严重度:** 🟠重要
- **分类:** FastAPI 实践
- **问题:** 各路由自行 try/except，无全局 `exception_handler`。新路由忘记处理异常会暴露内部 traceback 给客户端。
- **建议:** 在 `main.py` 注册 `@app.exception_handler(Exception)` 全局返回 `{"success": False, "error": "Internal server error"}`。

### 问题 P15: Pydantic 模型字段使用 camelCase 而非 Python snake_case
- **文件:** `app/schemas/schema_analysis.py`, `app/schemas/generation_plan.py`, `app/api/routes.py`
- **行号:** 遍布
- **严重度:** 🟠重要
- **分类:** 命名
- **问题:** 所有 Pydantic 字段使用 `tableName`, `dbType`, `semanticLabel` 等 camelCase，不符合 PEP 8。Java 端接受 camelCase 可以作为理由，但 Python 内部代码应用 snake_case + Pydantic alias 自动转换。
- **建议:** 配置 `model_config = {"alias_generator": to_camel, "populate_by_name": True}`。

### 问题 P16: `DetectedFieldResult.type` 覆盖 Python 内置 `type` 函数
- **文件:** `app/api/routes.py`
- **行号:** 约 199
- **严重度:** 🟠重要
- **分类:** 命名
- **问题:** Pydantic 字段名 `type` 遮蔽了 Python `type()` 内置函数。类方法内部使用 `type()` 会产生意外行为。
- **建议:** 重命名为 `sensitive_type` 或 `field_type`。如 API 必须返回 `type`，使用 `Field(alias="type")`。

---

## 🟡 建议问题（8 个）

### 问题 P17: `TestDataAgent` 与 `SchemaAgent + StrategyAgent` 流水线功能重叠
- **文件:** `app/agents/testdata_agent.py`（全文 501 行）
- **严重度:** 🟡建议
- **分类:** 过度设计
- **问题:** `TestDataAgent` 直接从原始 schema dict 生成 plan，而 `GenerationChain` 通过 SchemaAgent + StrategyAgent 流水线也能完成同样功能且更精准。`routes.py` 中无任何路由使用 `TestDataAgent`。
- **建议:** 评估是否仍被 Java 后端直接调用。如不需要，标记为废弃。

### 问题 P18: `LLMService` 是冗余抽象
- **文件:** `app/services/llm_service.py`
- **行号:** 第 1-89 行
- **严重度:** 🟡建议
- **分类:** 过度设计
- **问题:** 仅封装 DeepSeek 单模型调用，功能完全被 `DeepSeekProvider` + `LLMRouter` 覆盖。`extract_json` 方法与 `LLMRouter.extract_json` 重复。
- **建议:** 删除 `app/services/llm_service.py`。

### 问题 P19: `StrategyAgent` 混合了 5 种独立逻辑（418 行）
- **文件:** `app/agents/strategy_agent.py`
- **行号:** 约 34-451
- **严重度:** 🟡建议
- **分类:** 类职责过多
- **问题:** 包含拓扑排序、需求解析、分析描述构建、生成器兜底匹配、Plan 解析。需求解析逻辑与 TestDataAgent 重复。
- **建议:** `_order_by_dependency` 移到独立模块，`_extract_count`/`_extract_task_name` 与 TestDataAgent 共享。

### 问题 P20: 缺少请求级 Trace ID / Correlation ID
- **文件:** 所有文件
- **行号:** 遍布
- **严重度:** 🟡建议
- **分类:** 日志
- **问题:** 所有日志调用无请求标识符。并发请求下无法将同一请求的 Schema 分析 → 策略生成 → LLM 调用日志关联。
- **建议:** FastAPI middleware 生成 `X-Request-ID`，通过 `contextvars` 传递，使用 loguru 的 `logger.bind(request_id=...)`。

### 问题 P21: LLM 调用缺少 Token 消耗和延迟指标
- **文件:** `app/llm/deepseek_provider.py`（第 68-75 行）, `app/llm/qwen_provider.py`（第 72-80 行）
- **严重度:** 🟡建议
- **分类:** 日志
- **问题:** 仅记录响应字符数，未记录 `usage.prompt_tokens`/`completion_tokens`、调用延迟、实际使用的模型名称。
- **建议:** 从 OpenAI response 对象提取 `usage` 和 `model`，以 `logger.info` 级别输出。

### 问题 P22: `response_model` 使用不一致
- **文件:** `app/api/routes.py`
- **行号:** 约 37, 72, 94, 103, 136, 255
- **严重度:** 🟡建议
- **分类:** FastAPI 实践
- **问题:** 部分路由有 `response_model`（如 `/analyze-schema`），部分没有（如 `/tool-agent`）。无者无法享受自动响应校验和 OpenAPI schema 生成。
- **建议:** 为所有路由统一添加 `response_model`。

### 问题 P23: 全局单例导致测试需复杂 monkeypatch
- **文件:** `app/agents/schema_agent.py`（第 587 行）, `app/agents/strategy_agent.py`（第 451 行）, 等
- **严重度:** 🟡建议
- **分类:** Agent 设计 / 可测试性
- **问题:** 所有 Agent/Provider/Tool 使用模块级全局单例，测试中需 monkeypatch 或直接替换模块属性，并发测试不安全。
- **建议:** 使用 FastAPI `Depends()` 依赖注入。模块级保留默认单例，测试中通过 override 替换。

### 问题 P24: `Tool` 基类输入输出使用 `dict[str, Any]` 无类型约束
- **文件:** `app/tools/base.py`
- **行号:** 约 36-65
- **严重度:** 🟡建议
- **分类:** Agent 设计
- **问题:** `Tool.execute()` 参数和返回值都是 `dict[str, Any]`，调用方和实现方之间缺乏类型契约。工具返回不符合预期的数据结构只能在运行时发现。
- **建议:** 为每个工具定义泛型 Pydantic 输入/输出模型，如 `Tool[SchemaToolInput, SchemaToolOutput]`。

---

## 🔵 风格问题（3 个）

### 问题 P25: 空 `app/agent/` 与 `app/agents/` 并存
- **文件:** `app/agent/__init__.py`
- **严重度:** 🔵风格
- **分类:** 过度设计 / 命名
- **问题:** `app/agent/` 和 `app/agents/` 两个目录共存，`agent/` 仅含空 `__init__.py`，易混淆。
- **建议:** 删除 `app/agent/` 目录。

### 问题 P26: 路由导入放在 `app` 实例创建之后
- **文件:** `app/main.py`
- **行号:** 约 41
- **严重度:** 🔵风格
- **分类:** FastAPI 实践
- **问题:** `from app.api.routes import router` 放在文件末尾而非顶部，虽然避免了循环导入但不符合标准实践。
- **建议:** 确认无循环导入后移到文件顶部。如有问题，重构模块拆分。

### 问题 P27: `_analyze_mock` 中使用 `list(dict.fromkeys(...))` 去重不直观
- **文件:** `app/agents/schema_agent.py`
- **行号:** 约 314
- **严重度:** 🔵风格
- **分类:** 代码可读性
- **问题:** `list(dict.fromkeys(recommendations))` 是 Python 技巧但不直观。
- **建议:** 改用 `list(set(recommendations))` 或添加注释说明意图。

---

## 🔵 补充（1 个）

### 问题 P28: 缺少 Prompt 版本管理和 Token 预算监控
- **文件:** `app/prompts/schema_prompt.py`, `app/prompts/strategy_prompt.py`, `app/agents/tool_agent.py`
- **行号:** 各 Prompt 定义处
- **严重度:** 🟡建议
- **分类:** Agent 设计
- **问题:** System Prompt 以字符串常量硬编码无版本号。大 Schema 可能超出 context window 但无预检查。
- **建议:** 添加版本注释 `# v1.2.0`。在 `LLMRouter.chat()` 中粗略估算消息长度，超限时提前 warning。

---

# 第三部分：前端 Vue（26 个问题）

---

## 🔴 严重问题（3 个）

### 问题 F1: Sidebar/Header/Layout 在 14 个页面中逐字重复
- **文件:** `views/Dashboard.vue`, `views/ProjectList.vue`, `views/DatasourceManage.vue`, `views/TestDataGenerate.vue`, `views/TestDataTask.vue`, `views/TaskMonitor.vue`, `views/TestDataResult.vue`, `views/SchemaView.vue`, `views/RelationGraph.vue`, `views/GenerationPlan.vue`, `views/AgentTrace.vue`, `views/MaskConfig.vue`, `views/DataQuality.vue`, `views/DatabaseMask.vue`, `views/DataExport.vue`
- **行号:** 每个文件第 1-62 行
- **严重度:** 🔴严重
- **分类:** 重复代码
- **问题:** 14 个视图文件（除 Login.vue）包含完全相同的大段 sidebar + header + layout 模板代码，约 62 行 template 重复。每次修改导航菜单需要同步 15 个文件。**这是整个项目最大的技术债务。**
- **建议:** 创建 `AppLayout.vue` 布局组件，使用 `<slot>` 渲染子内容。路由改为嵌套路由，父路由指向 AppLayout，子路由指向各页面内容。

### 问题 F2: Layout CSS 在每个视图中完全重复
- **文件:** 同上 14 个文件
- **行号:** 每个文件 `<style scoped>` 中约 304-370 行
- **严重度:** 🔴严重
- **分类:** 重复代码
- **问题:** `.layout`, `.layout-header`, `.header-left`, `.header-right`, `.user-info`, `.layout-aside`, `.layout-main` 等样式在每个视图中逐字重复，每文件 ~15 行重复。
- **建议:** 随 F1 一起提取到 `AppLayout.vue`，各页面不再重复。

### 问题 F3: `quality.js` 导入路径错误 → 运行时崩溃
- **文件:** `api/quality.js`
- **行号:** 第 1 行
- **严重度:** 🔴严重
- **分类:** 代码组织 / 异常处理
- **问题:** `import request from '@/utils/request'` — 路径不存在（应为 `'./request'`）。DataQuality.vue 页面导入该模块时直接报错，质量评估功能完全不可用。
- **建议:** 将 `@/utils/request` 改为 `./request`。

---

## 🟠 重要问题（11 个）

### 问题 F4: `handleLogout` 在 14 个页面中重复
- **文件:** 所有含 sidebar 的视图文件
- **行号:** 每个文件中最后 3 行
- **严重度:** 🟠重要
- **分类:** 重复代码
- **问题:** 每个页面定义了完全相同的 `handleLogout`：清空 store + 跳转 `/login`。
- **建议:** 随 F1 的 AppLayout 提取，各子页面不再需要导入和定义。

### 问题 F5: `getColumns` 工具函数在两个文件中完全重复
- **文件:** `views/TestDataResult.vue`（约第 217 行）, `views/DataExport.vue`（约第 329 行）
- **严重度:** 🟠重要
- **分类:** 重复代码
- **问题:** 从 rows 数组中提取所有唯一列名的 `getColumns` 函数在两个文件中实现一字不差。
- **建议:** 创建 `src/utils/table.js`，提取为 `extractColumnKeys(rows)`。

### 问题 F6: 路由未利用嵌套特性导致 Layout 重复
- **文件:** `router/index.js`
- **行号:** 约 3-104
- **严重度:** 🟠重要
- **分类:** 代码组织
- **问题:** 路由配置扁平化，每个需 sidebar 的路由标记了 `meta: { requireAuth: true }`，但未使用 Vue Router 嵌套路由 + `<router-view>`。
- **建议:** 重构为嵌套路由。父路由指向 `AppLayout.vue`，子路由指向各页面内容组件。

### 问题 F7: `activeMenu` 值与实际路由不匹配
- **文件:** `views/SchemaView.vue`, `views/RelationGraph.vue`, `views/GenerationPlan.vue`, `views/TestDataResult.vue`, `views/TaskMonitor.vue`
- **行号:** 各文件中 `activeMenu` 变量定义处
- **严重度:** 🟠重要
- **分类:** 命名 / 缺陷
- **问题:** 多个视图的 `activeMenu` 硬编码为 `/testdata/task`，但实际路由分别为 `/schema/view`、`/schema/relation`、`/testdata/plan`、`/testdata/result`、`/task-monitor`。侧边栏高亮始终停在错误菜单项。
- **建议:** 使用 `route.path` 或 `route.matched` 动态计算 `activeMenu`。

### 问题 F8: 多个文件超过 300 行（去重后）
- **文件:** `views/RelationGraph.vue`（464 行）, `views/DataQuality.vue`（522 行）, `views/MaskConfig.vue`（456 行）, `views/DatabaseMask.vue`（428 行）, `views/DataExport.vue`（412 行）, `views/AgentTrace.vue`（405 行）
- **严重度:** 🟠重要
- **分类:** 组件过长
- **问题:** 即使去掉重复 layout 代码（~85 行），仍有 6 个文件超 300 行。DataQuality.vue 达到 522 行。
- **建议:** DataQuality 的 ECharts 雷达图逻辑可提取为 `useRadarChart` composable。RelationGraph 的力导向图配置可提取为 `useRelationGraph` composable。DatabaseMask 的步骤向导可拆分子组件。

### 问题 F9: `DatasourceManage.vue` 承担过多职责
- **文件:** `views/DatasourceManage.vue`
- **行号:** 全文 371 行
- **严重度:** 🟠重要
- **分类:** 组件职责
- **问题:** 同时负责：项目筛选、数据源 CRUD、连接测试、Schema 查看对话框（含完整表/字段明细表格）。Schema 查看对话框本身就是一个完整子功能。
- **建议:** 将 Schema 查看对话框提取为 `<SchemaViewerDialog>` 子组件，通过 props 接收数据源 ID。

### 问题 F10: `v-for` 使用 `index` 作为 key
- **文件:** `views/TestDataResult.vue`（第 121-123 行）, `views/GenerationPlan.vue`（第 148-149 行）, `views/DataExport.vue`（第 107-108 行）
- **严重度:** 🟠重要
- **分类:** Vue 实践
- **问题:** 列表可能增删/排序时，`:key="index"` 导致 Vue 无法正确跟踪节点身份，可能引发渲染错误。
- **建议:** 改为 `:key="table.tableName"` 或其他唯一标识符。

### 问题 F11: API 响应拦截器过度拆包导致元信息丢失
- **文件:** `api/request.js`
- **行号:** 约 22-36
- **严重度:** 🟠重要
- **分类:** 异常处理
- **问题:** 响应拦截器 `response => response.data` 拆包导致 `response.status`/`response.headers` 全部丢失。blob 下载和 text 预览需要 `transformResponse` 覆盖此行为。部分视图中有 `res.code === 200` 的冗余检查，说明开发者不确定响应结构。
- **建议:** 统一后端返回格式，前端信任拦截器拆包。或不在拦截器中拆包，调用者自行访问。

### 问题 F12: 大量空 catch 块静默吞掉异常
- **文件:** `views/Dashboard.vue`（121-123）, `views/ProjectList.vue`（202-203）, `views/TestDataGenerate.vue`（268, 278, 331）, `views/TestDataTask.vue`（189, 199）, `views/DatasourceManage.vue`（238, 332）, `views/DatabaseMask.vue`（252, 280）, `views/DataExport.vue`（229）
- **行号:** 如上
- **严重度:** 🟠重要
- **分类:** 异常处理
- **问题:** 大量 catch 块注释为 `/* handled by interceptor */` 但实际为空。500、网络超时、CORS 等错误被完全吞掉，用户无任何错误感知。
- **建议:** 至少调用 `ElMessage.error('操作失败，请稍后重试')` 作为兜底。关键操作（删除、保存）必须确保错误被用户感知。

### 问题 F13: 防抖定时器未在组件卸载时清除（内存泄漏）
- **文件:** `views/MaskConfig.vue`
- **行号:** 约 258（定义 `testTimer`）；约 301-314（使用 `setTimeout`）
- **严重度:** 🟠重要
- **分类:** 前端专项
- **问题:** `handleTestMask` 中使用防抖定时器 `testTimer`，`onUnmounted` 中无 `clearTimeout(testTimer)`。用户在 300ms 防抖内离开页面，定时器仍触发 API 调用。
- **建议:** 添加 `onUnmounted(() => { if (testTimer) clearTimeout(testTimer) })`。

### 问题 F14: 缺少 `utils/` 工具函数目录
- **文件:** 项目整体
- **严重度:** 🟠重要
- **分类:** 代码组织
- **问题:** 无 `src/utils/` 目录，导致工具函数散落各处、重复定义。`getColumns` 在两个文件中存在，`formatTime` 仅在 TaskMonitor 定义，`downloadBlob` 在 DataExport 定义。
- **建议:** 创建 `src/utils/`，拆分为 `table.js`、`date.js`、`file.js`、`status.js`。

---

## 🟡 建议问题（9 个）

### 问题 F15: 时间格式化逻辑分散在多处
- **文件:** `views/TaskMonitor.vue`（约 293-298）, `views/DataExport.vue`（约 344-353）
- **严重度:** 🟡建议
- **分类:** 重复代码
- **问题:** TaskMonitor 有 `formatTime`，DataExport 有 `timestamp`，功能相似但实现不同。
- **建议:** 统一到 `src/utils/date.js` → `formatDateTime(dateStr)` + `timestamp()`。

### 问题 F16: statusTagType/statusLabel 模式在多个页面重复
- **文件:** `views/TestDataTask.vue`, `views/TaskMonitor.vue`, `views/DatabaseMask.vue`
- **严重度:** 🟡建议
- **分类:** 重复代码
- **问题:** 三个文件中都有 status→tag-type / status→label 的 map 映射函数，模式完全相同。
- **建议:** 创建通用 `createStatusMapper(map)` 工厂函数放在 utils 中。

### 问题 F17: Loading 状态展示模板重复
- **文件:** 几乎所有视图文件
- **严重度:** 🟡建议
- **分类:** 重复代码
- **问题:** 加载中展示（旋转图标+提示文字）在每个页面中至少 1 处重复，个别页面有 2-3 处。
- **建议:** 创建 `<LoadingCard>` 组件，接受 `text` prop。

### 问题 F18: 遗留占位组件未清理（HelloWorld.vue）
- **文件:** `components/HelloWorld.vue`
- **严重度:** 🟡建议
- **分类:** 代码组织
- **问题:** Vite 脚手架生成的演示组件，无任何引用，属于死代码。
- **建议:** 删除 `HelloWorld.vue` 及相关 assets（vite.svg, hero.png, vue.svg 等）。

### 问题 F19: 模板中过度复杂的条件渲染链
- **文件:** `views/TestDataResult.vue`
- **行号:** 约 76-153
- **严重度:** 🟡建议
- **分类:** 组件过长
- **问题:** 多层 `v-if/v-else-if/v-else` 处理 5 种状态（参数缺失/加载中/加载失败/空结果/有结果），每个分支都是完整 `<el-card>`。
- **建议:** 使用 `<component :is="stateComponent">` 动态组件模式，模板缩为 1 行。

### 问题 F20: MaskConfig.vue 中业务逻辑与展示混在一起
- **文件:** `views/MaskConfig.vue`
- **行号:** 约 182-226
- **严重度:** 🟡建议
- **分类:** 组件职责
- **问题:** "三层融合检测流程" 展示块是纯静态内容，与脱敏配置/测试功能无关，但嵌入在主组件中增加复杂度。
- **建议:** 提取为独立 `<DetectionPipeline />` 组件。

### 问题 F21: 全局注册所有 Element Plus 图标但个别文件重复手动导入
- **文件:** `main.js`（第 13-16 行）, `views/AgentTrace.vue`（第 185-188 行）, `views/MaskConfig.vue`（第 237-241 行）
- **严重度:** 🟡建议
- **分类:** Vue 实践
- **问题:** `main.js` 全局注册了所有图标，但 AgentTrace 和 MaskConfig 额外手动导入了未用的图标组件。导入属于死代码。
- **建议:** 删除多余 import。或反向操作——移除全局注册改为按需导入以减小打包体积。

### 问题 F22: `document.execCommand('copy')` 已废弃
- **文件:** `views/DataExport.vue`
- **行号:** 约 310-321
- **严重度:** 🟡建议
- **分类:** 前端专项
- **问题:** `handleCopy` 降级方案使用被废弃的 `execCommand('copy')`。`navigator.clipboard.writeText` 已是现代标准。
- **建议:** 移除 `execCommand` 分支，改用 `ElMessage.warning('请手动复制')` 兜底。

### 问题 F23: 硬编码常量散落各文件
- **文件:** `views/TestDataGenerate.vue`（约 244-249）, `views/MaskConfig.vue`（约 262-268）, `views/DataQuality.vue`（约 333-339）
- **严重度:** 🟡建议
- **分类:** 代码组织
- **问题:** 快捷需求模板、敏感类型颜色映射、等级颜色映射等内联定义在各组件中。
- **建议:** 创建 `src/constants/index.js` 集中管理业务常量。

---

## 🔵 风格问题（3 个）

### 问题 F24: 事件处理函数命名不一致（handleXxx vs onXxx）
- **文件:** `views/DatabaseMask.vue`, `views/DataExport.vue`, `views/TestDataGenerate.vue`
- **严重度:** 🔵风格
- **分类:** 命名
- **问题:** 项目整体使用 `handleXxx` 风格，但 DatabaseMask/DataExport/TestDataGenerate 混用了 `onXxx` 风格。
- **建议:** 统一为 `handleXxx`。

### 问题 F25: 表单变量命名过于通用
- **文件:** `views/TestDataGenerate.vue`
- **行号:** 约 238-241
- **严重度:** 🔵风格
- **分类:** 命名
- **问题:** `form` 这个 reactive 对象仅含 `requirement` 和 `targetTable`，无校验规则，更像查询参数。
- **建议:** 改为 `generateParams` 或 `queryConfig`。

### 问题 F26: `reactive` 与 `ref` 使用不一致
- **文件:** 多个视图文件
- **行号:** 各处表单状态定义
- **严重度:** 🔵风格
- **分类:** Vue 实践
- **问题:** 表单数据在 Login/DatasourceManage/TestDataGenerate/TestDataTask/MaskConfig 中使用 `reactive()`，在 ProjectList 中使用 `ref({})`。Vue 3 推荐 Composition API 中优先 `ref()`。
- **建议:** 统一使用 `ref()` 包裹对象。

---

# 优先修复清单（Top 15）

| 优先级 | 编号 | 问题 | 区域 | 预计时间 |
|--------|------|------|------|----------|
| 1 | F3 | quality.js 导入路径 Bug | 前端 | 1 分钟 |
| 2 | J3 | SecurityConfig API 全部 permitAll | 后端 | 10 分钟 |
| 3 | F1+F2 | 14 页面 Layout 重复 | 前端 | 1.5 小时 |
| 4 | P2+P3 | Python except Exception 吞异常 | AI | 30 分钟 |
| 5 | J4 | TestDataTaskExecutor 方法 160+ 行 | 后端 | 1 小时 |
| 6 | P1 | 删除废弃 LLMRouter 实现 | AI | 5 分钟 |
| 7 | J5 | DataQualityEvaluator 675 行违反 SRP | 后端 | 2 小时 |
| 8 | J1 | buildJdbcUrl 5 处重复 | 后端 | 30 分钟 |
| 9 | P5+P6 | ReAct 循环超时+死循环防护 | AI | 1 小时 |
| 10 | F12 | 前端大量空 catch 吞异常 | 前端 | 30 分钟 |
| 11 | P7-P10 | Python Agent 间重复映射/解析 | AI | 1 小时 |
| 12 | F9 | DatasourceManage 职责拆分 | 前端 | 30 分钟 |
| 13 | J2 | evaluatePrivacy 重复实现敏感检测 | 后端 | 15 分钟 |
| 14 | F6 | 路由嵌套重构 | 前端 | 1 小时 |
| 15 | P4 | GeneratePlanRequest 字段名修复 | AI | 10 分钟 |

---

> **结论：** 三大代码区域共发现 77 个问题。最突出的模式是**重复代码**（15 个，19.5%）——前端 14 页面 Layout 重复和后端 JdbcUrl 5 处重复是最典型的技术债务。其次是**异常处理**（12 个，15.6%）——大面积 `except Exception` 静默吞异常。建议在答辩前优先完成 Top 15 修复项，预计总工作量约 10-12 小时。
