# 基于大模型 Agent 的智能测试数据生成与隐私脱敏平台 — 毕业设计方案

## Context

毕业设计项目，目标三合一：**毕业答辩通过 + 简历有含金量 + 对就业有帮助**。

选题结合 LLM Agent 和数据安全两个热门方向。核心场景：开发人员连接数据库 → Java 自动分析表结构并采样数据 → Python Agent（LLM）理解字段语义 → 返回 generation_plan.json → Java 端 Faker/JDBC 批量生成测试数据并写入目标库，同时自动识别敏感字段并完成脱敏。

---

## 一、技术栈

| 层级 | 技术 | 说明 |
|------|------|------|
| 前端 | Vue 3 + Element Plus | 管理控制台 |
| 后端 | Spring Boot + MyBatis-Plus | Java 后端服务，提供 REST API 和业务逻辑处理 |
| AI 服务 | Python FastAPI + LangChain + Prompt 模板 + 工具调用 | 独立部署，LangChain 仅作为 LLM 调用框架 |
| 系统数据库（MySQL） | platform_db | 存储用户、项目、任务、规则等平台数据 |
| 目标数据库（MySQL） | 用户连接的外部库 | 读取表结构/字段/外键，写入生成的测试数据 |
| LLM | 主模型 DeepSeek + 备用模型 Qwen（OpenAI 兼容协议） | 模型适配层，支持主备切换 |
| 通信 | REST + WebSocket | 常规请求 + 实时进度推送 |

---

## 二、系统架构

```
                 ┌───────────────────────────────┐
                 │   Vue 3 前端 (Element Plus)     │
                 │  项目管理 │ 数据源 │ Schema     │
                 │  生成策略 │ 脱敏规则 │ 预览导出  │
                 └──────────────┬────────────────┘
                                │ REST / WebSocket
                 ┌──────────────▼────────────────┐
                 │      Java 后端 (Spring Boot)    │
                 │  ┌────────┐ ┌──────────────┐  │
                 │  │ 用户管理 │ │   任务管理    │  │
                 │  │ JWT登录 │ │ 异步调度+轮询 │  │
                 │  └────────┘ └──────────────┘  │
                 │  ┌────────┐ ┌──────────────┐  │
                 │  │ Schema  │ │ 数据生成执行  │  │
                 │  │ 元数据  │ │ Faker/JDBC   │  │
                 │  │ 关系分析│ │ 批量写入MySQL│  │
                 │  └────────┘ └──────────────┘  │
                 │  ┌────────┐ ┌──────────────┐  │
                 │  │ 规则引擎│ │  脱敏执行器   │  │
                 │  │ 三层规则│ │ SQL生成+执行 │  │
                 │  └────────┘ └──────────────┘  │
                 └──┬────────────┬───────────────┘
                    │            │
        ┌───────────▼──┐  ┌─────▼──────────┐
        │ 系统数据库     │  │  目标数据库     │
        │ platform_db   │  │  (用户MySQL)    │
        │ sys_user      │  │  读取结构/字段  │
        │ project       │  │  写入测试数据   │
        │ datasource    │  │  执行脱敏SQL   │
        │ schema_table  │  └────────────────┘
        │ schema_column │
        │ generate_task │
        │ mask_rule     │
        └──────┬────────┘
               │ REST API
   ┌───────────▼────────────────────────────┐
   │  Python AI 服务 (FastAPI)               │
   │  LangChain 仅作 LLM 调用框架            │
   │  ┌──────────┐ ┌──────────────────────┐ │
   │  │ 模型适配层│ │     Agent (仅2个)     │ │
   │  │ LLMProvider│ │  Schema Agent       │ │
   │  │ 主:DeepSeek│ │  Strategy Agent     │ │
   │  │ 备: Qwen  │ │  返回generation_plan │ │
   │  └──────────┘ └──────────────────────┘ │
   │  ┌──────────┐ ┌──────────────────────┐ │
   │  │ Chains   │ │ Tools / Schemas      │ │
   │  └──────────┘ └──────────────────────┘ │
   └───────────┬────────────────────────────┘
               │
       ┌───────▼────────┐
       │  DeepSeek / Qwen│
       │  (OpenAI 兼容)  │
       └────────────────┘
```

---

## 三、核心模块详细设计

### 3.1 认证鉴权（简化）

