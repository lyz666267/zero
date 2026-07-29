"""
LLM 服务 — 统一封装 DeepSeek API 调用

支持：
- DeepSeek API（通过环境变量 DEEPSEEK_API_KEY）
- Mock 降级（无 API Key 时自动使用规则引擎生成计划）
"""
import json
import os
import re
from loguru import logger
from openai import OpenAI


class LLMService:
    """
    LLM 调用统一接口

    使用方式：
        service = LLMService()
        result = await service.chat(messages=[...])
    """

    def __init__(self):
        self.api_key = os.getenv("DEEPSEEK_API_KEY", "")
        self.base_url = os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com/v1")
        self.model = os.getenv("DEEPSEEK_MODEL", "deepseek-chat")
        self.has_key = bool(self.api_key)

        if self.has_key:
            self.client = OpenAI(api_key=self.api_key, base_url=self.base_url)
            logger.info(f"LLMService 初始化: model={self.model}, base_url={self.base_url}")
        else:
            self.client = None
            logger.warning("DEEPSEEK_API_KEY 未设置，将使用 mock 模式")

    async def chat(self, messages: list[dict[str, str]], temperature: float = 0.1,
                   max_tokens: int = 4096) -> str:
        """
        发送对话请求到 LLM

        Args:
            messages: 标准 OpenAI 消息列表 [{"role": "system"|"user"|"assistant", "content": "..."}]
            temperature: 生成温度 (0-2)
            max_tokens: 最大输出 token 数

        Returns:
            LLM 响应文本
        """
        if not self.has_key:
            raise ValueError("LLM API Key 未配置，请设置 DEEPSEEK_API_KEY 环境变量")

        try:
            response = self.client.chat.completions.create(
                model=self.model,
                messages=messages,
                temperature=temperature,
                max_tokens=max_tokens,
            )
            content = response.choices[0].message.content
            logger.debug(f"LLM 响应长度: {len(content)} 字符")
            return content
        except Exception as e:
            logger.error(f"LLM 调用失败: {e}")
            raise

    @staticmethod
    def extract_json(text: str) -> str:
        """
        从 LLM 响应中提取 JSON

        处理 LLM 可能包裹 ```json ... ``` 的情况
        """
        # 尝试匹配 ```json ... ```
        match = re.search(r"```(?:json)?\s*\n?([\s\S]*?)\n?```", text)
        if match:
            return match.group(1).strip()

        # 尝试匹配第一个 { 到最后一个 }
        start = text.find("{")
        end = text.rfind("}")
        if start != -1 and end != -1 and end > start:
            return text[start:end + 1]

        return text.strip()


# 单例
llm_service = LLMService()
