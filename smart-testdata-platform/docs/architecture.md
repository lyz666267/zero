# 系统架构

## 整体架构

```text
┌──────────────┐      ┌──────────────┐      ┌──────────────┐
│   Vue 3      │ ───▶ │  Spring Boot │ ───▶ │    MySQL     │
│  Frontend    │      │   Backend    │      │  Platform DB │
└──────────────┘      └──────┬───────┘      └──────────────┘
                             │
                             ▼
                    ┌──────────────┐      ┌──────────────┐
                    │   FastAPI    │ ───▶ │ LLM Provider │
                    │  AI Service  │      │ DeepSeek/Qwen│
                    └──────────────┘      └──────────────┘
```

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
- `ai-service`：LLM Agent、Schema 分析、策略生成、Tool Calling。
- `frontend`：登录、项目管理、数据源、Schema、生成任务、脱敏、质量、导出页面。
