import type { AgentMode } from "../types";

const MODES: { value: AgentMode; label: string; description: string }[] = [
  {
    value: "developer",
    label: "Developer – Learn by Code",
    description: "Focus on code impact, snippets, and test guidance.",
  },
  {
    value: "sales",
    label: "Sales – Learn by Communication",
    description: "Draft proposals and messaging with coaching tips.",
  },
  {
    value: "support",
    label: "Support – Learn by Troubleshooting",
    description: "Explain root causes and remediation runbooks.",
  },
];

interface ModeSelectorProps {
  mode: AgentMode;
  onChange: (mode: AgentMode) => void;
}

export function ModeSelector({ mode, onChange }: ModeSelectorProps): JSX.Element {
  return (
    <div className="mode-selector">
      <label htmlFor="mode">Role Mode</label>
      <select
        id="mode"
        value={mode}
        onChange={(event) => onChange(event.target.value as AgentMode)}
      >
        {MODES.map((m) => (
          <option key={m.value} value={m.value}>
            {m.label}
          </option>
        ))}
      </select>
      <p className="mode-description">
        {MODES.find((m) => m.value === mode)?.description}
      </p>
    </div>
  );
}
