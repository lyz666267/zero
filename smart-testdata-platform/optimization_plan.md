# 项目优化执行计划 — 从"毕业设计完成版"到"测试开发面试展示版"

> **制定日期：** 2026-08-04
> **制定人：** 项目技术负责人
> **数据来源：** 架构审查报告 + Java 代码质量审查 + Python AI 服务审查 + Vue 代码质量审查 + 测试开发面试评审报告
> **基本原则：** ①不新增大型业务功能 ②不改变现有核心架构 ③不破坏已有测试 ④优先安全/稳定性/面试问题

---

## 总览

| 优先级 | 数量 | 预计总工时 | 面试影响 |
|--------|------|-----------|----------|
| 🔴 P0 必须修复 | **10** | ~6 小时 | 不修复面试直接挂 |
| 🟠 P1 强烈建议 | **15** | ~16 小时 | 显著提升岗位竞争力 |
| 🟢 P2 可选优化 | **12** | ~8 小时 | 锦上添花，展示工程素养 |
| **合计** | **37** | **~30 小时** | — |

---

# A. P0 必须修复（10 项）

> **判定标准：** 安全漏洞、真实 Bug、面试致命问题

---

### P0-01 🔴 SecurityConfig — 所有业务 API `permitAll()`，JWT 认证形同虚设

- **涉及文件：** [SecurityConfig.java](backend/src/main/java/com/platform/config/SecurityConfig.java):27-39
- **问题描述：** 除 `/api/auth/**` 外，所有端点（datasource、project、schema、privacy、quality、testdata、export、agent、mask）全部标记为 `.permitAll()`。JWT 过滤器虽然存在并被注入过滤器链，但安全配置将其完全绕过。任何人均可未认证调用所有业务 API。
- **面试场景：** 面试官问"你的 API 怎么鉴权？"→ 你答 JWT → 面试官看代码发现全部 permitAll → 直接判定安全意识为零
- **修改方案：**
  1. 仅保留 `/api/auth/**`、`/api/ai/health`、静态资源为 `permitAll()`
  2. 其余所有 `/api/**` 路径统一要求 `.authenticated()`
  3. 如需保留开发便利性，使用 Spring profile（`dev` 放行 / `prod` 鉴权）
- **预计影响范围：**
  - 前端所有 API 调用将需要进行 JWT 认证（已有 token 在 request 拦截器中携带，**预期前端改动为零**）
  - 集成测试 `FullWorkflowIntegrationTest` 可能需要增加 token 获取步骤
- **修改后需执行的测试：**
  ```bash
  mvn test -pl backend -Dtest=FullWorkflowIntegrationTest
  # 验证：未认证请求 → 401，带 token 请求 → 200
  ```

---

### P0-02 🔴 AES 使用 ECB 模式 + 密钥硬编码 — 数据源密码可被解密

- **涉及文件：**
  - [AesUtil.java](backend/src/main/java/com/platform/util/AesUtil.java):30-56
  - [application.yml](backend/src/main/resources/application.yml)（`platform.datasource.aes-key`）
- **问题描述：**
  1. `Cipher.getInstance("AES")` 在 Java 中默认使用 **ECB 模式**（无 IV、相同明文产生相同密文，可被模式分析攻击）
  2. AES 密钥硬编码在 `application.yml` 中（`SmartPlatformAESK`），提交到 Git 仓库
- **面试场景：** 面试官问"AES 用的什么模式？"→ ECB → 追问"ECB 有什么问题？"→ 直接暴露密码学基础薄弱
- **修改方案：**
  1. 改为 `Cipher.getInstance("AES/GCM/NoPadding")`（带认证加密）
  2. 加密时随机生成 12 字节 IV，拼接到密文前面：`Base64(IV + ciphertext)`
  3. 解密时从密文中分离 IV，用 GCMParameterSpec 指定
  4. 密钥全部改为环境变量读取：`${AES_ENCRYPT_KEY:}`（移除 fallback）
  5. **数据兼容性处理：** 新增 `encryptV2()`/`decryptV2()` 方法，旧 ECB 密文在解密时优先尝试 GCM，失败后回退 ECB 解密并自动重新加密为 GCM 格式
- **预计影响范围：**
  - 已有数据库中的 ECB 密文需兼容读取
  - `DatasourceService` 中加解密调用不受影响（AesUtil 接口不变）
- **修改后需执行的测试：**
  ```bash
  mvn test -pl backend -Dtest=DatasourceServiceTest
  # 验证：ECB 旧密文可解密、GCM 新密文可加解密、密钥为空时启动报错
  ```

---

### P0-03 🔴 JWT Secret 有硬编码 fallback

- **涉及文件：**
  - [application.yml](backend/src/main/resources/application.yml)（`jwt.secret`）
  - [application.yml](backend/src/test/resources/application.yml)（测试环境）
- **问题描述：** `jwt.secret: ${JWT_SECRET:smart-testdata-platform-secret-key-2026}` — 如果环境变量 `JWT_SECRET` 未设置，使用硬编码 fallback。该值已提交到 Git 仓库，任何人可用此 secret 签发有效 token。
- **修改方案：**
  1. 生产配置移除 fallback：`jwt.secret: ${JWT_SECRET:}`（未设置时启动报错）
  2. 测试配置保留 fallback（仅用于自动化测试）
  3. 在 `application-prod.yml` 移除 fallback，`application-dev.yml` 保留
