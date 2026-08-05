# 智能测试数据平台 — 前端

基于 Vue 3 + Vite + Element Plus 构建的智能测试数据生成与隐私脱敏平台前端。

## CI 状态

[![CI Test Pipeline](https://github.com/lyz666267/zero/actions/workflows/test.yml/badge.svg)](https://github.com/lyz666267/zero/actions/workflows/test.yml)

CI 流水线包含三个独立 Job，每个 Job 自动生成覆盖率报告并上传为 Artifact：

| Job | 技术栈 | 验证内容 | 覆盖率工具 |
|-----|--------|----------|------------|
| **Backend** | Spring Boot 3.3 + Maven + JDK 17 | `mvn test` 全部后端单元测试 | JaCoCo → `target/site/jacoco/` |
| **Python AI** | FastAPI + Pytest + Python 3.12 | `pytest tests -v --cov=app` AI 服务测试 | pytest-cov → `htmlcov/` |
| **Frontend** | Vue 3 + Vite + Vitest + Node 20 | `npm run build` + `npm run test` | — |

### 测试覆盖率

#### Backend（Java — JaCoCo）

```bash
cd backend
mvn test -B                                    # 自动执行 JaCoCo 插桩 + 生成报告
open target/site/jacoco/index.html             # 查看 HTML 覆盖率报告
```

JaCoCo 配置在 `backend/pom.xml`，绑定到 `test` 阶段自动运行。报告包含指令级、分支级、行级、方法级、类级覆盖率。

#### Python AI Service（pytest-cov）

```bash
cd ai-service
pytest tests -v --cov=app --cov-report=html --cov-report=term-missing
open htmlcov/index.html                        # 查看 HTML 覆盖率报告
```

`pytest.ini` 已预配置 `--cov=app --cov-report=html --cov-report=term-missing`，直接运行 `pytest` 即可生成报告。

#### CI Artifacts

每次 CI 运行后，覆盖率报告可从 GitHub Actions → Artifacts 下载：
- `jacoco-coverage-report` — Java 后端覆盖率
- `pytest-coverage-report` — Python AI 服务覆盖率

## 技术栈

- **框架**: Vue 3 (Composition API + `<script setup>`)
- **构建**: Vite 8
- **UI 库**: Element Plus 2.x
- **状态管理**: Pinia 4
- **路由**: Vue Router 4
- **HTTP**: Axios
- **图表**: ECharts 6
- **测试**: Vitest + @vue/test-utils

## 本地开发

```bash
npm install        # 安装依赖
npm run dev        # 启动开发服务器
npm run build      # 生产构建
npm run test       # 运行测试
```
