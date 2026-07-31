# 智能测试数据生成与隐私脱敏平台 — 全面代码审查报告

> **审查人：** 高级Java测试开发工程师 / 系统架构师视角  
> **审查日期：** 2026-08-01  
> **审查范围：** 全栈项目（148 Java文件 + 31 Python文件 + 36 Vue/JS文件）  
> **审查原则：** 仅分析，不修改代码

---

## A. 项目整体评分

| 维度 | 得分 | 满分 | 评价 |
|------|------|------|------|
| 整体架构设计 | 16 | 20 | 三层分离清晰，RestTemplate 待迁移 RestClient |
| 后端代码质量 | 14 | 20 | 业务逻辑扎实，存在硬编码和逻辑重复 |
| AI Agent 模块 | 15 | 20 | LLM+Mock 双模式设计好，缺超时/死循环防护 |
| 测试体系 | 13 | 15 | 后端220+Python106用例覆盖好，前端0测试 |
| 安全性 | **5** | **15** | 🔴 最差维度 — API全开、密钥硬编码、AES ECB |
| 性能设计 | 7 | 10 | 批量写入优化好，缺连接池/内存溢出风险 |
| **总分** | **70** | **100** | **良好（毕业设计标准 ≥70）** |

---

## B. 当前最大风险 TOP 10

| 排名 | 风险 | 严重度 | 影响范围 |
|------|------|--------|----------|
| 1 | **所有业务 API `permitAll()` — JWT 认证形同虚设** | 🔴 严重 | 全站 API 可被未认证调用 |
| 2 | **AES 密钥 + JWT Secret 硬编码在 application.yml** | 🔴 严重 | 密钥泄露 = 所有数据源密码可解密 |
| 3 | **AES 使用 ECB 模式** (`Cipher.getInstance("AES")`) | 🔴 严重 | 相同明文产生相同密文，可被分析 |
| 4 | **异步流程跳过隐私脱敏**（第5步被注释跳过） | 🔴 严重 | 敏感数据明文写入数据库 |
| 5 | **DatabaseMaskService SQL 注入风险**（表名拼接） | 🟠 高危 | 用户输入 `tableName` 可绕过反引号转义 |
| 6 | **前端 `quality.js` 导入路径错误** → 运行时崩溃 | 🟠 高危 | 质量评估功能完全不可用 |
| 7 | **动态数据源无连接池** — 每次新建物理连接 | 🟠 高危 | 多表操作时性能极差，连接泄漏风险 |
| 8 | **LLM 调用无超时配置** — 默认600秒 | 🟡 中危 | DeepSeek 超时时用户体验极差 |
| 9 | **全量数据加载到内存** — 无分页/流式处理 | 🟡 中危 | 大数据集 OOM 风险 |
| 10 | **CORS 全开** (`allow_origins=["*"]`) | 🟡 中危 | CSRF 攻击面扩大 |

---

## C. 必须修改的问题（答辩前）

### C1. 🔴 锁定 API 鉴权（预计10分钟）

**文件：** `backend/src/main/java/com/platform/config/SecurityConfig.java`

**问题：** 几乎所有业务 API 都配置了 `.permitAll()`，JWT 过滤器虽然存在但完全不生效。

```java
// 当前状态 — 全部放行
.requestMatchers("/api/testdata/generator/**").permitAll()
.requestMatchers("/api/testdata/sql/**").permitAll()
.requestMatchers("/api/testdata/write/**").permitAll()
.requestMatchers("/api/testdata/task/**").permitAll()
.requestMatchers("/api/privacy/**").permitAll()
.requestMatchers("/api/quality/**").permitAll()
.requestMatchers("/api/export/**").permitAll()
```

**修复方向：** 仅保留 `/api/auth/**` 和 `/api/ai/health` 为 `permitAll()`，其余全部要求 `authenticated()`。

---

### C2. 🔴 移除硬编码密钥（预计10分钟）

**文件：** `backend/src/main/resources/application.yml`

**问题：**
```yaml
# 硬编码 AES 密钥
aes-key: SmartPlatformAESK

# JWT Secret 有硬编码 fallback
jwt:
  secret: ${JWT_SECRET:smart-testdata-platform-secret-key-2026}
```

**修复方向：** 全部改为仅从环境变量读取，移除 fallback 默认值。答辩时通过 IDE 环境变量注入。

