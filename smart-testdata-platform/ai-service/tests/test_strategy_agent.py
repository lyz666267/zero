"""
StrategyAgent + GenerationChain 单元测试

测试场景：
1. StrategyAgent Mock 模式 — 分析结果 → 生成计划
2. StrategyAgent LLM 正常 — LLM 返回有效计划
3. StrategyAgent LLM 失败 → Mock 降级
4. StrategyAgent 外键依赖排序
5. GenerationChain 完整流水线 — schema dict → plan
6. GenerationChain 预分析输入
7. GenerationChain Schema 分析失败
"""
import pytest
import json
from unittest.mock import AsyncMock, patch, MagicMock

from app.schemas.schema_analysis import (
    SchemaAnalyzeRequest, SchemaAnalyzeResponse,
    SchemaAnalysisResult, AnalysisSummary,
    AnalyzedTable, AnalyzedColumn,
    SensitiveDetection, InferredForeignKey, GeneratorSuggestion,
    TableInfo, ColumnInfo,
)
from app.schemas.generation_plan import (
    GenerationPlan, TablePlan, FieldPlan, FieldRange,
    GeneratePlanRequest, GeneratePlanResponse,
)
from app.agents.strategy_agent import StrategyAgent
from app.chains.generation_chain import GenerationChain
from app.llm.base import LLMProviderError, RouterExhaustedError


# ============================================================
# 测试数据构建器
# ============================================================

def make_analyzed_column(
    name: str,
    col_type: str = "VARCHAR(50)",
    semantic_label: str = "UNKNOWN",
    sensitive: bool = False,
    sensitive_type: str = "NONE",
    confidence: float = 0.0,
    fk: InferredForeignKey | None = None,
    generator: str | None = None,
    generator_reason: str = "",
    primary: bool = False,
) -> AnalyzedColumn:
    """快速构建 AnalyzedColumn"""
    gs = None
    if generator:
        gs = GeneratorSuggestion(generator=generator, reason=generator_reason)
    return AnalyzedColumn(
        name=name,
        type=col_type,
        nullable=True,
        defaultValue=None,
        comment=None,
        semanticLabel=semantic_label,
        sensitiveDetection=SensitiveDetection(
            sensitive=sensitive,
            sensitiveType=sensitive_type,
            confidence=confidence,
        ),
        inferredForeignKey=fk,
        generatorSuggestion=gs,
    )


def make_users_schema_result() -> SchemaAnalysisResult:
    """构建 users 表的分析结果（含 FK 引用 orders）"""
    return SchemaAnalysisResult(
        database="test_db",
        dbType="MySQL",
        tables=[
            AnalyzedTable(
                tableName="users",
                tableComment="用户表",
                primaryKey=["id"],
                rowEstimate=1000,
                columns=[
                    make_analyzed_column("id", "INT", "IDENTIFIER", primary=True),
                    make_analyzed_column(
                        "username", "VARCHAR(50)", "PERSON_NAME",
                        sensitive=True, sensitive_type="NAME", confidence=0.95,
                        generator="faker.name", generator_reason="匹配姓名语义",
                    ),
                    make_analyzed_column(
                        "email", "VARCHAR(100)", "EMAIL",
                        sensitive=True, sensitive_type="EMAIL", confidence=0.95,
                        generator="faker.email", generator_reason="匹配邮箱语义",
                    ),
                    make_analyzed_column(
                        "phone", "VARCHAR(20)", "PHONE",
                        sensitive=True, sensitive_type="PHONE", confidence=0.95,
                        generator="faker.phone_number", generator_reason="匹配手机号语义",
                    ),
                    make_analyzed_column(
                        "created_at", "DATETIME", "DATE_TIME",
                        generator="time.past_datetime", generator_reason="匹配日期语义",
                    ),
                ],
            ),
        ],
        summary=AnalysisSummary(
            totalTables=1, totalColumns=5,
            sensitiveColumns=3, foreignKeyRelations=0,
            recommendations=[],
        ),
    )


