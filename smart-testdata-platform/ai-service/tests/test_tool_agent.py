"""
Tool Calling Agent + Tools 单元测试

测试场景：
1. SchemaTool — 获取 Schema、表过滤、缓存注册
2. SampleTool — 获取样本、字段过滤、不同类型表
3. RelationTool — 获取关系、表过滤、缓存注册
4. ToolRegistry — 注册、查找、执行、描述生成、重复注册
5. ToolAgent Mock — 关键词路由（Schema/样本/关系/默认）
6. ToolAgent LLM — 正常流程、RouterExhaustedError 降级、JSON 解析
7. ToolAgent 多轮 — 多工具连续调用
"""
import pytest
import json
from unittest.mock import AsyncMock, patch, MagicMock

from app.tools.base import Tool
from app.tools.schema_tool import SchemaTool, schema_tool
from app.tools.sample_tool import SampleTool, sample_tool
from app.tools.relation_tool import RelationTool, relation_tool
from app.tools.tool_registry import ToolRegistry
from app.agents.tool_agent import ToolAgent, TOOL_AGENT_SYSTEM_PROMPT
from app.llm.base import RouterExhaustedError, LLMProviderError


# ============================================================
# Test SchemaTool
# ============================================================

class TestSchemaTool:
    """Schema 信息工具测试"""

    @pytest.mark.asyncio
    async def test_get_schema_mock(self):
        """获取完整 Schema（Mock 模式）"""
        tool = SchemaTool()
        result = await tool.execute({"database": "my_shop"})

        assert result["success"] is True
        assert result["data"]["database"] == "my_shop"
        assert result["data"]["tableCount"] == 3
        tables = result["data"]["tables"]
        table_names = [t["tableName"] for t in tables]
        assert "users" in table_names
        assert "orders" in table_names
        assert "products" in table_names

    @pytest.mark.asyncio
    async def test_get_schema_table_filter(self):
        """按表名过滤 Schema"""
        tool = SchemaTool()
        result = await tool.execute({"database": "my_shop", "table": "users"})

        assert result["success"] is True
        assert result["data"]["tableCount"] == 1
        assert result["data"]["tables"][0]["tableName"] == "users"
        assert len(result["data"]["tables"][0]["columns"]) == 5

    @pytest.mark.asyncio
    async def test_get_schema_table_not_found(self):
        """过滤不存在的表"""
        tool = SchemaTool()
        result = await tool.execute({"database": "my_shop", "table": "nonexistent"})

        assert result["success"] is False
        assert "不存在" in result["error"]

    @pytest.mark.asyncio
    async def test_get_schema_missing_database(self):
        """缺少 database 参数"""
        tool = SchemaTool()
        result = await tool.execute({"table": "users"})

        assert result["success"] is False
        assert "database" in result["error"].lower()

    @pytest.mark.asyncio
    async def test_get_schema_columns_structure(self):
        """验证字段结构完整性"""
        tool = SchemaTool()
        result = await tool.execute({"database": "my_shop", "table": "users"})

        columns = result["data"]["tables"][0]["columns"]
        col = columns[0]
        assert "name" in col
        assert "type" in col
        assert "nullable" in col
        assert "primaryKey" in col
        assert "comment" in col

    @pytest.mark.asyncio
    async def test_register_and_query_schema(self):
        """注册自定义 Schema 后查询"""
        tool = SchemaTool()
        custom_tables = [
            {
                "tableName": "custom_table",
                "comment": "自定义表",
                "columns": [
                    {"name": "id", "type": "INT", "nullable": False, "primaryKey": True, "comment": "ID"},
                    {"name": "name", "type": "VARCHAR(100)", "nullable": False, "primaryKey": False, "comment": "名称"},
                ],
            }
        ]
        tool.register_schema("custom_db", custom_tables)

        result = await tool.execute({"database": "custom_db"})
        assert result["success"] is True
        assert result["data"]["tableCount"] == 1
        assert result["data"]["tables"][0]["tableName"] == "custom_table"


