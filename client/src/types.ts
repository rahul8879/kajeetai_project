export type AgentMode = "developer" | "sales" | "support";

export interface ChatRequestPayload {
  session_id: string;
  mode: AgentMode;
  message: string;
}

export interface ChatResponse {
  session_id: string;
  mode: AgentMode;
  answer: string;
  explanation: string;
  actions: string[];
  references: string[];
  teach_back_prompt?: string | null;
  tool_traces: Array<{
    tool: string;
    input: Record<string, unknown>;
    output: Record<string, unknown>;
  }>;
  history: Array<{
    role: string;
    content: string;
  }>;
}

export interface ChatMessage {
  role: "user" | "assistant";
  content: string;
  timestamp: string;
  metadata?: {
    explanation?: string;
    actions?: string[];
    references?: string[];
    teach_back_prompt?: string | null;
  };
}
