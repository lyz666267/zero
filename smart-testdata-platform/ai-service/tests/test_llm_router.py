"""
LLM Router 单元测试

测试场景：
1. DeepSeek 正常调用 — 主模型返回有效 JSON 响应
2. DeepSeek 失败 → Qwen 自动切换 — 主模型超时，备用模型成功
3. 双模型失败 → Mock 降级 — 所有模型失败，抛出 RouterExhaustedError
"""
import pytest
from unittest.mock import AsyncMock, patch, MagicMock

from app.llm.base import LLMProvider, LLMProviderError, RouterExhaustedError
from app.llm.deepseek_provider import DeepSeekProvider
from app.llm.qwen_provider import QwenProvider
from app.llm.router import LLMRouter


# ============================================================
# Mock Provider — 可控的测试用 Provider
# ============================================================

class MockLLMProvider(LLMProvider):
    """测试用 LLM Provider，行为完全可控"""

    def __init__(self, name: str, available: bool = True):
        self._name = name
        self._available = available
        self._response: str | None = None
        self._error: LLMProviderError | None = None
        self.call_count = 0  # 记录被调用次数

    @property
    def name(self) -> str:
        return self._name

    @property
    def is_available(self) -> bool:
        return self._available

    async def chat(
        self,
        messages: list[dict[str, str]],
        temperature: float = 0.1,
        max_tokens: int = 4096,
    ) -> str:
        self.call_count += 1
        if self._error:
            raise self._error
        if self._response is not None:
            return self._response
        return '{"result": "ok"}'

    def set_response(self, response: str):
        """设置成功响应"""
        self._response = response
        self._error = None

    def set_error(self, error: LLMProviderError):
        """设置错误"""
        self._error = error
        self._response = None


# ============================================================
# 测试数据
# ============================================================

SCHEMA_ANALYSIS_RESPONSE = """```json
{
  "database": "test_db",
  "dbType": "MySQL",
  "tables": [
    {
      "tableName": "users",
      "tableComment": "用户表",
      "primaryKey": ["id"],
      "rowEstimate": 1000,
      "columns": [
        {
          "name": "id",
          "type": "INT",
          "nullable": false,
          "semanticLabel": "IDENTIFIER",
          "sensitiveDetection": {"sensitive": false, "sensitiveType": "NONE", "confidence": 0.0},
          "inferredForeignKey": null,
          "generatorSuggestion": null
        },
        {
          "name": "phone",
          "type": "VARCHAR(20)",
          "nullable": true,
          "semanticLabel": "PHONE",
          "sensitiveDetection": {"sensitive": true, "sensitiveType": "PHONE", "confidence": 0.95},
          "inferredForeignKey": null,
          "generatorSuggestion": {"generator": "faker.phone_number", "reason": "字段名匹配手机号", "params": {}}
        }
      ]
    }
  ],
  "summary": {
    "totalTables": 1,
    "totalColumns": 2,
    "sensitiveColumns": 1,
    "foreignKeyRelations": 0,
    "recommendations": []
  }
}
```"""

# 不带 markdown 代码块包裹的纯 JSON（某些模型可能返回这种格式）
SCHEMA_ANALYSIS_RESPONSE_PLAIN = """{
  "database": "test_db",
  "dbType": "MySQL",
  "tables": [],
  "summary": {"totalTables": 0, "totalColumns": 0, "sensitiveColumns": 0, "foreignKeyRelations": 0, "recommendations": []}
}"""


# ============================================================
# 测试用例
# ============================================================

class TestLLMRouterBasic:
    """路由器基础功能测试"""

    def test_router_initialization(self):
        """测试路由器初始化"""
        primary = MockLLMProvider("DeepSeek", available=True)
        fallback = MockLLMProvider("Qwen", available=True)

        router = LLMRouter(primary=primary, fallback=fallback)

        assert router.has_llm is True
        assert router.primary.name == "DeepSeek"
        assert router.fallback.name == "Qwen"

    def test_router_no_providers_available(self):
        """测试所有提供商都不可用的情况"""
        primary = MockLLMProvider("DeepSeek", available=False)
        fallback = MockLLMProvider("Qwen", available=False)

        router = LLMRouter(primary=primary, fallback=fallback)

        assert router.has_llm is False

    def test_router_without_fallback(self):
        """测试没有备用模型的路由器"""
        primary = MockLLMProvider("DeepSeek", available=True)

        router = LLMRouter(primary=primary, fallback=None)

        assert router.has_llm is True
        assert router.fallback is None