# ============================================================
# Test SampleTool
# ============================================================

class TestSampleTool:
    """数据采样工具测试"""

    @pytest.mark.asyncio
    async def test_get_sample_users(self):
        """获取 users 表样本"""
        tool = SampleTool()
        result = await tool.execute({
            "database": "my_shop",
            "table": "users",
            "limit": 3,
        })

        assert result["success"] is True
        assert result["data"]["database"] == "my_shop"
        assert result["data"]["table"] == "users"
        samples = result["data"]["samples"]
        assert "username" in samples
        assert "email" in samples
        assert "phone" in samples
        assert len(samples["username"]) <= 3

    @pytest.mark.asyncio
    async def test_get_sample_orders(self):
        """获取 orders 表样本"""
        tool = SampleTool()
        result = await tool.execute({
            "database": "my_shop",
            "table": "orders",
            "limit": 5,
        })

        assert result["success"] is True
        samples = result["data"]["samples"]
        assert "product_name" in samples
        assert "amount" in samples
        assert "status" in samples
        # 状态值应是订单相关枚举
        for s in samples["status"]:
            assert s in ("completed", "pending", "shipped", "processing")

    @pytest.mark.asyncio
    async def test_get_sample_products(self):
        """获取 products 表样本"""
        tool = SampleTool()
        result = await tool.execute({
            "database": "my_shop",
            "table": "products",
            "limit": 2,
        })

        assert result["success"] is True
        samples = result["data"]["samples"]
        assert "name" in samples
        assert "price" in samples
        assert "category" in samples
        assert "stock" in samples

    @pytest.mark.asyncio
    async def test_get_sample_column_filter(self):
        """按字段过滤样本"""
        tool = SampleTool()
        result = await tool.execute({
            "database": "my_shop",
            "table": "users",
            "columns": ["username", "email"],
            "limit": 3,
        })

        assert result["success"] is True
        samples = result["data"]["samples"]
        assert "username" in samples
        assert "email" in samples
        assert "phone" not in samples  # 未请求

    @pytest.mark.asyncio
    async def test_get_sample_missing_table(self):
        """缺少 table 参数"""
        tool = SampleTool()
        result = await tool.execute({"database": "my_shop"})

        assert result["success"] is False
        assert "table" in result["error"].lower()

    @pytest.mark.asyncio
    async def test_register_and_query_samples(self):
        """注册自定义样本后查询"""
        tool = SampleTool()
        custom_samples = {
            "username": ["alice", "bob"],
            "email": ["alice@test.com", "bob@test.com"],
        }
        tool.register_samples("test_db", "accounts", custom_samples)

        result = await tool.execute({
            "database": "test_db",
            "table": "accounts",
        })
        assert result["success"] is True
        assert result["data"]["samples"]["username"] == ["alice", "bob"]

    @pytest.mark.asyncio
    async def test_get_sample_generic_table(self):
        """未匹配的表名 → 返回通用默认数据"""
        tool = SampleTool()
        result = await tool.execute({
            "database": "test_db",
            "table": "some_random_table",
        })

        assert result["success"] is True
        samples = result["data"]["samples"]
        assert "data" in samples  # 默认字段


# ============================================================
# Test RelationTool
# ============================================================