- JWT 登录，用户表：`id, username, password(加密), nickname, created_at`
- 一个登录接口，一个注册接口
- 不做：RBAC、角色、权限、多租户
- Spring Security 只做请求拦截 + JWT 校验

### 3.2 Schema 元数据分析（Java）

连接目标 MySQL → 查询 `information_schema`：

```
TABLES → 表名、注释
COLUMNS → 字段名、类型、长度、是否可空、默认值、注释
KEY_COLUMN_USAGE → 主键、外键关系
STATISTICS → 索引信息
```

**重点1：关系分析**

不只是列出字段，还要分析：
- 外键依赖关系图（表 A 依赖表 B）
- 引用完整性约束（生成数据时的顺序）
- 字段间的语义关联（`user_id` 对应 `users.id`）

**重点2：数据采样（提升 LLM 理解准确度）**

仅靠字段名和注释，LLM 有时无法准确判断字段语义。例如 `remark text` 到底是什么？需要采样实际数据辅助判断。

采样策略：对每个字段取前 5 条实际数据，附在 Schema JSON 中一起送给 LLM。

示例输出：
```json
{
  "table": "users",
  "columns": [
    { "name": "remark", "type": "text", "comment": "备注",
      "samples": ["客户有过敏史，需注意", "喜欢购买电子产品，高消费用户", "VIP客户"] }
  ]
}
```

→ LLM 根据采样数据推断：remark 可能包含"个人健康信息 + 消费偏好"，敏感等级 medium

**输出**：结构化 Schema JSON，包含字段列表 + 关系列表 + 数据采样

### 3.3 规则引擎（Java，三层，无 Drools）

自己实现 `SensitiveRuleEngine`，不引入 Drools、Easy Rules 等复杂框架。

```
RuleEngine
  ├── FieldNameRule      # 第1层：字段名关键词匹配
  ├── RegexRule          # 第2层：正则表达式检测
  └── LLMRule            # 第3层：调用 Python AI 服务进行语义判断
```

**第 1 层 — 字段名规则**（纯字符串匹配，毫秒级）：
```
字段名含 phone/mobile → 标记为"手机号格式"
字段名含 email/mail → 标记为"邮箱格式"
字段名含 id_card/idcard → 标记为"身份证格式"
字段名含 password/pwd → 标记为"密码类敏感"
字段名含 name/姓名 → 标记为"姓名格式"
字段名含 address/addr/地址 → 标记为"地址"
字段名含 bank/银行卡 → 标记为"银行卡号"
```

**第 2 层 — 正则检测**（对字段实际数据进行采样扫描）：
```java
Patterns:
  手机号: ^1[3-9]\d{9}$
  身份证: ^\d{17}[\dXx]$
  邮箱: ^[\w.-]+@[\w.-]+\.\w+$
  银行卡: ^\d{16,19}$
  IP地址: ^\d{1,3}(\.\d{1,3}){3}$
```

**第 3 层 — LLM 辅助判断**（仅对前两层未命中的字段）：
将字段名 + 注释 + 数据样本送给 LLM → "这个字段是否含敏感信息？"

### 3.4 LLM Agent（Python，核心）

**Agent 只负责两个任务，不直接生成数据。**

核心思路：用 Prompt 模板 + 工具调用 + LangChain 框架实现 Agent，LangChain 仅作为 LLM 调用和工具编排的辅助框架。

Agent 本质：Spring Boot 提交 Schema JSON → Python Agent 分析 → 返回 `generation_plan.json` → **Java 端执行计划**（Faker/JDBC 批量生成 + 写入数据库）。

#### Agent 1：Schema 理解 Agent

```
输入:
  表: users
  字段: id(int), name(varchar), phone(varchar), email(varchar), 
        dept_id(int, FK→departments.id), remark(text)
  采样数据: remark → ["客户有过敏史", "喜欢购买电子产品", "VIP客户"]
  
输出:
  {
    "fields": [
      { "field": "name",    "label": "姓名",   "sensitive": false },
      { "field": "phone",   "label": "手机号",  "sensitive": true, 
        "mask_type": "phone" },
      { "field": "email",   "label": "邮箱",    "sensitive": true, 
        "mask_type": "email" },
      { "field": "dept_id", "label": "部门ID",  "sensitive": false,
        "fk_ref": "departments.id" },
      { "field": "remark",  "label": "用户备注", "sensitive": true,
        "sensitivity_reason": "可能包含用户健康/偏好等个人信息" }
    ]
  }
```

