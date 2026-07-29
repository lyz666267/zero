# Task Plan: 基于大模型 Agent 的智能测试数据生成与隐私脱敏平台

## Goal
完成毕业设计项目——基于 LLM Agent 的智能测试数据生成与隐私脱敏平台，实现 Spring Boot + Vue 3 + Python FastAPI 全栈系统，通过毕业答辩并产出有含金量的简历项目。

## Current Phase
Phase 3.2 ✅ | 数据采样 + Schema 缓存（暂未开始）

## Phases

### Phase 1: 项目初始化与环境搭建（1-2天）
- [x] 创建 Maven 项目（Spring Boot 3.x + MyBatis-Plus）— ✅ 编译通过 (Maven 3.9.16)
- [x] 创建 Python 项目（FastAPI + LangChain）— ✅ 完整骨架 + 依赖安装完成
- [x] 创建 Vue 项目（Vite + Element Plus）— ✅ 脚手架 + npm install 已完成
- [x] 平台数据库初始化（Flyway 建表）— ✅ 10 张表创建成功
- [x] Docker Compose（MySQL + Java + Python + Nginx）— ✅ 完成
- [x] 各服务能启动，互相 ping 通 — ✅ Spring Boot 8088 + Python 8000 连通
- **Status:** ✅ complete

### Phase 2: 认证 + 项目管理基础 CRUD（1天）
- [x] JWT 登录/注册接口 — ✅ 注册+登录返回 Token
- [x] 项目 CRUD 接口 — ✅ 创建/列表/详情/更新/删除 + 分页
- [x] 前端：Login.vue 登录页 + Dashboard.vue 首页仪表盘（项目数/任务数/成功率统计）
- [x] 前端：ProjectList.vue 项目列表页 — ✅ 含创建/编辑/删除对话框
- [x] 验证：能注册、登录、创建项目 — ✅ 全 API 测试通过
- **Status:** ✅ complete

### Phase 3: 数据源管理 + Schema 分析（3-4天）
- [x] 3.1 数据源配置 CRUD — ✅ 创建/列表/详情/更新/删除
- [x] 3.1 测试数据库连接 — ✅ POST /api/datasource/test
- [x] 3.1 Schema 读取（information_schema）— ✅ getSchema() 返回表+列+主键+外键
- [x] 3.1 AES 密码加密存储 — ✅ AesUtil
- [x] 3.1 Flyway V2 迁移 — ✅ datasource 表添加 db_type
- [x] 3.1 前端 DatasourceManage.vue — ✅ 项目选择+列表+测试+Schema查看
- [x] 3.1 AI 服务预留接口 — ✅ POST /api/ai/schema/analyze
- [x] 3.2 LLM Agent 测试数据生成规划器 — ✅ DeepSeek + Mock 规则引擎
- [ ] 3.3 数据采样（每字段取前 5 条实际数据）
- [ ] 3.3 Schema 缓存（存入 schema_table + schema_column）
- [ ] 3.4 关系分析器（外键依赖关系图）
- **Status:** 3.1 ✅ | 3.2 ✅ | 3.3-3.4 pending

### Phase 4: 规则引擎（2天）
- **Status:** pending

### Phase 5: Python AI 服务 + 模型路由（3-4天）
- **Status:** pending

### Phase 6: 数据生成引擎（3-4天）
- **Status:** pending

### Phase 7: 任务调度 + 进度展示（1-2天）
- **Status:** pending

### Phase 8: 隐私脱敏（2天）
- **Status:** pending

### Phase 9: 前端完善 + 联调（3-4天）
- **Status:** pending

### Phase 10: 论文 + 答辩准备（5-7天）
- **Status:** pending

### Phase 11: 简历包装 + 上线部署（2-3天）
- **Status:** pending

## Key Questions
1. DeepSeek API Key 是否已申请？
2. Qwen API Key（阿里云百炼）是否已申请？
3. 本地开发环境：JDK 17+、Python 3.10+、Node.js 18+、Docker 是否已安装？

## Decisions Made
| Decision | Rationale |
|----------|-----------|
| Spring Boot 3.x + JDK 17 | 长期支持版本，生态成熟 |
| MyBatis-Plus | 简化 CRUD，代码生成器提效 |
| FastAPI + LangChain | 异步高性能 + LLM 调用框架 |
| Vue 3 + Vite + Element Plus | 生态活跃，组件丰富 |
| Docker Compose 管理中间件 | MySQL/Nginx 容器化，开发环境一致 |
| Flyway 数据库迁移 | 版本化管理 DDL，可追溯 |

## Errors Encountered
| Error | Attempt | Resolution |
|-------|---------|------------|
|       |         |            |

## Notes
- 所有命令等待用户说"执行"后再运行
- 每个子任务完成后验证再进入下一步