---

### C3. 🔴 AES 改为 GCM 模式（预计30分钟）

**文件：** `backend/src/main/java/com/platform/util/AesUtil.java`

**问题：**
```java
// ECB 模式 — 不安全
Cipher cipher = Cipher.getInstance("AES");
```

**修复方向：**
```java
// GCM 模式 — 带认证加密
Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
// 需要随机生成 12 字节 IV，与密文一起存储
```

---

### C4. 🟠 修复 quality.js 导入路径（预计1分钟）

**文件：** `frontend/src/api/quality.js`

**问题：**
```javascript
import request from '@/utils/request'  // 路径不存在！
```

**修复方向：**
```javascript
import request from './request'  // 或 '../utils/request'
```

---

### C5. 🟠 修复 SQL 注入风险（预计15分钟）

**文件：** `backend/src/main/java/com/platform/service/DatabaseMaskService.java`

**问题（约第424行）：**
```java
String sql = String.format("SELECT * FROM `%s` LIMIT %d", tableName, limit);
```

**修复方向：** 使用 `DatabaseMetaData` 或白名单校验 `tableName` 是否存在于 `information_schema` 中。

---

### C6. 🔴 异步流程自动触发脱敏（预计1小时）

**文件：** `backend/src/main/java/com/platform/generator/task/TestDataTaskExecutor.java`

**问题（约第168行）：** 第5步隐私处理被跳过，注释写"需通过 POST /api/privacy/process-auto 独立调用"。

**修复方向：** 在数据写入成功后自动调用 `PrivacyService.processAuto()` 完成脱敏。

---

## D. 可以提升竞争力的问题

### D1. 提取共享 Layout 组件（消除14处重复）
14个 Vue 页面各自复制了 ~60行的 sidebar 布局代码。提取为 `<AppLayout>` 组件，使用 `<slot>` 嵌入内容区。

### D2. 复用敏感字段检测器（消除逻辑重复）
`DataQualityEvaluator.evaluatePrivacy()` 重新实现了敏感字段关键词检测，应直接复用 `CompositeSensitiveDetector`。

### D3. 清理 Python 死代码
- `app/services/llm_service.py` — 废弃的单模型封装
- `app/models/router.py` — 废弃的 LangChain 路由
- `app/agent/` 目录 — 空目录
- `app/utils/` 目录 — 空目录

### D4. RestTemplate → RestClient 迁移
Spring Boot 3.3 推荐使用 `RestClient`（支持流式 + 虚拟线程），当前 `RestTemplate` 已进入维护模式。

### D5. 动态数据源增加连接池
`DatabaseMaskService` 和 `MultiTableWriteService` 使用 `DriverManager.getConnection()` 每次创建新连接。应引入 HikariCP 动态注册。

### D6. LLM 调用增加超时配置
`deepseek_provider.py` 中 `chat.completions.create()` 无 `timeout` 参数，建议设为 60 秒。

### D7. 大表采样增加流式分页
当前 `readSampleRows` 一次性加载全表数据到内存，对大表应使用 `LIMIT + OFFSET` 分页采样。

---

## E. 不建议继续开发的功能

| 功能 | 理由 |
|------|------|
| **自定义脱敏规则持久化（Phase 8）** | 当前 Mock 规则引擎已能满足演示需求，自定义规则是锦上添花，投入产出比低 |
| **WebSocket 实时进度推送** | 依赖已引入但未使用，实现复杂且对答辩加分有限，用轮询即可 |
| **多租户/SaaS 化** | 超出毕业设计范围，面试时作为"未来规划"提及即可 |
| **数据生成调度/Cron 定时任务** | 偏离核心创新点（LLM Agent），且引入 Quartz 会增加答辩复杂度 |

---

## F. 各维度详细分析

### F1. 项目整体架构

**优点：**
- Spring Boot（业务主体） + FastAPI（AI 推理） + Vue 3（前端展示）三层分离清晰
- 通过 REST API 实现 Java↔Python 解耦，职责明确
- MyBatis-Plus + Flyway 数据访问层规范
- Docker Compose 一键部署

**待改进：**
- RestTemplate 已标记为维护模式，Spring Boot 3.3 推荐 RestClient
- Python 侧存在死代码目录和废弃文件
- 前端 14 个页面大量重复布局代码