#### Agent 2：数据生成策略 Agent

```
输入: Schema 理解结果 + 用户指定的生成行数
输出: generation_plan.json（不是数据本身！）
  {
    "plan": [
      { "field": "name",    "strategy": "faker", 
        "generator": "name_cn", "params": {} },
      { "field": "phone",   "strategy": "faker", 
        "generator": "phone_number", "params": {} },
      { "field": "email",   "strategy": "faker", 
        "generator": "email", "params": {"domain": "test.com"} },
      { "field": "dept_id", "strategy": "fk_query", 
        "ref_table": "departments", "ref_column": "id" },
      { "field": "remark",  "strategy": "faker", 
        "generator": "sentence", "params": {"nb_words": 10} }
    ],
    "table_order": ["departments", "users"]  // 按外键依赖排序
  }
```

**策略执行由 Java 端完成**，不经过 LLM，不经过 Python：

| 策略 | Java 实现 | 说明 |
|------|-----------|------|
| `faker` | Java Faker 库（com.github.javafaker） | 姓名、邮箱、手机号、地址、身份证、银行卡、IP、URL、UUID |
| `fk_query` | JDBC 查询引用表 | 从关联表取真实 ID |
| `range` | `ThreadLocalRandom.current().nextInt()` | 数值范围随机 |
| `enum` | `Random.nextInt()` 取值 | 字典/枚举随机 |
| `weighted_enum` | 加权随机 | 如 80% 男 20% 女 |
| `increment` | `AtomicInteger` 自增 | 流水号 |
| `formula` | 表达式引擎（如 Aviator）或预设公式 | 如 `price = cost * 1.3` |
| `consistent` | HashMap 缓存已生成值 | 同列/跨表一致性 |

**流程**：
```
Spring Boot 提交 Schema → Python Agent 返回 generation_plan.json
→ Java DataGenerator 解析计划 → 按 table_order 顺序
→ 每张表：根据每个字段的 strategy 调用对应 Java Generator
→ 批量 JDBC INSERT 写入目标数据库
```

### 3.5 模型适配层（主模型 + 备用模型）

架构设计：系统设计模型适配层，实现不同大模型服务的切换能力。
```
        LLM Provider（OpenAI 兼容协议统一接口）
              |
     ┌────────┴────────┐
     │                 │
  主模型              备用模型
  DeepSeek            Qwen
  (deepseek-chat)     (qwen-plus)
```

- **主模型 DeepSeek**：默认所有 Agent 调用走 DeepSeek，性价比高、中文能力强
- **备用模型 Qwen**：当 DeepSeek 不可用时自动切换（超时/限流/API 异常）
- **扩展能力**：任何兼容 OpenAI 协议的模型均可接入，论文可提但不实际接 GPT

实现方式：
```python
# 配置文件
models:
  primary: deepseek
  fallback: qwen
  providers:
    deepseek:
      base_url: https://api.deepseek.com/v1
      api_key: ${DEEPSEEK_API_KEY}
      model: deepseek-chat
    qwen:
      base_url: https://dashscope.aliyuncs.com/compatible-mode/v1
      api_key: ${QWEN_API_KEY}
      model: qwen-plus
```

路由逻辑：默认走 primary → 失败时自动降级到 fallback → 都失败返回错误

> **实现范围**：只做上述主备切换。**不做**：自动模型选择、成本优化、负载均衡——本科没必要，答辩也不加分。

### 3.6 任务调度

**第一版（必须完成）**：数据库轮询

```
用户提交生成任务 → Java 创建 Task 记录（status=WAITING）
→ Spring @Async 异步执行
  → 调用 Python Agent（Schema 理解 + 策略生成）→ 保存 generation_plan
  → Java DataGenerator 按计划逐表生成 → 更新 progress 字段到 DB
  → 完成 → status=SUCCESS
→ 前端定时轮询 GET /api/tasks/{id} → 读取 status + progress → 展示进度条
```

Task 状态机：
```
WAITING → RUNNING → SUCCESS
                  → FAILED
```

**第二版（有时间再做）**：WebSocket 替代轮询

把前端轮询改为 WebSocket 实时推送。答辩时进度条演示效果更直观，但核心功能不依赖它。

