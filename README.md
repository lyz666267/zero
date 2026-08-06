# 智能测试数据生成与隐私脱敏平台

## 项目简介

基于大模型 Agent 的智能测试数据生成与隐私脱敏平台，实现：

- 数据库 Schema 分析
- 测试数据智能生成
- 隐私字段识别
- 数据脱敏
- 数据质量评估

## 核心功能

- AI 需求分析
- 测试数据生成
- 多表关联生成
- 隐私脱敏
- 数据质量评估
- 数据导出

## 技术架构

### Frontend

- Vue 3
- Element Plus
- Vite
- Pinia
- ECharts

### Backend

- Spring Boot
- MyBatis-Plus
- MySQL
- Flyway
- JWT

### AI Service

- Python FastAPI
- LLM Agent
- DeepSeek / Qwen
- ReAct Tool Calling

### 部署

- Docker Compose
- Nginx

## 核心流程

```text
用户需求
  ↓
Agent 分析
  ↓
Schema 解析
  ↓
测试数据生成
  ↓
隐私脱敏
  ↓
质量评估
  ↓
数据导出
```

## 启动方式

### 1. 环境变量

```bash
cp smart-testdata-platform/.env.example .env
```

### 2. MySQL 初始化

```bash
cd smart-testdata-platform
docker compose up -d mysql
```

后端启动时会通过 Flyway 自动执行数据库迁移。

### 3. Backend 启动

```bash
cd smart-testdata-platform/backend
mvn spring-boot:run
```

启动前确保 `JWT_SECRET`、`AES_KEY` 等环境变量已配置。

### 4. AI Service 启动

```bash
cd smart-testdata-platform/ai-service
pip install -r requirements.txt
python -m uvicorn app.main:app --reload --port 8000
```

### 5. Frontend 启动

```bash
cd smart-testdata-platform/frontend
npm install
npm run dev
```

浏览器访问：

```text
http://localhost:5173
```

## 测试说明

### Backend

- JUnit 5
- MockMvc
- Integration Test
- JaCoCo

```bash
cd smart-testdata-platform/backend
mvn test
```

覆盖率报告：

```text
smart-testdata-platform/backend/target/site/jacoco/index.html
```

### AI Service

- pytest
- FastAPI TestClient
- pytest-cov

```bash
cd smart-testdata-platform/ai-service
python -m pytest tests/
```

覆盖率报告：

```text
smart-testdata-platform/ai-service/htmlcov/index.html
```

### Frontend

- Vitest

```bash
cd smart-testdata-platform/frontend
npm test
```

## 项目文档

- [系统架构](smart-testdata-platform/docs/architecture.md)
- [面试讲解](smart-testdata-platform/docs/interview.md)
- [演示脚本](smart-testdata-platform/docs/demo-script.md)
- [常见问题排查](smart-testdata-platform/docs/troubleshooting.md)
