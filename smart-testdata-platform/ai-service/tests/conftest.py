import pytest

from app.llm.base import LLMProvider, LLMProviderError
from app.schemas.generation_plan import (
    FieldPlan,
    GeneratePlanResponse,
    GenerationPlan,
    TablePlan,
)


class MockLLMProvider(LLMProvider):
    """Test-only LLM provider with fully controllable behavior."""

    def __init__(self, name: str = "Mock", available: bool = True):
        self._name = name
        self._available = available
        self._response: str | None = None
        self._error: LLMProviderError | None = None
        self.call_count = 0

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
        self._response = response
        self._error = None

    def set_error(self, error: LLMProviderError):
        self._error = error
        self._response = None


def make_valid_schema() -> dict:
    """Build a valid schema request payload."""
    return {
        "schema_data": {
            "database": "test_db",
            "dbType": "MySQL",
            "tables": [
                {
                    "tableName": "users",
                    "comment": "user table",
                    "columns": [
                        {"name": "id", "type": "INT", "nullable": False, "primaryKey": True},
                        {"name": "username", "type": "VARCHAR(50)", "nullable": False},
                        {"name": "email", "type": "VARCHAR(100)", "nullable": False},
                    ],
                },
            ],
        },
        "requirement": "generate 1000 user records",
    }


def make_mock_plan_response() -> GeneratePlanResponse:
    """Build a mock generation plan response."""
    return GeneratePlanResponse(
        success=True,
        plan=GenerationPlan(
            taskName="users 表测试数据生成",
            tables=[
                TablePlan(
                    table="users",
                    count=1000,
                    fields=[
                        FieldPlan(name="username", generator="faker.name"),
                        FieldPlan(name="email", generator="faker.email"),
                    ],
                ),
            ],
        ),
        mock=False,
    )


@pytest.fixture
def mock_llm_provider():
    return MockLLMProvider()


@pytest.fixture
def schema_builder():
    return make_valid_schema


@pytest.fixture
def plan_builder():
    return make_mock_plan_response