---

### F2. 后端代码质量

**优点：**
- Controller-Service-Mapper 分层规范
- 使用 TransactionTemplate 编程式事务控制
- JdbcTemplate 参数化批量插入防 SQL 注入
- GlobalExceptionHandler 统一异常处理
- @Async 异步任务执行

**待改进：**
- `DataQualityEvaluator` 中存在逻辑重复（重新实现敏感字段检测）
- 部分 Service 方法过长（DatabaseMaskService 单方法超 200 行）
- 动态数据源获取使用 DriverManager 无连接池
- MyBatis SQL 日志开启在生产环境 `application.yml` 中

---

### F3. AI Agent 模块

**优点：**
- ReAct 模式 Agent（Thought → Action → Observation）设计合理
- 三层故障降级：LLM → RouterExhaustedError → Mock 规则引擎
- 双模型路由（DeepSeek 主 / Qwen 备用）
- 10 种数据生成器覆盖常见类型
- 三层敏感字段检测融合（Regex > Keyword > LLM 优先级）

**待改进：**
- `deepseek_provider.py` 无显式超时（默认600秒过长）
- ReAct 循环仅靠 max_rounds=5 防止死循环，缺少语义重复检测
- 废弃文件未清理
- Prompt 模板缺少版本管理和 A/B 测试能力

---

### F4. 测试体系

**优点：**
- 后端 220+ 测试用例覆盖 Controller/Service/Generator
- Python 106 测试用例覆盖 Agent/Tool/Chain
- 使用 TestContainers 进行真实 MySQL 集成测试
- 包含完整工作流 E2E 测试（FullWorkflowIntegrationTest）

**待改进：**
- **前端 0 测试** — Vitest + Vue Test Utils 完全未引入
- 缺少 API 契约测试（Pact/Spring Cloud Contract）
- 部分单测依赖 H2 内存库而非 TestContainers（行为差异风险）

---

### F5. 安全性

**优点：**
- 数据源密码 AES 加密存储
- JdbcTemplate 参数化查询防 SQL 注入
- 有 JWT 认证机制设计

**问题（最多）：**
1. 🔴 所有 API `permitAll()` — JWT 完全被绕过
2. 🔴 AES 密钥硬编码 + ECB 模式
3. 🔴 JWT Secret 有硬编码 fallback
4. 🟠 DatabaseMaskService 表名拼接可被 SQL 注入
5. 🟡 CORS 全开 `allow_origins=["*"]`
6. 🟡 MyBatis SQL 日志泄漏数据到标准输出
7. 🟡 DEBUG 日志级别泄漏敏感信息

---

### F6. 性能设计

**优点：**
- 批量 INSERT 优化（JdbcTemplate batchUpdate）
- 异步任务执行（@Async + ThreadPoolTaskExecutor）
- Kahn 拓扑排序优化外键依赖写入顺序

**待改进：**
- 动态数据源无连接池（`DriverManager.getConnection()` 每次新建）
- 全量数据加载到内存，无流式处理
- CallerRunsPolicy 在队列满时会让调用线程执行，可能阻塞 Tomcat
- 线程池配置偏保守（core=2, max=4）

---

## G. 答辩前 3 天行动清单

| 优先级 | 任务 | 预计时间 |
|--------|------|----------|
| 🔴 Day 1 | C1 锁定 API 鉴权 + C2 移除硬编码密钥 | 20 分钟 |
| 🔴 Day 1 | C3 AES 改 GCM + C5 修复 SQL 注入 | 45 分钟 |
| 🔴 Day 1 | C4 修复 quality.js 导入路径 | 1 分钟 |
| 🟠 Day 2 | C6 异步流程自动触发脱敏 | 1 小时 |
| 🟠 Day 2 | D1 提取共享 Layout 组件 | 1.5 小时 |
| 🟡 Day 3 | D3 清理 Python 死代码 + D6 LLM 超时 | 30 分钟 |
| 🟡 Day 3 | 全流程演示测试 + PPT 准备 | 2 小时 |

---

> **结论：** 项目整体达到毕业设计良好水平（70/100），架构设计合理，核心功能完整。安全配置是最大短板，修复后可达 78-80 分。建议优先完成 C1-C6 必须修改项，确保答辩演示顺利。