- **预计影响范围：** 启动时必须设置 `JWT_SECRET` 环境变量，否则启动失败
- **修改后需执行的测试：**
  ```bash
  mvn test -pl backend
  # 验证：测试环境使用 fallback、生产环境无 JWT_SECRET 启动失败
  ```

---

### P0-04 🔴 `quality.js` 导入路径错误 → 质量评估功能运行时崩溃

- **涉及文件：** [quality.js](frontend/src/api/quality.js):1
- **问题描述：** `import request from '@/utils/request'` — 路径不存在，应为 `'./request'`。DataQuality.vue 导入此模块时直接抛错，整个数据质量评分功能完全不可用。
- **面试场景：** 演示质量评分功能 → 页面白屏 → 面试官 F12 看到红色报错 → 直接判定代码未自测
- **修改方案：** 将 `import request from '@/utils/request'` 改为 `import request from './request'`
- **预计影响范围：** 仅 1 行改动，零风险。DataQuality.vue 恢复正常。
- **修改后需执行的测试：**
  - 手动：打开 `/data-quality` 页面 → 页面正常渲染

---

### P0-05 🔴 DatabaseMaskService SQL 注入风险 — 表名直接拼接到 SELECT 语句

- **涉及文件：** [DatabaseMaskService.java](backend/src/main/java/com/platform/service/DatabaseMaskService.java):~424
- **问题描述：** `String.format("SELECT * FROM %s LIMIT %d", tableName, limit)` — 虽然使用了反引号包裹，但攻击者可输入 `` `users`; DROP TABLE users; -- `` 绕过。`tableName` 来自前端用户输入，未经白名单校验。
- **面试场景：** 你说"项目做了 SQL 注入防护" → 面试官追问"表名拼接怎么处理？"→ 暴露盲区
- **修改方案：**
  1. 在执行查询前，校验 `tableName` 是否存在于目标数据库的 `information_schema.TABLES` 中
  2. 提取 `validateTableExists(dataSource, tableName)` 方法，查询前先白名单校验
  3. 同时校验表名格式：仅允许 `[a-zA-Z_][a-zA-Z0-9_]*` 正则匹配
- **预计影响范围：** `DatabaseMaskService` 的 preview/execute 流程增加一步校验
- **修改后需执行的测试：**
  ```bash
  mvn test -pl backend -Dtest=DatabaseMaskServiceTest
  # 验证：正常表名通过、恶意表名抛出异常
  ```

---

### P0-06 🔴 异步流程跳过隐私脱敏 — 敏感数据明文写入数据库

- **涉及文件：** [TestDataTaskExecutor.java](backend/src/main/java/com/platform/generator/task/TestDataTaskExecutor.java):~168
- **问题描述：** 异步执行器的第 5 步（隐私处理）被注释跳过，注释写"需通过 POST /api/privacy/process-auto 独立调用"。数据生成后直接写入数据库，敏感字段（手机号/邮箱/身份证）明文存储，违背项目核心卖点。
- **面试场景：** 你说"生成的数据自动做了脱敏" → 面试官看代码发现被注释掉 → 被质疑诚信
- **修改方案：**
  1. 在数据写入成功后、保存结果前，自动调用 `PrivacyService.processAuto()`
  2. 脱敏失败记录 warn 日志但不阻塞整体流程（降级策略）
  3. 在 Agent 执行日志中增加 `PRIVACY` 步骤记录
- **预计影响范围：** `TestDataTaskExecutor.executeTask()` 增加一步操作
- **修改后需执行的测试：**
  ```bash
  mvn test -pl backend -Dtest=FullWorkflowIntegrationTest
  # 验证：生成的敏感字段被脱敏（phone → 138****5678 等）
  ```

---

### P0-07 🔴 Python `except Exception` 大面积静默吞异常

- **涉及文件：**
  - [schema_agent.py](ai-service/app/agents/schema_agent.py):177-179, 234-236
  - [strategy_agent.py](ai-service/app/agents/strategy_agent.py):74-76, 131-133
  - [testdata_agent.py](ai-service/app/agents/testdata_agent.py):251-253, 308-312
  - [routes.py](ai-service/app/api/routes.py):322-324（detect_sensitive 端点吞异常返回 `success=True`）
- **问题描述：** 多个 Agent 使用 `except Exception as e` 捕获所有异常直接降级 Mock，不区分 `MemoryError`、`SystemExit`、Pydantic `ValidationError` 等不可恢复错误。真正的 bug 被隐藏。`detect_sensitive` 端点更严重——吞掉所有异常后返回 `success=True` + 空字段列表，调用方无法区分"无敏感字段"和"系统崩溃"。
- **面试场景：** 面试官看测试覆盖率 → 疑问"真出了问题怎么排查？"→ 大量静默异常无法定位
- **修改方案：**
  1. 替换为具体异常类型：
     ```python
     except (LLMProviderError, RouterExhaustedError, json.JSONDecodeError) as e:
         # 可恢复 LLM 错误 → 降级 Mock
     except asyncio.TimeoutError as e:
         # 超时 → 降级 Mock
     # 其他异常（MemoryError/SystemExit）必须传播，不能被静默捕获
     ```
  2. `detect_sensitive` 区分 LLM 可恢复错误（返回 mock=true）和系统错误（返回 success=false）
- **预计影响范围：** 4 个 Agent 文件 + 1 个路由文件。已有测试预期 Mock 降级行为不变。
- **修改后需执行的测试：**
  ```bash
  cd ai-service && python -m pytest tests/ -v
  # 验证：83 个测试全部通过
  ```

