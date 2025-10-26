"""Support tooling to recommend runbooks and remediation steps."""

from typing import Dict, List

from app.knowledge.retriever import knowledge_retriever


def diagnose_issue(error_signature: str) -> Dict[str, List[str]]:
    """Return probable cause, remediation, and insights."""
    entry = knowledge_retriever.find("support_playbooks", error_signature)
    if entry:
        return entry
    # Fallback heuristics
    return {
        "cause": "Unclassified issue; requires manual triage.",
        "actions": [
            "Review recent deployment logs and feature toggles.",
            "Collect CloudWatch metrics for the affected window.",
            "Apply exponential backoff on client retries where applicable.",
        ],
        "prevention": [
            "Set alert thresholds on API throttling errors.",
            "Benchmark expected throughput and adjust limits accordingly.",
        ],
        "references": ["Support Runbook Template"],
    }