class TestRelationTool:
    """外键关系工具测试"""

    @pytest.mark.asyncio
    async def test_get_relations_all(self):
        """获取全部外键关系"""
        tool = RelationTool()
        result = await tool.execute({"database": "my_shop"})

        assert result["success"] is True
        assert result["data"]["database"] == "my_shop"
        assert result["data"]["relationCount"] == 5
        relations = result["data"]["relations"]

        # 验证 orders → users 关系
        orders_user = [r for r in relations if r["sourceTable"] == "orders" and r["sourceColumn"] == "user_id"]
        assert len(orders_user) == 1
        assert orders_user[0]["targetTable"] == "users"
        assert orders_user[0]["targetColumn"] == "id"

    @pytest.mark.asyncio
    async def test_get_relations_table_filter(self):
        """按表过滤外键关系"""
        tool = RelationTool()
        result = await tool.execute({"database": "my_shop", "table": "orders"})

        assert result["success"] is True
        relations = result["data"]["relations"]
        # orders 有 2 条关系：orders.user_id → users.id + order_items.order_id → orders.id
        for r in relations:
            assert r["sourceTable"] == "orders" or r["targetTable"] == "orders"

    @pytest.mark.asyncio
    async def test_get_relations_missing_database(self):
        """缺少 database 参数"""
        tool = RelationTool()
        result = await tool.execute({})

        assert result["success"] is False
        assert "database" in result["error"].lower()

    @pytest.mark.asyncio
    async def test_relation_structure(self):
        """验证外键关系字段完整性"""
        tool = RelationTool()
        result = await tool.execute({"database": "my_shop"})

        rel = result["data"]["relations"][0]
        assert "sourceTable" in rel
        assert "sourceColumn" in rel
        assert "targetTable" in rel
        assert "targetColumn" in rel
        assert "type" in rel
        assert "confidence" in rel
        assert rel["type"] == "inferred"
        assert 0.0 <= rel["confidence"] <= 1.0

    @pytest.mark.asyncio
    async def test_register_and_query_relations(self):
        """注册自定义关系后查询"""
        tool = RelationTool()
        custom_relations = [
            {
                "sourceTable": "posts",
                "sourceColumn": "author_id",
                "targetTable": "users",
                "targetColumn": "id",
                "type": "explicit",
                "confidence": 1.0,
            }
        ]
        tool.register_relations("blog_db", custom_relations)

        result = await tool.execute({"database": "blog_db"})
        assert result["success"] is True
        assert result["data"]["relationCount"] == 1
        assert result["data"]["relations"][0]["type"] == "explicit"


# ============================================================
# Test ToolRegistry
# ============================================================

class TestToolRegistry:
    """工具注册中心测试"""

    def test_register_tools(self):
        """注册三个默认工具"""
        registry = ToolRegistry()
        registry.register(SchemaTool())
        registry.register(SampleTool())
        registry.register(RelationTool())

        assert registry.tool_count == 3
        assert "get_schema" in registry.list_tools()
        assert "get_sample" in registry.list_tools()
        assert "get_relations" in registry.list_tools()

    def test_register_duplicate(self):
        """重复注册应抛出异常"""
        registry = ToolRegistry()
        registry.register(SchemaTool())
        with pytest.raises(ValueError, match="已注册"):
            registry.register(SchemaTool())

    def test_get_tool(self):
        """按名称获取工具"""
        registry = ToolRegistry()
        tool = SchemaTool()
        registry.register(tool)
        assert registry.get("get_schema") is tool
        assert registry.get("nonexistent") is None

    def test_unregister_tool(self):
        """注销工具"""
        registry = ToolRegistry()
        tool = SchemaTool()
        registry.register(tool)
        assert registry.tool_count == 1
        registry.unregister("get_schema")
        assert registry.tool_count == 0
        assert registry.get("get_schema") is None

    def test_execute_nonexistent_tool(self):
        """执行不存在的工具"""
        registry = ToolRegistry()
        import asyncio
        result = asyncio.run(registry.execute("nonexistent", {}))
        assert result["success"] is False
        assert "未找到工具" in result["error"]

    @pytest.mark.asyncio
    async def test_execute_registered_tool(self):
        """执行已注册的工具"""
        registry = ToolRegistry()
        registry.register(SchemaTool())
        result = await registry.execute("get_schema", {"database": "test_db"})

        assert result["success"] is True
        assert result["data"]["database"] == "test_db"

    def test_get_tools_description(self):
        """生成 LLM 工具描述"""
        registry = ToolRegistry()
        registry.register(SchemaTool())
        registry.register(SampleTool())
        registry.register(RelationTool())

        desc = registry.get_tools_description()
        assert "get_schema" in desc
        assert "get_sample" in desc
        assert "get_relations" in desc
        assert "## 可用工具" in desc
        assert "参数" in desc

    def test_get_tools_schema(self):
        """获取工具 JSON Schema 列表"""
        registry = ToolRegistry()
        registry.register(SchemaTool())
        registry.register(SampleTool())

        schemas = registry.get_tools_schema()
        assert len(schemas) == 2
        assert schemas[0]["name"] == "get_schema"
        assert "parameters" in schemas[0]
        assert "description" in schemas[0]

    def test_empty_registry(self):
        """空注册中心"""
        registry = ToolRegistry()
        assert registry.tool_count == 0
        assert registry.list_tools() == []
        assert "无可用工具" in registry.get_tools_description()