---

### P0-08 🔴 Python ReAct 循环缺少单步超时和重复动作防护

- **涉及文件：** [tool_agent.py](ai-service/app/agents/tool_agent.py):162-255
- **问题描述：**
  1. 单次 LLM 调用无 timeout，默认 600 秒，卡住整个请求
  2. 无重复动作检测 — LLM 可能反复调用同一工具相同参数而不自知
  3. `RouterExhaustedError` 在 `_run_with_llm` 内部直接返回 `success=False`，不降级 Mock（与 `run()` 方法外层逻辑不一致）
- **修改方案：**
  1. LLM 调用添加 `asyncio.wait_for(timeout=30)`（已有常量 `TOOL_CALL_TIMEOUT` 但未在调用处使用）
  2. 工具执行添加 `asyncio.wait_for(timeout=30)`（已有常量 `TOOL_EXEC_TIMEOUT`）
  3. 添加重复调用检测：维护 `call_history: dict[tuple, int]`，同一 `(tool_name, params_json)` 被调用超过 2 次时，注入提示引导 LLM 改变策略（代码中已定义了 `MAX_DUPLICATE_CALLS` 和检测逻辑但提示未注入到 LLM 消息）
  4. `_run_with_llm` 中 `RouterExhaustedError` → 调用 `_run_mock` 补全剩余工具，而非返回失败
- **预计影响范围：** `tool_agent.py`，ReAct 循环更健壮
- **修改后需执行的测试：**
  ```bash
  cd ai-service && python -m pytest tests/test_tool_agent.py -v
  # 验证：41 个测试全部通过
  ```

---

### P0-09 🔴 Pydantic `GeneratePlanRequest` 禁用命名空间保护

- **涉及文件：** [generation_plan.py](ai-service/app/schemas/generation_plan.py):~44
- **问题描述：** `model_config = {"protected_namespaces": ()}` 关闭了所有 Pydantic 命名空间冲突检查，仅因字段名 `schema` 与 BaseModel 内部属性冲突。这是安全风险——未来新增字段可能与 Pydantic 内部方法名冲突而无声失败。
- **修改方案：**
  1. 将字段名 `schema` 改为 `schema_data`（同时更新 [routes.py](ai-service/app/api/routes.py) 和 [generation_chain.py](ai-service/app/chains/generation_chain.py) 中的引用）
  2. 移除 `protected_namespaces = ()`
  3. 检查 Java 后端发送的 JSON 字段名，同步修改
- **预计影响范围：** Python 侧 3 个文件 + Java 侧 `TestdataService` 的请求构建
- **修改后需执行的测试：**
  ```bash
  cd ai-service && python -m pytest tests/ -v
  # 验证：所有测试通过
  ```

---

### P0-10 🔴 CORS 全开 + MyBatis SQL 日志泄露 + DEBUG 日志泄露

- **涉及文件：**
  - [main.py](ai-service/app/main.py)（CORS `allow_origins=["*"]`）
  - [application.yml](backend/src/main/resources/application.yml)（MyBatis SQL 日志级别）
- **问题描述：**
  1. FastAPI CORS 配置 `allow_origins=["*"]` — 任意域名可跨域调用 API
  2. MyBatis SQL 日志在默认配置中输出完整 SQL 语句，可能含敏感数据
  3. 应统一降低日志级别为 `WARN`（生产）或限制 `INFO` 仅关键操作
- **修改方案：**
  1. CORS → 限制为具体前端域名（或使用环境变量 `CORS_ORIGINS`）
  2. MyBatis 日志 → `application-prod.yml` 中设置 `logging.level.com.platform.mapper: WARN`
- **预计影响范围：** Frontend dev 需配置 CORS 白名单
- **修改后需执行的测试：**
  - 手动：前端 dev 模式正常调用 API；`application-prod.yml` 不输出 SQL 日志

---

# B. P1 强烈建议优化（15 项）

> **判定标准：** 显著提升工程质量和测试开发岗位竞争力

---

### P1-01 🟠 前端 14 个页面 Layout 重复 — 提取 `<AppLayout>` 组件

- **涉及文件：** 14 个 Vue 页面（除 Login.vue 外全部）
- **问题描述：** 14 个视图文件各自复制了 ~62 行 template（sidebar + header + layout）+ ~15 行 CSS + `handleLogout` 方法。每次修改导航菜单需同步 15 个文件。**这是整个项目最大的技术债务，在 code_quality_review 中被标记为 F1+F2，面试时最容易被发现。**
- **修改方案：**
  1. 创建 [AppLayout.vue](frontend/src/components/AppLayout.vue) — 包含 sidebar + header + `<slot>` 内容区
  2. 路由改为嵌套路由：父路由指向 AppLayout，子路由指向各页面内容
  3. `handleLogout` 仅在 AppLayout 中定义一次
  4. `activeMenu` 通过 `route.path` 动态计算（同时修复 F7 bug）
- **预计影响范围：** 15 个文件（14 页面删除布局代码 + 1 新增 + router 重构）
- **修改后需执行的测试：**
  - 手动：逐个页面验证 sidebar 导航正常、菜单高亮正确、退出登录正常

---

### P1-02 🟠 补充 Controller 层 MockMvc 测试 — 面试致命缺口

