# Kajeet AI Learning Buddy – Agentic Chatbot Design

## 1. Vision
- Provide a role-aware, company-specific assistant that delivers answers _and_ teaches the “why” behind them.
- Reduce repetitive manual work across Development, Sales, and Support teams by automating research, drafting, and troubleshooting steps.
- Preserve institutional knowledge by grounding every response in verified Kajeet sources and encouraging learning loops.

## 2. Target Personas & Flagship Scenarios
- **Developer – “Learn by Code”**  
  Impact analysis for schema/UI changes, guided diffs, dependency explanations, secure coding guardrails.
- **Sales – “Learn by Communication”**  
  Proposal drafts, persona-based messaging, ROI narratives, objection handling with collateral citations.
- **Support – “Learn by Troubleshooting”**  
  Root-cause reasoning, runbook retrieval, log/metric interpretation, escalation playbooks.

## 3. Solution Overview
```
User (Web / Slack / Teams) 
   ↓
Session Manager & Role Selector → Conversation Orchestrator (LLM)
   → Role Agent (Developer / Sales / Support)
        ↘ Tooling Adapters (code graph, CRM, runbooks, templates, observability APIs)
   ↘ Learning Coach Layer (explanations, quizzes, follow-ups)
Shared Services: Knowledge Ingestion & Vector Store, Policy Guardrails, Analytics, Feedback Loop
```

## 4. Core Components
- **Client Experience**
  - Web chat widget within Sentinel portal.
  - Optional Slack/Teams bot wrapper.
  - Mode selector, transcript download, feedback controls.
- **Conversation Orchestrator**
  - Stateless API accepting mode + message.
  - Dispatches to a role agent; manages memory per session.
  - Enforces response contract (answer → explanation → references → actionable steps).
- **Role Agents (Agentic Workers)**
  - Structured as ReAct-style agents with tool-use planning.
  - Maintain playbooks (prompt templates + tool chains) tuned to each role’s behaviors.
- **Tooling Adapters**
  - Retrieval service for company knowledge (vector search + metadata filtering).
  - Code intelligence (repo index, dependency graph traversal, build manifest scan).
  - CRM/proposal library access (Salesforce/HubSpot APIs, product sheets).
  - Incident intelligence (CloudWatch, Datadog, JIRA, runbooks).
  - Automation hooks (Jenkins pipelines, JIRA ticket creation, email drafts via MS Graph).
- **Learning Coach Layer**
  - Adds “why it matters” explanation, teaching tips, glossary links.
  - Can issue micro-quizzes or “teach-back” prompts based on confidence scores.
  - Tracks user proficiency progression for personalization.
- **Knowledge Ingestion Pipeline**
  - Batch/stream ingestion of documents (code, runbooks, collateral, FAQs).
  - Chunking + embedding (LlamaIndex / LangChain) stored in vector DB (OpenSearch, Pinecone, pgvector).
  - Metadata tagging (role, product line, version, sensitivity).
  - Scheduled refresh jobs; manual curation UI for SMEs.
- **Governance & Safety**
  - Access control tied to Sentinel SSO roles.
  - Content classification + redaction filter.
  - Audit logging of prompts, tool calls, outputs.
  - Human review workflow for new knowledge entries.
- **Analytics & Feedback**
  - Capture interaction metrics, satisfaction scores.
  - Highlight knowledge gaps and candidate documents for ingestion.
  - Offline evaluation harness for regression testing of prompts + tools.

## 5. Detailed Agent Behaviors

### Developer Mode Agent
- **Goals:** Impact analysis, code walkthrough, best practices coaching.
- **Toolset:** 
  1. Repo Retriever (embedding search scoped by module).  
  2. Dependency Analyzer (builds reference graph from Maven/Gradle + imports).  
  3. Test Coverage Query (pulls from SonarQube/Jacoco).  
  4. Sample Snippet Generator (LLM with secure coding guardrails).  
  5. Jira Sprint Context (optional).
- **Workflow:** 
  1. Understand change request → draft plan of affected layers.  
  2. Retrieve relevant modules/files → highlight lines and dependencies.  
  3. Produce code impacts + recommended refactors/tests.  
  4. Explain reasoning, cite files, suggest “learning next steps” (docs, patterns).

### Sales Mode Agent
- **Goals:** Produce tailored proposals, educate on value messaging.
- **Toolset:** 
  1. Brand Voice Prompt Library.  
  2. Collateral Retriever (case studies, pricing, compliance).  
  3. Persona Mapper (industry pain points, solution angles).  
  4. Email/Doc Formatter (HTML/Markdown, integration with Outlook/Gmail).  
  5. Analytics Hook (CRM stage data).
- **Workflow:** 
  1. Qualify request (industry, audience, tone).  
  2. Retrieve relevant collateral & success stories.  
  3. Draft structured output (subject, intro, value bullets, CTA).  
  4. Provide coaching notes (why chosen messaging works, variations).  
  5. Offer follow-up assets (ppt, ROI calculator).