# ============================================================
# Test ToolAgent Mock Mode
# ============================================================

class TestToolAgentMock:
    """ToolAgent Mock 模式测试"""

    @pytest.mark.asyncio
    async def test_mock_schema_request(self):
        """Mock: 分析 Schema 需求 → 自动调用 get_schema"""
        agent = ToolAgent()
        agent.has_llm = False

        result = await agent.run("分析 my_shop 数据库的表结构")

        assert result["success"] is True
        assert result["mock"] is True
        assert len(result["tool_calls"]) == 1
        assert result["tool_calls"][0]["action"] == "get_schema"
        assert result["tool_calls"][0]["parameters"]["database"] == "my_shop"

    @pytest.mark.asyncio
    async def test_mock_sample_request(self):
        """Mock: 采样需求 → 自动调用 get_schema + get_sample"""
        agent = ToolAgent()
        agent.has_llm = False

        result = await agent.run("获取 my_shop 数据库 users 表的样本数据")

        assert result["success"] is True
        assert result["mock"] is True
        actions = [tc["action"] for tc in result["tool_calls"]]
        assert "get_schema" in actions
        assert "get_sample" in actions

    @pytest.mark.asyncio
    async def test_mock_relation_request(self):
        """Mock: 外键关系需求 → 自动调用 get_schema + get_relations"""
        agent = ToolAgent()
        agent.has_llm = False

        result = await agent.run("查看 my_shop 数据库的表结构和外键关系")

        assert result["success"] is True
        assert result["mock"] is True
        actions = [tc["action"] for tc in result["tool_calls"]]
        assert "get_schema" in actions
        assert "get_relations" in actions

    @pytest.mark.asyncio
    async def test_mock_default_to_schema(self):
        """Mock: 未匹配到关键词 → 默认调用 Schema"""
        agent = ToolAgent()
        agent.has_llm = False

        result = await agent.run("hello world")

        assert result["success"] is True
        assert len(result["tool_calls"]) == 1
        assert result["tool_calls"][0]["action"] == "get_schema"

    @pytest.mark.asyncio
    async def test_mock_comprehensive_request(self):
        """Mock: 综合分析需求 → 调用全部三个工具"""
        agent = ToolAgent()
        agent.has_llm = False

        result = await agent.run(
            "分析 my_shop 数据库：查看表结构、获取 users 表的真实数据样本、分析外键依赖关系"
        )

        assert result["success"] is True
        actions = [tc["action"] for tc in result["tool_calls"]]
        assert "get_schema" in actions
        assert "get_sample" in actions
        assert "get_relations" in actions

    @pytest.mark.asyncio
    async def test_mock_final_answer_format(self):
        """Mock: 最终回答格式正确"""
        agent = ToolAgent()
        agent.has_llm = False

        result = await agent.run("分析 my_shop 数据库的表结构")

        assert result["final_answer"] is not None
        assert "my_shop" in result["final_answer"]
        assert "工具调用" in result["final_answer"]

    @pytest.mark.asyncio
    async def test_mock_no_tools_registered(self):
        """Mock: 没有注册工具时返回错误"""
        from app.tools.tool_registry import ToolRegistry
        empty_registry = ToolRegistry()

        # 使用 sys.modules 获取真正的模块对象
        import sys
        ta_mod = sys.modules["app.agents.tool_agent"]
        original = ta_mod.tool_registry
        ta_mod.tool_registry = empty_registry
        try:
            agent = ToolAgent()
            result = await agent.run("分析数据库")
            assert result["success"] is False
            assert "没有注册任何工具" in result.get("error", "")
        finally:
            ta_mod.tool_registry = original


