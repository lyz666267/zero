# 系统架构

## 架构图

```text
                Vue3
                 |
              REST API
                 |
        Spring Boot Backend
                 |
    -------------------------
    |           |           |
 MySQL     Generator     Security
             |
        Python AI Service
             |
      Agent + LLM Router
             |
   DeepSeek / Qwen
```

## 30 秒架构介绍

这是一个基于大模型 Agent 的智能测试数据生成与隐私脱敏平台。

前端使用 Vue 3，通过 REST API 调用 Spring Boot 后端。后端负责数据源管理、Schema 解析、测试数据生成、隐私脱敏、质量评估和导出。

当用户提出生成需求时，后端会把数据库 Schema 和需求交给 Python FastAPI AI 服务。AI 服务内部使用 Agent 和 LLM Router，自动选择 DeepSeek 或 Qwen，生成测试数据计划。

计划回到后端后，由生成器引擎执行多表关联生成，再经过隐私字段识别和脱敏，最后进行质量评估并导出 CSV、SQL、JSON。

## 技术栈

- Frontend：Vue 3 + Element Plus + Vite
- Backend：Spring Boot + MyBatis-Plus + Flyway + JWT
- Database：MySQL
- AI Service：Python FastAPI + LLM Agent + DeepSeek / Qwen
- Deployment：Docker Compose + Nginx

## 数据流

```text
用户需求
  ↓
Vue 前端
  ↓
Spring Boot 后端
  ↓
Schema 分析 / 数据源管理
  ↓
FastAPI AI Service
  ↓
LLM Agent 生成测试数据计划
  ↓
测试数据生成引擎
  ↓
隐私字段识别与脱敏
  ↓
数据质量评估
  ↓
CSV / SQL / JSON 导出
```

## 关键模块

- `backend`：业务 API、数据源管理、Schema 缓存、测试数据生成、脱敏、质量评估、导出。
- `ai-service`：LLM Agent、Schema 分析、策略生成、Tool Calling、模型故障切换。
- `frontend`：登录、项目管理、数据源、Schema、生成任务、脱敏、质量、导出页面。
