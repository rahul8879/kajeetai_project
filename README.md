# Kajeet AI Learning Buddy – Prototype

This repository contains an end-to-end, role-aware agentic assistant that serves developers, sales representatives, and support engineers inside Kajeet.

## Features
- **FastAPI backend** exposing `/chat` endpoint with session persistence.
- **Mode-specific agents** (Developer, Sales, Support) orchestrated through a shared conversation service.
- **Tool adapters** providing code-impact analysis, sales collateral, and support runbooks backed by local JSON knowledge stores.
- **Java-aware RAG** that parses code (AST + lexical scoring) to surface grounded snippets for the developer workflow.
- **Learning layer** that augments responses with explanations, actions, references, and teach-back prompts.
- **Automated tests** covering flagship scenarios for each role.

## Project Layout
```
app/
  agents/            # Role agents
  knowledge/         # Lightweight knowledge retriever
  services/          # Conversation orchestration, session memory, LLM stub
  tools/             # Tool adapters leveraged by agents
  main.py            # FastAPI entrypoint
data/                # Demo knowledge assets
tests/               # Pytest smoke tests
```

## Getting Started
1. **Install dependencies**
   ```bash
   python -m venv .venv
   source .venv/bin/activate
   pip install -r requirements.txt
   ```
2. **Configure environment**
   ```bash
   cp .env.example .env
   ```
   - For local stubbed responses keep `LLM_PROVIDER=stub`.
   - To invoke OpenAI, set `LLM_PROVIDER=openai` and supply `OPENAI_API_KEY` (and optional `OPENAI_BASE_URL`, `OPENAI_ORGANIZATION`, `OPENAI_TEMPERATURE`). If initialization fails the server now raises an error instead of falling back to the stub.
3. **Run the API**
   ```bash
   uvicorn app.main:app --reload
   ```
4. **Exercise the assistant**
   - Navigate to `http://127.0.0.1:8000/docs`.
   - Use the `/chat` endpoint with one of the role modes (`developer`, `sales`, `support`).

## React Client
1. **Install Node dependencies**
   ```bash
   cd client
   npm install
   ```
2. **Run the dev server**
   ```bash
   npm run dev
   ```
   - By default the client proxies `/api` to `http://127.0.0.1:8000`; if your API lives elsewhere, set `VITE_API_BASE` in a `.env` file under `client/`.
   - The developer agent now uses AST parsing (via `javalang`) so ensure Python deps are reinstalled after pulling these changes (`pip install -r requirements.txt`).
   - Open the URL shown in the terminal (typically `http://127.0.0.1:5173`) and start chatting.

## Running Tests
```bash
pytest
```

## Next Steps
- Replace the stubbed LLM composer with an approved provider (Azure OpenAI, Anthropic) and integrate retrieval-augmented generation.
- Connect tool adapters to live systems (code graph, CRM, observability platforms).
- Extend front-end experience with a web widget or Slack bot.
- Harden governance: RBAC, audit logging, and knowledge curation workflows.
# kajeetai_project
