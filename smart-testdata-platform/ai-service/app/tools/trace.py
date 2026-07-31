"""
Agent 执行轨迹记录器

职责：
    在 Agent 每次调用 Tool 时记录执行轨迹，包括：
    - 工具名称
    - 输入参数
    - 输出结果
    - 执行耗时

设计原则：
    - 非侵入式 — 不影响已有 Agent 流程
    - 线程安全 — 每个 Agent 运行实例持有独立 trace
    - 降级安全 — 记录失败不影响工具调用

使用方式：
    from app.tools.trace import AgentTrace

    trace = AgentTrace("tool_agent")
    trace.record_tool_call("get_schema", {"database": "my_db"}, result, 120.5)
    trace.record_final_answer("分析完成...")

    # 获取完整轨迹
    trace_data = trace.to_dict()
"""

import time
from typing import Any
from loguru import logger


class AgentTrace:
    """
    Agent 执行轨迹记录器

    记录每一步工具调用的完整信息，用于前端时间线展示。
    """

    def __init__(self, agent_name: str = "ToolAgent"):
        """
        初始化轨迹记录器

        Args:
            agent_name: Agent 名称标识
        """
        self.agent_name = agent_name
        self.start_time = time.time()
        self.steps: list[dict[str, Any]] = []

    def record_tool_call(
        self,
        tool_name: str,
        parameters: dict[str, Any],
        result: dict[str, Any],
        duration_ms: float,
    ) -> None:
        """
        记录一次工具调用

        Args:
            tool_name: 工具名称
            parameters: 输入参数
            result: 执行结果
            duration_ms: 执行耗时（毫秒）
        """
        step_num = len(self.steps) + 1
        success = result.get("success", False)

        step_record = {
            "step_number": step_num,
            "step_type": "TOOL_CALL",
            "action": f"调用工具: {tool_name}",
            "tool_name": tool_name,
            "status": "SUCCESS" if success else "FAILED",
            "execution_time": round(duration_ms),
            "input_data": self._safe_serialize(parameters),
            "output_data": self._safe_summary(result),
        }
        self.steps.append(step_record)
        logger.debug(
            f"AgentTrace: step={step_num} tool={tool_name} "
            f"status={'SUCCESS' if success else 'FAILED'} "
            f"time={round(duration_ms)}ms"
        )

    def record_step(
        self,
        step_type: str,
        action: str,
        input_data: Any = None,
        output_data: Any = None,
        tool_name: str = "",
        status: str = "SUCCESS",
        execution_time: float = 0.0,
    ) -> None:
        """
        记录通用步骤（非工具调用）

        Args:
            step_type: 步骤类型
            action: 动作描述
            input_data: 输入数据（可选）
            output_data: 输出数据（可选）
            tool_name: 关联工具名（可选）
            status: 执行状态
            execution_time: 耗时（毫秒）
        """
        step_num = len(self.steps) + 1
        step_record = {
            "step_number": step_num,
            "step_type": step_type,
            "action": action,
            "tool_name": tool_name,
            "status": status,
            "execution_time": round(execution_time),
            "input_data": self._safe_serialize(input_data),
            "output_data": self._safe_serialize(output_data),
        }
        self.steps.append(step_record)
        logger.debug(
            f"AgentTrace: step={step_num} type={step_type} action={action}"
        )

    def record_final_answer(self, answer: str) -> None:
        """
        记录最终回答

        Args:
            answer: 最终回答文本
        """
        step_num = len(self.steps) + 1
        self.steps.append({
            "step_number": step_num,
            "step_type": "COMPLETE",
            "action": "Agent 回答完成",
            "tool_name": "",
            "status": "SUCCESS",
            "execution_time": 0,
            "input_data": None,
            "output_data": self._safe_serialize(answer[:500]),
        })

    def get_total_time(self) -> float:
        """获取从开始到现在的总耗时（毫秒）"""
        return (time.time() - self.start_time) * 1000

    def to_dict(self) -> dict[str, Any]:
        """
        导出为 dict，方便序列化返回

        Returns:
            dict: 包含 agent_name、total_time、steps 的完整轨迹
        """
        return {
            "agent_name": self.agent_name,
            "total_time_ms": round(self.get_total_time()),
            "steps": self.steps,
        }

    # ==================== 私有方法 ====================

    @staticmethod
    def _safe_serialize(obj: Any) -> str | None:
        """安全序列化对象为字符串"""
        if obj is None:
            return None
        if isinstance(obj, str):
            # 截断过长字符串
            return obj[:1000] if len(obj) > 1000 else obj
        try:
            import json
            result = json.dumps(obj, ensure_ascii=False, default=str)
            return result[:2000] if len(result) > 2000 else result
        except Exception:
            return str(obj)[:500]

    @staticmethod
    def _safe_summary(result: dict[str, Any]) -> str | None:
        """安全提取结果摘要"""
        if not result:
            return None
        if not result.get("success", False):
            return result.get("error", "工具执行失败")
        data = result.get("data")
        if data is None:
            return "执行成功"
        if isinstance(data, dict):
            # 提取关键统计
            summary_parts = []
            for key in ("tableCount", "relationCount", "fieldCount", "message"):
                if key in data:
                    summary_parts.append(f"{key}={data[key]}")
            if summary_parts:
                return ", ".join(summary_parts)
            if "samples" in data:
                field_count = len(data["samples"])
                return f"获取到 {field_count} 个字段的样本数据"
            return f"返回 {len(data)} 个字段"
        if isinstance(data, list):
            return f"返回 {len(data)} 条记录"
        return str(data)[:200]
