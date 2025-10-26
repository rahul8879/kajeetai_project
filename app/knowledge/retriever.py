"""Simple offline knowledge retriever using local JSON assets."""

import json
from pathlib import Path
from typing import Dict, List, Optional

DATA_DIR = Path(__file__).resolve().parent.parent.parent / "data"


class KnowledgeRetriever:
    """Loads and queries small JSON datasets for demo purposes."""

    def __init__(self) -> None:
        self._cache: Dict[str, Dict[str, dict]] = {}

    def load(self, name: str) -> Dict[str, dict]:
        if name in self._cache:
            return self._cache[name]
        file_path = DATA_DIR / f"{name}.json"
        if not file_path.exists():
            raise FileNotFoundError(f"Knowledge file {file_path} is missing.")
        with file_path.open("r", encoding="utf-8") as handle:
            payload = json.load(handle)
        self._cache[name] = payload
        return payload

    def find(
        self, dataset: str, key: str, default: Optional[dict] = None
    ) -> Optional[dict]:
        entries = self.load(dataset)
        return entries.get(key, default)

    def search_by_keyword(
        self, dataset: str, keyword: str, limit: int = 3
    ) -> List[dict]:
        entries = self.load(dataset)
        matches = []
        keyword_lower = keyword.lower()
        for value in entries.values():
            joined = " ".join(str(v) for v in value.values())
            if keyword_lower in joined.lower():
                matches.append(value)
            if len(matches) >= limit:
                break
        return matches


knowledge_retriever = KnowledgeRetriever()
