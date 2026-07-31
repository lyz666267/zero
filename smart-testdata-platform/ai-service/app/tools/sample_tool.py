"""
数据采样工具

获取指定字段的真实样本数据（前 N 条），用于：
1. 辅助 LLM 理解字段的实际内容格式
2. 辅助敏感字段检测（通过实际数据模式匹配）
3. 辅助生成器推荐（通过样本值推断合理的数据范围）
"""
from typing import Any
from loguru import logger

from app.tools.base import Tool


class SampleTool(Tool):
    """
    获取字段真实样本数据

    输入：database + table + columns（可选 limit）
    输出：每个字段的样本值列表
    """

    def __init__(self):
        # 模拟样本数据缓存（实际场景从目标数据库 SELECT ... LIMIT 获取）
        self._sample_cache: dict[str, dict[str, list[Any]]] = {}

    @property
    def name(self) -> str:
        return "get_sample"

    @property
    def description(self) -> str:
        return (
            "获取指定表中字段的真实样本数据（前 N 条），"
            "用于了解字段的实际数据格式、内容模式和取值范围。"
            "当需要判断字段的语义类型或了解数据分布时调用此工具。"
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
                    "description": "表名",
                },
                "columns": {
                    "type": "array",
                    "items": {"type": "string"},
                    "description": "要采样的字段名列表，为空则采样全部字段",
                },
                "limit": {
                    "type": "integer",
                    "description": "采样行数，默认 5",
                    "default": 5,
                },
            },
            "required": ["database", "table"],
        }

    async def execute(self, parameters: dict[str, Any]) -> dict[str, Any]:
        """获取字段样本数据"""
        database = parameters.get("database", "")
        table = parameters.get("table", "")
        columns: list[str] | None = parameters.get("columns")
        limit: int = parameters.get("limit", 5)

        if not database or not table:
            return {
                "success": False,
                "data": None,
                "error": "缺少必要参数: database 和 table",
            }

        logger.info(
            f"SampleTool: database={database}, table={table}, "
            f"columns={columns}, limit={limit}"
        )

        # 检查缓存
        cache_key = f"{database}.{table}"
        if cache_key in self._sample_cache:
            samples = self._sample_cache[cache_key]
            if columns:
                samples = {c: samples.get(c, []) for c in columns}
            return {
                "success": True,
                "data": {
                    "database": database,
                    "table": table,
                    "limit": limit,
                    "samples": samples,
                },
                "error": None,
            }

        # 模拟样本数据
        samples = self._mock_samples(table, limit)

        # 按字段过滤
        if columns:
            samples = {c: samples.get(c, []) for c in columns}

        return {
            "success": True,
            "data": {
                "database": database,
                "table": table,
                "limit": limit,
                "samples": samples,
            },
            "error": None,
        }

    def _mock_samples(self, table: str, limit: int) -> dict[str, list[Any]]:
        """模拟返回样本数据"""
        table_lower = table.lower()

        if "user" in table_lower:
            return {
                "username": ["张三", "李四", "王五", "赵六", "孙七"][:limit],
                "email": ["zhangsan@example.com", "lisi@qq.com", "wangwu@163.com", "zhaoliu@gmail.com", "sunqi@outlook.com"][:limit],
                "phone": ["13800138001", "13900139002", "13700137003", "13600136004", "13500135005"][:limit],
                "created_at": ["2024-01-15 10:30:00", "2024-02-20 14:22:00", "2024-03-10 09:15:00", "2024-04-05 16:45:00", "2024-05-18 11:00:00"][:limit],
            }

        if "order" in table_lower:
            return {
                "product_name": ["iPhone 15 Pro", "MacBook Air", "AirPods Pro", "iPad Mini", "Apple Watch"][:limit],
                "amount": [8999.00, 7999.00, 1299.00, 3499.00, 2999.00][:limit],
                "status": ["completed", "pending", "completed", "shipped", "processing"][:limit],
                "created_at": ["2024-06-01 08:00:00", "2024-06-02 10:15:00", "2024-06-03 12:30:00", "2024-06-04 14:45:00", "2024-06-05 16:00:00"][:limit],
            }

        if "product" in table_lower:
            return {
                "name": ["iPhone 15 Pro", "MacBook Air M3", "AirPods Pro 2", "iPad Mini 7", "Apple Watch Ultra"][:limit],
                "price": [8999.00, 7999.00, 1299.00, 3499.00, 2999.00][:limit],
                "category": ["电子产品", "电脑", "耳机", "平板", "手表"][:limit],
                "stock": [150, 80, 300, 45, 120][:limit],
            }

        # 通用默认
        return {"data": [f"sample_{i}" for i in range(limit)]}

    def register_samples(
        self, database: str, table: str, samples: dict[str, list[Any]]
    ) -> None:
        """
        注册真实样本数据到缓存

        Args:
            database: 数据库名
            table: 表名
            samples: 字段→样本值列表
        """
        cache_key = f"{database}.{table}"
        self._sample_cache[cache_key] = samples
        logger.info(
            f"SampleTool: 注册 {database}.{table}, {len(samples)} 个字段样本"
        )


# 单例
sample_tool = SampleTool()
