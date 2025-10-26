"""Learning reinforcement utilities."""

from typing import Dict, List


def build_teach_back_prompt(mode: str, key_takeaways: List[str]) -> str:
    """Prompt the user to reflect on the solution."""
    if not key_takeaways:
        return (
            "What is one action you will take after this conversation to cement the learning?"
        )
    joined = "; ".join(key_takeaways)
    return (
        f"In your own words, summarize how you will apply these points ({joined}) "
        "to solve similar requests."
    )


def format_actions(actions: List[str]) -> List[str]:
    """Normalize action items."""
    return [action.strip() for action in actions if action]


def explain_why(mode: str, anchors: Dict[str, List[str]]) -> str:
    """Create the 'why it matters' narrative based on retrieved anchors."""
    if mode == "developer":
        return (
            "Understanding module dependencies reduces regression risk and highlights "
            "where test coverage must expand."
        )
    if mode == "sales":
        return (
            "Linking value points to the buyer's mission demonstrates empathy and builds credibility."
        )
    if mode == "support":
        return (
            "Diagnosing throttling patterns quickly prevents cascading outages and shortens MTTR."
        )
    return "Applying these insights develops reusable intuition for future tasks."