def make_users_orders_schema_result() -> SchemaAnalysisResult:
    """构建 users + orders 表的分析结果（含 FK 关系）"""
    return SchemaAnalysisResult(
        database="test_db",
        dbType="MySQL",
        tables=[
            AnalyzedTable(
                tableName="users",
                tableComment="用户表",
                primaryKey=["id"],
                rowEstimate=100,
                columns=[
                    make_analyzed_column("id", "INT", primary=True),
                    make_analyzed_column(
                        "username", "VARCHAR(50)", "PERSON_NAME",
                        generator="faker.name",
                    ),
                    make_analyzed_column(
                        "email", "VARCHAR(100)", "EMAIL",
                        generator="faker.email",
                    ),
                ],
            ),
            AnalyzedTable(
                tableName="orders",
                tableComment="订单表",
                primaryKey=["id"],
                rowEstimate=500,
                columns=[
                    make_analyzed_column("id", "INT", primary=True),
                    make_analyzed_column(
                        "user_id", "INT", "IDENTIFIER",
                        fk=InferredForeignKey(
                            referencedTable="users",
                            referencedColumn="id",
                            confidence=0.85,
                        ),
                    ),
                    make_analyzed_column(
                        "amount", "DECIMAL(10,2)", "AMOUNT",
                        generator="random.decimal",
                    ),
                    make_analyzed_column(
                        "status", "VARCHAR(20)", "ENUM_VALUE",
                        generator="enum.values",
                    ),
                    make_analyzed_column(
                        "created_at", "DATETIME", "DATE_TIME",
                        generator="time.past_datetime",
                    ),
                ],
            ),
        ],
        summary=AnalysisSummary(
            totalTables=2, totalColumns=8,
            sensitiveColumns=0, foreignKeyRelations=1,
            recommendations=["表 orders 的 user_id 引用表 users，建议按依赖顺序生成"],
        ),
    )


def make_schema_dict(tables: list[dict]) -> dict:
    """构建 GeneratePlanRequest 的 schema dict"""
    return {
        "database": "test_db",
        "dbType": "MySQL",
        "tables": tables,
    }


def make_users_schema_dict() -> dict:
    """构建简单的 users 表 schema dict"""
    return make_schema_dict([
        {
            "tableName": "users",
            "comment": "用户表",
            "columns": [
                {"name": "id", "type": "INT", "nullable": False, "primaryKey": True},
                {"name": "username", "type": "VARCHAR(50)", "nullable": False},
                {"name": "email", "type": "VARCHAR(100)", "nullable": False},
                {"name": "phone", "type": "VARCHAR(20)", "nullable": True},
                {"name": "created_at", "type": "DATETIME", "nullable": True},
            ],
        },
    ])


# ============================================================
# LLM 响应模板
# ============================================================

LLM_PLAN_RESPONSE = json.dumps({
    "taskName": "users 表测试数据生成",
    "tables": [
        {
            "table": "users",
            "count": 1000,
            "fields": [
                {"name": "username", "generator": "faker.name"},
                {"name": "email", "generator": "faker.email"},
                {"name": "phone", "generator": "faker.phone_number"},
                {"name": "created_at", "generator": "time.past_datetime"},
            ],
        },
    ],
})

LLM_MULTI_TABLE_RESPONSE = json.dumps({
    "taskName": "用户和订单数据生成",
    "tables": [
        {
            "table": "users",
            "count": 100,
            "fields": [
                {"name": "username", "generator": "faker.name"},
                {"name": "email", "generator": "faker.email"},
            ],
        },
        {
            "table": "orders",
            "count": 500,
            "fields": [
                {"name": "user_id", "generator": "constant.value", "params": {"value": 1, "refTable": "users"}},
                {"name": "amount", "generator": "random.decimal", "range": {"min": 0, "max": 99999}},
                {"name": "status", "generator": "enum.values", "params": {"values": ["pending", "paid", "shipped"]}},
                {"name": "created_at", "generator": "time.past_datetime"},
            ],
        },
    ],
})


