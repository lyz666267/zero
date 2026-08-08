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

## Demo 演示数据库初始化

### 自动初始化（推荐）

Docker Compose 启动时会自动挂载 `01-demo-ecommerce.sql` 到 MySQL 容器的 `/docker-entrypoint-initdb.d`，自动创建 Demo 数据库。

```bash
docker compose up -d mysql
```

### Demo 数据库信息

- 数据库名：`smart_test_demo`
- 包含 5 张电商业务表：`categories`、`users`、`products`、`orders`、`order_items`
- 含完整外键关系（通过 RelationGraph 可视化展示）
- 预置样例数据：6 个分类、3 个用户、7 个商品、4 个订单、6 个订单明细

### 表关系图

```
categories (1) ──→ (N) products
users      (1) ──→ (N) orders
orders     (1) ──→ (N) order_items
products   (1) ──→ (N) order_items
```

### 手动初始化

如果使用本地 MySQL，手动执行初始化脚本：

```bash
mysql -u root -p < docs/mysql-init/01-demo-ecommerce.sql
```

### Demo 演示数据源配置

在平台中添加 Demo 数据源时，填写以下参数：

| 参数 | 值 |
|------|-----|
| 名称 | Demo 电商数据库 |
| 数据库类型 | MySQL |
| 主机 | localhost（或 mysql，Docker 内） |
| 端口 | 3306 |
| 数据库名 | smart_test_demo |
| 用户名 | platform |
| 密码 | platform123 |
