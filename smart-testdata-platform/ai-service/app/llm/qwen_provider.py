"""
Qwen LLM 提供商（备用模型）

封装阿里云百炼 Qwen API（OpenAI 兼容接口），作为 DeepSeek 的备用模型。
当 DeepSeek 超时/限流/服务异常时，LLMRouter 自动切换到 Qwen。
"""
import os
import time
from loguru import logger
from openai import OpenAI

from app.llm.base import LLMProvider, LLMProviderError


class QwenProvider(LLMProvider):
    """
    阿里云百炼 Qwen API 提供商

    环境变量：
        QWEN_API_KEY: API 密钥（阿里云百炼平台）
        QWEN_BASE_URL: API 基础 URL（默认 dashscope 兼容模式端点）
        QWEN_MODEL: 模型名称（默认 qwen-plus）
    """

    def __init__(self):
        self.api_key = os.getenv("QWEN_API_KEY", "")
        self.base_url = os.getenv(
            "QWEN_BASE_URL",
            "https://dashscope.aliyuncs.com/compatible-mode/v1",
        )
        self.model = os.getenv("QWEN_MODEL", "qwen-plus")
        self._available = bool(self.api_key)

        if self._available:
            self._client = OpenAI(api_key=self.api_key, base_url=self.base_url)
            logger.info(
                f"QwenProvider 初始化: model={self.model}, "
                f"base_url={self.base_url}"
            )
        else:
            self._client = None
            logger.warning("QWEN_API_KEY 未设置，Qwen 不可用（将跳过备用切换）")

    @property
    def name(self) -> str:
        return "Qwen"

    @property
    def is_available(self) -> bool:
        return self._available

    async def chat(
        self,
        messages: list[dict[str, str]],
        temperature: float = 0.1,
        max_tokens: int = 4096,
    ) -> str:
        """
        调用 Qwen API

        Qwen 的 OpenAI 兼容接口支持标准的 messages 格式。
        系统提示词需要作为 role="system" 的消息传入。

        Raises:
            LLMProviderError: API Key 未配置或调用失败
        """
        if not self._available:
            raise LLMProviderError(
                self.name,
                "QWEN_API_KEY 未配置，无法调用 Qwen API",
            )

        try:
            start_time = time.monotonic()
            response = self._client.chat.completions.create(
                model=self.model,
                messages=messages,
                temperature=temperature,
                max_tokens=max_tokens,
                timeout=60,
            )
            elapsed_ms = (time.monotonic() - start_time) * 1000
            content = response.choices[0].message.content

            # 记录 token 消耗与延迟
            usage = response.usage
            if usage:
                logger.info(
                    f"[Qwen] model={self.model} "
                    f"prompt_tokens={usage.prompt_tokens} "
                    f"completion_tokens={usage.completion_tokens} "
                    f"latency={elapsed_ms:.0f}ms"
                )
            else:
                logger.info(f"[Qwen] model={self.model} latency={elapsed_ms:.0f}ms")
            return content

        except Exception as e:
            logger.error(f"[Qwen] 调用失败: {type(e).__name__}: {e}")
            status_code = getattr(e, "status_code", None)
            retriable = self._is_retriable(e, status_code)
            raise LLMProviderError(
                self.name,
                f"{type(e).__name__}: {e}",
                status_code=status_code,
                retriable=retriable,
            )

    @staticmethod
    def _is_retriable(exception: Exception, status_code: int | None) -> bool:
        """判断异常是否可重试（应切换到备用模型）"""
        exc_name = type(exception).__name__
        retriable_types = {
            "APITimeoutError",
            "RateLimitError",
            "APIConnectionError",
            "InternalServerError",
            "ServiceUnavailableError",
        }
        if exc_name in retriable_types:
            return True
        if status_code is not None and status_code >= 500:
            return True
        if status_code == 429:
            return True
        return False


# 单例
qwen_provider = QwenProvider()