# ============================================================
# Test: StrategyAgent Mock 模式
# ============================================================

class TestStrategyAgentMock:
    """StrategyAgent Mock 模式测试 — 无 LLM 时使用分析结果直接生成计划"""

    @pytest.mark.asyncio
    async def test_mock_single_table(self):
        """Mock: 单表生成 — 使用 generatorSuggestion"""
        agent = StrategyAgent()
        agent.has_llm = False

        analysis = make_users_schema_result()
        response = await agent.generate(analysis, "生成1000条用户数据")

        assert response.success is True
        assert response.mock is True
        assert response.plan is not None
        assert response.plan.taskName is not None
        assert len(response.plan.tables) == 1

        table_plan = response.plan.tables[0]
        assert table_plan.table == "users"
        assert table_plan.count == 1000

        # 不应包含主键 id
        field_names = [f.name for f in table_plan.fields]
        assert "id" not in field_names
        assert "username" in field_names
        assert "email" in field_names
        assert "phone" in field_names
        assert "created_at" in field_names

        # 验证生成器使用
        for f in table_plan.fields:
            if f.name == "username":
                assert f.generator == "faker.name"
            elif f.name == "email":
                assert f.generator == "faker.email"
            elif f.name == "phone":
                assert f.generator == "faker.phone_number"
            elif f.name == "created_at":
                assert f.generator == "time.past_datetime"

    @pytest.mark.asyncio
    async def test_mock_multi_table_with_fk(self):
        """Mock: 多表 + FK — 验证依赖排序和 FK 处理"""
        agent = StrategyAgent()
        agent.has_llm = False

        analysis = make_users_orders_schema_result()
        response = await agent.generate(analysis, "生成用户和订单数据")

        assert response.success is True
        assert response.mock is True
        assert len(response.plan.tables) == 2

        # users 表应在 orders 表之前（被引用表在前）
        assert response.plan.tables[0].table == "users"
        assert response.plan.tables[1].table == "orders"

        # orders 表的 user_id 应使用 constant.value
        orders_plan = response.plan.tables[1]
        user_id_field = next(
            (f for f in orders_plan.fields if f.name == "user_id"), None
        )
        assert user_id_field is not None
        assert user_id_field.generator == "constant.value"
        assert "refTable" in user_id_field.params

    @pytest.mark.asyncio
    async def test_mock_empty_tables(self):
        """Mock: 空表列表 → 返回错误"""
        agent = StrategyAgent()
        agent.has_llm = False

        analysis = SchemaAnalysisResult(
            database="test_db",
            dbType="MySQL",
            tables=[],
            summary=AnalysisSummary(),
        )
        response = await agent.generate(analysis, "生成数据")

        assert response.success is False
        assert response.error is not None
        assert "没有表信息" in response.error

    @pytest.mark.asyncio
    async def test_mock_count_extraction(self):
        """Mock: 从需求中提取行数"""
        agent = StrategyAgent()
        agent.has_llm = False

        test_cases = [
            ("生成500条用户数据", 500),
            ("生成100条测试数据", 100),
            ("生成2000行订单数据", 2000),
            ("生成30笔交易", 30),
            ("生成一些测试数据", 1000),  # 未指定行数 → 使用 rowEstimate (1000)
        ]

        for requirement, expected_count in test_cases:
            analysis = make_users_schema_result()
            response = await agent.generate(analysis, requirement)
            assert response.plan.tables[0].count == expected_count, \
                f"requirement='{requirement}' expected count={expected_count}"

    @pytest.mark.asyncio
    async def test_mock_fallback_generator(self):
        """Mock: 无 generatorSuggestion 时使用语义标签兜底"""
        agent = StrategyAgent()
        agent.has_llm = False

        # 构建没有 generatorSuggestion 的列
        analysis = SchemaAnalysisResult(
            database="test_db",
            dbType="MySQL",
            tables=[
                AnalyzedTable(
                    tableName="products",
                    tableComment="商品表",
                    primaryKey=["id"],
                    rowEstimate=50,
                    columns=[
                        make_analyzed_column("id", "INT", primary=True),
                        make_analyzed_column("price", "DECIMAL(10,2)", "AMOUNT"),
                        make_analyzed_column("status", "VARCHAR(20)", "ENUM_VALUE"),
                        make_analyzed_column("description", "TEXT", "TEXT_CONTENT"),
                        make_analyzed_column("unknown_field", "VARCHAR(100)", "UNKNOWN"),
                    ],
                ),
            ],
            summary=AnalysisSummary(),
        )

        response = await agent.generate(analysis, "生成100条商品数据")
        assert response.success is True

        fields = response.plan.tables[0].fields
        field_map = {f.name: f for f in fields}

        assert field_map["price"].generator == "random.decimal"
        assert field_map["status"].generator == "enum.values"
        assert field_map["description"].generator == "faker.text"
        assert field_map["unknown_field"].generator == "faker.word"

    @pytest.mark.asyncio
    async def test_mock_range_for_numeric(self):
        """Mock: 数值字段自动添加 range"""
        agent = StrategyAgent()
        agent.has_llm = False

        analysis = SchemaAnalysisResult(
            database="test_db",
            dbType="MySQL",
            tables=[
                AnalyzedTable(
                    tableName="products",
                    tableComment="",
                    primaryKey=["id"],
                    rowEstimate=10,
                    columns=[
                        make_analyzed_column("id", "INT", primary=True),
                        make_analyzed_column(
                            "price", "DECIMAL(10,2)", "AMOUNT",
                            generator="random.decimal",
                        ),
                        make_analyzed_column(
                            "quantity", "INT", "AMOUNT",
                            generator="random.integer",
                        ),
                    ],
                ),
            ],
            summary=AnalysisSummary(),
        )

        response = await agent.generate(analysis, "生成10条数据")
        fields = response.plan.tables[0].fields
        field_map = {f.name: f for f in fields}

        assert field_map["price"].range is not None
        assert field_map["price"].range.min == 0
        assert field_map["quantity"].range is not None
        assert field_map["quantity"].range.min == 0


