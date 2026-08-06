# MySQL 初始化说明

## 数据库初始化方式

推荐使用 Docker Compose 启动 MySQL：

```bash
docker compose up -d mysql
```

Compose 中默认配置：

- 数据库名：`platform_db`
- 用户名：`platform`
- 密码：`platform123`
- 端口：`3307`

也可以使用本地 MySQL，手工创建数据库：

```sql
CREATE DATABASE IF NOT EXISTS platform_db
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

## Flyway 迁移说明

后端启动时通过 Flyway 自动执行迁移脚本：

```text
backend/src/main/resources/db/migration/
```

迁移脚本按版本顺序执行：

```text
V1__init_schema.sql
V2__add_db_type.sql
...
V10__add_data_mask_task.sql
```

注意事项：

- 迁移脚本由 Flyway 管理，不要手工修改已执行过的版本。
- 生产环境首次启动已开启 `baseline-on-migrate`，可用于已有非空库。
- 如果新增表结构，应新增新的 `V11__xxx.sql`，不要修改旧脚本。

## 测试环境数据库准备方式

### 单元测试

后端单元测试默认使用 H2 内存数据库：

```text
backend/src/test/resources/schema-h2.sql
```

无需额外准备 MySQL。

### 集成测试

`FullWorkflowIntegrationTest` 默认使用 Testcontainers 自动创建 MySQL 容器。

也可以连接外部 MySQL，通过环境变量指定：

```bash
E2E_MYSQL_URL=jdbc:mysql://127.0.0.1:3306/testdb?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
E2E_MYSQL_USERNAME=test
E2E_MYSQL_PASSWORD=test
E2E_MYSQL_HOST=127.0.0.1
E2E_MYSQL_PORT=3306
E2E_MYSQL_DB=testdb
```

外部测试数据库需要提前创建 `testdb` 和对应账号，业务表由测试代码自动创建。
