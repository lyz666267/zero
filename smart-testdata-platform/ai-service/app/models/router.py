"""模型路由 — 主备切换逻辑"""
from langchain_openai import ChatOpenAI
from app.models.config import app_config
from loguru import logger


class LLMRouter:
    """主模型 + 备用模型路由器"""

    def __init__(self):
        self.primary = self._create_llm("deepseek")
        self.fallback = self._create_llm("qwen")

    def _create_llm(self, provider: str) -> ChatOpenAI:
        """创建 LangChain ChatOpenAI 实例"""
        if provider == "deepseek":
            base_url = app_config.deepseek_base_url
            api_key = app_config.deepseek_api_key
            model = app_config.deepseek_model
        else:
            base_url = app_config.qwen_base_url
            api_key = app_config.qwen_api_key
            model = app_config.qwen_model

        return ChatOpenAI(
            base_url=base_url,
            api_key=api_key,
            model=model,
            temperature=0.1,
            max_tokens=4096,
            timeout=app_config.timeout if hasattr(app_config, 'timeout') else 60,
            max_retries=2,
        )

    def get_llm(self) -> ChatOpenAI:
        """获取可用的 LLM 实例（默认返回主模型）"""
        if app_config.deepseek_api_key:
            return self.primary
        elif app_config.qwen_api_key:
            logger.warning("DeepSeek 未配置，降级到 Qwen")
            return self.fallback
        else:
            raise ValueError("未配置任何 LLM API Key！")
