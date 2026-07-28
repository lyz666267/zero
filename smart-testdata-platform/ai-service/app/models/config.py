"""模型配置 — 从环境变量读取"""
from pydantic_settings import BaseSettings


class AppConfig(BaseSettings):
    """全局配置"""
    primary_provider: str = "deepseek"
    fallback_provider: str = "qwen"

    deepseek_base_url: str = "https://api.deepseek.com/v1"
    deepseek_api_key: str = ""
    deepseek_model: str = "deepseek-chat"

    qwen_base_url: str = "https://dashscope.aliyuncs.com/compatible-mode/v1"
    qwen_api_key: str = ""
    qwen_model: str = "qwen-plus"

    class Config:
        env_file = ".env"
        env_file_encoding = "utf-8"


app_config = AppConfig()
