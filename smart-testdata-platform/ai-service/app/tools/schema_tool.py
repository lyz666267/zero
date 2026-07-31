"""
Schema 信息工具

获取数据库 Schema 信息（表列表、字段、类型、主键、注释）。
模拟从 information_schema 读取元数据的过程。
"""
from typing import Any
from loguru import logger

from app.tools.base import Tool


class SchemaTool(Tool):
    """
    获取数据库 Schema 信息

    输入：database 名称（可选 table 过滤）
    输出：表列表 + 每个表的字段详情（名称、类型、是否主键、注释）
    """

    def __init__(self):
        # 模拟数据库中的 Schema 数据（实际场景从 information_schema 读取）
        self._schema_cache: dict[str, list[dict[str, Any]]] = {}

    @property
    def name(self) -> str:
        return "get_schema"

    @property
    def description(self) -> str:
        return (
            "获取数据库的 Schema 信息，包括表列表、字段名称、字段类型、"
            "主键、是否可为空、字段注释等元数据。"
            "当需要了解数据库有哪些表、每个表有哪些字段时调用此工具。"
        )

    @property
    def parameters_schema(self) -> dict[str, Any]:
        return {
            "type": "object",
            "properties": {
                "database": {
                    "type": "string",
                    "description": "要查询的数据库名称",
                },
                "table": {
                    "type": "string",
                    "description": "可选，指定表名以只返回该表的信息",
                },
            },
            "required": ["database"],
        }

    async def execute(self, parameters: dict[str, Any]) -> dict[str, Any]:
        """获取 Schema 信息"""
        database = parameters.get("database", "")
        table_filter = parameters.get("table")

        if not database:
            return {
                "success": False,
                "data": None,
                "error": "缺少参数: database",
            }

        logger.info(f"SchemaTool: 查询 database={database}, table={table_filter}")

        # 检查缓存
        tables = self._schema_cache.get(database, [])

        if not tables:
            # 模拟返回 Schema（实际场景从 information_schema 查询）
            tables = self._mock_schema(database)

        # 按表名过滤
        if table_filter:
            tables = [t for t in tables if t["tableName"].lower() == table_filter.lower()]
            if not tables:
                return {
                    "success": False,
                    "data": None,
                    "error": f"表 '{table_filter}' 在数据库 '{database}' 中不存在",
                }

        return {
            "success": True,
            "data": {
                "database": database,
                "tableCount": len(tables),
                "tables": tables,
            },
            "error": None,
        }

    def _mock_schema(self, database: str) -> list[dict[str, Any]]:
        """模拟返回 Schema 数据（无真实数据库连接时的默认行为）"""
        # 返回通用示例 Schema，让 LLM 有个大致结构参考
        mock = [
            {
                "tableName": "users",
                "comment": "用户表",
                "columns": [
                    {"name": "id", "type": "INT", "nullable": False, "primaryKey": True, "comment": "主键ID"},
                    {"name": "username", "type": "VARCHAR(50)", "nullable": False, "primaryKey": False, "comment": "用户名"},
                    {"name": "email", "type": "VARCHAR(100)", "nullable": True, "primaryKey": False, "comment": "邮箱"},
                    {"name": "phone", "type": "VARCHAR(20)", "nullable": True, "primaryKey": False, "comment": "手机号"},
                    {"name": "created_at", "type": "DATETIME", "nullable": False, "primaryKey": False, "comment": "创建时间"},
                ],
            },
            {
                "tableName": "orders",
                "comment": "订单表",
                "columns": [
                    {"name": "id", "type": "INT", "nullable": False, "primaryKey": True, "comment": "主键ID"},
                    {"name": "user_id", "type": "INT", "nullable": False, "primaryKey": False, "comment": "用户ID（外键）"},
                    {"name": "product_name", "type": "VARCHAR(200)", "nullable": False, "primaryKey": False, "comment": "商品名称"},
                    {"name": "amount", "type": "DECIMAL(10,2)", "nullable": False, "primaryKey": False, "comment": "订单金额"},
                    {"name": "status", "type": "VARCHAR(20)", "nullable": False, "primaryKey": False, "comment": "订单状态"},
                    {"name": "created_at", "type": "DATETIME", "nullable": False, "primaryKey": False, "comment": "创建时间"},
                ],
            },
            {
                "tableName": "products",
                "comment": "商品表",
                "columns": [
                    {"name": "id", "type": "INT", "nullable": False, "primaryKey": True, "comment": "主键ID"},
                    {"name": "name", "type": "VARCHAR(200)", "nullable": False, "primaryKey": False, "comment": "商品名称"},
                    {"name": "price", "type": "DECIMAL(10,2)", "nullable": False, "primaryKey": False, "comment": "价格"},
                    {"name": "category", "type": "VARCHAR(50)", "nullable": True, "primaryKey": False, "comment": "分类"},
                    {"name": "stock", "type": "INT", "nullable": False, "primaryKey": False, "comment": "库存"},
                ],
            },
        ]
        return mock

    def register_schema(self, database: str, tables: list[dict[str, Any]]) -> None:
        """
        注册真实 Schema 数据到缓存

        Args:
            database: 数据库名
            tables: 表列表
        """
        self._schema_cache[database] = tables
        logger.info(f"SchemaTool: 注册 database={database}, {len(tables)} 张表")


# 单例
schema_tool = SchemaTool()
