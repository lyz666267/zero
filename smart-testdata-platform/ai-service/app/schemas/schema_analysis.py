"""
Schema 分析 — Pydantic 模型

定义 Schema 分析请求、响应及内部数据结构的验证规则。
"""
from pydantic import BaseModel, Field
from typing import Optional, Any


# ============================================================
# 输入模型
# ============================================================

class ColumnInfo(BaseModel):
    """输入：单个字段信息"""
    name: str = Field(..., description="字段名")
    type: str = Field(..., description="字段类型，如 VARCHAR(50)、INT")
    nullable: bool = Field(default=True, description="是否可为空")
    defaultValue: Optional[str] = Field(None, description="默认值")
    comment: Optional[str] = Field(None, description="字段注释")
    primaryKey: bool = Field(default=False, description="是否为主键")


class TableInfo(BaseModel):
    """输入：单表信息"""
    tableName: str = Field(..., description="表名")
    comment: Optional[str] = Field(None, description="表注释")
    columns: list[ColumnInfo] = Field(default_factory=list, description="表字段列表")


class SchemaAnalyzeRequest(BaseModel):
    """Schema 分析请求"""
    database: str = Field(..., description="数据库名")
    dbType: str = Field(default="MySQL", description="数据库类型")
    tables: list[TableInfo] = Field(default_factory=list, description="待分析的表列表")


# ============================================================
# 输出模型
# ============================================================

class SensitiveDetection(BaseModel):
    """敏感字段检测结果"""
    sensitive: bool = Field(default=False, description="是否包含敏感信息")
    sensitiveType: str = Field(default="NONE", description="敏感类型：PHONE|EMAIL|ID_CARD|NAME|ADDRESS|BANK_CARD|NONE")
    confidence: float = Field(default=0.0, ge=0.0, le=1.0, description="置信度 0.0~1.0")


class InferredForeignKey(BaseModel):
    """推断的外键关系"""
    referencedTable: str = Field(..., description="引用的目标表名")
    referencedColumn: str = Field(default="id", description="引用的目标字段名")
    confidence: float = Field(default=0.7, ge=0.0, le=1.0, description="推断置信度")


class GeneratorSuggestion(BaseModel):
    """生成器推荐"""
    generator: str = Field(..., description="推荐生成器名，如 faker.name")
    reason: str = Field(default="", description="推荐理由")
    params: dict[str, Any] = Field(default_factory=dict, description="生成器参数")


class AnalyzedColumn(BaseModel):
    """输出：分析后的字段"""
    name: str = Field(..., description="字段名")
    type: str = Field(..., description="字段类型")
    nullable: bool = Field(default=True)
    defaultValue: Optional[str] = Field(None)
    comment: Optional[str] = Field(None)
    semanticLabel: str = Field(default="UNKNOWN", description="语义标签")
    sensitiveDetection: SensitiveDetection = Field(default_factory=SensitiveDetection)
    inferredForeignKey: Optional[InferredForeignKey] = Field(None, description="推断的外键关系")
    generatorSuggestion: Optional[GeneratorSuggestion] = Field(None, description="推荐生成器")


class AnalyzedTable(BaseModel):
    """输出：分析后的表"""
    tableName: str = Field(..., description="表名")
    tableComment: Optional[str] = Field(None, description="表注释")
    primaryKey: list[str] = Field(default_factory=list, description="主键字段列表")
    rowEstimate: int = Field(default=100, description="建议生成行数")
    columns: list[AnalyzedColumn] = Field(default_factory=list, description="分析后的字段列表")


class AnalysisSummary(BaseModel):
    """分析摘要"""
    totalTables: int = Field(default=0)
    totalColumns: int = Field(default=0)
    sensitiveColumns: int = Field(default=0)
    foreignKeyRelations: int = Field(default=0)
    recommendations: list[str] = Field(default_factory=list, description="分析建议")


class SchemaAnalysisResult(BaseModel):
    """完整的 Schema 分析结果"""
    database: str = Field(..., description="数据库名")
    dbType: str = Field(default="MySQL")
    tables: list[AnalyzedTable] = Field(default_factory=list, description="分析后的表列表")
    summary: AnalysisSummary = Field(default_factory=AnalysisSummary)


# ============================================================
# 请求/响应包装
# ============================================================

class SchemaAnalyzeResponse(BaseModel):
    """Schema 分析 API 响应"""
    success: bool = Field(default=True)
    result: Optional[SchemaAnalysisResult] = Field(None, description="分析结果")
    error: Optional[str] = Field(None, description="错误信息")
    mock: bool = Field(default=False, description="是否使用了 Mock/规则引擎降级")
