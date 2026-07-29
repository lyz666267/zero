# Progress Log

## Session: 2026-07-29

### Phase 3.3-1 — 数据采样 ✅
- **Status:** ✅ complete
- **说明：** 基于 JDBC 实现数据库字段真实数据采样（每字段前5条），含 SQL 注入防护
- Actions taken:
  - ✅ `dto/SampleRequest.java` — 采样请求 DTO（datasourceId + tables）
  - ✅ `dto/SampleResponse.java` — 采样响应 DTO（TableSample + Map<String,List<Object>> 动态列）
  - ✅ `schema/SchemaSampleService.java` — 核心采样服务（JDBC 直连 + 三层安全防护）
  - ✅ `controller/SchemaController.java` — POST /api/schema/sample 端点
  - ✅ `mvn clean compile` BUILD SUCCESS
  - ✅ `POST /api/schema/sample` 接口测试通过
  - ✅ SQL 注入测试通过（`sys_user; DROP TABLE` → 被正则拒绝）
  - ✅ 不存在表/数据源 → 优雅降级
  - ✅ NULL 值过滤（`WHERE col IS NOT NULL`）
  - ✅ 多表采样（`datasource` + `flyway_schema_history` 同时采样）
- Key decisions:
  - 三层 SQL 注入防护：正则校验 `^[a-zA-Z_][a-zA-Z0-9_]*$` → information_schema 交叉验证 → 仅拼入已校验的列名
  - 列名从 information_schema.COLUMNS 获取后才拼入 `SELECT \`col\` FROM \`table\``，彻底杜绝注入
  - 单表采样失败不影响其他表（catch BusinessException → 返回空 columns）
  - 与 DatasourceService 共享 JDBC URL 构建逻辑、AesUtil 解密
  - 使用 `java.sql.Statement`（非 PreparedStatement）执行采样 SQL，因为列名/表名已通过安全校验无法参数化
- Test results:

| 测试场景 | 结果 |
|----------|------|
| sys_user 表采样 | ✅ 8 columns, 1 row each |
| project 表采样 | ✅ 6 columns, 空表 |
| datasource 表采样 | ✅ 12 columns |
| flyway_schema_history | ✅ 10 columns, 2 rows each (LIMIT 5 生效) |
| SQL 注入 `sys_user; DROP TABLE` | ✅ 被正则拒绝，返回空 columns |
| 不存在表 `nonexistent_table` | ✅ 优雅降级，空 columns |
| 不存在数据源 `999` | ✅ 404 错误 |
| NULL 值字段 `email` | ✅ 返回 `[]`（IS NOT NULL 过滤） |
| 多表同时采样 | ✅ 正确返回各表结果 |

### Phase 3.2 — LLM Agent 测试数据生成规划器 ✅
- **Status:** ✅ complete
- **分支:** `phase3-schema` (继续使用)
- Actions taken:
  - ✅ AI Service: `schemas/generation_plan.py` — Pydantic 模型（FieldPlan/TablePlan/GenerationPlan）
  - ✅ AI Service: `services/llm_service.py` — DeepSeek API 封装（OpenAI SDK）+ mock 降级
  - ✅ AI Service: `agents/testdata_agent.py` — 规则引擎（50+ 字段名映射）+ LLM Prompt 模板
  - ✅ AI Service: `POST /api/ai/generate-plan` — 接收 Schema + 需求 → 返回生成计划
  - ✅ Spring Boot: `TestdataService` + `TestdataController` — 代理转发到 AI 服务
  - ✅ Spring Boot: `RestTemplateConfig` — 配置超时（连接 5s，读取 60s）
  - ✅ Frontend: `TestDataGenerate.vue` — 项目选择 → 数据源选择 → 加载 Schema → 输入需求 → 生成计划
  - ✅ 前端: 侧边栏新增 "测试数据生成" 入口（Dashboard/ProjectList/DatasourceManage）
  - ✅ 后端编译 BUILD SUCCESS
  - ✅ 前端 Vite 构建成功
  - ✅ AI 服务 Mock 模式测试: 字段映射正确（id 跳过、username→faker.name、age→random.integer、status→enum.values）
  - ✅ Spring Boot 代理测试: 全链路 Vue → SB → FastAPI → Agent 通过
