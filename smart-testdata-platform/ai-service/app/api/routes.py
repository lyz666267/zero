"""AI 服务 API 路由"""
from fastapi import APIRouter
from pydantic import BaseModel
from typing import Any

from app.schemas.generation_plan import GeneratePlanRequest, GeneratePlanResponse
from app.agents.testdata_agent import testdata_agent

router = APIRouter(prefix="/api/ai", tags=["AI"])


# ============================================================
# 请求/响应模型
# ============================================================

class SchemaAnalyzeRequest(BaseModel):
    """Schema 分析请求"""
    database: str
    db_type: str = "MySQL"
    tables: list[dict] = []


class SchemaAnalyzeResponse(BaseModel):
    """Schema 分析响应（预留）"""
    status: str
    database: str
    analyzed_tables: list[dict] = []


# ============================================================
# 路由
# ============================================================

@router.post("/schema/analyze")
async def analyze_schema(request: SchemaAnalyzeRequest):
    """
    Schema 理解 Agent（Phase 5 实现）
    输入：完整 Schema JSON（database + tables + columns）
    输出：字段语义标签 + 敏感字段标识
    """
    # TODO: 阶段 5 — 调用 LLM 进行 Schema 语义分析
    return SchemaAnalyzeResponse(
        status="not_implemented",
        database=request.database,
        analyzed_tables=[],
    )


@router.post("/generate-plan", response_model=GeneratePlanResponse)
async def generate_plan(request: GeneratePlanRequest):
    """
    测试数据生成规划 Agent（Phase 3.2）

    输入：
        - schema: 数据库 Schema JSON（来自 Phase 3.1 Schema 分析结果）
        - requirement: 用户需求描述，如"生成1000条用户数据"

    输出：
        - plan: 结构化生成计划（表名、行数、字段生成器映射）
        - mock: 是否使用了 Mock 模式（无 LLM API Key 时）

    流程：
        Schema + 需求 → TestDataAgent → GenerationPlan JSON
    """
    return await testdata_agent.generate_plan(request)


@router.post("/analyze-schema")
async def analyze_schema_legacy(request: dict):
    """
    [兼容] 旧接口 — Agent 1: Schema 理解
    """
    # TODO: 阶段 5 实现
    return {"status": "not_implemented", "message": "阶段 5 实现"}


@router.post("/generate-strategy")
async def generate_strategy(request: dict):
    """
    Agent 2: 数据生成策略
    输入：Schema 理解结果 + 目标行数
    输出：generation_plan.json
    """
    # TODO: 阶段 5 实现
    return {"status": "not_implemented", "message": "阶段 5 实现"}