class TestLLMRouterSuccess:
    """场景 1: DeepSeek 正常调用测试"""

    @pytest.mark.asyncio
    async def test_primary_success(self):
        """主模型 DeepSeek 正常返回"""
        primary = MockLLMProvider("DeepSeek", available=True)
        primary.set_response(SCHEMA_ANALYSIS_RESPONSE)
        fallback = MockLLMProvider("Qwen", available=True)

        router = LLMRouter(primary=primary, fallback=fallback)

        messages = [
            {"role": "system", "content": "You are a schema analyzer."},
            {"role": "user", "content": "Analyze schema for table users."},
        ]

        result = await router.chat(messages)

        # 验证：DeepSeek 被调用，Qwen 未被调用
        assert primary.call_count == 1
        assert fallback.call_count == 0
        # 验证响应正确
        assert "test_db" in result
        assert "PHONE" in result

    @pytest.mark.asyncio
    async def test_json_extraction_with_code_block(self):
        """测试 extract_json 从 markdown 代码块中提取 JSON"""
        text = SCHEMA_ANALYSIS_RESPONSE
        result = LLMRouter.extract_json(text)

        assert result.startswith("{")
        assert '"database": "test_db"' in result
        assert not result.startswith("```")

    @pytest.mark.asyncio
    async def test_json_extraction_plain(self):
        """测试 extract_json 处理纯 JSON（无 markdown 包裹）"""
        result = LLMRouter.extract_json(SCHEMA_ANALYSIS_RESPONSE_PLAIN)

        assert result.startswith("{")
        assert '"database": "test_db"' in result


