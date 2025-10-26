"""Sales mode agent for proposal generation."""

from typing import List

from app.agents.base import AgentContext, BaseAgent
from app.models import AgentResponse, AgentRole, ChatMessage, ToolTrace
from app.services import learning
from app.services.llm import compose_answer
from app.tools.collateral import fetch_proposal_blueprint


class SalesAgent(BaseAgent):
    """Provides sales collateral and communication coaching."""

    def run(self, context: AgentContext) -> AgentResponse:
        product, audience = self._extract_dimensions(context.request.message)
        blueprint = fetch_proposal_blueprint(product, audience)

        context.add_trace(
            ToolTrace(
                tool="sales_blueprint",
                input={"product": product, "audience": audience},
                output={
                    "value_points": blueprint.get("value_points", []),
                    "references": blueprint.get("references", []),
                },
            )
        )

        answer = compose_answer(
            mode="sales", prompt=context.request.message, insights=blueprint
        )
        explanation = learning.explain_why("sales", {})
        actions = learning.format_actions(
            [
                "Personalize the opening with district goals.",
                "Highlight two quantifiable outcomes the district cares about.",
                "Send proposal via CRM and set follow-up reminder.",
            ]
        )
        references = blueprint.get("references", [])
        teach_back = learning.build_teach_back_prompt(
            "sales",
            [
                "Connect pains to differentiated value",
                "Use strong CTA to anchor next steps",
            ],
        )

        history = context.history + [
            ChatMessage(role="assistant", content=answer)
        ]

        return AgentResponse(
            session_id=context.request.session_id,
            mode=AgentRole.sales,
            answer=answer,
            explanation=explanation,
            actions=actions,
            references=references,
            teach_back_prompt=teach_back,
            tool_traces=context.tool_traces,
            history=history,
        )

    def _extract_dimensions(self, message: str) -> tuple[str, str]:
        message_lower = message.lower()
        product = "smartbus wi-fi"
        audience = "school district"
        if "private lte" in message_lower:
            product = "private lte"
        if "enterprise" in message_lower:
            audience = "enterprise"
        if "higher ed" in message_lower:
            audience = "higher education"
        return product, audience