# ============================================================
# Test ToolAgent LLM Mode
# ============================================================

def _make_mock_router(chat_side_effect=None, chat_return=None):
    """构建一个兼容 LLMRouter 接口的 mock 对象"""
    from app.llm.router import LLMRouter

    mock = MagicMock()
    mock.chat = AsyncMock()
    if chat_side_effect:
        mock.chat.side_effect = chat_side_effect
    elif chat_return:
        mock.chat.return_value = chat_return
    # 使用真实的 extract_json 静态方法
    mock.extract_json = LLMRouter.extract_json
    mock.has_llm = True
    return mock


class TestToolAgentLLM:
    """ToolAgent LLM 模式测试"""

    @pytest.mark.asyncio
    async def test_llm_single_tool_call(self):
        """LLM: 单轮工具调用 → 返回最终答案"""
        agent = ToolAgent()
        agent.has_llm = True

        mock_router = _make_mock_router(chat_return=json.dumps({
            "thought": "用户需要查看数据库结构，我已经了解了信息",
            "final_answer": "数据库 my_shop 包含 3 张表：users、orders、products",
        }, ensure_ascii=False))

        with patch("app.agents.tool_agent.llm_router", mock_router):
            result = await agent.run("分析 my_shop 数据库")

        assert result["success"] is True
        assert "3 张表" in result["final_answer"]
        assert result["rounds"] == 1

    @pytest.mark.asyncio
    async def test_llm_multi_tool_calls(self):
        """LLM: 多轮工具调用 — Schema → 样本 → 最终回答"""
        agent = ToolAgent()
        agent.has_llm = True

        mock_router = _make_mock_router(chat_side_effect=[
            json.dumps({
                "thought": "先了解数据库结构",
                "action": "get_schema",
                "parameters": {"database": "my_shop"},
            }, ensure_ascii=False),
            json.dumps({
                "thought": "查看 users 表的具体数据",
                "action": "get_sample",
                "parameters": {"database": "my_shop", "table": "users", "limit": 3},
            }, ensure_ascii=False),
            json.dumps({
                "thought": "已获取全部信息",
                "final_answer": "分析完成：my_shop 有 3 张表，users 表包含用户基本信息",
            }, ensure_ascii=False),
        ])

        with patch("app.agents.tool_agent.llm_router", mock_router):
            result = await agent.run("分析 my_shop 数据库的完整信息")

        assert result["success"] is True
        assert result["rounds"] == 3
        assert len(result["tool_calls"]) == 3
        assert result["tool_calls"][0]["action"] == "get_schema"
        assert result["tool_calls"][0]["result"]["success"] is True
        assert result["tool_calls"][1]["action"] == "get_sample"
        assert result["tool_calls"][1]["result"]["success"] is True
        assert result["tool_calls"][2]["final"] is True

    @pytest.mark.asyncio
    async def test_llm_router_exhausted_fallback(self):
        """LLM: RouterExhaustedError → 降级为 Mock"""
        agent = ToolAgent()
        agent.has_llm = True

        mock_router = _make_mock_router(chat_side_effect=RouterExhaustedError([]))

        with patch("app.agents.tool_agent.llm_router", mock_router):
            result = await agent.run("分析数据库")

        # P6: RouterExhaustedError → 降级为 Mock 模式（与其他 Agent 保持一致）
        assert result["success"] is True
        assert result.get("mock") is True

    @pytest.mark.asyncio
    async def test_llm_json_parse_error(self):
        """LLM: 无法解析 JSON → 记录错误但继续"""
        agent = ToolAgent()
        agent.has_llm = True

        mock_router = _make_mock_router(chat_side_effect=[
            "这不是有效的 JSON 格式",
            json.dumps({
                "thought": "虽然之前失败了，但我可以直接回答",
                "final_answer": "数据库查询完成",
            }, ensure_ascii=False),
        ])

        with patch("app.agents.tool_agent.llm_router", mock_router):
            result = await agent.run("分析数据库")

        assert result["success"] is True
        parse_errors = [tc for tc in result["tool_calls"] if tc.get("parse_error")]
        assert len(parse_errors) >= 1

    @pytest.mark.asyncio
    async def test_llm_unknown_tool_action(self):
        """LLM: 调用不存在的工具 → 返回错误"""
        agent = ToolAgent()
        agent.has_llm = True

        mock_router = _make_mock_router(chat_side_effect=[
            json.dumps({
                "thought": "让我试试这个工具",
                "action": "non_existent_tool",
                "parameters": {},
            }, ensure_ascii=False),
            json.dumps({
                "thought": "工具不可用，我直接回答",
                "final_answer": "无法获取信息",
            }, ensure_ascii=False),
        ])

        with patch("app.agents.tool_agent.llm_router", mock_router):
            result = await agent.run("做某事")

        assert result["success"] is True
        tool_result = result["tool_calls"][0]["result"]
        assert tool_result["success"] is False
        assert "未找到工具" in tool_result["error"]


