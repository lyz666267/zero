"""
FastAPI HTTP API 路由测试

使用 TestClient + 依赖 Mock 覆盖核心 AI 端点:
- POST /api/ai/generate-plan
- POST /api/ai/tool-agent
- POST /api/ai/detect-sensitive
"""
import json
import pytest
from unittest.mock import AsyncMock, patch, MagicMock
from fastapi.testclient import TestClient

from app.main import app
from app.schemas.generation_plan import (
    GenerationPlan, TablePlan, FieldPlan, FieldRange,
    GeneratePlanRequest, GeneratePlanResponse,
)
from tests.conftest import make_valid_schema, make_mock_plan_response

client = TestClient(app)


# ============================================================
# POST /api/ai/generate-plan 测试
# ============================================================

class TestGeneratePlanEndpoint:
    """测试 /api/ai/generate-plan 端点"""

    def test_generate_plan_success(self):
        """正常请求: 返回完整生成计划"""
        mock_response = make_mock_plan_response()

        with patch("app.api.routes.generation_chain.run", new_callable=AsyncMock) as mock_run:
            mock_run.return_value = mock_response

            response = client.post("/api/ai/generate-plan", json=make_valid_schema())

            assert response.status_code == 200
            data = response.json()
            assert data["success"] is True
            assert data["plan"]["taskName"] == "users 表测试数据生成"
            assert len(data["plan"]["tables"]) == 1
            assert data["plan"]["tables"][0]["table"] == "users"
            assert data["plan"]["tables"][0]["count"] == 1000
            assert len(data["plan"]["tables"][0]["fields"]) == 2
            assert data["mock"] is False

    def test_generate_plan_mock_mode(self):
        """LLM 不可用时: 返回 Mock 计划"""
        mock_response = GeneratePlanResponse(
            success=True,
            plan=GenerationPlan(
                taskName="Mock 生成计划",
                tables=[
                    TablePlan(
                        table="users",
                        count=100,
                        fields=[FieldPlan(name="username", generator="faker.name")],
                    ),
                ],
            ),
            mock=True,
        )

        with patch("app.api.routes.generation_chain.run", new_callable=AsyncMock) as mock_run:
            mock_run.return_value = mock_response

            response = client.post("/api/ai/generate-plan", json=make_valid_schema())

            assert response.status_code == 200
            data = response.json()
            assert data["success"] is True
            assert data["mock"] is True

    def test_generate_plan_empty_tables(self):
        """空表列表: 返回错误"""
        mock_response = GeneratePlanResponse(
            success=False,
            error="Schema 中没有表信息，请先分析数据库结构",
            mock=False,
        )

        with patch("app.api.routes.generation_chain.run", new_callable=AsyncMock) as mock_run:
            mock_run.return_value = mock_response

            payload = {
                "schema_data": {"database": "empty", "tables": []},
                "requirement": "生成数据",
            }
            response = client.post("/api/ai/generate-plan", json=payload)

            assert response.status_code == 200
            data = response.json()
            assert data["success"] is False
            assert "表信息" in data["error"]

    def test_generate_plan_invalid_json(self):
        """非法请求体: 返回 422"""
        response = client.post("/api/ai/generate-plan", json={"invalid": "data"})
        assert response.status_code == 422

    def test_generate_plan_missing_requirement(self):
        """缺少必填字段: 返回 422"""
        response = client.post("/api/ai/generate-plan", json={"schema_data": {"tables": []}})
        assert response.status_code == 422


# ============================================================
# POST /api/ai/tool-agent 测试
# ============================================================

class TestToolAgentEndpoint:
    """测试 /api/ai/tool-agent 端点"""

    def test_tool_agent_success(self):
        """正常请求: Tool Agent 返回结果"""
        mock_result = {
            "success": True,
            "final_answer": "数据库 test_db 包含 3 张表: users, orders, products",
            "tool_calls": [
                {
                    "tool_name": "get_schema",
                    "params": {"database": "test_db"},
                    "result": {"tableCount": 3},
                }
            ],
            "rounds": 1,
            "mock": False,
        }

        with patch("app.api.routes.tool_agent.run", new_callable=AsyncMock) as mock_run:
            mock_run.return_value = mock_result

            response = client.post("/api/ai/tool-agent", json={
                "requirement": "分析 test_db 数据库的 Schema 结构"
            })

            assert response.status_code == 200
            data = response.json()
            assert data["success"] is True
            assert "3 张表" in data["final_answer"]
            assert len(data["tool_calls"]) == 1
            assert data["tool_calls"][0]["tool_name"] == "get_schema"
            assert data["rounds"] == 1

    def test_tool_agent_mock_mode(self):
        """LLM 不可用时: Mock 模式返回"""
        mock_result = {
            "success": True,
            "final_answer": "Mock 模式: 已返回默认 Schema 分析",
            "tool_calls": [],
            "rounds": 0,
            "mock": True,
        }

        with patch("app.api.routes.tool_agent.run", new_callable=AsyncMock) as mock_run:
            mock_run.return_value = mock_result

            response = client.post("/api/ai/tool-agent", json={
                "requirement": "帮我看看数据库结构"
            })

            assert response.status_code == 200
            data = response.json()
            assert data["success"] is True
            assert data["mock"] is True

    def test_tool_agent_empty_requirement(self):
        """空需求: 由 Agent 自行处理"""
        mock_result = {
            "success": False,
            "final_answer": "需求不能为空，请提供有效的数据生成需求描述",
            "tool_calls": [],
            "rounds": 0,
            "mock": False,
        }

        with patch("app.api.routes.tool_agent.run", new_callable=AsyncMock) as mock_run:
            mock_run.return_value = mock_result

            response = client.post("/api/ai/tool-agent", json={"requirement": ""})

            assert response.status_code == 200
            data = response.json()
            assert data["success"] is False

    def test_tool_agent_missing_requirement(self):
        """缺少必填字段: 返回 422"""
        response = client.post("/api/ai/tool-agent", json={})
        assert response.status_code == 422