- **涉及文件：** 新建 `src/test/java/com/platform/controller/` 下 3 个测试类
- **问题描述：** 后端 0 个 Controller HTTP 层测试——没有 @WebMvcTest、没有 MockMvc。面试官问"你的 REST API 怎么测试？"→ 答不上来。code_review_report 和面试评审都标记为致命缺失。
- **修改方案：** 新增以下测试类（每个 1-3 个 @Test）：
  1. **`AuthControllerTest`** — `@WebMvcTest(AuthController.class)`，测试 login 成功/失败、register 成功/重复用户名
  2. **`DatasourceControllerTest`** — `@WebMvcTest(DatasourceController.class)`，测试 CRUD + 跨用户隔离（404）
  3. **`TestdataControllerTest`** — `@WebMvcTest(TestdataController.class)`，测试生成计划请求参数校验
- **预计影响范围：** 新增文件，不修改已有代码
- **修改后需执行的测试：**
  ```bash
  mvn test -pl backend -Dtest="AuthControllerTest,DatasourceControllerTest,TestdataControllerTest"
  # 验证：所有 MockMvc 测试通过
  ```

---

### P1-03 🟠 前端引入 vitest — 零前端测试无法通过面试

- **涉及文件：**
  - [package.json](frontend/package.json)（新增测试依赖和 npm script）
  - 新建 `src/components/__tests__/` 目录
  - 新建 `vitest.config.js`
- **问题描述：** 前端 0 测试。面试评审明确标记为"❌ 零分"。面试官问"你的 Vue 组件怎么测试？"→ 无法回答。
- **修改方案：**
  1. `npm install -D vitest @vue/test-utils jsdom`
  2. 创建 `vitest.config.js`
  3. 在 `package.json` 添加 `"test": "vitest run"`
  4. 至少写 3 个组件测试：
     - `LoginForm.spec.js` — 表单验证 + 登录按钮点击
     - `Dashboard.spec.js` — 统计卡片渲染
     - `AppLayout.spec.js` — sidebar 导航渲染 + 退出登录
- **预计影响范围：** 新增文件，不修改已有代码
- **修改后需执行的测试：**
  ```bash
  cd frontend && npm test
  # 验证：至少 3 个测试通过
  ```

---

### P1-04 🟠 补充 FastAPI TestClient HTTP 测试

- **涉及文件：** 新建 `ai-service/tests/test_api_routes.py`
- **问题描述：** Python 侧有 83 个单元测试，但 0 个 API 路由 HTTP 测试。面试评审标记为"无 FastAPI TestClient 测试"。
- **修改方案：** 新增 `test_api_routes.py`，使用 `TestClient` 测试关键端点：
  1. `POST /api/ai/generate-plan` — 正常请求 + 空 Schema + 无 LLM 降级 Mock
  2. `POST /api/ai/tool-agent` — 正常请求 + 超时降级
  3. `POST /api/ai/detect-sensitive` — 含敏感字段 + 无敏感字段
- **预计影响范围：** 新增文件
- **修改后需执行的测试：**
  ```bash
  cd ai-service && python -m pytest tests/test_api_routes.py -v
  ```

---

### P1-05 🟠 GitHub Actions CI 流水线

- **涉及文件：** 新建 `.github/workflows/test.yml`
- **问题描述：** 项目无 CI/CD。面试评审标记为"❌ 零分"。"你提交了代码怎么保证没问题的？"→ 答不上来。
- **修改方案：** 创建 `.github/workflows/test.yml`：
  ```yaml
  name: Test
  on: [push, pull_request]
  jobs:
    backend:
      runs-on: ubuntu-latest
      services:
        mysql:
          image: mysql:8.0.33
          env:
            MYSQL_ROOT_PASSWORD: root
            MYSQL_DATABASE: smart_testdata
          ports: ['3306:3306']
      steps:
        - uses: actions/checkout@v4
        - uses: actions/setup-java@v4
          with: { java-version: '17', distribution: 'temurin' }
        - run: mvn test -pl backend
    python:
      runs-on: ubuntu-latest
      steps:
        - uses: actions/checkout@v4
        - uses: actions/setup-python@v5
          with: { python-version: '3.12' }
        - run: pip install -r ai-service/requirements.txt
        - run: cd ai-service && python -m pytest tests/ -v
    frontend:
      runs-on: ubuntu-latest
      steps:
        - uses: actions/checkout@v4
        - run: cd frontend && npm ci && npx vitest run
  ```
- **预计影响范围：** 新增文件，不修改代码
- **修改后需执行的测试：** 推送到 GitHub → Actions 自动运行

---

### P1-06 🟠 Java `buildJdbcUrl` 5 个类重复 — 提取统一工具组件

- **涉及文件：**
  - [DatasourceService.java](backend/src/main/java/com/platform/service/DatasourceService.java):~159-167
  - [DatabaseMaskService.java](backend/src/main/java/com/platform/service/DatabaseMaskService.java):~380-388
  - [SchemaCacheService.java](backend/src/main/java/com/platform/schema/SchemaCacheService.java):~378-387
  - [SchemaSampleService.java](backend/src/main/java/com/platform/schema/SchemaSampleService.java):~200-208
  - [MultiTableWriteService.java](backend/src/main/java/com/platform/generator/persistence/MultiTableWriteService.java):~126-135
- **问题描述：** 完全相同的 JDBC URL 构建逻辑复制粘贴到 5 个类中（30+ 行 × 5 = 150+ 行重复）。code_quality_review J1。
- **修改方案：**
  1. 创建 `JdbcUrlBuilder` 工具类（`@Component`），提供 `String build(Datasource ds)` 方法
  2. 5 个类改为注入 `JdbcUrlBuilder`
  3. 使用策略模式支持未来新数据库类型（PostgreSQL 等）
