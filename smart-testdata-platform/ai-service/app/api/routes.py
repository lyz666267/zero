"""AI 服务 API 路由"""
import asyncio
import json
from fastapi import APIRouter
from pydantic import BaseModel
from typing import Any
from loguru import logger

from app.schemas.generation_plan import GeneratePlanRequest, GeneratePlanResponse
from app.schemas.schema_analysis import SchemaAnalyzeRequest, SchemaAnalyzeResponse
from app.agents.schema_agent import schema_agent
from app.agents.strategy_agent import strategy_agent
from app.agents.tool_agent import tool_agent
from app.chains import generation_chain

router = APIRouter(prefix="/api/ai", tags=["AI"])


# ============================================================
# 请求/响应模型（兼容旧接口）
# ============================================================

class StrategyGenerateRequest(BaseModel):
    """策略生成请求 — 接收已分析的 Schema + 需求"""
    analysis: dict[str, Any]  # SchemaAnalysisResult 的 dict
    requirement: str


class ToolAgentRequest(BaseModel):
    """Tool Agent 请求"""
    requirement: str  # 用户需求描述


# ============================================================
# 路由
# ============================================================

@router.post("/analyze-schema", response_model=SchemaAnalyzeResponse)
async def analyze_schema(request: SchemaAnalyzeRequest):
    """
    Schema 理解 Agent（Phase 5.1）

    输入：完整 Schema JSON（database + tables + columns）
    输出：字段语义标签 + 敏感字段标识 + 外键推断 + 生成器推荐

    两种模式：
    - LLM 模式：调用 DeepSeek 进行深度语义分析
    - Mock 模式：基于规则引擎进行字段名模式匹配（无 API Key 时自动降级）

    请求示例：
    ```json
    {
      "database": "my_shop",
      "dbType": "MySQL",
      "tables": [
        {
          "tableName": "users",
          "comment": "用户表",
          "columns": [
            {"name": "id", "type": "INT", "nullable": false, "primaryKey": true},
            {"name": "username", "type": "VARCHAR(50)", "nullable": false},
            {"name": "phone", "type": "VARCHAR(20)", "nullable": true},
            {"name": "email", "type": "VARCHAR(100)", "nullable": true}
          ]
        }
      ]
    }
    ```
    """
    return await schema_agent.analyze(request)


@router.post("/generate-plan", response_model=GeneratePlanResponse)
async def generate_plan(request: GeneratePlanRequest):
    """
    测试数据生成规划（Phase 5.3 — GenerationChain）

    内部流水线：
        SchemaAgent.analyze() → SchemaAnalysisResult
            → StrategyAgent.generate() → GenerationPlan

    输入：
        - schema: 数据库 Schema JSON（来自 Phase 3.1 Schema 分析结果）
        - requirement: 用户需求描述，如"生成1000条用户数据"

    输出：
        - plan: 结构化生成计划（表名、行数、字段生成器映射）
        - mock: 是否使用了 Mock 模式（无 LLM API Key 时）

    每个步骤独立支持 LLM + Mock 双模式，任一步骤失败自动降级。
    """
    return await generation_chain.run(request)


@router.post("/schema/analyze")
async def analyze_schema_v2(request: SchemaAnalyzeRequest):
    """
    [别名] Schema 分析 — POST /api/ai/schema/analyze
    与 /api/ai/analyze-schema 功能相同，路径风格不同
    """
    return await schema_agent.analyze(request)


@router.post("/generate-strategy", response_model=GeneratePlanResponse)
async def generate_strategy(request: StrategyGenerateRequest):
    """
    策略生成（Phase 5.3 — StrategyAgent）

    跳过 Schema 分析，直接基于已有的分析结果生成策略。

    输入：
        - analysis: SchemaAgent 分析结果 dict
        - requirement: 用户需求描述

    输出：
        - plan: 结构化生成计划
        - mock: 是否使用了 Mock 模式

    适用场景：
        1. 已经调用过 /analyze-schema，只需重新生成计划
        2. 不同的需求（行数不同）重用在同一个 Schema 分析结果
    """
    from app.schemas.schema_analysis import SchemaAnalysisResult

    try:
        analysis = SchemaAnalysisResult(**request.analysis)
    except Exception as e:
        return GeneratePlanResponse(
            success=False,
            error=f"分析结果格式无效: {str(e)}",
            mock=False,
        )

    return await generation_chain.run_with_analysis(analysis, request.requirement)