class TestLLMRouterFailover:
    """场景 2: DeepSeek 失败 → Qwen 自动切换测试"""

    @pytest.mark.asyncio
    async def test_deepseek_timeout_fallback_to_qwen(self):
        """DeepSeek 超时 → 自动切换到 Qwen"""
        primary = MockLLMProvider("DeepSeek", available=True)
        primary.set_error(LLMProviderError(
            "DeepSeek", "APITimeoutError: Request timed out",
            retriable=True,
        ))
        fallback = MockLLMProvider("Qwen", available=True)
        fallback.set_response(SCHEMA_ANALYSIS_RESPONSE)

        router = LLMRouter(primary=primary, fallback=fallback)

        messages = [
            {"role": "user", "content": "Analyze schema"},
        ]

        result = await router.chat(messages)

        # 验证：两个都被调用了（DeepSeek 失败，Qwen 成功）
        assert primary.call_count == 1
        assert fallback.call_count == 1
        # 验证响应来自 Qwen
        assert "test_db" in result

    @pytest.mark.asyncio
    async def test_deepseek_rate_limit_fallback_to_qwen(self):
        """DeepSeek 429 限流 → 自动切换到 Qwen"""
        primary = MockLLMProvider("DeepSeek", available=True)
        primary.set_error(LLMProviderError(
            "DeepSeek", "RateLimitError: Too many requests",
            status_code=429, retriable=True,
        ))
        fallback = MockLLMProvider("Qwen", available=True)
        fallback.set_response(SCHEMA_ANALYSIS_RESPONSE)

        router = LLMRouter(primary=primary, fallback=fallback)

        result = await router.chat([
            {"role": "user", "content": "Analyze schema"},
        ])

        assert primary.call_count == 1
        assert fallback.call_count == 1
        assert "test_db" in result

    @pytest.mark.asyncio
    async def test_deepseek_server_error_fallback_to_qwen(self):
        """DeepSeek 5xx 服务器错误 → 自动切换到 Qwen"""
        primary = MockLLMProvider("DeepSeek", available=True)
        primary.set_error(LLMProviderError(
            "DeepSeek", "InternalServerError: Server error",
            status_code=500, retriable=True,
        ))
        fallback = MockLLMProvider("Qwen", available=True)
        fallback.set_response(SCHEMA_ANALYSIS_RESPONSE)

        router = LLMRouter(primary=primary, fallback=fallback)

        result = await router.chat([
            {"role": "user", "content": "Analyze schema"},
        ])

        assert primary.call_count == 1
        assert fallback.call_count == 1
        assert "test_db" in result

    @pytest.mark.asyncio
    async def test_deepseek_connection_error_fallback_to_qwen(self):
        """DeepSeek 连接错误 → 自动切换到 Qwen"""
        primary = MockLLMProvider("DeepSeek", available=True)
        primary.set_error(LLMProviderError(
            "DeepSeek", "APIConnectionError: Connection refused",
            retriable=True,
        ))
        fallback = MockLLMProvider("Qwen", available=True)
        fallback.set_response(SCHEMA_ANALYSIS_RESPONSE)

        router = LLMRouter(primary=primary, fallback=fallback)

        result = await router.chat([
            {"role": "user", "content": "Analyze schema"},
        ])

        assert primary.call_count == 1
        assert fallback.call_count == 1
        assert "test_db" in result

    @pytest.mark.asyncio
    async def test_primary_unavailable_skip_to_fallback(self):
        """主模型未配置 API Key → 直接使用备用模型"""
        primary = MockLLMProvider("DeepSeek", available=False)
        fallback = MockLLMProvider("Qwen", available=True)
        fallback.set_response(SCHEMA_ANALYSIS_RESPONSE)

        router = LLMRouter(primary=primary, fallback=fallback)

        result = await router.chat([
            {"role": "user", "content": "Analyze schema"},
        ])

        # 验证：DeepSeek 未被调用，Qwen 被调用
        assert primary.call_count == 0
        assert fallback.call_count == 1
        assert "test_db" in result

    @pytest.mark.asyncio
    async def test_non_retriable_error_not_fallback(self):
        """不可重试错误（如 401 认证失败）→ 不切换备用，直接抛出"""
        primary = MockLLMProvider("DeepSeek", available=True)
        primary.set_error(LLMProviderError(
            "DeepSeek", "AuthenticationError: Invalid API key",
            status_code=401, retriable=False,
        ))
        fallback = MockLLMProvider("Qwen", available=True)

        router = LLMRouter(primary=primary, fallback=fallback)

        with pytest.raises(LLMProviderError) as exc_info:
            await router.chat([
                {"role": "user", "content": "Analyze schema"},
            ])

        # 验证：直接抛出错误，不尝试备用
        assert exc_info.value.retriable is False
        assert primary.call_count == 1
        assert fallback.call_count == 0


class TestLLMRouterExhausted:
    """场景 3: 双模型失败 → Mock 降级测试"""

    @pytest.mark.asyncio
    async def test_both_providers_fail(self):
        """DeepSeek 和 Qwen 都失败 → 抛出 RouterExhaustedError"""
        primary = MockLLMProvider("DeepSeek", available=True)
        primary.set_error(LLMProviderError(
            "DeepSeek", "APITimeoutError: Request timed out",
            retriable=True,
        ))
        fallback = MockLLMProvider("Qwen", available=True)
        fallback.set_error(LLMProviderError(
            "Qwen", "RateLimitError: Too many requests",
            status_code=429, retriable=True,
        ))

        router = LLMRouter(primary=primary, fallback=fallback)

        with pytest.raises(RouterExhaustedError) as exc_info:
            await router.chat([
                {"role": "user", "content": "Analyze schema"},
            ])

        # 验证：两个都被调用了
        assert primary.call_count == 1
        assert fallback.call_count == 1
        # 验证错误信息包含两个提供商名称
        assert len(exc_info.value.errors) == 2
        assert exc_info.value.errors[0].provider_name == "DeepSeek"
        assert exc_info.value.errors[1].provider_name == "Qwen"

    @pytest.mark.asyncio
    async def test_both_unavailable(self):
        """两个模型都未配置 API Key"""
        primary = MockLLMProvider("DeepSeek", available=False)
        fallback = MockLLMProvider("Qwen", available=False)

        router = LLMRouter(primary=primary, fallback=fallback)

        assert router.has_llm is False

        with pytest.raises(RouterExhaustedError):
            await router.chat([
                {"role": "user", "content": "Analyze schema"},
            ])

    @pytest.mark.asyncio
    async def test_primary_unavailable_fallback_fails(self):
        """主模型不可用 + 备用模型失败 → RouterExhaustedError"""
        primary = MockLLMProvider("DeepSeek", available=False)
        fallback = MockLLMProvider("Qwen", available=True)
        fallback.set_error(LLMProviderError(
            "Qwen", "InternalServerError: Server error",
            status_code=500, retriable=True,
        ))

        router = LLMRouter(primary=primary, fallback=fallback)

        with pytest.raises(RouterExhaustedError) as exc_info:
            await router.chat([
                {"role": "user", "content": "Analyze schema"},
            ])

        assert primary.call_count == 0
        assert fallback.call_count == 1
        assert len(exc_info.value.errors) == 1


