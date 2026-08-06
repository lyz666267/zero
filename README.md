智能测试数据生成与隐私脱敏平台
项目简介
基于大模型Agent的智能测试数据生成与隐私脱敏平台，实现：

数据库模式分析
数据测试智能生成
隐私字段识别
数据脱敏
数据质量评估
核心功能
AI需求分析
测试数据生成
多表关联生成
隐私脱敏
数据质量评估
数据导出
技术架构
前端
Vue 3
元素加
维特
皮尼亚
电子图表
后端
Spring Boot
MyBatis-Plus
MySQL
迁徙路线
JWT
人工智能服务
Python FastAPI
LLM代理人
DeepSeek / Qwen
React 工具调用
部署
Docker Compose
Nginx
核心流程
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
启动方式
1.环境指标
复制根目录.env.example为.env，迭代填写：

cp .env.example .env
2.MySQL初始化
使用 Docker Compose 启动 MySQL：

docker compose up -d mysql
也可以使用本地 MySQL，并创建platform_db数据库。启动时会通过 Flyway 自动执行backend/src/main/resources/db/migration下的迁移脚本。

3. 后端启动
cd backend
mvn spring-boot:run
启动前确保JWT_SECRET、AES_KEY等环境指标已配置。

4.AI服务启动
cd ai-service
pip install -r requirements.txt
python -m uvicorn app.main:app --reload --port 8000
5. 前端启动
cd frontend
npm install
npm run dev
浏览器访问：

http://localhost:5173
测试说明
后端
JUnit 5
MockMvc
集成测试
JaCoCo
cd backend
mvn test
覆盖率报告：

backend/target/site/jacoco/index.html
人工智能
pytest
FastAPI 测试客户端
cd ai-service
python -m pytest tests/
覆盖率报告：

ai-service/htmlcov/index.html
前端
维泰斯特
cd frontend
npm test
项目目录结构
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
