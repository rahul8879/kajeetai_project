"""Developer agent tooling for dynamic code impact analysis."""

from __future__ import annotations

from typing import Dict, Iterable, List, Sequence

from app.tools.java_rag import load_java_file, search_modules


def analyze_code_impact(
    message: str,
    search_terms: Sequence[str],
    *,
    max_modules: int = 4,
) -> Dict[str, List[str]]:
    """Return impacted modules, dependencies, and suggested tests derived from code."""
    modules = search_modules(search_terms, max_modules=max_modules)
    dependencies: List[str] = []
    notes: List[str] = [f"Question focus: {message.strip()}"]

    for module in modules:
        try:
            lines = load_java_file(module)
        except FileNotFoundError:
            continue

        imports = _collect_relevant_imports(lines, search_terms)
        dependencies.extend(imports)

        class_signature = next(
            (line.strip() for line in lines if line.strip().startswith("public class")), ""
        )
        if class_signature:
            notes.append(f"{module}: {class_signature}")

    dependencies = sorted(set(dependencies))
    suggested_tests = _derive_test_names(modules)

    return {
        "modules": modules,
        "dependencies": dependencies,
        "tests": suggested_tests,
        "notes": notes,
    }


def _collect_relevant_imports(
    lines: Sequence[str], search_terms: Sequence[str]
) -> List[str]:
    normalized_terms = [term.lower() for term in search_terms if term]
    results: List[str] = []
    for line in lines:
        stripped = line.strip()
        if not stripped.startswith("import "):
            continue
        clean = stripped.replace("import ", "").rstrip(";")
        if not normalized_terms or any(term in clean.lower() for term in normalized_terms):
            results.append(clean)
        elif "activation" in clean.lower():
            results.append(clean)
    return results


def _derive_test_names(modules: Iterable[str]) -> List[str]:
    tests = []
    for module in modules:
        if module.endswith(".java"):
            base = module[:-5]
            tests.append(f"{base}Test")
            tests.append(f"{base}IntegrationTest")
    return sorted(set(tests))