# ============================================================
# Test: StrategyAgent LLM 模式
# ============================================================

class TestStrategyAgentLLM:
    """StrategyAgent LLM 模式测试"""

    @pytest.mark.asyncio
    async def test_llm_success(self):
        """LLM 成功返回有效计划"""
        agent = StrategyAgent()
        agent.has_llm = True

        analysis = make_users_schema_result()

        with patch.object(agent, '_generate_with_llm', new_callable=AsyncMock) as mock_llm:
            mock_llm.return_value = GeneratePlanResponse(
                success=True,
                plan=GenerationPlan(
                    taskName="test",
                    tables=[TablePlan(table="users", count=1000, fields=[])],
                ),
                mock=False,
            )

            response = await agent.generate(analysis, "生成1000条用户数据")

            assert response.success is True
            assert response.mock is False
            mock_llm.assert_called_once()

    @pytest.mark.asyncio
    async def test_llm_fallback_to_mock_on_error(self):
        """LLM 抛出异常 → 降级为 Mock"""
        agent = StrategyAgent()
        agent.has_llm = True

        analysis = make_users_schema_result()

        with patch.object(agent, '_generate_with_llm', new_callable=AsyncMock) as mock_llm:
            mock_llm.side_effect = RouterExhaustedError([])

            response = await agent.generate(analysis, "生成1000条用户数据")

            # 应该降级为 Mock
            assert response.success is True
            assert response.mock is True
            mock_llm.assert_called_once()

    @pytest.mark.asyncio
    async def test_llm_with_multi_table(self):
        """LLM 多表 + FK 响应验证"""
        agent = StrategyAgent()
        agent.has_llm = True

        analysis = make_users_orders_schema_result()

        with patch.object(agent, '_generate_with_llm', new_callable=AsyncMock) as mock_llm:
            plan = GenerationPlan(
                taskName="用户和订单",
                tables=[
                    TablePlan(table="users", count=100, fields=[
                        FieldPlan(name="username", generator="faker.name"),
                        FieldPlan(name="email", generator="faker.email"),
                    ]),
                    TablePlan(table="orders", count=500, fields=[
                        FieldPlan(name="user_id", generator="constant.value",
                                  params={"value": 1}),
                        FieldPlan(name="amount", generator="random.decimal",
                                  range=FieldRange(min=0, max=99999)),
                    ]),
                ],
            )
            mock_llm.return_value = GeneratePlanResponse(
                success=True, plan=plan, mock=False,
            )

            response = await agent.generate(analysis, "生成用户和订单数据")

            assert response.success is True
            assert len(response.plan.tables) == 2


