# 面试讲解

## 30 秒项目介绍

这是一个智能测试数据生成与隐私脱敏平台。

用户在前端输入测试数据需求，后端读取数据库 Schema 后交给 AI Agent 分析，Agent 通过 LLM Router 调用 DeepSeek 或 Qwen 生成测试数据计划。

计划回到后端后，系统会自动生成多表关联测试数据，识别手机号、邮箱、姓名等隐私字段并完成脱敏，再进行数据质量评估，最后支持 CSV、SQL、JSON 导出。

## 架构讲解

```text
Vue3 → REST API → Spring Boot → MySQL / Generator / Security
                              ↓
                      Python AI Service
                              ↓
                      Agent + LLM Router
                              ↓
                      DeepSeek / Qwen
```

讲解重点：

1. 前端只负责交互和展示。
2. 后端负责业务编排、数据源管理、生成、脱敏、质量、导出。
3. AI 服务独立部署，负责 Schema 理解和测试数据计划生成。
4. LLM Router 支持 DeepSeek 和 Qwen 自动故障切换。

## 核心功能

- AI 需求分析
- 测试数据生成
- 多表关联生成
- 隐私脱敏
- 数据质量评估
- 数据导出

## 安全设计

- JWT 认证，未登录请求返回 401
- AES-GCM 加密数据源密码
- 动态 SQL 表名白名单校验
- CORS 白名单
- 生产环境密钥全部从环境变量读取

## 测试体系

- Backend：JUnit 5、MockMvc、Integration Test、JaCoCo
- AI Service：pytest、FastAPI TestClient、pytest-cov
- Frontend：Vitest

## AI Agent 设计

- ReAct 模式：思考 → 行动 → 观察 → 循环
- LLM 调用超时：30 秒
- 工具执行超时：30 秒
- 重复动作检测
- LLM 故障时自动降级 Mock

## 面试高频问题

### 为什么不用 LangChain？

项目核心是可控的测试数据生成流程，自研 Agent 和 LLM Router 更轻量，便于展示模型调用、故障切换和工具调用逻辑。

### 数据源密码怎么保护？

使用 AES-GCM 加密存储，密钥从环境变量读取，生产环境不写死。

### 动态 SQL 如何防注入？

表名和列名先做白名单校验，再通过 information_schema 确认表存在，然后才拼接 SQL。

### 生成的数据如何保证隐私安全？

生成后自动识别敏感字段，执行脱敏，再进行质量评估，确保展示和导出数据不包含明文手机号、邮箱等隐私信息。
