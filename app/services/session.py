"""Simple in-memory session store for conversation history."""

from collections import deque
from typing import Deque, Dict, List

from app.config import get_settings
from app.models import ChatMessage


class SessionStore:
    """Maintains per-session chat history."""

    def __init__(self) -> None:
        self._sessions: Dict[str, Deque[ChatMessage]] = {}
        self._settings = get_settings()

    def append(self, session_id: str, message: ChatMessage) -> None:
        history = self._sessions.setdefault(
            session_id, deque(maxlen=self._settings.max_history_messages)
        )
        history.append(message)

    def get_history(self, session_id: str) -> List[ChatMessage]:
        history = self._sessions.get(session_id)
        if not history:
            return []
        return list(history)

    def clear(self, session_id: str) -> None:
        if session_id in self._sessions:
            del self._sessions[session_id]