# ============================================================
# POST /api/ai/detect-sensitive 测试
# ============================================================

class TestDetectSensitiveEndpoint:
    """测试 /api/ai/detect-sensitive 端点"""

    def test_detect_sensitive_success(self):
        """LLM 检测到敏感字段"""
        llm_response = json.dumps({
            "fields": [
                {"columnName": "phone", "type": "PHONE", "confidence": 0.95,
                 "reason": "字段名 phone 匹配手机号关键词，样本值符合手机号格式"},
                {"columnName": "email", "type": "EMAIL", "confidence": 0.92,
                 "reason": "字段名包含 email，样本值包含 @ 符号"},
            ]
        })

        with patch("app.llm.llm_router") as mock_llm_router:
            mock_llm_router.has_llm = True
            mock_llm_router.chat = AsyncMock(return_value=f"```json\n{llm_response}\n```")
            mock_llm_router.extract_json = lambda text: text.replace("```json\n", "").replace("\n```", "")

            response = client.post("/api/ai/detect-sensitive", json={
                "columns": [
                    {"columnName": "phone", "columnType": "varchar(20)",
                     "columnComment": "用户手机号"},
                    {"columnName": "email", "columnType": "varchar(100)",
                     "columnComment": "邮箱"},
                    {"columnName": "nickname", "columnType": "varchar(50)",
                     "columnComment": "昵称"},
                ],
                "sampleValues": {
                    "phone": ["13812345678", "13900001111"],
                    "email": ["test@qq.com", "user@163.com"],
                    "nickname": ["小明", "小红"],
                },
            })

            assert response.status_code == 200
            data = response.json()
            assert data["success"] is True
            assert data["mock"] is False
            assert len(data["fields"]) == 2
            assert data["fields"][0]["columnName"] == "phone"
            assert data["fields"][0]["type"] == "PHONE"
            assert data["fields"][1]["columnName"] == "email"
            assert data["fields"][1]["type"] == "EMAIL"

    def test_detect_sensitive_no_llm(self):
        """LLM 不可用: 返回 mock 空结果"""
        with patch("app.llm.llm_router") as mock_llm_router:
            mock_llm_router.has_llm = False

            response = client.post("/api/ai/detect-sensitive", json={
                "columns": [
                    {"columnName": "phone", "columnType": "varchar(20)",
                     "columnComment": "手机号"},
                ],
                "sampleValues": {
                    "phone": ["13800000000"],
                },
            })

            assert response.status_code == 200
            data = response.json()
            assert data["success"] is True
            assert data["mock"] is True
            assert data["fields"] == []

    def test_detect_sensitive_empty_result_fallback_to_mock(self):
        """LLM 返回空 fields → 降级为 mock"""
        llm_response = json.dumps({"fields": []})

        with patch("app.llm.llm_router") as mock_llm_router:
            mock_llm_router.has_llm = True
            mock_llm_router.chat = AsyncMock(return_value=f"```json\n{llm_response}\n```")
            mock_llm_router.extract_json = lambda text: text.replace("```json\n", "").replace("\n```", "")

            response = client.post("/api/ai/detect-sensitive", json={
                "columns": [
                    {"columnName": "nickname", "columnType": "varchar(50)",
                     "columnComment": "昵称"},
                ],
                "sampleValues": {
                    "nickname": ["小明"],
                },
            })

            assert response.status_code == 200
            data = response.json()
            assert data["success"] is True
            assert data["mock"] is True
            assert data["fields"] == []

    def test_detect_sensitive_invalid_json(self):
        """非法请求体: 返回 422"""
        response = client.post("/api/ai/detect-sensitive", json={"bad": "data"})
        assert response.status_code == 422


# ============================================================
# 健康检查端点测试
# ============================================================

class TestHealthEndpoints:
    """测试健康检查端点"""

    def test_health_check(self):
        """GET /health — 服务健康检查"""
        response = client.get("/health")
        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "ok"
        assert data["service"] == "ai-service"

    def test_ai_health_check(self):
        """GET /api/ai/health — AI 服务健康检查"""
        response = client.get("/api/ai/health")
        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "ok"
        assert "primary_model" in data
        assert "fallback_model" in data
