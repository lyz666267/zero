# Progress Log

## Session: 2026-07-29

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
