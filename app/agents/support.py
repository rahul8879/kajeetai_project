"""Support troubleshooting agent."""

from app.agents.base import AgentContext, BaseAgent
from app.models import AgentResponse, AgentRole, ChatMessage, ToolTrace
from app.services import learning
from app.services.llm import compose_answer
from app.tools.runbooks import diagnose_issue


class SupportAgent(BaseAgent):
    """Resolves support incidents with guided troubleshooting."""

    def run(self, context: AgentContext) -> AgentResponse:
        signature = self._extract_signature(context.request.message)
        playbook = diagnose_issue(signature)

        context.add_trace(
            ToolTrace(
                tool="support_playbook",
                input={"signature": signature},
                output={
                    "cause": playbook.get("cause", ""),
                    "actions": playbook.get("actions", []),
                },
            )
        )

        answer = compose_answer(
            mode="support", prompt=context.request.message, insights=playbook
        )
        explanation = learning.explain_why("support", {})
        actions = learning.format_actions(playbook.get("actions", []))
        references = playbook.get("references", [])
        teach_back = learning.build_teach_back_prompt(
            "support",
            [
                "Identify throttling symptoms early",
                "Instrument alerting around API rate limits",
            ],
        )

        history = context.history + [
            ChatMessage(role="assistant", content=answer)
        ]

        return AgentResponse(
            session_id=context.request.session_id,
            mode=AgentRole.support,
            answer=answer,
            explanation=explanation,
            actions=actions,
            references=references,
            teach_back_prompt=teach_back,
            tool_traces=context.tool_traces,
            history=history,
        )

    def _extract_signature(self, message: str) -> str:
        if "too many requests" in message.lower():
            return "apigateway:429"
        if "timeout" in message.lower():
            return "apigateway:504"
        return "generic"
