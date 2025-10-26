"""Entry point for the Kajeet AI Learning Buddy FastAPI application."""

from fastapi import Depends, FastAPI, HTTPException

from app.config import Settings, get_settings
from app.models import AgentResponse, ChatRequest
from app.services.conversation import conversation_service


def get_app(settings: Settings = Depends(get_settings)) -> FastAPI:
    return FastAPI(
        title=settings.app_name,
        version="0.1.0",
        docs_url="/docs",
        redoc_url="/redoc",
    )


app = FastAPI(title="Kajeet AI Learning Buddy", version="0.1.0")


@app.get("/healthz")
def healthcheck(settings: Settings = Depends(get_settings)) -> dict:
    return {"status": "ok", "environment": settings.environment}


@app.post("/chat", response_model=AgentResponse)
def chat(request: ChatRequest) -> AgentResponse:
    try:
        response = conversation_service.handle_message(request)
        return response
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc

