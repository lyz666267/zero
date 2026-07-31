"""
Tool Calling Agent

职责：
    接收用户需求 → LLM 决定调用哪些工具 → 自动执行工具 → 返回结果给 LLM → 循环至完成

流程：
    1. 用户输入：需求描述（如"分析 my_shop 数据库的 Schema"）
    2. LLM 输出 action: {"action": "tool_name", "parameters": {...}}
    3. Agent 通过 ToolRegistry 自动执行对应工具
    4. 工具结果返回给 LLM
    5. LLM 继续输出下一个 action 或最终回答
    6. 最多 N 轮（默认 5），防止无限循环

设计原则：
    - ReAct 风格：Thought → Action → Observation → Thought → ... → Final Answer
    - LLM + Mock 双模式，与其他 Agent 保持一致
    - Mock 模式：模拟工具调用流程，返回合理的结果
    - AgentTrace：每次工具调用自动记录轨迹（不侵入已有流程）
"""
import asyncio
import json
import re
from loguru import logger

from app.llm import llm_router, RouterExhaustedError, LLMProviderError
from app.tools import tool_registry
from app.tools.trace import AgentTrace

# ============================================================
# System Prompt
# ============================================================

TOOL_AGENT_SYSTEM_PROMPT = """你是一个拥有工具调用能力的 AI Agent。你可以使用工具来获取数据库 Schema 信息、样本数据和关系信息。

## 工具调用格式

当你需要使用工具时，严格按照以下 JSON 格式输出：

```json
{
  "thought": "我需要先了解数据库中有哪些表...",
  "action": "get_schema",
  "parameters": {
    "database": "my_shop"
  }
}
```

**重要规则：**
1. `thought` — 你的推理过程，说明为什么要调用这个工具
2. `action` — 必须是一个可用工具的名称
3. `parameters` — 传递给工具的参数，必须符合工具的参数要求
4. 每次只能调用一个工具，调用后等待结果再决定下一步

## 最终回答格式

当你已经收集到足够信息，可以给出最终回答时，使用以下格式：

```json
{
  "thought": "我已经获取了所有需要的信息...",
  "final_answer": "这里是你的完整回答..."
}
```

## 核心原则

1. **逐步收集信息**：先了解数据库结构（get_schema），再获取样本数据（get_sample），最后查关系（get_relations）
2. **不要猜测**：需要什么信息就调用相应工具获取，不要凭空编造
3. **适时结束**：当信息足够回答用户问题时，给出 final_answer 而不是继续调用工具
4. **错误处理**：如果工具返回 error，尝试其他方式获取信息或告知用户
"""


# ============================================================
# ReAct 循环保护常量
# ============================================================

TOOL_CALL_TIMEOUT = 30       # 单次 LLM 调用超时（秒）
TOOL_EXEC_TIMEOUT = 30       # 单次工具执行超时（秒）
MAX_DUPLICATE_CALLS = 2      # 同一工具+参数最大重复调用次数

# ============================================================
# Tool Calling Agent
# ============================================================