### Support Mode Agent
- **Goals:** Diagnose issues, recommend fixes, build intuition.
- **Toolset:** 
  1. Runbook Retriever.  
  2. Observability Query Runner (Datadog dashboards, CloudWatch logs).  
  3. Rate-limit & quota calculators (API Gateway configs).  
  4. Incident History lookup.  
  5. Escalation wizard (create ticket with prefilled context).
- **Workflow:** 
  1. Parse error payload → map to known issues.  
  2. Run targeted log/metric queries if user authorizes (future enhancement).  
  3. Return cause analysis, fix actions (backoff, rate adjustments, monitoring).  
  4. Teach through summarized pattern recognition + prevention tips.  
  5. Offer automation (trigger remediation script, create incident).

## 6. Learning Reinforcement Features
- **Structured Responses:** `Answer → Why it matters → How to verify → References`.
- **Teach-Back:** Prompt user to restate plan; agent checks understanding.
- **Progress Tracking:** Maintain user skill profile; adapt depth of explanations.
- **Micro-Lessons:** After resolving a request, suggest short related learning snippets.
- **Sandbox Exercises:** For developers/support, generate practice tasks using synthetic data.

## 7. Data & Knowledge Governance
- **Source Hierarchy:**  
  Level 1: Official documentation (policies, runbooks)  
  Level 2: Code repositories & test cases  
  Level 3: SME-authored notes  
  Level 4: Community contributions (flagged for review)
- **Approval Workflow:** SMEs review new docs before publishing to vector store.
- **Sensitivity Controls:** Metadata tags enforce role-based filtering (e.g., finance-only).
- **PII & Secrets Handling:** Automatic scanning/redaction prior to storage.

## 8. Technology Stack (Baseline Proposal)
- **LLM Orchestration:** LangChain or Semantic Kernel with Azure OpenAI, Anthropic, or local tuned model.
- **Vector Store:** OpenSearch Serverless with KNN or Managed pgvector (self-hosted option: Qdrant).
- **Embeddings:** text-embedding-3-large or InstructorXL (self-host). 
- **Runtime:** Python FastAPI or Node.js (NestJS) service for orchestration; deploy on AWS (Fargate/EKS).
- **Auth & SSO:** Integrate with Sentinel SSO (SAML/OAuth). 
- **Observability:** Datadog APM, structured logging, trace per conversation.
- **CI/CD:** GitHub Actions → Terraform infrastructure provisioning.
- **Front-End:** React widget (TypeScript) embedded in Sentinel portal; Slack app (Bolt framework).

## 9. Deployment Topology
- **Environments:** Dev (LLM sandbox), QA (synthetic data), Prod (audited access). 
- **Network:** VPC with private subnets for agents/tools; outbound proxied to LLM endpoints.
- **Secrets:** AWS Secrets Manager for API keys and embeddings credentials.
- **Scaling:** Stateless orchestrator behind ALB; auto-scaling by request volume.

## 10. Implementation Roadmap
1. **Discovery & Data Audit (2–3 weeks):** Inventory documents, systems, security constraints. Define SME reviewers.
2. **MVP Slice (6 weeks):**
   - Build ingestion pipeline (docs, code index).
   - Implement conversation orchestrator + Developer agent with retrieval + code impact tool.
   - Deliver web chat UI with SSO and transcript history.
3. **Pilot & Feedback (4 weeks):** Roll to small cohort; track precision, satisfaction, learning outcomes.
4. **Mode Expansion:**
   - Add Sales agent (proposal drafting) with CRM integration.
   - Add Support agent (runbooks + throttling scenarios) with observability read-only tools.
5. **Learning Analytics:** Progress tracking, quizzes, feedback dashboards.
6. **Automation Hooks:** Jira ticketing, Jenkins job triggers, automated alerts.
7. **Scale & Governance:** RBAC hardening, audit dashboards, localization, model fine-tuning.

## 11. Risks & Mitigations
- **Hallucinated instructions** → Ground responses in retrieved citations, include confidence scoring, enable SME review workflow.
- **Data privacy leakage** → Enforce strict RBAC filters and redact sensitive fields before prompting.
- **Tool integration complexity** → Start with read-only adapters; incrementally add write capabilities with approvals.
- **User trust adoption** → Provide transparent reasoning, co-browsing of sources, opt-in learning prompts.
- **Model drift** → Establish regression test suites, regular prompt reviews, fallback deterministic flows for critical automations.

## 12. Success Metrics
- 30% reduction in time spent on routine code impact, proposal drafting, or L1 troubleshooting.
- ≥80% of responses include verified citations.
- User satisfaction ≥4.3/5 after pilot; improvement in skill self-assessments by role.
- Decrease in duplicate support tickets and knowledge gaps flagged per quarter.

## 13. Near-Term Next Steps
1. Confirm data availability & access approvals for each role (code repos, CRM, observability).  
2. Select LLM provider(s) aligned with compliance and cost targets.  
3. Prototype ingestion pipeline + retrieval evaluation on sample documents.  
4. Draft detailed API contracts for the conversation orchestrator and UI integration.  
5. Schedule SME workshops to curate priority learning modules per role.
