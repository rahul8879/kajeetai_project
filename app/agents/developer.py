"""Developer mode agent implementation."""

from __future__ import annotations

import re
from typing import List

from app.agents.base import AgentContext, BaseAgent
from app.models import AgentResponse, AgentRole, ChatMessage, ToolTrace
from app.services import learning
from app.services.llm import compose_answer
from app.tools.code_impact import analyze_code_impact
from app.tools.java_rag import gather_snippets


class DeveloperAgent(BaseAgent):
    """Handles developer-focused requests like code impact analysis."""

    def run(self, context: AgentContext) -> AgentResponse:
        if self._is_greeting(context.request.message):
            answer = (
                "Hi there! I’m ready to map code impacts once you describe the change "
                "or question you’re exploring. Let me know what feature, field, or scenario "
                "you’d like to analyze."
            )
            actions = [
                "Describe the code change or feature you want to evaluate.",
                "Include files, fields, or APIs you expect to touch so I can inspect them.",
            ]
            teach_back = learning.build_teach_back_prompt(
                "developer",
                ["Share the scenario so we can trace code, dependencies, and tests together"],
            )
            history = context.history + [ChatMessage(role="assistant", content=answer)]
            return AgentResponse(
                session_id=context.request.session_id,
                mode=AgentRole.developer,
                answer=answer,
                explanation="Providing concrete change context lets me inspect the right modules and dependencies.",
                actions=actions,
                references=[],
                teach_back_prompt=teach_back,
                tool_traces=context.tool_traces,
                history=history,
            )

        search_terms = self._extract_search_terms(context.request.message)
        analysis = analyze_code_impact(
            context.request.message, search_terms, max_modules=4
        )
        code_snippets = gather_snippets(
            analysis.get("modules", []), search_terms, max_snippets=3
        )

        context.add_trace(
            ToolTrace(
                tool="code_impact",
                input={
                    "question": context.request.message,
                    "search_terms": search_terms,
                },
                output={
                    "modules": analysis.get("modules", []),
                    "dependencies": analysis.get("dependencies", []),
                    "tests": analysis.get("tests", []),
                    "notes": analysis.get("notes", []),
                },
            )
        )

        if code_snippets:
            context.add_trace(
                ToolTrace(
                    tool="java_rag",
                    input={"search_terms": search_terms},
                    output={"snippets": code_snippets},
                )
            )

        answer = compose_answer(
            mode="developer",
            prompt=context.request.message,
            insights={**analysis, "snippets": code_snippets},
        )
        explanation = learning.explain_why("developer", {})
        actions = learning.format_actions(
            self._suggest_actions(search_terms, analysis.get("modules", []))
        )

        references = analysis.get("modules", [])
        if code_snippets:
            references = [
                f"{snippet['module']}:{snippet['line']}" for snippet in code_snippets
            ]

        teach_back = learning.build_teach_back_prompt(
            "developer",
            [
                "Trace code changes across DAO, service, and UI layers",
                "Validate serialization contracts stay backward compatible",
            ],
        )

        history = context.history + [
            ChatMessage(role="assistant", content=answer)
        ]

        return AgentResponse(
            session_id=context.request.session_id,
            mode=AgentRole.developer,
            answer=answer,
            explanation=explanation,
            actions=actions,
            references=references,
            teach_back_prompt=teach_back,
            tool_traces=context.tool_traces,
            history=history,
        )

    def _extract_search_terms(self, message: str) -> List[str]:
        camel_terms = re.findall(r"[a-z]+[A-Z][a-zA-Z0-9]*", message)
        snake_terms = re.findall(r"[A-Za-z0-9_]+", message)
        keywords = [term for term in camel_terms if len(term) > 3]
        keywords.extend(
            word for word in snake_terms if word.lower() in {"activation", "activationtype"}
        )
        if not keywords:
            keywords.append("activation")
        return list({term.lower() for term in keywords})

    def _suggest_actions(self, search_terms: List[str], modules: List[str]) -> List[str]:
        primary = search_terms[0] if search_terms else "change"
        suggestions = [
            f"Inspect {', '.join(modules) or 'relevant modules'} to add or adjust '{primary}' handling.",
            "Propagate serialization updates through service and API payload contracts.",
            "Extend unit and integration tests that cover request/response flows touching the new data.",
        ]
        return suggestions

    def _is_greeting(self, message: str) -> bool:
        normalized = message.strip().lower()
        greetings = {"hi", "hello", "hey", "howdy", "greetings"}
        if normalized in greetings:
            return True
        if len(normalized) <= 4 and normalized.replace("!", "") in greetings:
            return True
        return False
