# 测试说明

## Java 测试

Backend 使用：

- JUnit 5
- MockMvc
- Integration Test
- JaCoCo 覆盖率报告

```bash
cd backend
mvn test
```

覆盖率报告：

```text
backend/target/site/jacoco/index.html
```

## Python 测试

AI Service 使用：

- pytest
- FastAPI TestClient
- pytest-cov 覆盖率

```bash
cd ai-service
python -m pytest tests/
```

覆盖率报告：

```text
ai-service/htmlcov/index.html
```

## 前端测试

Frontend 使用 Vitest。

```bash
cd frontend
npm test
```

生产构建：

```bash
cd frontend
npm run build
```