# ============================================================
# Test: StrategyAgent LLM 内部实现
# ============================================================

class TestStrategyAgentLLMInternal:
    """StrategyAgent._generate_with_llm 内部逻辑测试"""

    @pytest.mark.asyncio
    async def test_llm_chat_called_with_analysis(self):
        """验证 LLM 调用包含分析描述和需求"""
        agent = StrategyAgent()
        agent.has_llm = True

        analysis = make_users_schema_result()

        mock_response = '```json\n' + LLM_PLAN_RESPONSE + '\n```'

        with patch('app.agents.strategy_agent.llm_router') as mock_router:
            mock_router.chat = AsyncMock(return_value=mock_response)
            mock_router.extract_json = lambda text: json.dumps(
                json.loads(text.replace("```json\n", "").replace("\n```", ""))
            )

            response = await agent._generate_with_llm(analysis, "生成1000条用户数据")

            assert response.success is True
            assert response.mock is False
            assert response.plan is not None
            assert response.plan.taskName is not None
            assert len(response.plan.tables) == 1
            assert response.plan.tables[0].table == "users"
            assert response.plan.tables[0].count == 1000

            # 验证 LLM 被调用
            mock_router.chat.assert_called_once()
            call_args = mock_router.chat.call_args
            messages = call_args.kwargs.get("messages", call_args.args[0] if call_args.args else [])

            # system prompt 应包含策略指导
            system_msg = messages[0]["content"]
            assert "策略" in system_msg or "生成器" in system_msg

            # user prompt 应包含分析和需求
            user_msg = messages[1]["content"]
            assert "users" in user_msg
            assert "1000" in user_msg

    @pytest.mark.asyncio
    async def test_llm_router_exhausted_fallback(self):
        """LLM Router 耗尽 → 降级 Mock"""
        agent = StrategyAgent()
        agent.has_llm = True

        analysis = make_users_schema_result()

        with patch('app.agents.strategy_agent.llm_router') as mock_router:
            errors = [
                LLMProviderError("DeepSeek", "timeout", status_code=408),
                LLMProviderError("Qwen", "timeout", status_code=408),
            ]
            mock_router.chat = AsyncMock(side_effect=RouterExhaustedError(errors))

            response = await agent._generate_with_llm(analysis, "生成1000条数据")

            assert response.success is True
            assert response.mock is True  # 降级为 Mock

    @pytest.mark.asyncio
    async def test_llm_json_parse_error_fallback(self):
        """LLM 返回非法 JSON → 降级 Mock"""
        agent = StrategyAgent()
        agent.has_llm = True

        analysis = make_users_schema_result()

        with patch('app.agents.strategy_agent.llm_router') as mock_router:
            mock_router.chat = AsyncMock(return_value="这不是有效的 JSON 格式{broken")
            mock_router.extract_json = lambda text: text  # 返回原文本

            response = await agent._generate_with_llm(analysis, "生成数据")

            assert response.success is True
            assert response.mock is True  # JSON 解析失败 → Mock

    @pytest.mark.asyncio
    async def test_llm_extract_json_from_markdown(self):
        """从 markdown 代码块中提取 JSON"""
        agent = StrategyAgent()
        agent.has_llm = True

        analysis = make_users_schema_result()

        with patch('app.agents.strategy_agent.llm_router') as mock_router:
            mock_router.chat = AsyncMock(
                return_value='```json\n' + LLM_PLAN_RESPONSE + '\n```'
            )
            # extract_json 应返回字符串，由调用方自行 json.loads
            mock_router.extract_json = lambda text: text.replace("```json\n", "").replace("\n```", "")

            response = await agent._generate_with_llm(analysis, "生成数据")

            assert response.success is True