- **预计影响范围：** 5 个消费类改用注入，不改变 URL 格式
- **修改后需执行的测试：**
  ```bash
  mvn test -pl backend
  # 验证：所有 220+ 测试通过
  ```

---

### P1-07 🟠 `DataQualityEvaluator` 675 行 → 策略模式拆分

- **涉及文件：** [DataQualityEvaluator.java](backend/src/main/java/com/platform/service/DataQualityEvaluator.java)
- **问题描述：** 单个类同时负责 5 种评估算法 + Schema 解析 + 报告持久化 + JSON 序列化 + 重复实现敏感字段检测。code_quality_review J5。也复现了 J2（重复敏感字段检测逻辑）。
- **修改方案：**
  1. 定义 `QualityMetric` 接口：`MetricResult evaluate(data, schema)`
  2. 分别实现：`CompletenessMetric`（25%）、`UniquenessMetric`（20%）、`ConsistencyMetric`（25%）、`ValidityMetric`（15%）、`PrivacyMetric`（15%）
  3. `DataQualityEvaluator` 变为编排器：注入 `List<QualityMetric>`，汇总评分
  4. `PrivacyMetric` 注入 `SensitiveFieldDetector` 而非硬编码关键词
  5. 合并 `evaluateValidity` 的两次遍历为一次
- **预计影响范围：** DataQualityEvaluator 从 675 行拆分为 1 个编排器 + 5 个策略类
- **修改后需执行的测试：**
  ```bash
  mvn test -pl backend -Dtest=DataQualityEvaluatorTest
  # 验证：18 个测试全部通过，评分结果一致
  ```

---

### P1-08 🟠 `TestDataTaskExecutor` 160+ 行方法拆分

- **涉及文件：** [TestDataTaskExecutor.java](backend/src/main/java/com/platform/generator/task/TestDataTaskExecutor.java):~70-231
- **问题描述：** `executeTask()` 单方法涵盖：状态更新、Schema 构建、AI 调用、数据生成、DB 写入、结果保存、质量评估、Agent 日志（160+ 行，深层嵌套 try-catch）。code_quality_review J4。
- **修改方案：** 拆分为私有方法，每个 20-30 行：
  1. `loadAndValidateTask(taskId)` — 加载任务 + 校验状态
  2. `buildSchemaMap(datasourceId)` — 构建 Schema JSON
  3. `callAiService(schema, requirement)` — 调用 AI 生成计划
  4. `generateAndWriteData(plan, datasourceId)` — 生成 + 写入
  5. `saveResults(task, generatedData)` — 保存生成结果
  6. `runQualityEvaluation(taskId, datasourceId)` — 质量评估
- **预计影响范围：** 仅 TestDataTaskExecutor 内部重构，外部接口不变
- **修改后需执行的测试：**
  ```bash
  mvn test -pl backend -Dtest=FullWorkflowIntegrationTest
  ```

---

### P1-09 🟠 Python 删除废弃 LLMRouter + 清理死代码目录

- **涉及文件：**
  - [router.py](ai-service/app/models/router.py)（LangChain 版废弃实现，43 行）
  - [llm_service.py](ai-service/app/services/llm_service.py)（冗余单模型封装，89 行）
  - `ai-service/app/agent/`（空目录）
  - `ai-service/app/utils/`（空目录）
- **问题描述：** code_quality_review P1+P18+P25。两个 LLMRouter 实现并存，废弃文件混淆维护。
- **修改方案：**
  1. 删除 `app/models/router.py` 和 `app/services/llm_service.py`
  2. 删除空目录 `app/agent/` 和 `app/utils/`
  3. 从 `requirements.txt` 移除 `langchain`（如不再使用）
  4. 检查所有 import 引用，确认无依赖
- **预计影响范围：** 仅删除，不修改功能代码
- **修改后需执行的测试：**
  ```bash
  cd ai-service && python -m pytest tests/ -v
  # 验证：所有测试通过（确认无隐式依赖）
  ```

---

### P1-10 🟠 Python Agent 间重复映射表/解析逻辑合并

- **涉及文件：**
  - [testdata_agent.py](ai-service/app/agents/testdata_agent.py):28-115（FIELD_GENERATOR_MAP）
  - [schema_agent.py](ai-service/app/agents/schema_agent.py):30-87（SEMANTIC_LABEL_MAP）
  - [strategy_agent.py](ai-service/app/agents/strategy_agent.py):361-375（label_to_gen）
  - `_extract_count` / `_extract_task_name` / `_parse_plan` 在多个 Agent 中重复
- **问题描述：** 三个 Agent 各自维护字段名→生成器/语义标签映射表，覆盖几乎相同的字段名集合。code_quality_review P7-P10。
- **修改方案：**
  1. 创建 `app/shared/field_mappings.py` — 集中定义所有映射
  2. 创建 `app/shared/text_utils.py` — 提取 `_extract_count`, `_extract_task_name`
  3. `_parse_plan` 移到 `GenerationPlan.from_dict()` classmethod
  4. `_is_retriable` 提升到 `LLMProvider` 基类
  5. 所有 Agent 改为从共享模块导入
- **预计影响范围：** 3 个 Agent 文件，行为不变
- **修改后需执行的测试：**
  ```bash
  cd ai-service && python -m pytest tests/ -v
  # 验证：全部 83 测试通过
  ```