> **开发原则**：优先保证"数据生成没问题"，不要卡在"实时推送调不通"上。

### 3.7 隐私脱敏流程（调整）

```
Java 规则引擎执行第1层（字段名）+ 第2层（正则采样）
    ↓ 前两层命中的字段 → 直接标记敏感
    ↓ 未命中的字段
    ↓
Python Agent 执行第3层（LLM 语义判断）
    ↓ 返回敏感标签
    ↓
Java 汇总所有敏感字段 → 展示给用户确认
    ↓ 用户确认后
    ↓
Java 执行脱敏 SQL（Java 代码生成，非 LLM 生成）：

| 方法 | 实现 | 适用场景 |
|------|------|----------|
| 掩码（mask） | `UPDATE x SET phone = CONCAT(LEFT(phone,3), '****', RIGHT(phone,4))` | 手机号、身份证 |
| 替换（replace） | `UPDATE x SET email = CONCAT('masked_', id, '@example.com')` | 姓名、邮箱 |
| 哈希（hash） | `UPDATE x SET account = MD5(CONCAT('salt_', account))` | 账号、ID |
| 泛化（generalize） | `UPDATE x SET age = CASE WHEN age<20 THEN '10-20' WHEN age<30 THEN '20-30' ... END` | 年龄、地址 |
| 随机扰动（perturb） | `UPDATE x SET salary = salary * (0.8 + RAND() * 0.4)` | 数值字段（薪资、金额） |

> **注意**：不宣称"差分隐私"（Differential Privacy 涉及 ε 隐私预算、噪声机制和数学证明，本科答辩容易被追问）。论文中用"基于随机扰动的数据保护方法"表述。

**关键原则：SQL 由 Java 代码生成，不由 LLM 生成。**

---

## 四、项目目录结构

```
smart-testdata-platform/
│
├── frontend/                    # Vue 3 + Element Plus
│   ├── src/
│   │   ├── views/               # 页面
│   │   │   ├── Login.vue              # 登录页
│   │   │   ├── Dashboard.vue           # 首页仪表盘（项目数/任务数/成功率）
│   │   │   ├── ProjectList.vue      # 项目管理
│   │   │   ├── DatasourceManage.vue # 数据源配置
│   │   │   ├── SchemaView.vue       # Schema 可视化（Vue Flow / ECharts Graph 表关系图）
│   │   │   ├── GenerateConfig.vue   # 生成策略配置
│   │   │   ├── MaskConfig.vue       # 脱敏规则配置
│   │   │   └── TaskMonitor.vue      # 任务监控（进度轮询）
│   │   ├── components/
│   │   ├── api/                 # 后端 API 封装
│   │   ├── router/
│   │   └── store/               # Pinia 状态管理
│   ├── package.json
│   └── vite.config.js
│
├── backend/                     # Spring Boot
│   ├── src/main/java/com/platform/
│   │   ├── controller/          # REST API 层
│   │   │   ├── AuthController.java       # 登录/注册
│   │   │   ├── ProjectController.java    # CRUD
│   │   │   ├── DatasourceController.java  # 数据源管理
│   │   │   ├── SchemaController.java     # Schema 分析
│   │   │   ├── GenerateController.java   # 数据生成
│   │   │   ├── MaskController.java       # 脱敏处理
│   │   │   └── TaskController.java       # 任务状态 + WebSocket
│   │   ├── service/             # 业务逻辑（按模块分包）
│   │   │   ├── AuthService.java
│   │   │   ├── schema/
│   │   │   │   ├── SchemaAnalyzer.java       # Schema 分析（核心）
│   │   │   │   └── RelationAnalyzer.java     # 关系分析（核心）
│   │   │   ├── generate/
│   │   │   │   ├── DataGenerationService.java # 数据生成执行（核心）
│   │   │   │   └── TaskOrchestrator.java     # 任务编排
│   │   │   └── masking/
│   │   │       ├── RuleEngine.java           # 规则引擎（核心）
│   │   │       └── MaskExecutor.java         # 脱敏执行
│   │   ├── connector/           # 数据库连接管理（核心）
│   │   │   ├── JdbcConnector.java         # JDBC 连接管理
│   │   │   ├── MetadataReader.java        # information_schema 元数据读取
│   │   │   └── ConnectionValidator.java   # 连接测试验证
│   │   ├── generator/            # 数据生成器（Java 实现）
│   │   │   ├── FakerGenerator.java       # Faker 生成器
│   │   │   ├── RegexGenerator.java       # 正则模板生成器
│   │   │   ├── ForeignKeyGenerator.java  # 外键关联生成器
│   │   │   └── CustomGenerator.java      # 自定义公式生成器
│   │   ├── entity/              # 数据库实体
│   │   │   ├── User.java
│   │   │   ├── Project.java
│   │   │   ├── Datasource.java
│   │   │   ├── SchemaTable.java          # Schema 缓存-表
│   │   │   ├── SchemaColumn.java         # Schema 缓存-列
│   │   │   ├── GenerateTask.java
│   │   │   ├── GenerationStrategy.java   # LLM 生成的策略
│   │   │   ├── TaskLog.java              # 任务日志
│   │   │   └── MaskRule.java
│   │   ├── mapper/              # MyBatis-Plus Mapper
│   │   ├── dto/                 # 数据传输对象
│   │   ├── config/              # Spring 配置
│   │   │   ├── SecurityConfig.java
│   │   │   ├── WebSocketConfig.java
│   │   │   └── AsyncConfig.java
│   │   └── exception/           # 全局异常处理
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── db/migration/        # Flyway 数据库迁移脚本
│   └── pom.xml
│
├── ai-service/                  # Python FastAPI（仅负责 LLM 调用 + Agent）
│   ├── app/
│   │   ├── api/
│   │   │   ├── routes.py            # API 路由（/api/ai/*）
│   │   ├── agent/
│   │   │   ├── schema_agent.py      # Agent 1: Schema 理解
│   │   │   └── strategy_agent.py    # Agent 2: 生成策略
│   │   ├── prompts/             # Prompt 模板（论文可写"提示词模板设计"）
│   │   │   ├── schema_prompt.py     # Schema 理解 Prompt
│   │   │   ├── strategy_prompt.py   # 策略生成 Prompt
│   │   │   └── mask_prompt.py       # 敏感识别 Prompt
│   │   ├── chains/              # 处理流程编排
│   │   │   ├── schema_chain.py      # Schema 理解流程
│   │   │   └── strategy_chain.py    # 策略生成流程
│   │   ├── tools/               # Agent 可调用的工具（仅 LLM 交互工具）
│   │   │   ├── schema_tool.py       # Schema 查询工具
│   │   │   ├── database_tool.py     # 数据库交互工具
│   │   │   └── llm_tool.py          # LLM 调用封装
│   │   ├── models/              # 模型适配层
│   │   │   ├── router.py            # 主备模型路由器（DeepSeek 主 / Qwen 备）
│   │   │   └── config.py            # 模型配置
│   │   ├── schemas/             # Pydantic 数据模型
│   │   │   ├── schema_request.py    # Schema 请求/响应
│   │   │   ├── strategy.py          # 策略数据模型（generation_plan）
│   │   │   └── task.py              # 任务相关模型
│   │   └── utils/
│   │       └── logger.py
│   ├── requirements.txt
│   ├── Dockerfile
│   └── .env.example
│
├── docs/                        # 毕业设计文档
│   ├── 开题报告.md
│   ├── 系统设计文档.md
│   └── 毕业论文.md
│
└── docker-compose.yml           # 一键启动全栈（MySQL + Java + Python + Nginx，不加 Redis）
```

---

## 五、数据库设计

### 5.1 系统数据库（platform_db）

Spring Boot 自身使用的数据库，存储平台元数据：

```sql
-- 用户表
sys_user (id, username, password, nickname, created_at, updated_at)