# ============================================================
# Test: StrategyAgent 辅助方法
# ============================================================

class TestStrategyAgentHelpers:
    """辅助方法测试"""

    def test_build_analysis_description(self):
        """构建分析描述文本"""
        agent = StrategyAgent()
        analysis = make_users_schema_result()

        desc = agent._build_analysis_description(analysis)

        assert "test_db" in desc
        assert "MySQL" in desc
        assert "users" in desc
        assert "username" in desc
        assert "email" in desc
        assert "PERSON_NAME" in desc
        assert "EMAIL" in desc

    def test_order_by_dependency_simple(self):
        """FK 依赖排序：被引用表在前"""
        agent = StrategyAgent()
        analysis = make_users_orders_schema_result()

        ordered = agent._order_by_dependency(analysis.tables)

        # users 应该在前（被 orders 引用）
        assert ordered[0].tableName == "users"
        assert ordered[1].tableName == "orders"

    def test_order_by_dependency_no_fk(self):
        """无 FK 关系 → 保持原有顺序"""
        agent = StrategyAgent()
        analysis = make_users_schema_result()

        ordered = agent._order_by_dependency(analysis.tables)

        assert len(ordered) == 1
        assert ordered[0].tableName == "users"

    def test_order_by_dependency_circular_safe(self):
        """循环依赖不崩溃"""
        agent = StrategyAgent()

        analysis = SchemaAnalysisResult(
            database="test_db",
            dbType="MySQL",
            tables=[
                AnalyzedTable(
                    tableName="table_a",
                    primaryKey=["id"],
                    columns=[
                        make_analyzed_column("id", "INT", primary=True),
                        make_analyzed_column(
                            "b_id", "INT",
                            fk=InferredForeignKey(referencedTable="table_b"),
                        ),
                    ],
                ),
                AnalyzedTable(
                    tableName="table_b",
                    primaryKey=["id"],
                    columns=[
                        make_analyzed_column("id", "INT", primary=True),
                        make_analyzed_column(
                            "a_id", "INT",
                            fk=InferredForeignKey(referencedTable="table_a"),
                        ),
                    ],
                ),
            ],
            summary=AnalysisSummary(),
        )

        ordered = agent._order_by_dependency(analysis.tables)

        # 不应崩溃，两个表都应返回
        assert len(ordered) == 2
        names = [t.tableName for t in ordered]
        assert "table_a" in names
        assert "table_b" in names

    def test_fallback_generator_by_label(self):
        """按语义标签兜底生成器"""
        agent = StrategyAgent()

        tests = [
            ("PERSON_NAME", "faker.name"),
            ("EMAIL", "faker.email"),
            ("PHONE", "faker.phone_number"),
            ("ID_CARD", "faker.ssn"),
            ("ADDRESS", "faker.address"),
            ("BANK_CARD", "faker.ssn"),
            ("AMOUNT", "random.decimal"),
            ("DATE_TIME", "time.past_datetime"),
            ("BOOLEAN_FLAG", "random.boolean"),
            ("ENUM_VALUE", "enum.values"),
            ("IDENTIFIER", "uuid"),
            ("TEXT_CONTENT", "faker.text"),
            ("URL_PATH", "faker.url"),
            ("UNKNOWN", "faker.word"),
        ]

        for label, expected_gen in tests:
            col = AnalyzedColumn(
                name="test", type="VARCHAR(50)", semanticLabel=label,
                sensitiveDetection=SensitiveDetection(),
            )
            result = agent._fallback_generator(col)
            assert result == expected_gen, f"label={label}: expected {expected_gen}, got {result}"

    def test_fallback_generator_by_type(self):
        """UNKNOWN 标签时按类型兜底"""
        agent = StrategyAgent()

        type_tests = [
            ("VARCHAR(100)", "faker.word"),
            ("TEXT", "faker.text"),
            ("INT", "random.integer"),
            ("BIGINT", "random.integer"),
            ("DECIMAL(10,2)", "random.decimal"),
            ("FLOAT", "random.decimal"),
            ("DATETIME", "time.past_datetime"),
            ("TIMESTAMP", "time.past_datetime"),
            ("DATE", "time.past_date"),
            ("BOOLEAN", "random.boolean"),
        ]

        for col_type, expected_gen in type_tests:
            col = AnalyzedColumn(
                name="test", type=col_type, semanticLabel="UNKNOWN",
                sensitiveDetection=SensitiveDetection(),
            )
            result = agent._fallback_generator(col)
            assert result == expected_gen, f"type={col_type}: expected {expected_gen}, got {result}"