---

### P1-11 🟠 Python pytest 配置补充 + 依赖声明

- **涉及文件：**
  - 新建 [pytest.ini](ai-service/pytest.ini)
  - 新建 [conftest.py](ai-service/tests/conftest.py)
  - [requirements.txt](ai-service/requirements.txt)（补充测试依赖）
- **问题描述：** 测试依赖（pytest/pytest-asyncio）未在 requirements.txt 中声明，无 pytest 配置文件。面试评审标记。
- **修改方案：**
  1. `requirements.txt` 补充：`pytest>=8.0`, `pytest-asyncio>=0.23`, `pytest-cov>=4.0`
  2. 创建 `pytest.ini`：
     ```ini
     [pytest]
     asyncio_mode = auto
     testpaths = tests
     ```
  3. conftest.py 提取公共 fixture（MockLLMProvider, Schema builders 等）
- **预计影响范围：** 新增文件
- **修改后需执行的测试：**
  ```bash
  cd ai-service && pip install pytest pytest-asyncio && python -m pytest tests/ -v
  ```

---

### P1-12 🟠 前端空 catch 块补充用户反馈

- **涉及文件：** 12 个 Vue 文件（详见 code_quality_review F12）
- **问题描述：** 大量 catch 块注释为 `/* handled by interceptor */` 但实际为空。网络超时、500 错误被静默吞掉，用户无任何错误感知。
- **修改方案：** 每个空 catch 块至少添加 `ElMessage.error('操作失败，请稍后重试')` 作为兜底。关键操作（删除数据源、删除项目、创建任务）必须确保错误被用户感知。
- **预计影响范围：** ~15 处修改，功能行为不变
- **修改后需执行的测试：** 手动：断开网络 → 触发各操作 → 确认有红色错误提示

---

### P1-13 🟠 前端 `activeMenu` 硬编码 bug 修复

- **涉及文件：**
  - [SchemaView.vue](frontend/src/views/SchemaView.vue)（硬编码 `/testdata/task`）
  - [RelationGraph.vue](frontend/src/views/RelationGraph.vue)（同上）
  - [GenerationPlan.vue](frontend/src/views/GenerationPlan.vue)（同上）
  - [TestDataResult.vue](frontend/src/views/TestDataResult.vue)（同上）
  - [TaskMonitor.vue](frontend/src/views/TaskMonitor.vue)（同上）
- **问题描述：** 多个视图的 `activeMenu` 硬编码为 `/testdata/task`，实际路由不同。侧边栏高亮始终停在错误菜单项。code_quality_review F7。
- **修改方案：** 使用 `const activeMenu = computed(() => route.path)` 动态计算，或通过 `route.meta.activeMenu` 配置。与 P1-01（AppLayout 提取）一起完成。
- **预计影响范围：** 与 P1-01 合并，在 AppLayout 中一次性修复
- **修改后需执行的测试：** 手动：逐个页面验证侧边栏高亮位置正确

---

### P1-14 🟠 JaCoCo 测试覆盖率报告

- **涉及文件：** [pom.xml](backend/pom.xml)（新增 jacoco-maven-plugin）
- **问题描述：** 无覆盖率报告。面试时"你的测试覆盖率是多少？"→ 答不上来。
- **修改方案：** `pom.xml` 添加 `jacoco-maven-plugin`：
  ```xml
  <plugin>
      <groupId>org.jacoco</groupId>
      <artifactId>jacoco-maven-plugin</artifactId>
      <version>0.8.12</version>
      <executions>
          <execution>
              <goals><goal>prepare-agent</goal></goals>
          </execution>
          <execution>
              <id>report</id>
              <phase>test</phase>
              <goals><goal>report</goal></goals>
          </execution>
      </executions>
  </plugin>
  ```
- **预计影响范围：** 仅 pom.xml，不影响已有测试
- **修改后需执行的测试：** `mvn test -pl backend` → 查看 `target/site/jacoco/index.html`

---

### P1-15 🟠 动态数据源增加连接池

- **涉及文件：**
  - [MultiTableWriteService.java](backend/src/main/java/com/platform/generator/persistence/MultiTableWriteService.java)
  - [DatabaseMaskService.java](backend/src/main/java/com/platform/service/DatabaseMaskService.java)
- **问题描述：** 两个类使用 `DriverManager.getConnection()` 每次新建物理连接。多表操作时大量连接创建/销毁，连接泄漏风险。code_review_report D5。
- **修改方案：**
  1. 引入 HikariCP 动态数据源注册（Spring `AbstractRoutingDataSource` 或手动 HikariDataSource 缓存）
  2. 创建 `DatasourceConnectionPool`，缓存 `datasourceId → HikariDataSource` 映射
  3. 添加 `@PreDestroy` 关闭所有连接池
- **预计影响范围：** MultiTableWriteService + DatabaseMaskService 注入连接池管理器
- **修改后需执行的测试：**
  ```bash
  mvn test -pl backend -Dtest="DatabaseWriterTest,DatabaseMaskServiceTest"
  ```

---

# C. P2 可选优化（12 项）

> **判定标准：** 代码洁净度、重构、面试锦上添花

---

### P2-01 🟢 删除 `AppTest.java` Maven 空壳

- **涉及文件：** [AppTest.java](backend/src/test/java/com/platform/AppTest.java)
- **问题描述：** `assertTrue(true)` — Maven archetype 空壳测试
- **修改方案：** 删除文件
- **修改后需执行的测试：** `mvn test -pl backend`

