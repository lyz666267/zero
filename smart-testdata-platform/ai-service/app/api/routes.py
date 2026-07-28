"""AI 服务 API 路由"""
from fastapi import APIRouter

router = APIRouter(prefix="/api/ai", tags=["AI"])


@router.post("/analyze-schema")
async def analyze_schema(request: dict):
    """
    Agent 1: Schema 理解
    输入：Schema JSON（含采样数据）
    输出：字段语义标签 + 敏感字段标识
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
