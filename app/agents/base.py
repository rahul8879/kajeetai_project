"""Base class definitions for role agents."""

from abc import ABC, abstractmethod
from typing import List

from app.models import AgentResponse, ChatMessage, ChatRequest, ToolTrace


class AgentContext:
    """Context passed to agents."""

    def __init__(
        self,
        request: ChatRequest,
        history: List[ChatMessage],
    ) -> None:
        self.request = request
        self.history = history
        self.tool_traces: List[ToolTrace] = []

    def add_trace(self, trace: ToolTrace) -> None:
        self.tool_traces.append(trace)


class BaseAgent(ABC):
    """Common entry point for all role agents."""

    @abstractmethod
    def run(self, context: AgentContext) -> AgentResponse:
        """Execute the agent workflow."""