---

### P2-02 🟢 `Testdata` → `TestData` 命名统一

- **涉及文件：** [TestdataController.java](backend/src/main/java/com/platform/controller/TestdataController.java), [TestdataService.java](backend/src/main/java/com/platform/service/TestdataService.java) 及相关文件
- **问题描述：** `TestdataController`/`TestdataService` 使用小写 'd'，而 `TestDataTask`/`TestDataResult` 使用大写 'D'。code_quality_review J21。
- **修改方案：** 全部重命名为 `TestData*`。使用 IDE Refactor → Rename，同步更新所有引用（包括测试类、配置类）
- **预计影响范围：** 类名、import 语句、Spring Bean 名称
- **修改后需执行的测试：** `mvn test -pl backend`

---

### P2-03 🟢 Python FastAPI 全局异常处理中间件

- **涉及文件：** [main.py](ai-service/app/main.py)
- **问题描述：** 各路由自行 try/except，无全局 `exception_handler`。新路由忘记处理异常会暴露内部 traceback 给客户端。code_quality_review P14。
- **修改方案：** `main.py` 中添加：
  ```python
  @app.exception_handler(Exception)
  async def global_exception_handler(request, exc):
      logger.exception(f"Unhandled error: {exc}")
      return JSONResponse(
          status_code=500,
          content={"success": False, "error": "Internal server error"}
      )
  ```
- **预计影响范围：** 全局兜底，不影响已有异常处理
- **修改后需执行的测试：** 手动：触发 500 → 确认返回 JSON 而非 HTML traceback

---

### P2-04 🟢 前端死代码清理

- **涉及文件：**
  - [HelloWorld.vue](frontend/src/components/HelloWorld.vue)
  - `frontend/src/assets/vue.svg`
  - `frontend/public/vite.svg`
- **问题描述：** Vite 脚手架遗留文件。code_quality_review F18。
- **修改方案：** 删除上述文件
- **修改后需执行的测试：** `npm run build` → BUILD SUCCESS

---

### P2-05 🟢 前端 `utils/` 工具函数目录创建

- **涉及文件：** 新建 `frontend/src/utils/`
- **问题描述：** 工具函数散落各组件中并重复。`getColumns` 在 2 个文件中重复，`formatTime` 和 `timestamp` 功能相似但实现不同。code_quality_review F14+F15+F16。
- **修改方案：**
  1. 创建 `src/utils/table.js` — 提取 `extractColumnKeys(rows)`
  2. 创建 `src/utils/date.js` — 提取 `formatDateTime(dateStr)`
  3. 创建 `src/utils/status.js` — 提取 `createStatusMapper(map)`
  4. 创建 `src/constants/index.js` — 集中管理业务常量（快捷需求模板、颜色映射、等级映射）
  5. 各组件改为导入工具函数
- **预计影响范围：** 多组件 import 指向统一工具方法
- **修改后需执行的测试：** `npm run build` → BUILD SUCCESS

---

### P2-06 🟢 Python LLM 调用增加超时 + Token 消耗日志

- **涉及文件：**
  - [deepseek_provider.py](ai-service/app/llm/deepseek_provider.py):~68-75
  - [qwen_provider.py](ai-service/app/llm/qwen_provider.py):~72-80
- **问题描述：** LLM 调用无显式 timeout（默认 600 秒），不记录 token 消耗。code_review_report D6 + code_quality_review P21。
- **修改方案：**
  1. `chat.completions.create()` 添加 `timeout=60`
  2. 日志记录 `usage.prompt_tokens`/`completion_tokens`/`model`/延迟
- **预计影响范围：** 2 个 Provider 文件
- **修改后需执行的测试：** `cd ai-service && python -m pytest tests/test_llm_router.py -v`

---

### P2-07 🟢 Python `response_model` 统一添加到所有路由

- **涉及文件：** [routes.py](ai-service/app/api/routes.py)
- **问题描述：** 部分路由有 `response_model`（如 `/analyze-schema`），部分没有（如 `/tool-agent`）。code_quality_review P22。
- **修改方案：** 为 `/tool-agent` 和 `/generate-strategy` 添加 Pydantic 响应模型，享受自动校验和 OpenAPI schema 生成
- **预计影响范围：** 路由定义，行为不变
- **修改后需执行的测试：** 打开 `http://localhost:8000/docs` → 检查 Swagger 响应结构完整

---

### P2-08 🟢 后端 Controller 补充关键操作日志

- **涉及文件：** AuthController、DatasourceController、ProjectController、SchemaController、ExportController
- **问题描述：** 多数 Controller 完全没有 `log.info()` 调用，无法追溯用户操作。code_quality_review J17。
- **修改方案：** 在关键操作添加日志：
  - `log.info("Datasource created: userId={}, dsId={}", userId, dsId)`
  - `log.info("Project deleted: userId={}, projectId={}", userId, projectId)`
  - `log.info("Task created: userId={}, taskId={}, datasourceId={}", ...)`
- **预计影响范围：** 5 个 Controller 文件
- **修改后需执行的测试：** `mvn test -pl backend`（日志不影响测试）

---

### P2-09 🟢 Python 添加请求级 Request ID

