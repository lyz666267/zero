"""
生成编排链 — GenerationChain

编排 Schema 分析 → 策略生成的完整流水线：

    raw_schema (dict) + requirement
        │
        ▼
    SchemaAgent.analyze()
        │
        ▼
    SchemaAnalysisResult (语义标签 + 敏感检测 + FK + 生成器推荐)
        │
        ▼
    StrategyAgent.generate()
        │
        ▼
    GenerationPlan (可执行的表/字段/生成器映射)

特点：
- 两个 Agent 各自独立支持 LLM + Mock 双模式
- 任一步骤失败都有降级策略
- 也可直接传入已分析结果，跳过 SchemaAgent
"""
from loguru import logger

from app.schemas.generation_plan import GeneratePlanRequest, GeneratePlanResponse
from app.schemas.schema_analysis import (
    SchemaAnalyzeRequest, SchemaAnalyzeResponse,
    SchemaAnalysisResult, TableInfo, ColumnInfo,
)
from app.agents.schema_agent import schema_agent
from app.agents.strategy_agent import strategy_agent


class GenerationChain:
    """
    生成编排链

    使用方式：
        chain = GenerationChain()
        response = await chain.run(schema_dict, requirement)
    """

    def __init__(self):
        logger.info("GenerationChain 初始化: SchemaAgent → StrategyAgent")

    async def run(
        self,
        request: GeneratePlanRequest,
    ) -> GeneratePlanResponse:
        """
        执行完整生成流水线

        Args:
            request: GeneratePlanRequest (schema dict + requirement)

        Returns:
            GeneratePlanResponse 包含 GenerationPlan
        """
        schema_dict = request.schema_data
        requirement = request.requirement

        logger.info(
            f"GenerationChain 开始: requirement='{requirement[:50]}...', "
            f"tables={len(schema_dict.get('tables', []))}"
        )

        # ── Step 1: Schema 分析 ──
        schema_response = await self._analyze_schema(schema_dict)
        if not schema_response.success or not schema_response.result:
            return GeneratePlanResponse(
                success=False,
                error=f"Schema 分析失败: {schema_response.error or '未知错误'}",
                mock=False,
            )

        analysis_result = schema_response.result
        logger.info(
            f"Step 1/2 Schema 分析完成: {analysis_result.summary.totalTables} 表, "
            f"{analysis_result.summary.totalColumns} 字段, "
            f"mock={schema_response.mock}"
        )

        # ── Step 2: 策略生成 ──
        plan_response = await strategy_agent.generate(analysis_result, requirement)

        logger.info(
            f"Step 2/2 策略生成完成: success={plan_response.success}, "
            f"mock={plan_response.mock}"
        )

        return plan_response

    async def run_with_analysis(
        self,
        analysis: SchemaAnalysisResult,
        requirement: str,
    ) -> GeneratePlanResponse:
        """
        跳过 Schema 分析，直接用已有分析结果生成策略

        Args:
            analysis: 已有的 SchemaAnalysisResult
            requirement: 用户需求

        Returns:
            GeneratePlanResponse
        """
        logger.info(
            f"GenerationChain (skip-analysis): "
            f"tables={analysis.summary.totalTables}, "
            f"requirement='{requirement[:50]}...'"
        )
        return await strategy_agent.generate(analysis, requirement)

    async def _analyze_schema(
        self, schema_dict: dict
    ) -> SchemaAnalyzeResponse:
        """
        将原始 schema dict 转为 SchemaAnalyzeRequest 并调用 SchemaAgent
        """
        try:
            # 构建 SchemaAnalyzeRequest
            database = schema_dict.get("database", "unknown")
            db_type = schema_dict.get("dbType", "MySQL")

            tables = []
            for t in schema_dict.get("tables", []):
                columns = []
                for c in t.get("columns", []):
                    columns.append(ColumnInfo(
                        name=c.get("name", ""),
                        type=c.get("type", ""),
                        nullable=c.get("nullable", True),
                        defaultValue=c.get("defaultValue"),
                        comment=c.get("comment"),
                        primaryKey=c.get("primaryKey", c.get("primary", False)),
                    ))
                tables.append(TableInfo(
                    tableName=t.get("tableName", ""),
                    comment=t.get("comment"),
                    columns=columns,
                ))

            request = SchemaAnalyzeRequest(
                database=database,
                dbType=db_type,
                tables=tables,
            )

            return await schema_agent.analyze(request)

        except (KeyError, TypeError, ValueError) as e:
            logger.error(f"Schema 分析请求构建失败: {e}")
            return SchemaAnalyzeResponse(
                success=False,
                error=f"Schema 分析请求构建失败: {str(e)}",
                mock=False,
            )


# 单例
generation_chain = GenerationChain()
