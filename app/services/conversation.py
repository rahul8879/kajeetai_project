"""Conversation orchestration service connecting requests to role agents."""

from typing import Dict

from app.agents.base import AgentContext, BaseAgent
from app.agents.developer import DeveloperAgent
from app.agents.sales import SalesAgent
from app.agents.support import SupportAgent
from app.models import AgentResponse, AgentRole, ChatMessage, ChatRequest
from app.services.session import SessionStore


class ConversationService:
    """Dispatches chat requests to mode-specific agents and manages memory."""

    def __init__(self) -> None:
        self._sessions = SessionStore()
        self._agents: Dict[AgentRole, BaseAgent] = {
            AgentRole.developer: DeveloperAgent(),
            AgentRole.sales: SalesAgent(),
            AgentRole.support: SupportAgent(),
        }

    def handle_message(self, request: ChatRequest) -> AgentResponse:
        if request.mode not in self._agents:
            raise ValueError(f"Unsupported mode {request.mode}")

        # Persist the user turn
        self._sessions.append(
            request.session_id,
            ChatMessage(role="user", content=request.message),
        )
        history = self._sessions.get_history(request.session_id)

        agent = self._agents[request.mode]
        context = AgentContext(request=request, history=history)
        response = agent.run(context)

        # Save assistant turn so future context is available
        self._sessions.append(
            request.session_id,
            ChatMessage(role="assistant", content=response.answer),
        )
        response.history = self._sessions.get_history(request.session_id)
        return response


conversation_service = ConversationService()