# ============================================================
# 集成测试：SchemaAgent + LLMRouter
# ============================================================

class TestSchemaAgentWithRouter:
    """SchemaAgent 集成 LLMRouter 端到端测试"""

    @pytest.mark.asyncio
    async def test_schema_agent_llm_success(self, monkeypatch):
        """SchemaAgent 通过 LLMRouter 成功完成分析"""
        from app.agents.schema_agent import SchemaAgent
        from app.schemas.schema_analysis import SchemaAnalyzeRequest
        from app.llm import llm_router

        # 用 mock provider 替换路由器的 provider
        mock_primary = MockLLMProvider("DeepSeek", available=True)
        mock_primary.set_response(SCHEMA_ANALYSIS_RESPONSE)
        mock_fallback = MockLLMProvider("Qwen", available=True)

        original_primary = llm_router.primary
        original_fallback = llm_router.fallback
        original_has_llm = llm_router._has_llm

        try:
            llm_router.primary = mock_primary
            llm_router.fallback = mock_fallback
            llm_router._has_llm = True

            agent = SchemaAgent()
            assert agent.has_llm is True

            request = SchemaAnalyzeRequest(
                database="test_db",
                dbType="MySQL",
                tables=[
                    {
                        "tableName": "users",
                        "comment": "用户表",
                        "columns": [
                            {"name": "id", "type": "INT", "nullable": False, "primaryKey": True},
                            {"name": "phone", "type": "VARCHAR(20)", "nullable": True},
                        ],
                    }
                ],
            )

            response = await agent.analyze(request)

            assert response.success is True
            assert response.mock is False
            assert response.result is not None
            assert response.result.database == "test_db"

        finally:
            llm_router.primary = original_primary
            llm_router.fallback = original_fallback
            llm_router._has_llm = original_has_llm

    @pytest.mark.asyncio
    async def test_schema_agent_router_exhausted_fallback_to_mock(self, monkeypatch):
        """LLMRouter 耗尽 → SchemaAgent 降级为 Mock 模式"""
        from app.agents.schema_agent import SchemaAgent
        from app.schemas.schema_analysis import SchemaAnalyzeRequest
        from app.llm import llm_router

        # 两个 provider 都失败
        mock_primary = MockLLMProvider("DeepSeek", available=True)
        mock_primary.set_error(LLMProviderError(
            "DeepSeek", "APITimeoutError: timeout", retriable=True,
        ))
        mock_fallback = MockLLMProvider("Qwen", available=True)
        mock_fallback.set_error(LLMProviderError(
            "Qwen", "RateLimitError: rate limited", retriable=True,
        ))

        original_primary = llm_router.primary
        original_fallback = llm_router.fallback
        original_has_llm = llm_router._has_llm

        try:
            llm_router.primary = mock_primary
            llm_router.fallback = mock_fallback
            llm_router._has_llm = True

            agent = SchemaAgent()

            request = SchemaAnalyzeRequest(
                database="test_db",
                dbType="MySQL",
                tables=[
                    {
                        "tableName": "users",
                        "comment": "用户表",
                        "columns": [
                            {"name": "id", "type": "INT", "nullable": False, "primaryKey": True},
                            {"name": "phone", "type": "VARCHAR(20)", "nullable": True},
                            {"name": "email", "type": "VARCHAR(100)", "nullable": True},
                        ],
                    }
                ],
            )

            response = await agent.analyze(request)

            # 验证：降级到 Mock 模式
            assert response.success is True
            assert response.mock is True  # ← Mock 降级成功
            assert response.result is not None
            assert response.result.summary.totalColumns > 0

            # 验证 Mock 模式正确识别了敏感字段
            columns = response.result.tables[0].columns
            phone_col = next(c for c in columns if c.name == "phone")
            assert phone_col.semanticLabel == "PHONE"
            assert phone_col.sensitiveDetection.sensitive is True

        finally:
            llm_router.primary = original_primary
            llm_router.fallback = original_fallback
            llm_router._has_llm = original_has_llm

    @pytest.mark.asyncio
    async def test_schema_agent_mock_direct(self):
        """无 LLM 可用 → SchemaAgent 直接使用 Mock 模式"""
        from app.agents.schema_agent import SchemaAgent
        from app.schemas.schema_analysis import SchemaAnalyzeRequest
        from app.llm import llm_router

        # 两个 provider 都不可用
        mock_primary = MockLLMProvider("DeepSeek", available=False)
        mock_fallback = MockLLMProvider("Qwen", available=False)

        original_primary = llm_router.primary
        original_fallback = llm_router.fallback
        original_has_llm = llm_router._has_llm

        try:
            llm_router.primary = mock_primary
            llm_router.fallback = mock_fallback
            llm_router._has_llm = False

            agent = SchemaAgent()
            assert agent.has_llm is False

            request = SchemaAnalyzeRequest(
                database="test_db",
                dbType="MySQL",
                tables=[
                    {
                        "tableName": "orders",
                        "comment": "订单表",
                        "columns": [
                            {"name": "id", "type": "INT", "nullable": False, "primaryKey": True},
                            {"name": "user_id", "type": "INT", "nullable": False},
                            {"name": "total_amount", "type": "DECIMAL(10,2)", "nullable": True},
                            {"name": "status", "type": "VARCHAR(20)", "nullable": True},
                        ],
                    }
                ],
            )

            response = await agent.analyze(request)

            # 验证：Mock 模式正常工作
            assert response.success is True
            assert response.mock is True
            assert response.result.summary.totalTables == 1
            assert response.result.summary.totalColumns == 4

        finally:
            llm_router.primary = original_primary
            llm_router.fallback = original_fallback
            llm_router._has_llm = original_has_llm