-- 项目表
project (id, user_id, name, description, created_at, updated_at)

-- 数据源配置（密码 AES 加密存储，数据库不保存明文）
datasource (id, project_id, name, host, port, db_name, username, 
            password_encrypted, status, created_at)

-- Schema 缓存 — 表（避免每次扫描数据库）
schema_table (id, datasource_id, table_name, table_comment, 
              row_count_estimate, created_at)

-- Schema 缓存 — 列
schema_column (id, table_id, column_name, data_type, max_length, 
               is_nullable, is_primary_key, column_comment, 
               foreign_ref_table, foreign_ref_column, 
               sensitive_type, sample_data_json,
               created_at)

-- 生成任务
generate_task (id, project_id, datasource_id, tables_json, row_count,
               generated_rows, status, progress, error_msg, 
               created_at, finished_at)

-- 生成策略（保存 LLM 输出的 generation_plan）
generation_strategy (id, task_id, table_name, column_name, 
                     strategy_type, strategy_json, created_at)

-- 任务日志（WebSocket 事件落库，方便追踪）
task_log (id, task_id, event_type, message, created_at)

-- 脱敏规则
mask_rule (id, project_id, datasource_id, table_name, column_name,
           rule_type, rule_config_json, created_at)
```

> **Schema 缓存的作用**：首次连接扫描 information_schema 后存入 schema_table + schema_column（含采样数据），后续直接用缓存，不用每次扫描。用户可手动刷新。

### 5.2 目标数据库（用户连接的 MySQL）

用户自己的业务数据库，例如 `test_db`：
- **读取**：表结构、字段、外键关系（information_schema）
- **写入**：生成的测试数据（INSERT）
- **脱敏**：对原始敏感数据执行脱敏 SQL（UPDATE）

平台不修改目标数据库的表结构，只在数据层面操作。

---

## 六、分步实施计划

### 阶段 1：项目初始化与环境搭建（1-2天）

- [ ] 创建 Maven 项目（Spring Boot 3.x + MyBatis-Plus）
- [ ] 创建 Python 项目（FastAPI + LangChain）
- [ ] 创建 Vue 项目（Vite + Element Plus）
- [ ] 平台数据库初始化（Flyway 建表）
- [ ] Docker Compose（MySQL + Java + Python + Nginx）
- [ ] 各服务能启动，互相 ping 通

### 阶段 2：认证 + 项目管理基础 CRUD（1天）

- [ ] JWT 登录/注册接口
- [ ] 项目 CRUD 接口
- [ ] 前端：Login.vue 登录页 + Dashboard.vue 首页仪表盘（项目数/任务数/成功率统计）
- [ ] 前端：ProjectList.vue 项目列表页
- [ ] 验证：能注册、登录、创建项目，Dashboard 展示统计数据

### 阶段 3：数据源管理 + Schema 分析（3-4天）

- [ ] 数据源配置 CRUD（密码 AES 加密存储）
- [ ] 连接测试接口
- [ ] **Schema 分析器**：查询 information_schema，解析字段
- [ ] **数据采样**：每个字段取前 5 条实际数据，辅助 LLM 理解字段语义
- [ ] **关系分析器**：提取外键关系，输出表依赖关系图 JSON
- [ ] **Schema 缓存**：存入 schema_table + schema_column，避免每次扫描
- [ ] 前端：数据源配置页 + Schema 树形/表格展示
- [ ] 验证：连接 MySQL → 看到完整的表结构和关系 + 采样数据

### 阶段 4：规则引擎（2天）

- [ ] 第1层：字段名规则（配置化，支持扩展）
- [ ] 第2层：正则检测（采样实际数据 + 批量匹配）
- [ ] 敏感字段标记 + 脱敏类型推荐
- [ ] 前端：敏感字段高亮展示
- [ ] 验证：连接含敏感数据的表 → 正确标记手机号/身份证等

### 阶段 5：Python AI 服务 + 模型路由（3-4天）

- [ ] FastAPI 项目结构搭建
- [ ] 模型适配层（主模型 DeepSeek + 备用模型 Qwen，OpenAI 兼容协议）
- [ ] Pydantic Schemas 定义
- [ ] Agent 1：Schema 理解 Agent（Prompt 模板 + 工具调用）
- [ ] Agent 2：数据生成策略 Agent（Prompt 模板 + 工具调用）
- [ ] Chains：串联两个 Agent 的流程
- [ ] API 端点：`POST /api/ai/analyze-schema` + `POST /api/ai/generate-strategy`
- [ ] 验证：给 Schema → 返回语义标签 + 生成策略 JSON

### 阶段 6：数据生成引擎（Java 端，3-4天）

- [ ] 策略解析器（解析 generation_plan.json → 调度各 Generator）
- [ ] Faker 生成器（com.github.javafaker：姓名、地址、邮箱、手机号等）
- [ ] 正则模板生成器（自定义模式）
- [ ] 外键关联生成器（JDBC 查引用表取 ID）
- [ ] 自定义公式生成器（Aviator 表达式引擎或预设公式）
- [ ] 批量 JDBC INSERT + 进度更新（写入 DB 的 progress 字段）
- [ ] 流程：Java 提交 Schema → Python Agent 返回 generation_plan → Java 解析执行 → 写入目标库
- [ ] 验证：选择一张表 → 生成 1000 条数据 → 数据符合预期

### 阶段 7：任务调度 + 进度展示（1-2天）

**第一版（必须完成）：**
- [ ] 异步任务框架（Spring `@Async` + 线程池）
- [ ] Task 状态机（WAITING → RUNNING → SUCCESS / FAILED）
- [ ] 进度写入 DB（DataGenerator 每生成一批更新 progress 字段）
- [ ] REST 接口 `GET /api/tasks/{id}` 返回 status + progress
- [ ] 前端定时轮询 + 进度条展示

**第二版（有时间再做）：**
- [ ] WebSocket 替代轮询，推送实时进度

> **验证**：提交生成任务 → 前端轮询看到进度更新 → 完成

### 阶段 8：隐私脱敏（2天）

- [ ] 脱敏执行器（Java 生成脱敏 SQL）
- [ ] 五种脱敏方式：掩码（mask）、替换（replace）、哈希（hash）、泛化（generalize）、随机扰动（perturb）
- [ ] 前端：脱敏规则配置页 + 预览对比
- [ ] 验证：选表 + 执行脱敏 → 敏感数据被正确处理

### 阶段 9：前端完善 + 联调（3-4天）

- [ ] Schema 可视化页面（Vue Flow / ECharts Graph 绘制表关系图：users → orders → order_detail，答辩亮点）
- [ ] 生成策略配置页面（可编辑 Agent 返回的策略）
- [ ] 数据预览页面（生成的测试数据分页展示 + 导出 CSV/SQL）
- [ ] 全流程联调：连接 DB → 分析 → 生成策略 → 执行 → 预览
- [ ] UI 打磨、错误提示、加载状态

### 阶段 10：论文 + 答辩准备（5-7天）

- [ ] 系统设计文档 → 开题报告 → 毕业论文
- [ ] 架构图绘制（Draw.io 或 PlantUML）
- [ ] 核心流程图（序列图、活动图）
- [ ] 创新点提炼（LLM Agent + 规则混合、可插拔模型路由、策略生成与执行分离）
- [ ] 答辩 PPT
- [ ] 演示 Demo 录制

### 阶段 11：简历包装 + 上线部署（2-3天）

- [ ] Docker Compose 一键启动
- [ ] 简历项目描述（突出技术亮点 + 量化成果）
- [ ] GitHub README 英文版
- [ ] 可选：部署到云服务器（阿里云/腾讯云学生机）

---

## 七、毕业设计创新点（答辩重点）

1. **LLM Agent 与规则引擎混合架构**：规则处理可穷举（正则匹配、字段名匹配），Agent 处理语义理解（结合数据采样推断字段含义），两者互补而非对立
2. **策略生成与数据生成分离**：LLM 只输出 generation_plan.json，Java 端 Faker/JDBC 执行真正生成——避免幻觉、Token 浪费，同时让 Java 后端承担核心业务逻辑
3. **数据采样增强 LLM 理解**：不只看字段名和注释，还采样实际数据前 5 条送给 LLM 辅助判断，明显提升敏感字段识别准确率
4. **大模型提示词模板设计**：针对 Schema 理解、策略生成、敏感识别三个场景设计专用 Prompt 模板，论文可展开讨论 Prompt Engineering 实践
5. **模型适配层主备切换**：OpenAI 兼容协议统一接口，主模型 DeepSeek + 备用 Qwen，支持自动降级，有工程实用价值

---

## 八、验证方式

每完成一个阶段后执行端到端测试：

1. **阶段 3 验证**：连接任意 MySQL → 前端正确展示所有表和字段
2. **阶段 4 验证**：建一张含手机号/身份证的测试表 → 规则引擎正确标记
3. **阶段 5 验证**：给 Agent 发 Schema JSON → 返回合理的语义标签和策略
4. **阶段 6 验证**：选择 users 表 → 生成 100 条 → 数据格式正确、关联一致
5. **阶段 7 验证**：提交 10000 条生成任务 → 前端进度条实时更新
6. **阶段 8 验证**：对含敏感数据的表执行脱敏 → 数据隐藏、原始备份保留
7. **阶段 9 验证**：走通完整流程，无报错，UI 可用
