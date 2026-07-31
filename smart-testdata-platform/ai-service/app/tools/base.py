"""
Tool 统一接口

所有工具必须实现此接口，确保 ToolRegistry 和 ToolAgent
可以透明地调用任意工具。
"""
from abc import ABC, abstractmethod
from typing import Any


class Tool(ABC):
    """
    工具抽象基类

    每个工具需要实现：
    - name: 工具名称（LLM 通过名称选择工具）
    - description: 工具功能描述（LLM 据此决定是否使用）
    - parameters_schema: 参数 JSON Schema（LLM 据此生成参数）
    - execute(): 执行工具并返回结果
    """

    @property
    @abstractmethod
    def name(self) -> str:
        """工具名称，如 'get_schema'、'get_sample'、'get_relations'"""
        ...

    @property
    @abstractmethod
    def description(self) -> str:
        """工具功能描述，LLM 据此判断何时调用"""
        ...

    @property
    @abstractmethod
    def parameters_schema(self) -> dict[str, Any]:
        """
        参数 JSON Schema

        示例：
        {
            "type": "object",
            "properties": {
                "database": {"type": "string", "description": "数据库名"},
                "table": {"type": "string", "description": "表名"}
            },
            "required": ["database"]
        }
        """
        ...

    @abstractmethod
    async def execute(self, parameters: dict[str, Any]) -> dict[str, Any]:
        """
        执行工具，返回结构化结果

        Args:
            parameters: 工具参数字典

        Returns:
            dict 包含 success 和执行结果
                - success: bool — 是否成功
                - data: Any — 结果数据
                - error: str | None — 错误信息
        """
        ...