# ============================================================
# Provider 测试：retriable 分类
# ============================================================

class TestRetriableClassification:
    """测试异常分类逻辑"""

    def test_timeout_is_retriable(self):
        """超时异常应标记为可重试"""
        assert DeepSeekProvider._is_retriable(
            Exception("Connection timed out"), None
        ) is False  # 普通 Exception 不是 retriable 类型
        # 但这个设计是为了让 provider 内部根据 exc 类型来判断
        # 实际 OpenAI SDK 的异常类型需要通过类名判断

    def test_http_500_is_retriable(self):
        """HTTP 500 应标记为可重试"""
        assert DeepSeekProvider._is_retriable(
            Exception("Server error"), status_code=500
        ) is True

    def test_http_429_is_retriable(self):
        """HTTP 429 应标记为可重试"""
        assert DeepSeekProvider._is_retriable(
            Exception("Rate limited"), status_code=429
        ) is True

    def test_http_401_is_not_retriable(self):
        """HTTP 401 不应标记为可重试（认证问题）"""
        assert DeepSeekProvider._is_retriable(
            Exception("Unauthorized"), status_code=401
        ) is False

    def test_http_400_is_not_retriable(self):
        """HTTP 400 不应标记为可重试（请求错误）"""
        assert DeepSeekProvider._is_retriable(
            Exception("Bad request"), status_code=400
        ) is False

    def test_openai_timeout_class_name(self):
        """模拟 OpenAI SDK APITimeoutError 类名检测"""
        class APITimeoutError(Exception):
            pass

        exc = APITimeoutError("timeout")
        assert type(exc).__name__ == "APITimeoutError"
        assert DeepSeekProvider._is_retriable(exc, None) is True

    def test_openai_rate_limit_class_name(self):
        """模拟 OpenAI SDK RateLimitError 类名检测"""
        class RateLimitError(Exception):
            pass

        exc = RateLimitError("rate limited")
        assert type(exc).__name__ == "RateLimitError"
        assert DeepSeekProvider._is_retriable(exc, None) is True
