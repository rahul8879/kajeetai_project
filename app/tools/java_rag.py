"""Java code retrieval utilities combining AST insights and lexical scoring."""

from __future__ import annotations

import math
import re
from collections import Counter
from functools import lru_cache
from pathlib import Path
from typing import Dict, Iterable, List, Sequence

import javalang

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
MODULE_MAP = {
    "ActivationDaoImpl.java": REPO_ROOT / "ActivationDaoImpl.java",
    "ActivationServiceImpl.java": REPO_ROOT / "ActivationServiceImpl.java",
}


def _resolve_module_path(module: str) -> Path:
    if module in MODULE_MAP:
        return MODULE_MAP[module]
    candidate = REPO_ROOT / module
    if candidate.exists():
        return candidate
    raise FileNotFoundError(f"Module {module} not found under {REPO_ROOT}")


@lru_cache(maxsize=32)
def load_java_file(module: str) -> List[str]:
    file_path = _resolve_module_path(module)
    with file_path.open("r", encoding="utf-8") as handle:
        return handle.readlines()


def _window(lines: Sequence[str], index: int, radius: int = 4) -> str:
    start = max(index - radius, 0)
    end = min(index + radius + 1, len(lines))
    return "".join(lines[start:end]).strip()


def _tokenize(text: str) -> List[str]:
    return re.findall(r"[a-zA-Z_][a-zA-Z0-9_]*", text.lower())


def _counter(tokens: Iterable[str]) -> Counter[str]:
    counter = Counter()
    for token in tokens:
        counter[token] += 1
    return counter


def _cosine_similarity(a: Counter[str], b: Counter[str]) -> float:
    if not a or not b:
        return 0.0
    dot = sum(a[token] * b[token] for token in b if token in a)
    if dot == 0:
        return 0.0
    norm_a = math.sqrt(sum(count * count for count in a.values()))
    norm_b = math.sqrt(sum(count * count for count in b.values()))
    if norm_a == 0 or norm_b == 0:
        return 0.0
    return dot / (norm_a * norm_b)


@lru_cache(maxsize=32)
def _extract_chunks(module: str) -> List[dict]:
    """Parse Java file and extract method/field chunks for retrieval."""
    lines = load_java_file(module)
    source = "".join(lines)
    chunks: List[dict] = []

    try:
        tree = javalang.parse.parse(source)
    except (javalang.parser.JavaSyntaxError, IndexError, TypeError):  # pragma: no cover
        chunk = {
            "module": module,
            "kind": "file",
            "name": module,
            "line": 1,
            "snippet": _window(lines, 0, radius=12),
            "tokens": _counter(_tokenize(source)),
        }
        return [chunk]

    for _, node in tree.filter((javalang.tree.ClassDeclaration,)):
        if not node.position:
            continue
        line_idx = node.position.line - 1
        snippet = _window(lines, line_idx, radius=6)
        chunks.append(
            {
                "module": module,
                "kind": "class",
                "name": node.name,
                "line": node.position.line,
                "snippet": snippet,
                "tokens": _counter(_tokenize(snippet + " " + node.name)),
            }
        )

    target_nodes = (
        javalang.tree.MethodDeclaration,
        javalang.tree.ConstructorDeclaration,
        javalang.tree.FieldDeclaration,
    )
    for _, node in tree.filter(target_nodes):
        if not getattr(node, "position", None):
            continue
        line_idx = node.position.line - 1
        snippet = _window(lines, line_idx, radius=6)
        name = getattr(node, "name", None)
        if not name and isinstance(node, javalang.tree.FieldDeclaration):
            name = ",".join(declarator.name for declarator in node.declarators)
        tokens = _counter(_tokenize(snippet + " " + (name or "")))
        chunks.append(
            {
                "module": module,
                "kind": node.__class__.__name__.replace("Declaration", "").lower(),
                "name": name or "anonymous",
                "line": node.position.line,
                "snippet": snippet,
                "tokens": tokens,
            }
        )

    if not chunks:
        chunks.append(
            {
                "module": module,
                "kind": "file",
                "name": module,
                "line": 1,
                "snippet": _window(lines, 0, radius=12),
                "tokens": _counter(_tokenize(source)),
            }
        )

    return chunks


def gather_snippets(
    modules: Sequence[str],
    search_terms: Sequence[str],
    max_snippets: int = 4,
) -> List[dict]:
    """Return high-signal snippets based on lexical similarity and AST structure."""
    query_tokens = _counter(_tokenize(" ".join(search_terms)))
    snippets: List[dict] = []

    for module in modules:
        try:
            chunks = _extract_chunks(module)
        except FileNotFoundError:
            continue
        for chunk in chunks:
            score = _cosine_similarity(chunk["tokens"], query_tokens)
            if score <= 0:
                continue
            snippets.append(
                {
                    "module": chunk["module"],
                    "line": chunk["line"],
                    "snippet": chunk["snippet"],
                    "kind": chunk["kind"],
                    "name": chunk["name"],
                    "score": round(score, 3),
                }
            )

    if not snippets:
        # fallback to lexical window search
        fallback_terms = [term for term in search_terms if term] or ["activation"]
        snippets = _gather_fallback_snippets(modules, fallback_terms, max_snippets)
        return snippets

    snippets.sort(key=lambda item: item["score"], reverse=True)
    return snippets[:max_snippets]


def _gather_fallback_snippets(
    modules: Sequence[str], search_terms: Sequence[str], max_snippets: int
) -> List[dict]:
    snippets: List[dict] = []
    normalized_terms = [term.lower() for term in search_terms if term]
    for module in modules:
        try:
            lines = load_java_file(module)
        except FileNotFoundError:
            continue
        for idx, line in enumerate(lines):
            lower_line = line.lower()
            if any(term in lower_line for term in normalized_terms):
                snippet_text = _window(lines, idx, radius=4)
                snippets.append(
                    {
                        "module": module,
                        "line": idx + 1,
                        "snippet": snippet_text,
                        "kind": "line",
                        "name": "context",
                        "score": 0.0,
                    }
                )
                if len(snippets) >= max_snippets:
                    return snippets
    return snippets


def search_modules(
    search_terms: Sequence[str],
    max_modules: int = 4,
) -> List[str]:
    """Return Java module names ordered by relevance to the search terms."""
    normalized_terms = [term.lower() for term in search_terms if term]
    if not normalized_terms:
        return list(MODULE_MAP.keys())[:max_modules]

    scores: Dict[str, float] = {}
    query_counter = _counter(normalized_terms)

    for module in MODULE_MAP.keys():
        try:
            chunks = _extract_chunks(module)
        except FileNotFoundError:
            continue
        max_score = 0.0
        for chunk in chunks:
            score = _cosine_similarity(chunk["tokens"], query_counter)
            if score > max_score:
                max_score = score
        if max_score > 0:
            scores[module] = max_score

    if not scores:
        return list(MODULE_MAP.keys())[:max_modules]

    ranked = sorted(scores.items(), key=lambda item: item[1], reverse=True)
    return [module for module, _ in ranked[:max_modules]]
