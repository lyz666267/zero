"""
LLM Router — 主备模型自动故障切换

路由策略：
    1. 优先调用主模型（DeepSeek）
    2. 主模型发生可重试错误（超时/429/5xx/连接错误）→ 自动切换备用模型（Qwen）
    3. 备用模型也失败 → 抛出 RouterExhaustedError → 调用方降级为 Mock 模式
    4. 主模型发生不可重试错误（401/400）→ 直接抛出，不切换备用

设计原则：
    - 调用方无需关心底层使用哪个模型
    - 故障切换对上层透明
    - 每次切换都记录详细日志，便于排查
"""
from loguru import logger

from app.llm.base import LLMProvider, LLMProviderError, RouterExhaustedError


class LLMRouter:
    """
    LLM 主备路由

    使用方式：
        router = LLMRouter(primary=deepseek_provider, fallback=qwen_provider)
        if router.has_llm:
            try:
                text = await router.chat(messages=[...])
            except RouterExhaustedError:
                # 所有模型失败，降级为 Mock
                result = mock_analysis()
    """

    def __init__(
        self,
        primary: LLMProvider,
        fallback: LLMProvider | None = None,
    ):
        """
        Args:
            primary: 主模型提供商
            fallback: 备用模型提供商（可选），设为 None 则不启用备用
        """
        self.primary = primary
        self.fallback = fallback
        self._has_llm = primary.is_available or (
            fallback is not None and fallback.is_available
        )

        providers = [primary.name]
        if fallback:
            providers.append(fallback.name)
        logger.info(
            f"LLMRouter 初始化: 主={primary.name}"
            f"({'可用' if primary.is_available else '不可用'})"
            + (f", 备={fallback.name}"
               f"({'可用' if fallback.is_available else '不可用'})"
               if fallback else "")
        )

    @property
    def has_llm(self) -> bool:
        """是否至少有一个 LLM 提供商可用"""
        return self._has_llm

    async def chat(
        self,
        messages: list[dict[str, str]],
        temperature: float = 0.1,
        max_tokens: int = 4096,
    ) -> str:
        """
        发送对话请求，自动处理主备切换

        Args:
            messages: 标准 OpenAI 消息列表
            temperature: 生成温度
            max_tokens: 最大输出 token 数

        Returns:
            LLM 响应文本

        Raises:
            RouterExhaustedError: 所有提供商均已失败，调用方应降级为 Mock
            LLMProviderError: 主模型发生不可重试的错误（如 401 认证失败）
        """
        errors: list[LLMProviderError] = []

        # ── 第 1 层：主模型 ──
        if self.primary.is_available:
            try:
                logger.debug(f"[LLMRouter] 尝试主模型: {self.primary.name}")
                result = await self.primary.chat(
                    messages, temperature, max_tokens
                )
                logger.debug(f"[LLMRouter] 主模型 {self.primary.name} 成功")
                return result
            except LLMProviderError as e:
                errors.append(e)
                if not e.retriable:
                    # 不可重试错误（如 401）→ 直接失败，不尝试备用
                    logger.error(
                        f"[LLMRouter] 主模型 {self.primary.name} "
                        f"发生不可重试错误，直接失败"
                    )
                    raise
                logger.warning(
                    f"[LLMRouter] 主模型 {self.primary.name} "
                    f"失败（可重试）: {e}"
                )
        else:
            logger.info(
                f"[LLMRouter] 主模型 {self.primary.name} 不可用，跳过"
            )

        # ── 第 2 层：备用模型 ──
        if self.fallback and self.fallback.is_available:
            try:
                logger.info(
                    f"[LLMRouter] 切换到备用模型: {self.fallback.name}"
                )
                result = await self.fallback.chat(
                    messages, temperature, max_tokens
                )
                logger.info(
                    f"[LLMRouter] 备用模型 {self.fallback.name} 成功"
                )
                return result
            except LLMProviderError as e:
                errors.append(e)
                logger.error(
                    f"[LLMRouter] 备用模型 {self.fallback.name} "
                    f"也失败: {e}"
                )
        else:
            logger.warning(
                f"[LLMRouter] 备用模型不可用或未配置"
            )

        # ── 所有模型耗尽 ──
        raise RouterExhaustedError(errors)

    @staticmethod
    def extract_json(text: str) -> str:
        """
        从 LLM 响应中提取 JSON

        处理 LLM 可能包裹 ```json ... ``` 的情况。
        此为静态方法，方便在 router 层面做 JSON 提取而不依赖 llm_service。
        """
        import re

        # 尝试匹配 ```json ... ```
        match = re.search(r"```(?:json)?\s*\n?([\s\S]*?)\n?```", text)
        if match:
            return match.group(1).strip()

        # 尝试匹配第一个 { 到最后一个 }
        start = text.find("{")
        end = text.rfind("}")
        if start != -1 and end != -1 and end > start:
            return text[start : end + 1]

        return text.strip()
