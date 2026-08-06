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

复制根目录 `.env.example` 为 `.env`，按需填写：

```bash
cp .env.example .env
```

### 2. MySQL 初始化

使用 Docker Compose 启动 MySQL：

```bash
docker compose up -d mysql
```

也可以使用本地 MySQL，并创建 `platform_db` 数据库。后端启动时会通过 Flyway 自动执行 `backend/src/main/resources/db/migration` 下的迁移脚本。

### 3. Backend 启动

```bash
cd backend
mvn spring-boot:run
```

启动前确保 `JWT_SECRET`、`AES_KEY` 等环境变量已配置。

### 4. AI Service 启动

```bash
cd ai-service
pip install -r requirements.txt
python -m uvicorn app.main:app --reload --port 8000
```

### 5. Frontend 启动

```bash
cd frontend
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
cd backend
mvn test
```

覆盖率报告：

```text
backend/target/site/jacoco/index.html
```

### AI

- pytest
- FastAPI TestClient

```bash
cd ai-service
python -m pytest tests/
```

覆盖率报告：

```text
ai-service/htmlcov/index.html
```

### Frontend

- Vitest

```bash
cd frontend
npm test
```

## 项目目录结构

```text
smart-testdata-platform/
├── backend/                  # Spring Boot 后端
│   ├── src/main/java/        # Java 业务代码
│   ├── src/main/resources/   # 配置与 Flyway 迁移
│   ├── src/test/java/        # 后端测试
│   └── pom.xml
├── ai-service/               # FastAPI AI 服务
│   ├── app/                  # Agent、LLM、工具链
│   ├── tests/                # pytest 测试
│   └── requirements.txt
├── frontend/                 # Vue 3 前端
│   ├── src/                  # 页面、组件、状态管理
│   ├── package.json
│   └── vite.config.js
├── docs/mysql-init/          # MySQL 初始化说明
├── nginx/                    # Nginx 配置
├── docker-compose.yml        # Docker Compose 编排
└── .env.example              # 环境变量示例
```

## 项目文档

- [系统架构](docs/architecture.md)
- [面试讲解](docs/interview.md)
- [演示脚本](docs/demo-script.md)
- [常见问题排查](docs/troubleshooting.md)
