"""
外键关系工具

获取数据库中的外键关系（显式外键约束 + 推断关系）。
用于：
1. 确定表之间的依赖关系（生成数据时的顺序）
2. 确定哪些字段需要引用其他表的主键
3. 支持显式外键（information_schema.KEY_COLUMN_USAGE）和
   命名约定推断（xxx_id → xxx 表）
"""
from typing import Any
from loguru import logger

from app.tools.base import Tool


class RelationTool(Tool):
    """
    获取外键关系

    输入：database 名称（可选 table 过滤）
    输出：外键关系列表（源表、源字段 → 目标表、目标字段）
    """

    def __init__(self):
        # 模拟外键关系缓存
        self._relation_cache: dict[str, list[dict[str, Any]]] = {}

    @property
    def name(self) -> str:
        return "get_relations"

    @property
    def description(self) -> str:
        return (
            "获取数据库中的外键关系，包括显式外键约束和基于命名约定推断的关系。"
            "返回每个外键的源表、源字段、目标表、目标字段信息。"
            "当需要确定表之间的依赖关系或生成顺序时调用此工具。"
        )

    @property
    def parameters_schema(self) -> dict[str, Any]:
        return {
            "type": "object",
            "properties": {
                "database": {
                    "type": "string",
                    "description": "数据库名称",
                },
                "table": {
                    "type": "string",
                    "description": "可选，指定表名以只返回该表的外键关系",
                },
            },
            "required": ["database"],
        }

    async def execute(self, parameters: dict[str, Any]) -> dict[str, Any]:
        """获取外键关系"""
        database = parameters.get("database", "")
        table_filter = parameters.get("table")

        if not database:
            return {
                "success": False,
                "data": None,
                "error": "缺少参数: database",
            }

        logger.info(f"RelationTool: database={database}, table={table_filter}")

        # 检查缓存
        relations = self._relation_cache.get(database, [])

        if not relations:
            # 模拟返回外键关系
            relations = self._mock_relations(database)

        # 按表名过滤
        if table_filter:
            relations = [
                r for r in relations
                if r["sourceTable"].lower() == table_filter.lower()
                or r["targetTable"].lower() == table_filter.lower()
            ]

        return {
            "success": True,
            "data": {
                "database": database,
                "relationCount": len(relations),
                "relations": relations,
            },
            "error": None,
        }

    def _mock_relations(self, database: str) -> list[dict[str, Any]]:
        """模拟返回外键关系"""
        return [
            {
                "sourceTable": "orders",
                "sourceColumn": "user_id",
                "targetTable": "users",
                "targetColumn": "id",
                "type": "inferred",  # inferred | explicit
                "confidence": 0.85,
            },
            {
                "sourceTable": "order_items",
                "sourceColumn": "order_id",
                "targetTable": "orders",
                "targetColumn": "id",
                "type": "inferred",
                "confidence": 0.85,
            },
            {
                "sourceTable": "order_items",
                "sourceColumn": "product_id",
                "targetTable": "products",
                "targetColumn": "id",
                "type": "inferred",
                "confidence": 0.85,
            },
            {
                "sourceTable": "reviews",
                "sourceColumn": "user_id",
                "targetTable": "users",
                "targetColumn": "id",
                "type": "inferred",
                "confidence": 0.85,
            },
            {
                "sourceTable": "reviews",
                "sourceColumn": "product_id",
                "targetTable": "products",
                "targetColumn": "id",
                "type": "inferred",
                "confidence": 0.85,
            },
        ]

    def register_relations(
        self, database: str, relations: list[dict[str, Any]]
    ) -> None:
        """
        注册真实外键关系到缓存

        Args:
            database: 数据库名
            relations: 外键关系列表
        """
        self._relation_cache[database] = relations
        logger.info(
            f"RelationTool: 注册 database={database}, {len(relations)} 条外键关系"
        )


# 单例
relation_tool = RelationTool()
