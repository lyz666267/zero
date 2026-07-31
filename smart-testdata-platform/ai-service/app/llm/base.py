"""
LLM Provider 统一接口

所有 LLM 提供商（DeepSeek、Qwen 等）必须实现此接口，
确保 LLMRouter 可以透明地在不同提供商之间切换。
"""
from abc import ABC, abstractmethod


class LLMProvider(ABC):
    """
    LLM 提供商抽象基类

    每个提供商需要实现：
    - name: 提供商名称（用于日志和错误追踪）
    - is_available: 是否可用（API Key 是否配置）
    - chat(): 发送对话请求并返回响应文本
    """

    @property
    @abstractmethod
    def name(self) -> str:
        """提供商名称，如 'DeepSeek'、'Qwen'"""
        ...

    @property
    @abstractmethod
    def is_available(self) -> bool:
        """检查提供商是否可用（API Key 已配置）"""
        ...

    @abstractmethod
    async def chat(
        self,
        messages: list[dict[str, str]],
        temperature: float = 0.1,
        max_tokens: int = 4096,
    ) -> str:
        """
        发送对话请求到 LLM

        Args:
            messages: 标准 OpenAI 消息列表
            temperature: 生成温度 (0-2)
            max_tokens: 最大输出 token 数

        Returns:
            LLM 响应文本

        Raises:
            LLMProviderError: 调用失败时抛出
        """
        ...


class LLMProviderError(Exception):
    """LLM 提供商调用错误"""

    def __init__(
        self,
        provider_name: str,
        message: str,
        status_code: int | None = None,
        retriable: bool = True,
    ):
        self.provider_name = provider_name
        self.status_code = status_code
        self.retriable = retriable  # True = 可重试（应切换到备用模型）
        super().__init__(f"[{provider_name}] {message}")


class RouterExhaustedError(Exception):
    """
    所有 LLM 提供商均已失败

    调用方（SchemaAgent / TestDataAgent）捕获此异常后，
    应降级为 Mock 规则引擎模式。
    """

    def __init__(self, errors: list[LLMProviderError]):
        self.errors = errors
        provider_list = ", ".join(e.provider_name for e in errors)
        super().__init__(
            f"所有 LLM 提供商均已失败 ({provider_list})，请降级为 Mock 模式"
        )