# ============================================================
# Test: GenerationChain
# ============================================================

class TestGenerationChain:
    """GenerationChain 完整流水线测试"""

    @pytest.mark.asyncio
    async def test_full_pipeline_mock(self):
        """完整流水线: schema dict → SchemaAgent(Mock) → StrategyAgent(Mock) → plan"""
        chain = GenerationChain()

        schema_dict = make_users_schema_dict()
        request = GeneratePlanRequest(
            schema_data=schema_dict,
            requirement="生成1000条用户数据",
        )

        response = await chain.run(request)

        assert response.success is True
        assert response.plan is not None
        assert len(response.plan.tables) == 1
        assert response.plan.tables[0].table == "users"
        assert response.plan.tables[0].count == 1000

        # 主键不应包含在 fields 中
        field_names = [f.name for f in response.plan.tables[0].fields]
        assert "id" not in field_names
        assert "username" in field_names
        assert "email" in field_names
        assert "phone" in field_names

    @pytest.mark.asyncio
    async def test_full_pipeline_empty_schema(self):
        """空 schema → 错误"""
        chain = GenerationChain()

        request = GeneratePlanRequest(
            schema_data={"database": "test", "tables": []},
            requirement="生成数据",
        )

        response = await chain.run(request)

        assert response.success is False
        assert response.error is not None

    @pytest.mark.asyncio
    async def test_run_with_analysis(self):
        """跳过 Schema 分析，直接用预分析结果"""
        chain = GenerationChain()

        analysis = make_users_schema_result()
        response = await chain.run_with_analysis(analysis, "生成500条用户数据")

        assert response.success is True
        assert response.plan is not None
        assert len(response.plan.tables) == 1
        assert response.plan.tables[0].count == 500

    @pytest.mark.asyncio
    async def test_chain_with_multi_table(self):
        """完整流水线: 多表 + FK"""
        chain = GenerationChain()

        schema_dict = make_schema_dict([
            {
                "tableName": "users",
                "comment": "用户表",
                "columns": [
                    {"name": "id", "type": "INT", "nullable": False, "primaryKey": True},
                    {"name": "username", "type": "VARCHAR(50)", "nullable": False},
                    {"name": "email", "type": "VARCHAR(100)", "nullable": False},
                ],
            },
            {
                "tableName": "orders",
                "comment": "订单表",
                "columns": [
                    {"name": "id", "type": "INT", "nullable": False, "primaryKey": True},
                    {"name": "user_id", "type": "INT", "nullable": False},
                    {"name": "amount", "type": "DECIMAL(10,2)", "nullable": True},
                    {"name": "status", "type": "VARCHAR(20)", "nullable": True},
                ],
            },
        ])

        request = GeneratePlanRequest(
            schema_data=schema_dict,
            requirement="生成100个用户和500个订单",
        )

        response = await chain.run(request)

        assert response.success is True
        assert response.plan is not None

        # 应有 2 张表的计划
        table_names = [t.table for t in response.plan.tables]
        assert "users" in table_names
        assert "orders" in table_names

        # orders 表的 user_id 应该有外键处理
        orders_plan = next(
            (t for t in response.plan.tables if t.table == "orders"), None
        )
        assert orders_plan is not None
        user_id_field = next(
            (f for f in orders_plan.fields if f.name == "user_id"), None
        )
        assert user_id_field is not None
        assert "constant" in user_id_field.generator or "fk" in user_id_field.generator

    @pytest.mark.asyncio
    async def test_chain_with_invalid_schema(self):
        """schema 缺字段 → 部分仍可工作"""
        chain = GenerationChain()

        # 缺少 tableName 的 schema
        schema_dict = make_schema_dict([
            {
                "columns": [
                    {"name": "id", "type": "INT"},
                ],
            },
        ])

        request = GeneratePlanRequest(
            schema_data=schema_dict,
            requirement="生成数据",
        )

        response = await chain.run(request)

        # 应该能处理（SchemaAgent Mock 会容错）
        assert response.success is True
        assert response.plan is not None


