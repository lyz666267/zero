"""
DeepSeek LLM 提供商

封装 DeepSeek API（OpenAI 兼容接口），作为主模型。
使用 openai 库通过 DeepSeek 的 API 端点调用 deepseek-chat 模型。
"""
import os
import time
from loguru import logger
from openai import OpenAI

from app.llm.base import LLMProvider, LLMProviderError


class DeepSeekProvider(LLMProvider):
    """
    DeepSeek API 提供商

    环境变量：
        DEEPSEEK_API_KEY: API 密钥
        DEEPSEEK_BASE_URL: API 基础 URL（默认 https://api.deepseek.com/v1）
        DEEPSEEK_MODEL: 模型名称（默认 deepseek-chat）
    """

    def __init__(self):
        self.api_key = os.getenv("DEEPSEEK_API_KEY", "")
        self.base_url = os.getenv(
            "DEEPSEEK_BASE_URL", "https://api.deepseek.com/v1"
        )
        self.model = os.getenv("DEEPSEEK_MODEL", "deepseek-chat")
        self._available = bool(self.api_key)

        if self._available:
            self._client = OpenAI(api_key=self.api_key, base_url=self.base_url)
            logger.info(
                f"DeepSeekProvider 初始化: model={self.model}, "
                f"base_url={self.base_url}"
            )
        else:
            self._client = None
            logger.warning("DEEPSEEK_API_KEY 未设置，DeepSeek 不可用")

    @property
    def name(self) -> str:
        return "DeepSeek"

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
        调用 DeepSeek API

        Raises:
            LLMProviderError: API Key 未配置或调用失败
        """
        if not self._available:
            raise LLMProviderError(
                self.name,
                "DEEPSEEK_API_KEY 未配置，无法调用 DeepSeek API",
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
                    f"[DeepSeek] model={self.model} "
                    f"prompt_tokens={usage.prompt_tokens} "
                    f"completion_tokens={usage.completion_tokens} "
                    f"latency={elapsed_ms:.0f}ms"
                )
            else:
                logger.info(f"[DeepSeek] model={self.model} latency={elapsed_ms:.0f}ms")
            logger.debug(f"[DeepSeek] 响应长度: {len(content)} 字符")
            return content

        except Exception as e:
            logger.error(f"[DeepSeek] 调用失败: {type(e).__name__}: {e}")
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
        # OpenAI SDK 异常类型
        retriable_types = {
            "APITimeoutError",
            "RateLimitError",
            "APIConnectionError",
            "InternalServerError",
            "ServiceUnavailableError",
        }
        if exc_name in retriable_types:
            return True
        # 5xx 服务器错误可重试
        if status_code is not None and status_code >= 500:
            return True
        # 429 限流可重试
        if status_code == 429:
            return True
        # 4xx 客户端错误不可重试（如 401 认证失败、400 参数错误）
        return False


# 单例
deepseek_provider = DeepSeekProvider()
