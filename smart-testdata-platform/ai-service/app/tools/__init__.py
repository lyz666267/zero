"""
Tools 模块 — Agent 可调用的工具集合

架构：
    Tool (抽象接口)
    ├── SchemaTool  — 获取数据库 Schema 信息
    ├── SampleTool  — 获取字段真实样本数据
    └── RelationTool — 获取外键关系

    ToolRegistry (注册中心)
    └── 统一注册 + 调用 + LLM 工具描述生成

使用方式：
    from app.tools import tool_registry, schema_tool, sample_tool, relation_tool

    tool_registry.register(schema_tool)
    tool_registry.register(sample_tool)
    tool_registry.register(relation_tool)

    result = await tool_registry.execute("get_schema", {"database": "my_db"})
"""
from app.tools.base import Tool
from app.tools.schema_tool import SchemaTool, schema_tool
from app.tools.sample_tool import SampleTool, sample_tool
from app.tools.relation_tool import RelationTool, relation_tool
from app.tools.tool_registry import ToolRegistry, tool_registry

# 自动注册默认工具
tool_registry.register(schema_tool)
tool_registry.register(sample_tool)
tool_registry.register(relation_tool)

__all__ = [
    "Tool",
    "SchemaTool",
    "schema_tool",
    "SampleTool",
    "sample_tool",
    "RelationTool",
    "relation_tool",
    "ToolRegistry",
    "tool_registry",
]