class ToolAgent:
    """
    Tool Calling Agent

    支持 ReAct 风格的 "思考→行动→观察" 循环。

    两种模式：
    1. LLM 模式 — 调用 LLM 决策工具选择 + 解释结果
    2. Mock 模式 — 模拟工具调用流程，直接执行合理工具组合
    """

    MAX_ROUNDS = 5

    def __init__(self):
        self.has_llm = llm_router.has_llm
        self.tools_desc = tool_registry.get_tools_description()
        logger.info(f"ToolAgent 初始化: mode={'LLM' if self.has_llm else 'MOCK'}, tools={tool_registry.list_tools()}")

    async def run(self, requirement: str) -> dict:
        """
        执行工具调用 Agent

        Args:
            requirement: 用户需求描述

        Returns:
            dict 包含：
                - success: bool
                - final_answer: str | None — 最终回答
                - tool_calls: list — 工具调用历史
                - rounds: int — 总轮数
                - mock: bool — 是否使用 Mock 模式
                - trace: dict | None — Agent 执行轨迹（Phase 8.2）
        """
        logger.info(f"ToolAgent.run: requirement='{requirement[:80]}...'")

        # 创建执行轨迹记录器
        trace = AgentTrace(agent_name="ToolAgent")

        if not tool_registry.tool_count:
            return {
                "success": False,
                "final_answer": None,
                "tool_calls": [],
                "rounds": 0,
                "mock": False,
                "error": "没有注册任何工具",
                "trace": trace.to_dict(),
            }

        if self.has_llm:
            try:
                result = await self._run_with_llm(requirement, trace)
                trace.record_step("COMPLETE", "Agent 执行完成",
                    output_data={"final_answer": result.get("final_answer", "")[:200]},
                    execution_time=trace.get_total_time())
                result["trace"] = trace.to_dict()
                return result
            except (LLMProviderError, RouterExhaustedError, json.JSONDecodeError, asyncio.TimeoutError) as e:
                logger.warning(f"LLM Tool Agent 失败，降级为 Mock: {e}")
                trace.record_step("FALLBACK", f"LLM 失败降级 Mock: {e}",
                    status="WARNING")
                result = await self._run_mock(requirement, trace)
                result["trace"] = trace.to_dict()
                return result

        result = await self._run_mock(requirement, trace)
        trace.record_step("COMPLETE", "Mock Agent 执行完成",
            output_data={"final_answer": result.get("final_answer", "")[:200]},
            execution_time=trace.get_total_time())
        result["trace"] = trace.to_dict()
        return result

    async def _run_with_llm(self, requirement: str, trace: AgentTrace | None = None) -> dict:
        """LLM 模式 — ReAct 循环"""
        if trace is None:
            trace = AgentTrace(agent_name="ToolAgent")
        tool_calls = []
        messages = [
            {"role": "system", "content": TOOL_AGENT_SYSTEM_PROMPT + "\n\n" + self.tools_desc},
            {"role": "user", "content": requirement},
        ]

        # 工具调用历史：用于重复调用检测
        call_history: dict[tuple, int] = {}

        for round_num in range(1, self.MAX_ROUNDS + 1):
            logger.info(f"ToolAgent [LLM] 第 {round_num}/{self.MAX_ROUNDS} 轮")

            try:
                response_text = await asyncio.wait_for(
                    llm_router.chat(messages, temperature=0.1, max_tokens=4096),
                    timeout=TOOL_CALL_TIMEOUT,
                )
            except asyncio.TimeoutError:
                logger.warning(f"LLM 调用超时 ({TOOL_CALL_TIMEOUT}s)，降级为 Mock")
                return await self._run_mock(requirement, trace)
            except RouterExhaustedError:
                logger.warning("LLM 模型耗尽，降级为 Mock 补全剩余工具调用")
                return await self._run_mock(requirement, trace)

            # 解析 LLM 输出
            action = self._parse_action(response_text)

            if action is None:
                logger.warning(f"无法解析 LLM 输出: {response_text[:200]}")
                tool_calls.append({
                    "round": round_num,
                    "raw_response": response_text[:500],
                    "parse_error": True,
                })
                continue

            # 检查是否为最终回答
            if action.get("final_answer"):
                logger.info(f"ToolAgent [LLM] 第 {round_num} 轮返回最终答案")
                tool_calls.append({
                    "round": round_num,
                    "final": True,
                    "thought": action.get("thought", ""),
                    "final_answer": action["final_answer"],
                })
                return {
                    "success": True,
                    "final_answer": action["final_answer"],
                    "tool_calls": tool_calls,
                    "rounds": round_num,
                    "mock": False,
                }

            # 调用工具
            tool_name = action.get("action", "")
            parameters = action.get("parameters", {})

            if not tool_name:
                logger.warning("LLM 未输出有效的 action")
                tool_calls.append({
                    "round": round_num,
                    "error": "no_action",
                    "raw": response_text[:300],
                })
                continue

            logger.info(f"ToolAgent [LLM] 调用工具: {tool_name}({parameters})")

            # 重复调用检测：同一工具+参数超过阈值时注入提示引导 LLM 改变策略
            params_key = json.dumps(parameters, sort_keys=True, ensure_ascii=False)
            call_key = (tool_name, params_key)
            call_count = call_history.get(call_key, 0) + 1
            call_history[call_key] = call_count

            if call_count > MAX_DUPLICATE_CALLS:
                logger.warning(
                    f"重复调用检测: {tool_name}({parameters}) 已调用 {call_count} 次"
                )

            # 执行工具（带超时保护）
            try:
                result = await asyncio.wait_for(
                    tool_registry.execute(tool_name, parameters),
                    timeout=TOOL_EXEC_TIMEOUT,
                )
            except asyncio.TimeoutError:
                logger.warning(f"工具执行超时 ({TOOL_EXEC_TIMEOUT}s): {tool_name}")
                result = {
                    "success": False,
                    "error": f"工具 {tool_name} 执行超时（{TOOL_EXEC_TIMEOUT}秒）",
                }

            tool_calls.append({
                "round": round_num,
                "action": tool_name,
                "parameters": parameters,
                "result": result,
                "thought": action.get("thought", ""),
            })

            # 记录 AgentTrace
            trace.record_tool_call(
                tool_name=tool_name,
                parameters=parameters,
                result=result,
                duration_ms=result.get("execution_time_ms", 0),
            )

            # 将工具结果反馈给 LLM（重复调用时注入策略调整提示）
            observation = self._format_observation(result)
            if call_count > MAX_DUPLICATE_CALLS:
                observation += (
                    f"\n\n⚠️ **重要提示**: 工具 `{tool_name}` 使用相同参数已被调用 {call_count} 次。"
                    f"请尝试使用不同的参数或换用其他工具获取信息，不要再次重复相同的调用。"
                )
            messages.append({"role": "assistant", "content": response_text})
            messages.append({"role": "user", "content": observation})

        # 达到最大轮数
        logger.warning(f"达到最大轮数 {self.MAX_ROUNDS}，生成总结")
        final_answer = self._summarize_calls(tool_calls, requirement)
        return {
            "success": True,
            "final_answer": final_answer,
            "tool_calls": tool_calls,
            "rounds": self.MAX_ROUNDS,
            "mock": False,
        }

    async def _run_mock(self, requirement: str, trace: AgentTrace | None = None) -> dict:
        """
        Mock 模式 — 模拟工具调用流程

        根据需求关键词自动决定调用哪些工具，并按合理顺序执行。
        """
        if trace is None:
            trace = AgentTrace(agent_name="ToolAgent")
        logger.info(f"ToolAgent [Mock] requirement='{requirement[:80]}...'")

        tool_calls = []
        req_lower = requirement.lower()

        # 确定要调用哪些工具
        tools_to_call: list[tuple[str, dict]] = []

        # 提取数据库名（支持 "my_shop 数据库" 和 "数据库 my_shop" 两种模式）
        db_match = re.search(
            "([a-zA-Z_][\\w]*)\\s*(?:数据库|database|db)|(?:数据库|database|db)\\s*[：:\"\"\\s]*([a-zA-Z_][\\w]*)",
            requirement, re.IGNORECASE,
        )
        if db_match:
            database = db_match.group(1) or db_match.group(2)
        else:
            database = "unknown_db"

        # Schema 关键词
        if any(kw in req_lower for kw in ["schema", "表", "结构", "字段", "列", "table", "column", "analyze", "分析"]):
            tools_to_call.append(("get_schema", {"database": database}))

        # 样本关键词（注意："数据"太宽泛，避免匹配"数据库"）
        if any(kw in req_lower for kw in ["样本", "采样", "真实数据", "实际值", "sample", "示例数据"]):
            table_match = re.search(
                "(?:表|table)\\s*[：:\"\"\\s]*([a-zA-Z_][\\w]*)", requirement, re.IGNORECASE,
            )
            table = table_match.group(1) if table_match else "users"
            tools_to_call.append(("get_sample", {"database": database, "table": table, "limit": 5}))

        # 关系关键词
        if any(kw in req_lower for kw in ["关系", "外键", "关联", "依赖", "relation", "fk", "foreign", "dependency"]):
            tools_to_call.append(("get_relations", {"database": database}))

        # 如果没有匹配到任何关键词 → 默认调用 Schema
        if not tools_to_call:
            tools_to_call.append(("get_schema", {"database": database}))

        # 逐个执行
        for i, (tool_name, params) in enumerate(tools_to_call, 1):
            logger.info(f"ToolAgent [Mock] 第 {i} 轮: {tool_name}({params})")
            result = await tool_registry.execute(tool_name, params)
            tool_calls.append({
                "round": i,
                "action": tool_name,
                "parameters": params,
                "result": result,
                "mock": True,
            })
            # 记录 AgentTrace
            trace.record_tool_call(
                tool_name=tool_name,
                parameters=params,
                result=result,
                duration_ms=result.get("execution_time_ms", 0),
            )

        # 生成最终回答
        final_answer = self._summarize_calls(tool_calls, requirement)

        return {
            "success": True,
            "final_answer": final_answer,
            "tool_calls": tool_calls,
            "rounds": len(tool_calls),
            "mock": True,
        }

    # ==================== 辅助方法 ====================

    def _parse_action(self, text: str) -> dict | None:
        """
        解析 LLM 输出的 action JSON

        支持格式：
        1. ```json {...} ```
        2. ``` {...} ```
        3. 裸 JSON
        """
        # 提取 JSON
        json_text = llm_router.extract_json(text)

        try:
            action = json.loads(json_text)
            # 验证必要字段
            if "action" in action or "final_answer" in action:
                return action
            logger.warning(f"JSON 缺少 action/final_answer: {list(action.keys())}")
            return None
        except json.JSONDecodeError as e:
            logger.warning(f"JSON 解析失败: {e}")
            return None

    def _format_observation(self, result: dict) -> str:
        """将工具执行结果格式化为 LLM 可理解的 Observation"""
        if result.get("success"):
            data = result.get("data", {})
            return f"**工具执行结果 (Observation):**\n```json\n{json.dumps(data, ensure_ascii=False, indent=2)}\n```\n\n请根据以上结果决定下一步：继续调用工具或给出 final_answer。"
        else:
            error = result.get("error", "未知错误")
            return f"**工具执行失败:** {error}\n\n请尝试其他方式获取信息，或给出 final_answer 说明无法完成。"

    def _summarize_calls(self, tool_calls: list[dict], requirement: str) -> str:
        """基于工具调用结果生成最终总结"""
        parts = [f"## 工具调用完成\n\n根据需求「{requirement}」，执行了 {len(tool_calls)} 次工具调用：\n"]

        for tc in tool_calls:
            action = tc.get("action", "unknown")
            result = tc.get("result", {})
            if result.get("success"):
                data = result.get("data", {})
                if isinstance(data, dict):
                    # 提取关键统计
                    if "tableCount" in data:
                        parts.append(f"- **{action}**: 获取到 {data['tableCount']} 张表\n")
                    elif "relationCount" in data:
                        parts.append(f"- **{action}**: 获取到 {data['relationCount']} 条外键关系\n")
                    elif "samples" in data:
                        field_count = len(data.get("samples", {}))
                        parts.append(f"- **{action}**: 获取到 {field_count} 个字段的样本数据\n")
                    else:
                        parts.append(f"- **{action}**: 执行成功\n")
                else:
                    parts.append(f"- **{action}**: 执行成功\n")
            else:
                error = result.get("error", "未知错误")
                parts.append(f"- **{action}**: 失败 — {error}\n")

        parts.append(f"\n共 {len(tool_calls)} 轮工具调用完成。")

        return "\n".join(parts)


# 单例
tool_agent = ToolAgent()
