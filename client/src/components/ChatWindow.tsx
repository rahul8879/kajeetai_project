import { useEffect, useRef, useState } from "react";
import type { AgentMode, ChatMessage } from "../types";

interface ChatWindowProps {
  mode: AgentMode;
  messages: ChatMessage[];
  pending: boolean;
  error: string | null;
  onSend: (content: string) => void;
}

export function ChatWindow({
  mode,
  messages,
  pending,
  error,
  onSend,
}: ChatWindowProps): JSX.Element {
  const [input, setInput] = useState("");
  const listRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (!listRef.current) {
      return;
    }
    listRef.current.scrollTop = listRef.current.scrollHeight;
  }, [messages, pending]);

  const handleSubmit = (event: React.FormEvent) => {
    event.preventDefault();
    onSend(input);
    setInput("");
  };

  return (
    <div className="chat-window">
      <div className="message-list" ref={listRef}>
        {messages.map((message, index) => (
          <ChatBubble key={`${message.timestamp}-${index}`} message={message} />
        ))}
        {pending && (
          <div className="message assistant">
            <span className="role">Assistant</span>
            <div className="bubble pending">Thinking…</div>
          </div>
        )}
        {error && (
          <div className="message error">
            <span className="role">Error</span>
            <div className="bubble">{error}</div>
          </div>
        )}
        {!messages.length && !pending && (
          <div className="empty-state">
            Select a mode and ask a question. Example:
            <pre>
              {mode === "developer"
                ? "What's the code impact of adding activationType?"
                : mode === "sales"
                  ? "Draft a proposal email for SmartBus Wi-Fi."
                  : "How do I fix 429 Too Many Requests from API Gateway?"}
            </pre>
          </div>
        )}
      </div>
      <form className="composer" onSubmit={handleSubmit}>
        <textarea
          value={input}
          onChange={(event) => setInput(event.target.value)}
          placeholder="Type your question…"
          rows={3}
          disabled={pending}
        />
        <button type="submit" disabled={pending || !input.trim()}>
          Send
        </button>
      </form>
    </div>
  );
}

interface ChatBubbleProps {
  message: ChatMessage;
}

function ChatBubble({ message }: ChatBubbleProps): JSX.Element {
  const isUser = message.role === "user";
  const metadata = message.metadata;
  return (
    <div className={`message ${isUser ? "user" : "assistant"}`}>
      <span className="role">{isUser ? "You" : "Assistant"}</span>
      <div className="bubble">
        <p>{message.content}</p>
        {!isUser && metadata && (
          <AssistantMetadata metadata={metadata} />
        )}
      </div>
    </div>
  );
}

interface AssistantMetadataProps {
  metadata: NonNullable<ChatMessage["metadata"]>;
}

function AssistantMetadata({ metadata }: AssistantMetadataProps): JSX.Element {
  return (
    <div className="metadata">
      {metadata.explanation && (
        <section>
          <h4>Why it matters</h4>
          <p>{metadata.explanation}</p>
        </section>
      )}
      {metadata.actions && metadata.actions.length > 0 && (
        <section>
          <h4>Suggested actions</h4>
          <ul>
            {metadata.actions.map((action) => (
              <li key={action}>{action}</li>
            ))}
          </ul>
        </section>
      )}
      {metadata.references && metadata.references.length > 0 && (
        <section>
          <h4>References</h4>
          <ul>
            {metadata.references.map((ref) => (
              <li key={ref}>{ref}</li>
            ))}
          </ul>
        </section>
      )}
      {metadata.teach_back_prompt && (
        <section>
          <h4>Teach-back prompt</h4>
          <p>{metadata.teach_back_prompt}</p>
        </section>
      )}
    </div>
  );
}
