"""LLM integration service supporting OpenAI and stubbed fallbacks."""

import logging
from typing import Dict, List

from app.config import get_settings

logger = logging.getLogger(__name__)


def _format_insights(insights: Dict[str, List[str]]) -> str:
    """Convert structured insights into a readable bullet section for prompting."""
    if not insights:
        return "No supplemental insights supplied."
    sections = []
    for key, value in insights.items():
        if isinstance(value, list):
            if value and isinstance(value[0], dict):
                joined_items = []
                for item in value:
                    module = item.get("module")
                    line = item.get("line")
                    snippet = item.get("snippet", "").replace("\n", " ").strip()
                    joined_items.append(f"{module}:{line} -> {snippet}")
                joined = " | ".join(joined_items)
            else:
                joined = "; ".join(str(item) for item in value if item)
        else:
            joined = str(value)
        sections.append(f"{key}: {joined}".strip())
    return "\n".join(sections)


class LLMService:
    """Encapsulates LLM provider selection and response composition."""

    def __init__(self) -> None:
        self._settings = get_settings()
        self._provider = self._settings.llm_provider.lower()
        self._client = None
        if self._provider == "openai":
            self._client = self._init_openai_client()
            if self._client is None:
                raise RuntimeError(
                    "Failed to initialize OpenAI client. Verify OPENAI_API_KEY and installation."
                )

    def _init_openai_client(self):
        try:
            from openai import OpenAI  # type: ignore
        except ImportError as exc:
            raise RuntimeError("openai package is not installed") from exc

        if not self._settings.openai_api_key:
            raise RuntimeError("OPENAI_API_KEY is not set in the environment")

        client = OpenAI(
            api_key=self._settings.openai_api_key,
            base_url=self._settings.openai_base_url,
            organization=self._settings.openai_organization,
        )
        return client

    def compose_answer(
        self,
        mode: str,
        prompt: str,
        insights: Dict[str, List[str]],
    ) -> str:
        """Compose the assistant answer using the configured provider."""
        if self._provider == "openai":
            if not self._client:
                raise RuntimeError("OpenAI client not initialized")
            return self._compose_openai(mode, prompt, insights)
        return self._compose_stub(mode, prompt, insights)

    def _compose_openai(
        self,
        mode: str,
        prompt: str,
        insights: Dict[str, List[str]],
    ) -> str:
        role_guidance = {
            "developer": (
                "You are the Kajeet Developer Learning Buddy. Provide concise, factual analysis focusing on code impacts. "
                "Reference module names or artifacts from the provided insights. Avoid inventing repositories or classes."
            ),
            "sales": (
                "You are the Kajeet Sales Learning Buddy. Draft clear, persuasive messaging aligned with Kajeet voice. "
                "Leverage the provided insights for value points and references."
            ),
            "support": (
                "You are the Kajeet Support Learning Buddy. Explain root causes and remediation steps precisely. "
                "Use operational language and highlight preventive measures."
            ),
        }.get(
            mode,
            "You are the Kajeet Learning Buddy. Provide helpful, concise guidance grounded in the supplied insights.",
        )

        insight_block = _format_insights(insights)
        messages = [
            {
                "role": "system",
                "content": (
                    f"{role_guidance} Respond in two short paragraphs or fewer. "
                    "Do not fabricate data beyond the supplied insights and user context. "
                    "If the user message lacks a clear code or feature change (e.g., just greetings), acknowledge that and ask for specifics before attempting impact analysis."
                ),
            },
            {
                "role": "user",
                "content": (
                    f"User request:\n{prompt}\n\n"
                    f"Supporting insights:\n{insight_block}\n\n"
                    "Craft your answer tailored to the user's role."
                ),
            },
        ]

        completion = self._client.chat.completions.create(
            model=self._settings.llm_model,
            messages=messages,
            temperature=self._settings.openai_temperature,
        )
        return completion.choices[0].message.content.strip()

    def _compose_stub(
        self,
        mode: str,
        prompt: str,
        insights: Dict[str, List[str]],
    ) -> str:
        """Offline deterministic fallback mirroring earlier behavior."""
        modules = ", ".join(insights.get("modules", []) or ["(review repositories)"])
        dependencies = ", ".join(
            insights.get("dependencies", []) or ["(analyze build graph)"]
        )
        tests = ", ".join(insights.get("tests", []) or ["(add regression coverage)"])
        values = "; ".join(insights.get("value_points", []) or [])
        actions = "; ".join(insights.get("actions", []) or [])
        snippets = insights.get("snippets", [])
        notes = "; ".join(insights.get("notes", []) or [])

        if mode == "developer":
            module_summary = ", ".join(insights.get("modules", []) or [])
            dependency_summary = ", ".join(
                insights.get("dependencies", []) or ["review related imports"]
            )
            tests_summary = ", ".join(
                insights.get("tests", []) or ["identify relevant unit and integration tests"]
            )
            snippet_summary = ""
            if snippets:
                formatted = [
                    f"{snippet['module']}:{snippet['line']}"
                    for snippet in snippets
                ]
                snippet_summary = f"Highlighted locations: {', '.join(formatted)}. "
            note_summary = f"{notes}. " if notes else ""
            return (
                f"Serialization work concentrates on {module_summary or 'the core activation services'}. "
                f"Key dependencies to review: {dependency_summary}. "
                f"Update or create tests including {tests_summary}. "
                f"{snippet_summary}{note_summary}"
            ).strip()
        if mode == "sales":
            opening = " ".join(insights.get("opening", []) or [])
            cta = insights.get("call_to_action", "")
            return f"{opening} Key value points: {values}. Close with: {cta}"
        if mode == "support":
            cause = insights.get("cause", "Likely unknown cause.")
            return f"Observed pattern: {cause}. Recommended actions: {actions}."

        return "Here is the information you requested."


llm_service = LLMService()


def compose_answer(
    mode: str,
    prompt: str,
    insights: Dict[str, List[str]],
) -> str:
    """Convenience wrapper used by agents."""
    return llm_service.compose_answer(mode=mode, prompt=prompt, insights=insights)