- Key decisions:
  - Mock 模式: 无 DEEPSEEK_API_KEY 时自动降级为规则引擎（基于字段名/类型映射表）
  - LLM 模式: 通过 DeepSeek API（OpenAI 兼容接口）生成更智能的计划
  - JSON 反序列化: Spring Boot 使用原始 String 获取 + ObjectMapper（忽略未知字段）避免 Jackson 失败
  - 字段生成器映射: 按优先级 精确匹配 > 前缀匹配 > 包含匹配 > 类型匹配 > 默认 faker.word
- Errors:
  - Pydantic `schema` 字段名与 BaseModel 内置属性冲突 → `model_config = {"protected_namespaces": ()}`
  - Spring Boot RestTemplate 直接反序列化 GeneratePlanResponse 失败 → 改为 String + 容错 ObjectMapper

### Phase 3.1 — 数据库 Schema 智能分析 ✅
- **Status:** ✅ complete
- **分支:** `phase3-schema` → merged to master
- Actions taken:
  - ✅ Flyway V2 迁移: datasource 表添加 db_type 字段
  - ✅ AES 加密工具 (AesUtil): 密码加密存储/解密使用
  - ✅ 数据源 CRUD: Entity/Mapper/Service/Controller 全栈
  - ✅ JDBC 连接管理 + MetadataReader: 查询 information_schema
  - ✅ Schema 响应包含: 表名+注释+字段名+类型+长度+可空+默认值+主键+外键
  - ✅ AI 服务预留: POST /api/ai/schema/analyze
  - ✅ 前端 DatasourceManage.vue: 项目选择+列表+测试连接+Schema 折叠面板
  - ✅ 侧边栏增加 "数据源管理" 入口
  - ✅ 后端编译 BUILD SUCCESS (30 files)
  - ✅ 前端 Vite 构建成功
  - ✅ API 全链路测试: 7/7 endpoints 通过
- Decisions:
  - 密码 AES 加密后存储 (password_encrypted 字段)，明文仅用于 JDBC 连接
  - 测试连接 API 支持两种模式: 不保存直接测试 / 基于已保存 ID 测试
  - MetadataReader 直接用 DriverManager + information_schema SQL，不依赖 Spring DataSource

### 文件变更清单
| 文件 | 操作 | 说明 |
|------|------|------|
| V2__add_db_type.sql | 新建 | datasource 表增强 |
| AesUtil.java | 新建 | AES 加密工具 |
| Datasource.java | 新建 | 实体 (映射 datasource 表) |
| DatasourceMapper.java | 新建 | MyBatis-Plus Mapper |
| DatasourceRequest.java | 新建 | 创建/更新/测试 DTO |
| SchemaResponse.java | 新建 | Schema 响应 (TableInfo + ColumnInfo) |
| DatasourceService.java | 新建 | 业务逻辑 + 测试连接 + Schema 读取 |
| DatasourceController.java | 新建 | REST 接口 (7 endpoints) |
| MetadataReader.java | 新建 | JDBC information_schema 查询 |
| datasource.js | 新建 | 前端 API 封装 |
| DatasourceManage.vue | 新建 | 前端数据源管理页 |
| routes.py | 修改 | 新增 POST /api/ai/schema/analyze |
| application.yml | 修改 | 新增 platform.datasource.aes-key |
| router/index.js | 修改 | 新增 /datasources 路由 |
| Dashboard.vue | 修改 | 侧边栏增加入口 |
| ProjectList.vue | 修改 | 侧边栏增加入口 |

## Session: 2026-07-28 (续 2)

### Phase 2 完成
- **Status:** ✅ complete
- **说明：** JWT 认证 + 项目管理 CRUD + 前端页面，分支 `phase2-auth-crud`
- Actions taken:
  - ✅ JWT 工具类 (JwtUtil) + 认证过滤器 (JwtAuthenticationFilter)
  - ✅ Spring Security 配置：放行 `/api/auth/**`，其余需认证
  - ✅ 用户注册/登录 API → 返回 JWT Token
  - ✅ 项目管理完整 CRUD (分页 + 详情 + 创建 + 更新 + 删除)
  - ✅ MyBatis-Plus 分页插件 + 时间自动填充
  - ✅ 全局异常处理 + 统一响应格式 (ApiResponse<T>)
  - ✅ 前端 Login.vue（登录+注册双Tab，表单验证）
  - ✅ 前端 Dashboard.vue（统计卡片 + 侧边栏导航 + 退出登录）
  - ✅ 前端 ProjectList.vue（分页表格 + 新建/编辑对话框 + 删除确认）
  - ✅ 前端 API 封装（axios 拦截器 + JWT Token 自动携带 + 401 跳转）
  - ✅ 路由守卫（未登录 → /login，已登录 → /dashboard）
  - ✅ Pinia 用户状态管理
  - ✅ Maven 编译 BUILD SUCCESS (22 source files)
  - ✅ Vite 构建成功 (101 modules, 970ms)
  - ✅ Git: phase2-auth-crud 分支 + merge 到 master + push
