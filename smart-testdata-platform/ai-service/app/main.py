"""
智能测试数据生成平台 — AI 服务入口
职责：LLM 调用 + Agent 编排，不直接操作数据库写入
"""
import os
import uuid
import time
from contextvars import ContextVar

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from loguru import logger

app = FastAPI(
    title="Smart TestData Platform - AI Service",
    description="基于 LangChain Agent 的 Schema 理解与数据生成策略服务",
    version="1.0.0",
)

# ============================================================
# Request ID — 通过 ContextVar 在线程/协程间传递
# ============================================================
request_id_var: ContextVar[str] = ContextVar("request_id", default="")


# ============================================================
# 中间件：Request ID
# ============================================================

@app.middleware("http")
async def request_id_middleware(request: Request, call_next):
    """为每个请求生成 X-Request-ID，注入日志 context，添加到响应头"""
    request_id = request.headers.get("X-Request-ID", str(uuid.uuid4()))
    request_id_var.set(request_id)
    with logger.contextualize(request_id=request_id):
        start_time = time.monotonic()
        response = await call_next(request)
        elapsed_ms = (time.monotonic() - start_time) * 1000
        response.headers["X-Request-ID"] = request_id
        logger.info(
            f"{request.method} {request.url.path} → {response.status_code} "
            f"({elapsed_ms:.1f}ms)"
        )
        return response


# ============================================================
# CORS 配置（允许 Java 后端调用）
# ============================================================

allowed_origins = [
    origin.strip()
    for origin in os.getenv(
        "CORS_ORIGINS",
        "http://localhost:5173,http://127.0.0.1:5173"
    ).split(",")
    if origin.strip()
]

app.add_middleware(
    CORSMiddleware,
    allow_origins=allowed_origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# ============================================================
# 全局异常处理
# ============================================================

@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    """全局异常兜底：统一返回 JSON，不暴露内部 traceback"""
    logger.exception(f"未处理异常: {request.method} {request.url.path} — {exc}")
    return JSONResponse(
        status_code=500,
        content={"success": False, "error": "Internal server error"},
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