# ============================================================
# Test ToolAgent Helpers
# ============================================================

class TestToolAgentHelpers:
    """ToolAgent 辅助方法测试"""

    def test_parse_action_valid(self):
        """解析有效 action JSON"""
        agent = ToolAgent()
        text = '```json\n{"thought": "test", "action": "get_schema", "parameters": {"database": "test"}}\n```'
        action = agent._parse_action(text)
        assert action is not None
        assert action["action"] == "get_schema"
        assert action["parameters"]["database"] == "test"

    def test_parse_action_final_answer(self):
        """解析 final_answer JSON"""
        agent = ToolAgent()
        text = '{"thought": "done", "final_answer": "完成分析"}'
        action = agent._parse_action(text)
        assert action is not None
        assert action["final_answer"] == "完成分析"

    def test_parse_action_bare_json(self):
        """解析裸 JSON（无 markdown 代码块）"""
        agent = ToolAgent()
        text = '{"thought": "thinking", "action": "get_sample", "parameters": {"table": "users"}}'
        action = agent._parse_action(text)
        assert action is not None
        assert action["action"] == "get_sample"
        assert action["parameters"]["table"] == "users"

    def test_parse_action_invalid(self):
        """解析无效文本 → None"""
        agent = ToolAgent()
        action = agent._parse_action("这不是JSON")
        assert action is None

    def test_parse_action_no_action_field(self):
        """JSON 没有 action 或 final_answer → None"""
        agent = ToolAgent()
        action = agent._parse_action('{"thought": "just thinking"}')
        assert action is None

    def test_format_observation_success(self):
        """格式化成功的观察结果"""
        agent = ToolAgent()
        result = {
            "success": True,
            "data": {"tableCount": 3, "tables": []},
        }
        obs = agent._format_observation(result)
        assert "Observation" in obs
        assert "tableCount" in obs

    def test_format_observation_error(self):
        """格式化失败的观察结果"""
        agent = ToolAgent()
        result = {
            "success": False,
            "error": "表不存在",
        }
        obs = agent._format_observation(result)
        assert "失败" in obs
        assert "表不存在" in obs

    def test_summarize_calls(self):
        """工具调用总结"""
        agent = ToolAgent()
        tool_calls = [
            {
                "round": 1,
                "action": "get_schema",
                "parameters": {"database": "test"},
                "result": {"success": True, "data": {"tableCount": 3}},
            },
            {
                "round": 2,
                "action": "get_sample",
                "parameters": {"database": "test", "table": "users"},
                "result": {"success": True, "data": {"samples": {"username": ["张三"]}}},
            },
        ]
        summary = agent._summarize_calls(tool_calls, "分析 test 数据库")
        assert "分析 test 数据库" in summary
        assert "2 次工具调用" in summary
        assert "get_schema" in summary
        assert "get_sample" in summary

    def test_system_prompt_includes_tools(self):
        """验证 System Prompt 完整性"""
        assert "工具" in TOOL_AGENT_SYSTEM_PROMPT
        assert "action" in TOOL_AGENT_SYSTEM_PROMPT
        assert "final_answer" in TOOL_AGENT_SYSTEM_PROMPT
        assert "thought" in TOOL_AGENT_SYSTEM_PROMPT


