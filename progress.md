# Progress Log

## Session: 2026-07-28 (续)

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