- **涉及文件：** [main.py](ai-service/app/main.py)
- **问题描述：** 无请求标识符，并发请求下无法关联日志。code_quality_review P20。
- **修改方案：** FastAPI middleware 生成 `X-Request-ID`，通过 `contextvars` 传递，loguru 使用 `logger.bind(request_id=...)`
- **预计影响范围：** 日志输出更可追踪，不影响功能
- **修改后需执行的测试：** 手动：发起请求 → 检查响应头含 `X-Request-ID` + 日志中有关联 ID

---

### P2-10 🟢 删除 Python 调试代码 + 清理注释

- **涉及文件：** 多个 Agent 文件
- **问题描述：** 部分 Agent 中含 debug 级别的 print/logging 语句残留
- **修改方案：** 全局搜索 `logger.debug` 和散落的注释代码，统一清理
- **修改后需执行的测试：** `cd ai-service && python -m pytest tests/ -v`

---

### P2-11 🟢 GeneratorTest for 循环 → `@ParameterizedTest`

- **涉及文件：** [GeneratorTest.java](backend/src/test/java/com/platform/generator/GeneratorTest.java)
- **问题描述：** 使用 for 循环验证 50 次随机数据而非参数化测试。面试评审标记为轻微问题。
- **修改方案：** 关键边界值测试改为 `@ParameterizedTest` + `@ValueSource` / `@CsvSource`
- **预计影响范围：** 测试重构，断言不变
- **修改后需执行的测试：** `mvn test -pl backend -Dtest=GeneratorTest`

---

### P2-12 🟢 Vue `v-for` key 修复 + 防抖定时器清理 + composable 提取

- **涉及文件：**
  - TestDataResult.vue / GenerationPlan.vue / DataExport.vue（`:key="index"` 修复）
  - MaskConfig.vue（`onUnmounted` 清理 `testTimer`）
  - DataQuality.vue（ECharts 雷达图 → `useRadarChart` composable）
  - RelationGraph.vue（力导向图配置 → `useRelationGraph` composable）
- **修改方案：** 按 code_quality_review F9/F10/F13/F8 逐项修复
- **预计影响范围：** 6 个 Vue 文件，功能行为不变
- **修改后需执行的测试：** `npm run build` → BUILD SUCCESS + 手动验证

---

# D. 执行建议

## 推荐执行顺序

```
Day 1 (上午) — P0 安全修复冲刺
├── P0-01 SecurityConfig API 鉴权 (10 min)
├── P0-02 AES ECB → GCM (30 min)
├── P0-03 JWT Secret 移除 fallback (5 min)
├── P0-04 quality.js 路径修复 (1 min)
├── P0-05 SQL 注入修复 (15 min)
└── P0-10 CORS + 日志安全 (10 min)

Day 1 (下午) — P0 功能修复 + P1 测试基础设施
├── P0-06 异步脱敏恢复 (1 hr)
├── P0-07 Python except Exception 修复 (30 min)
├── P0-08 ReAct 超时+重复检测 (1 hr)
├── P0-09 Pydantic 命名空间修复 (10 min)
├── P1-02 Controller MockMvc 测试 (2 hr)
└── P1-03 Frontend vitest 引入 (1.5 hr)

Day 2 — P1 代码质量核心
├── P1-01 Frontend AppLayout 提取 (1.5 hr)
├── P1-04 FastAPI TestClient 测试 (1 hr)
├── P1-05 GitHub Actions CI (1 hr)
├── P1-06 buildJdbcUrl 提取 (30 min)
├── P1-07 DataQualityEvaluator 策略模式 (2 hr)
├── P1-08 TestDataTaskExecutor 拆分 (1 hr)
├── P1-13 activeMenu bug 修复 (合入 P1-01)
└── P1-12 前端空 catch 修复 (30 min)

Day 3 (上午) — P1 收尾
├── P1-09 Python 死代码清理 (30 min)
├── P1-10 Python Agent 重复合并 (1 hr)
├── P1-11 Python pytest 配置 (15 min)
├── P1-14 JaCoCo 覆盖率 (10 min)
└── P1-15 动态数据源连接池 (1.5 hr)

Day 3 (下午) — P2 可选 + 全流程验证
├── P2-01 ~ P2-12 任选低风险项 (~4 hr)
└── 全流程演示测试 (1 hr)
```

## 每阶段验证清单

| 阶段 | 验证方式 |
|------|----------|
| 每个 P0 完成 | `mvn test -pl backend` + `pytest` + 前端手动冒烟 |
| P1 完成 | `mvn test -pl backend` → 220+ 测试全通过 + `pytest` 83+ 全通过 |
| 全部完成 | FullWorkflowIntegrationTest → 12 步全流程通过 |

## 不建议做的

| 事项 | 理由 |
|------|------|
| 自定义脱敏规则持久化 | 当前 Mock 规则引擎已满足演示需求 |
| WebSocket 实时推送 | 依赖已引入但实现复杂，轮询即可 |
| RestTemplate → RestClient 全量迁移 | 无功能增益，仅风格变化 |
| 多租户/SaaS 化 | 超出毕业设计范围 |
| Quartz 定时调度 | 偏离核心创新点 |
| LangChain 深度集成 | LLMRouter 自研方案已足够展示能力 |

---

> **规划依据：**
> - [code_review_report.md](code_review_report.md) — 架构 + 安全审查（70/100 评分，10 大风险）
> - [code_quality_review.md](code_quality_review.md) — 企业级代码质量审查（77 问题，Top 15 优先级）
> - 测试开发面试评审报告 — 面试高频追问 Top 10
>
> **完成后预计项目评分：85+/100（测试开发展示级）**