@router.post("/tool-agent")
async def tool_agent_endpoint(request: ToolAgentRequest):
    """
    Tool Calling Agent（Phase 5.4）+ AgentTrace（Phase 8.2）

    基于 ReAct 模式的工具调用 Agent：
    1. 用户输入需求描述
    2. LLM 决定调用哪些工具（get_schema / get_sample / get_relations）
    3. Agent 自动执行工具并循环至完成
    4. 返回最终回答 + 工具调用历史 + Agent 执行轨迹

    请求示例：
    ```json
    {
      "requirement": "分析 my_shop 数据库的 Schema 结构，查看有哪些表和字段"
    }
    ```

    响应示例：
    ```json
    {
      "success": true,
      "final_answer": "数据库包含 3 张表...",
      "tool_calls": [...],
      "rounds": 2,
      "mock": false,
      "trace": {
        "agent_name": "ToolAgent",
        "total_time_ms": 1234,
        "steps": [
          {
            "step_number": 1,
            "step_type": "TOOL_CALL",
            "action": "调用工具: get_schema",
            "tool_name": "get_schema",
            "status": "SUCCESS",
            "execution_time": 120,
            "input_data": "{\"database\": \"my_shop\"}",
            "output_data": "tableCount=3"
          }
        ]
      }
    }
    ```
    """
    result = await tool_agent.run(request.requirement)
    return result


# ============================================================
# Phase 8.1 — LLM 敏感字段检测
# ============================================================

class DetectSensitiveRequest(BaseModel):
    """敏感字段检测请求"""
    columns: list[dict[str, Any]]  # [{columnName, columnType, columnComment}, ...]
    sampleValues: dict[str, list[Any]]  # {columnName: [value1, value2, ...]}


class DetectedFieldResult(BaseModel):
    """单个字段的检测结果"""
    columnName: str
    type: str  # PHONE, EMAIL, ID_CARD, NAME, ADDRESS, BANK_CARD
    confidence: float
    reason: str


class DetectSensitiveResponse(BaseModel):
    """敏感字段检测响应"""
    success: bool = True
    fields: list[DetectedFieldResult] = []
    mock: bool = False


DETECT_SENSITIVE_SYSTEM_PROMPT = """你是一个数据安全专家，负责识别数据库字段中可能包含的敏感信息。

## 任务

根据提供的字段名、数据类型、注释和样本值，判断哪些字段包含个人隐私数据。

## 敏感类型

| 类型 | 说明 | 典型字段名 |
|------|------|-----------|
| PHONE | 手机/电话号码 | phone, mobile, tel, telephone |
| EMAIL | 电子邮箱 | email, mail, e_mail |
| ID_CARD | 身份证号 | id_card, card_no, idcard |
| NAME | 姓名/用户名 | name, username, real_name |
| ADDRESS | 地址 | address, addr, location, city |
| BANK_CARD | 银行卡号 | bank_card, credit_card, card_number |

## 输出格式

严格返回以下 JSON 格式，不要包含任何其他文字：

```json
{
  "fields": [
    {
      "columnName": "phone",
      "type": "PHONE",
      "confidence": 0.95,
      "reason": "字段名 'phone' 包含手机关键词，样本值 '13812345678' 符合手机号格式"
    }
  ]
}
```

## 准则

1. 综合字段名、注释和样本值进行判断
2. confidence 取值范围 0.0 ~ 1.0
3. 字段名明确匹配时 confidence ≥ 0.85
4. 仅凭注释猜测时 confidence ≤ 0.70
5. 无法确定的字段不要输出
6. reason 必须使用中文简要说明判断依据
"""


