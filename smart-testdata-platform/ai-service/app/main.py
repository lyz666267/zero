"""
智能测试数据生成平台 — AI 服务入口
职责：LLM 调用 + Agent 编排，不直接操作数据库写入
"""
import os

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

app = FastAPI(
    title="Smart TestData Platform - AI Service",
    description="基于 LangChain Agent 的 Schema 理解与数据生成策略服务",
    version="1.0.0",
)

allowed_origins = [
    origin.strip()
    for origin in os.getenv(
        "CORS_ORIGINS",
        "http://localhost:5173,http://127.0.0.1:5173"
    ).split(",")
    if origin.strip()
]

# CORS 配置（允许 Java 后端调用）
app.add_middleware(
    CORSMiddleware,
    allow_origins=allowed_origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/health")
async def health_check():
    """健康检查端点"""
    return {"status": "ok", "service": "ai-service"}


@app.get("/api/ai/health")
async def ai_health():
    """AI 服务健康检查（供 Java 后端调用）"""
    return {
        "status": "ok",
        "primary_model": "deepseek-chat",
        "fallback_model": "qwen-plus",
    }


# 注册路由
from app.api.routes import router as ai_router
app.include_router(ai_router)


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("app.main:app", host="0.0.0.0", port=8000, reload=True)
