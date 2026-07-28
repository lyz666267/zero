# Findings & Decisions

## Requirements
- 毕业设计三合一目标：毕业答辩通过 + 简历有含金量 + 对就业有帮助
- 选题：LLM Agent + 数据安全（测试数据生成 + 隐私脱敏）
- 技术栈：Spring Boot 3.x + Vue 3 + Python FastAPI + LangChain + MySQL
- LLM：DeepSeek 为主模型，Qwen 为备用模型（OpenAI 兼容协议）
- 核心流程：Java 分析表结构 → Python Agent 返回 generation_plan.json → Java 执行生成/脱敏

## Research Findings
- Spring Boot 3.x 要求 JDK 17+
- com.github.javafaker 是 Java 端数据生成的核心依赖
- Flyway 比 Liquibase 更轻量，适合本项目规模
- LangChain 在本项目中仅作为 LLM 调用框架，不涉及复杂编排
- DeepSeek 和 Qwen 都兼容 OpenAI API 协议，可统一接口调用
- MyBatis-Plus 3.5.x 支持 Spring Boot 3.x
- Element Plus 2.x 支持 Vue 3

## Technical Decisions
| Decision | Rationale |
|----------|-----------|
| 使用 Flyway 而非 Liquibase | 轻量、SQL 原生、学习成本低 |
| Java Faker 生成数据而非 LLM 生成 | 避免幻觉、节省 Token、确定性输出 |
| 数据库轮询优先 WebSocket | 先保证核心功能可用，WebSocket 作为优化项 |
| 不做 RBAC/多租户 | 本科毕设范围，答辩不加分 |
| 规则引擎自实现，不引入 Drools | 避免复杂框架，代码量可控 |
| 密码 AES 加密存储 | 数据源密码不能明文存库 |

## Issues Encountered
| Issue | Resolution |
|-------|------------|
|       |            |

## Resources
- Spring Boot 3.x 文档: https://docs.spring.io/spring-boot/documentation.html
- MyBatis-Plus 文档: https://baomidou.com/
- FastAPI 文档: https://fastapi.tiangolo.com/
- LangChain 文档: https://python.langchain.com/docs/
- Element Plus 文档: https://element-plus.org/
- DeepSeek API: https://platform.deepseek.com/api-docs/
- 阿里云百炼 Qwen API: https://help.aliyun.com/zh/model-studio/
- Flyway 文档: https://flywaydb.org/documentation/