# ============================================================
# Test Tool Interface Compliance
# ============================================================

class TestToolInterface:
    """验证所有工具符合 Tool 接口"""

    def test_schema_tool_interface(self):
        """SchemaTool 实现完整接口"""
        tool = SchemaTool()
        assert isinstance(tool.name, str) and len(tool.name) > 0
        assert isinstance(tool.description, str) and len(tool.description) > 0
        schema = tool.parameters_schema
        assert schema["type"] == "object"
        assert "properties" in schema
        assert "database" in schema.get("required", [])

    def test_sample_tool_interface(self):
        """SampleTool 实现完整接口"""
        tool = SampleTool()
        assert isinstance(tool.name, str)
        assert isinstance(tool.description, str)
        schema = tool.parameters_schema
        assert "database" in schema.get("required", [])
        assert "table" in schema.get("required", [])

    def test_relation_tool_interface(self):
        """RelationTool 实现完整接口"""
        tool = RelationTool()
        assert isinstance(tool.name, str)
        assert isinstance(tool.description, str)
        schema = tool.parameters_schema
        assert "database" in schema.get("required", [])
        assert "properties" in schema


# ============================================================
# Integration: ToolAgent → Tools → Registry
# ============================================================

class TestToolAgentIntegration:
    """集成测试：ToolAgent 通过 ToolRegistry 调用真实工具"""

    @pytest.mark.asyncio
    async def test_full_mock_flow(self):
        """全流程集成：Agent → Registry → Tools → 返回结果"""
        from app.tools import tool_registry as global_registry

        agent = ToolAgent()
        agent.has_llm = False

        result = await agent.run(
            "分析 my_shop 数据库：查看表结构、users 表数据样本、外键依赖关系"
        )

        assert result["success"] is True
        assert result["mock"] is True
        assert len(result["tool_calls"]) == 3

        # 验证每个工具调用都成功
        for tc in result["tool_calls"]:
            assert tc["result"]["success"] is True, f"工具 {tc['action']} 失败"

        # 最终回答包含关键信息
        assert "my_shop" in result["final_answer"]

    @pytest.mark.asyncio
    async def test_mock_with_english_keywords(self):
        """英文关键词也能匹配"""
        agent = ToolAgent()
        agent.has_llm = False

        result = await agent.run("Analyze the schema of my_shop database")

        assert result["success"] is True
        actions = [tc["action"] for tc in result["tool_calls"]]
        assert "get_schema" in actions

    @pytest.mark.asyncio
    async def test_tool_agent_preserves_existing_imports(self):
        """验证所有模块导入正常（冒烟测试）"""
        # 验证 agents 模块
        from app.agents import (
            SchemaAgent, schema_agent,
            TestDataAgent, testdata_agent,
            StrategyAgent, strategy_agent,
            ToolAgent, tool_agent,
        )
        assert schema_agent is not None
        assert strategy_agent is not None
        assert tool_agent is not None

        # 验证 tools 模块
        from app.tools import (
            SchemaTool, SampleTool, RelationTool,
            ToolRegistry, tool_registry,
            schema_tool, sample_tool, relation_tool,
        )
        assert tool_registry.tool_count == 3
        assert schema_tool is not None
        assert sample_tool is not None
        assert relation_tool is not None

        # 验证 chains 模块
        from app.chains import generation_chain
        assert generation_chain is not None
