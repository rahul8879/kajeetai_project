"""Sales agent tooling for collateral retrieval and messaging hints."""

from typing import Dict, List

from app.knowledge.retriever import knowledge_retriever


def fetch_proposal_blueprint(product: str, audience: str) -> Dict[str, List[str]]:
    """Return proposal scaffolding tailored to product and audience."""
    key = f"{product}:{audience}".lower()
    entry = knowledge_retriever.find("sales_playbooks", key)
    if entry:
        return entry
    # Fallback blueprint
    return {
        "subject": f"{product} solution overview",
        "opening": [
            f"Thank you for exploring {product} with Kajeet.",
            "We understand your goals and have outlined how we can help.",
        ],
        "value_points": [
            "Reliable connectivity tailored for K-12 needs.",
            "Secure platform with granular policy controls.",
            "Dedicated support team for onboarding and expansion.",
        ],
        "call_to_action": "Let us schedule a 30-minute strategy session next week.",
        "references": ["Kajeet SmartBus overview deck", "Customer success stories"],
        "coaching": [
            "Tie value points to measurable student or district outcomes.",
            "Close with a confident, time-bound call-to-action.",
        ],
    }