- Key decisions:
  - Vite 8 的 Rolldown 与 Element Plus 2.14 兼容（原 Vite 5 计划不必要）
  - 前端 http://localhost:5173 通过 Vite proxy 转发 API 到 8088
  - 不做 RBAC/角色/权限：保持简单
  - Spring Security + JWT 无状态认证，不依赖 Session
- Errors:
  - Maven 3.5.4 多版本共存 → 手动 `export MAVEN_HOME` 到 3.9.16
  - 中文 curl 请求 JSON 编码错误 (0xb9) → Windows GBK vs UTF-8
  - npm ENOTEMPTY → `cmd /c rmdir /s /q` 强制清除
  - git commit 超时 → 缩短 commit message
  - git push master rejected → `git pull --rebase` 先合并远程变更

### Phase 1 完成
- **Status:** ✅ complete
- **说明：** 从 Maven 升级断点恢复，完成全部 Phase 1 任务。
- Actions taken:
  - ✅ Git 初始化：.gitignore + 首次提交 (36c54dd)
  - ✅ Maven 3.5.4 → 3.9.16：从阿里云镜像下载 (9.4MB，~2分钟)
  - ✅ `mvn clean compile` BUILD SUCCESS
  - ✅ Flyway V1__init_schema.sql 编写（9 张业务表）
  - ✅ docker-compose.yml + nginx.conf 编写
  - ✅ MySQL 建表验证：platform_db 中 10 张表（含 flyway_schema_history）
  - ✅ 后端端口改为 8088（避免与 studycoach 冲突）
  - ✅ 改用已有 MySQL (studycoach-mysql, port 3306)
  - ✅ Spring Boot 启动成功 → Tomcat on 8088, /login 返回 200
  - ✅ Python AI 启动成功 → /api/ai/health 返回 {"status":"ok"}
  - ✅ 第二次提交 (359c5e6)
- Key decisions:
  - 阿里云镜像下载 Maven 比 Apache Archive 快 5 倍
  - 复用已有 MySQL 容器，避免 Docker 重建初始化慢的问题
  - spring-boot-starter-security 自动生成密码，下次配置 JWT 认证
  - Python venv 需额外安装 uvicorn 和所有 langchain 依赖

## Test Results
| Test | Input | Expected | Actual | Status |
|------|-------|----------|--------|--------|
| Maven 编译 | mvn clean compile | BUILD SUCCESS | BUILD SUCCESS | ✅ |
| Flyway 迁移 | Spring Boot 启动 | 9 张表创建 | 10 张表（含 history） | ✅ |
| Spring Boot 启动 | mvn spring-boot:run | Tomcat on 8088 | Tomcat on 8088, /login 200 | ✅ |
| Python AI 启动 | uvicorn app.main:app | /api/ai/health 200 | {"status":"ok"} | ✅ |
| 数据库连接 | JDBC 127.0.0.1:3306 | platform_db 可读写 | Flyway 成功建表 | ✅ |

## Error Log
| Timestamp | Error | Attempt | Resolution |
|-----------|-------|---------|------------|
| 2026-07-28 | maven-compiler-plugin:3.13.0 requires Maven 3.6.3 (当前 3.5.4) | 1 | 阿里云镜像下载 Maven 3.9.16 → 解压到 d:/java/maven/ |
| 2026-07-28 | Oracle Java 8 javapath 优先级高于 JDK 17 | 1 | 每次 Maven 命令前 `export JAVA_HOME=/c/Program Files/Eclipse Adoptium/jdk-17.0.19.10-hotspot` |
| 2026-07-28 | JDBC: Unsupported character encoding 'utf8mb4' | 1 | characterEncoding 改为 UTF-8（Java 编码名） |
| 2026-07-28 | Access denied user@localhost via Docker port-forward | 2 | 改用已有 MySQL studycoach-mysql (3306) + platform 用户 |
| 2026-07-28 | Docker MySQL init 极慢 (WSL2) | 1 | 复用 studycoach-mysql，避免重建 |
| 2026-07-28 | Web server port 8080 冲突 | 1 | 改为 8088 |