# ============================================================
# Test: StrategyAgent — plan parsing
# ============================================================

class TestStrategyAgentPlanParsing:
    """LLM 返回值解析测试"""

    def test_parse_simple_plan(self):
        """解析简单计划 JSON"""
        agent = StrategyAgent()

        plan_dict = json.loads(LLM_PLAN_RESPONSE)
        plan = agent._parse_plan(plan_dict)

        assert plan.taskName == "users 表测试数据生成"
        assert len(plan.tables) == 1
        assert plan.tables[0].table == "users"
        assert plan.tables[0].count == 1000
        assert len(plan.tables[0].fields) == 4

    def test_parse_multi_table_plan(self):
        """解析多表计划 JSON"""
        agent = StrategyAgent()

        plan_dict = json.loads(LLM_MULTI_TABLE_RESPONSE)
        plan = agent._parse_plan(plan_dict)

        assert len(plan.tables) == 2
        assert plan.tables[0].table == "users"
        assert plan.tables[1].table == "orders"

        # 验证 range
        amount_field = next(
            f for f in plan.tables[1].fields if f.name == "amount"
        )
        assert amount_field.range is not None
        assert amount_field.range.min == 0
        assert amount_field.range.max == 99999

        # 验证 params
        status_field = next(
            f for f in plan.tables[1].fields if f.name == "status"
        )
        assert status_field.params is not None
        assert "values" in status_field.params

    def test_parse_plan_without_optionals(self):
        """解析无 range/params 的计划"""
        agent = StrategyAgent()

        plan_dict = {
            "taskName": "minimal",
            "tables": [
                {
                    "table": "test",
                    "count": 10,
                    "fields": [
                        {"name": "col1", "generator": "faker.word"},
                    ],
                },
            ],
        }
        plan = agent._parse_plan(plan_dict)

        assert plan.taskName == "minimal"
        assert plan.tables[0].fields[0].name == "col1"
        assert plan.tables[0].fields[0].range is None
        assert plan.tables[0].fields[0].params is None
