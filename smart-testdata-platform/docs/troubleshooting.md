# 常见问题排查

## MySQL 无法启动

检查 Docker Compose：

```bash
docker compose ps
docker compose logs mysql
```

常见原因：

- 3307 端口被占用
- MySQL 数据卷损坏

## 后端启动失败

检查环境变量：

```bash
AES_KEY
JWT_SECRET
```

后端读取不到环境变量时会启动失败。

## AI 服务无法访问

确认 AI 服务已启动：

```bash
curl http://localhost:8000/health
```

如果未配置 DeepSeek / Qwen Key，系统会降级 Mock 模式，演示流程仍然可用。

## 前端 API 返回 401

说明未登录或 Token 过期。

重新登录后，前端会重新写入 Token。

## Testcontainers 启动慢

本机 MySQL 容器首次初始化可能较慢。

可以改用外部 MySQL：

```bash
E2E_MYSQL_URL=jdbc:mysql://127.0.0.1:3306/testdb?...
E2E_MYSQL_USERNAME=test
E2E_MYSQL_PASSWORD=test
E2E_MYSQL_HOST=127.0.0.1
E2E_MYSQL_PORT=3306
E2E_MYSQL_DB=testdb
```

## pytest 覆盖率门槛失败

覆盖率门槛为 70%。

查看报告：

```bash
python -m pytest tests/
```

报告位置：

```text
ai-service/htmlcov/index.html
```

## 前端构建失败

先检查依赖：

```bash
cd frontend
npm install
npm run build
```

## 端口冲突

常用端口：

- 前端：5173
- 后端：8088
- AI 服务：8000
- MySQL：3307
- Nginx：80

如果端口被占用，修改 `docker-compose.yml` 或本地启动命令。
