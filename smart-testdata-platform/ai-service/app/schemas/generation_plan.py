"""
生成计划 Pydantic 模型

定义 LLM Agent 输出的测试数据生成计划结构
"""
from pydantic import BaseModel, Field
from typing import Optional, Any


class FieldRange(BaseModel):
    """数值字段的取值范围"""
    min: Optional[int] = None
    max: Optional[int] = None


class ForeignKeyInfo(BaseModel):
    """外键信息"""
    table: str
    column: str = "id"


class FieldPlan(BaseModel):
    """单个字段的生成计划"""
    name: str = Field(..., description="字段名")
    generator: str = Field(..., description="生成器名称，如 faker.name、random.integer")
    range: Optional[FieldRange] = Field(None, description="数值字段的取值范围")
    params: Optional[dict[str, Any]] = Field(None, description="生成器的额外参数")
    foreignKey: Optional[ForeignKeyInfo] = Field(None, description="外键信息")


class TablePlan(BaseModel):
    """单表的生成计划"""
    table: str = Field(..., description="目标表名")
    count: int = Field(..., description="生成数据行数")
    fields: list[FieldPlan] = Field(default_factory=list, description="字段生成计划列表")


class GenerationPlan(BaseModel):
    """完整生成计划 — Agent 输出"""
    taskName: str = Field(..., description="任务名称")
    tables: list[TablePlan] = Field(default_factory=list, description="各表的生成计划")

    # 兼容单表场景
    table: Optional[str] = Field(None, description="目标表名（单表兼容）")
    count: Optional[int] = Field(None, description="生成行数（单表兼容）")
    fields: Optional[list[FieldPlan]] = Field(None, description="字段计划（单表兼容）")


class GeneratePlanRequest(BaseModel):
    """生成计划请求"""

    schema_data: dict[str, Any] = Field(..., description="数据库 Schema JSON")
    requirement: str = Field(..., description="用户需求描述，如：生成1000条用户数据")


class GeneratePlanResponse(BaseModel):
    """生成计划响应"""
    success: bool = True
    plan: Optional[GenerationPlan] = None
    error: Optional[str] = None
    mock: bool = False  # 是否使用了 mock 结果


def default_enum_values(field_name: str | None) -> list[str]:
    """为枚举生成器提供默认值，避免 AI 返回缺少 params.values"""
    name = (field_name or "").lower().strip()
    if name in ("status", "state", "type", "category"):
        return ["ACTIVE", "INACTIVE", "PENDING"]
    if name in ("gender", "sex"):
        return ["MALE", "FEMALE"]
    if name == "role":
        return ["USER", "ADMIN", "MANAGER"]
    if name == "level":
        return ["LOW", "MEDIUM", "HIGH"]
    return ["VALUE_1", "VALUE_2", "VALUE_3"]
