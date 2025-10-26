"""Pydantic models for request/response contracts."""

from enum import Enum
from typing import Dict, List, Optional
from pydantic import BaseModel, Field


class AgentRole(str, Enum):
    developer = "developer"
    sales = "sales"
    support = "support"


class ChatMessage(BaseModel):
    role: str
    content: str


class ChatRequest(BaseModel):
    session_id: str = Field(..., example="session-123")
    mode: AgentRole = Field(..., description="Role-specific agent to engage.")
    message: str = Field(..., description="User question or task.")
    metadata: Optional[Dict[str, str]] = Field(default=None)


class ToolCall(BaseModel):
    name: str
    arguments: Dict[str, str]


class ToolTrace(BaseModel):
    tool: str
    input: Dict[str, object]
    output: Dict[str, object]


class AgentResponse(BaseModel):
    session_id: str
    mode: AgentRole
    answer: str
    explanation: str
    actions: List[str]
    references: List[str]
    teach_back_prompt: Optional[str]
    tool_traces: List[ToolTrace] = Field(default_factory=list)
    history: List[ChatMessage] = Field(default_factory=list)
