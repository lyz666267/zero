"""
LLM 模块 — 统一 LLM 调用 + 主备自动故障切换

架构：
    LLMProvider (抽象接口)
    ├── DeepSeekProvider  — 主模型（deepseek-chat）
    └── QwenProvider      — 备用模型（qwen-plus）

    LLMRouter (故障切换)
    └── 主模型 → 备用模型 → RouterExhaustedError → Mock 降级

使用方式：
    from app.llm import llm_router

    try:
        text = await llm_router.chat(messages=[...])
    except RouterExhaustedError:
        # 降级为 Mock
        result = mock_analysis()
"""
from app.llm.base import LLMProvider, LLMProviderError, RouterExhaustedError
from app.llm.deepseek_provider import DeepSeekProvider, deepseek_provider
from app.llm.qwen_provider import QwenProvider, qwen_provider
from app.llm.router import LLMRouter

# 全局单例路由器
llm_router = LLMRouter(
    primary=deepseek_provider,
    fallback=qwen_provider,
)

__all__ = [
    "LLMProvider",
    "LLMProviderError",
    "RouterExhaustedError",
    "DeepSeekProvider",
    "deepseek_provider",
    "QwenProvider",
    "qwen_provider",
    "LLMRouter",
    "llm_router",
]
