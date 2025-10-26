import { useState } from "react";

import { ChatWindow } from "./components/ChatWindow";
import { ModeSelector } from "./components/ModeSelector";
import { apiChat } from "./api";
import type { AgentMode, ChatMessage, ChatResponse } from "./types";

const SESSION_STORAGE_KEY = "kajeet-learning-buddy-session";

function getSessionId(): string {
  const existing = sessionStorage.getItem(SESSION_STORAGE_KEY);
  if (existing) {
    return existing;
  }
  const generated = `session-${crypto.randomUUID()}`;
  sessionStorage.setItem(SESSION_STORAGE_KEY, generated);
  return generated;
}

export default function App(): JSX.Element {
  const [mode, setMode] = useState<AgentMode>("developer");
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [pending, setPending] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  const sessionId = getSessionId();

  const handleSend = async (content: string) => {
    if (!content.trim()) {
      return;
    }
    setPending(true);
    setError(null);
    const userMessage: ChatMessage = {
      role: "user",
      content,
      timestamp: new Date().toISOString(),
    };
    setMessages((prev) => [...prev, userMessage]);

    try {
      const response: ChatResponse = await apiChat({
        session_id: sessionId,
        mode,
        message: content,
      });

      const assistantMessage: ChatMessage = {
        role: "assistant",
        content: response.answer,
        timestamp: new Date().toISOString(),
        metadata: {
          explanation: response.explanation,
          references: response.references,
          actions: response.actions,
          teach_back_prompt: response.teach_back_prompt,
        },
      };
      setMessages((prev) => [...prev, assistantMessage]);
    } catch (err) {
      console.error("Chat request failed", err);
      setError(
        err instanceof Error ? err.message : "Unexpected error contacting API",
      );
      // remove last user message if request failed
      setMessages((prev) => prev.slice(0, -1));
    } finally {
      setPending(false);
    }
  };

  return (
    <div className="app-shell">
      <header>
        <h1>Kajeet AI Learning Buddy</h1>
        <ModeSelector
          mode={mode}
          onChange={(next) => {
            setMode(next);
            setMessages([]);
          }}
        />
      </header>
      <main>
        <ChatWindow
          mode={mode}
          messages={messages}
          pending={pending}
          error={error}
          onSend={handleSend}
        />
      </main>
      <footer>
        <small>
          Responses cite code snippets and learning prompts for the selected
          mode. Session: {sessionId}
        </small>
      </footer>
    </div>
  );
}