@router.post("/detect-sensitive", response_model=DetectSensitiveResponse)
async def detect_sensitive(request: DetectSensitiveRequest):
    """
    LLM 敏感字段检测（Phase 8.1）

    使用 LLM 语义分析字段名、注释和样本值，识别可能包含隐私数据的字段。

    请求示例：
    ```json
    {
      "columns": [
        {"columnName": "phone", "columnType": "varchar(20)", "columnComment": "用户手机号"},
        {"columnName": "nickname", "columnType": "varchar(50)", "columnComment": "昵称"}
      ],
      "sampleValues": {
        "phone": ["13812345678", "13900001111"],
        "nickname": ["小明", "小红"]
      }
    }
    ```

    响应示例：
    ```json
    {
      "success": true,
      "fields": [
        {"columnName": "phone", "type": "PHONE", "confidence": 0.95, "reason": "..."}
      ],
      "mock": false
    }
    ```
    """
    from app.llm import llm_router, RouterExhaustedError, LLMProviderError

    # 构建 prompt
    columns_desc = _build_columns_description(request.columns, request.sampleValues)
    messages = [
        {"role": "system", "content": DETECT_SENSITIVE_SYSTEM_PROMPT},
        {"role": "user", "content": columns_desc},
    ]

    try:
        if not llm_router.has_llm:
            logger.info("LLM 不可用，返回空结果 (mock)")
            return DetectSensitiveResponse(success=True, fields=[], mock=True)

        raw = await llm_router.chat(messages, temperature=0.1, max_tokens=2048)
        json_str = llm_router.extract_json(raw)
        data = json.loads(json_str)

        fields_data = data.get("fields", [])
        fields = [
            DetectedFieldResult(
                columnName=f.get("columnName", ""),
                type=f.get("type", "UNKNOWN"),
                confidence=f.get("confidence", 0.0),
                reason=f.get("reason", ""),
            )
            for f in fields_data
        ]

        # 禁止 success=True + 空结果：LLM 返回空结果视为 LLM 能力不足，降级为 mock
        if not fields:
            logger.warning("LLM detect-sensitive: 返回空结果，降级为 mock")
            return DetectSensitiveResponse(success=True, fields=[], mock=True)

        logger.info(f"LLM detect-sensitive: {len(request.columns)} 列 → {len(fields)} 个敏感字段")
        return DetectSensitiveResponse(success=True, fields=fields, mock=False)

    except RouterExhaustedError:
        logger.warning("LLM detect-sensitive: 所有模型耗尽，返回 mock")
        return DetectSensitiveResponse(success=True, fields=[], mock=True)
    except (LLMProviderError, json.JSONDecodeError, asyncio.TimeoutError) as e:
        logger.warning(f"LLM detect-sensitive 可恢复错误，返回 mock: {e}")
        return DetectSensitiveResponse(success=True, fields=[], mock=True)
    except Exception as e:
        # 不可恢复的系统异常
        logger.error(f"LLM detect-sensitive 系统异常: {e}")
        return DetectSensitiveResponse(success=False, fields=[], mock=False)


def _build_columns_description(
    columns: list[dict[str, Any]],
    sample_values: dict[str, list[Any]],
) -> str:
    """构建发送给 LLM 的字段描述文本"""
    lines = ["请分析以下数据库字段是否包含敏感信息：\n"]
    lines.append("| 字段名 | 数据类型 | 注释 | 样本值 |")
    lines.append("|--------|----------|------|--------|")

    for col in columns:
        name = col.get("columnName", "")
        col_type = col.get("columnType", "")
        comment = col.get("columnComment", "")
        samples = sample_values.get(name, [])
        samples_str = ", ".join(str(v) for v in samples[:3]) if samples else "（无样本）"
        lines.append(f"| {name} | {col_type} | {comment} | {samples_str} |")

    lines.append("\n请返回 JSON 格式的检测结果。")
    return "\n".join(lines)
