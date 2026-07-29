"""AI 服务 API 路由"""
from fastapi import APIRouter
from pydantic import BaseModel
from typing import Any

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
