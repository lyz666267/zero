"""
工具注册中心

统一管理所有工具，提供：
1. 工具注册（register）
2. 工具查找（get）
3. 工具列表（list_tools）— 返回 LLM 可用的工具描述
4. 统一调用（execute）— 通过名称调用任意工具（含执行计时）
"""
import time
from typing import Any
from loguru import logger

from app.tools.base import Tool


class ToolRegistry:
    """
    工具注册中心

    使用方式：
        registry = ToolRegistry()
        registry.register(schema_tool)
        registry.register(sample_tool)
        registry.register(relation_tool)

        # 执行工具
        result = await registry.execute("get_schema", {"database": "my_db"})

        # 获取所有工具的 LLM 描述
        tools_desc = registry.get_tools_description()
    """

    def __init__(self):
        self._tools: dict[str, Tool] = {}

    def register(self, tool: Tool) -> None:
        """
        注册工具

        Args:
            tool: 工具实例

        Raises:
            ValueError: 工具名已存在
        """
        name = tool.name
        if name in self._tools:
            raise ValueError(f"工具 '{name}' 已注册，请勿重复注册")
        self._tools[name] = tool
        logger.info(f"ToolRegistry: 注册工具 '{name}' — {tool.description[:60]}...")

    def unregister(self, name: str) -> None:
        """注销工具"""
        if name in self._tools:
            del self._tools[name]
            logger.info(f"ToolRegistry: 注销工具 '{name}'")

    def get(self, name: str) -> Tool | None:
        """获取指定名称的工具"""
        return self._tools.get(name)

    def list_tools(self) -> list[str]:
        """列出所有已注册工具的名称"""
        return list(self._tools.keys())

    def get_tools_description(self) -> str:
        """
        生成所有工具的 LLM 友好描述文本

        用于注入到 LLM System Prompt 中，让 LLM 知道有哪些工具可用。
        """
        if not self._tools:
            return "（无可用工具）"

        lines = ["## 可用工具\n"]
        for i, (name, tool) in enumerate(self._tools.items(), 1):
            # 参数列表
            props = tool.parameters_schema.get("properties", {})
            required = tool.parameters_schema.get("required", [])
            param_strs = []
            for pname, pinfo in props.items():
                req_mark = " (必填)" if pname in required else ""
                param_strs.append(f"  - {pname}: {pinfo.get('description', '')}{req_mark}")

            lines.append(
                f"**{i}. {name}**\n"
                f"> {tool.description}\n"
                f"\n"
                f"参数：\n"
                f"{chr(10).join(param_strs)}\n"
            )

        return "\n".join(lines)

    def get_tools_schema(self) -> list[dict[str, Any]]:
        """
        获取所有工具的 JSON Schema 列表

        用于 OpenAI function calling 风格的接口。
        """
        return [
            {
                "name": tool.name,
                "description": tool.description,
                "parameters": tool.parameters_schema,
            }
            for tool in self._tools.values()
        ]

    async def execute(
        self, name: str, parameters: dict[str, Any]
    ) -> dict[str, Any]:
        """
        按名称执行工具

        Args:
            name: 工具名称
            parameters: 工具参数

        Returns:
            执行结果 dict
        """
        tool = self._tools.get(name)
        if not tool:
            return {
                "success": False,
                "data": None,
                "error": f"未找到工具: '{name}'。可用工具: {self.list_tools()}",
            }

        logger.info(f"ToolRegistry: 执行工具 '{name}' — params={parameters}")
        start_time = time.time()
        try:
            result = await tool.execute(parameters)
            elapsed_ms = (time.time() - start_time) * 1000
            # 非侵入式附加耗时（不修改已有字段）
            result["execution_time_ms"] = round(elapsed_ms)
            logger.info(f"ToolRegistry: 工具 '{name}' 执行完成 — success={result.get('success')}, time={round(elapsed_ms)}ms")
            return result
        except Exception as e:
            elapsed_ms = (time.time() - start_time) * 1000
            logger.error(f"ToolRegistry: 工具 '{name}' 执行异常 ({round(elapsed_ms)}ms): {e}")
            return {
                "success": False,
                "data": None,
                "error": f"工具 '{name}' 执行异常: {str(e)}",
                "execution_time_ms": round(elapsed_ms),
            }

    @property
    def tool_count(self) -> int:
        """已注册工具数量"""
        return len(self._tools)


# 全局单例注册中心
tool_registry = ToolRegistry()
