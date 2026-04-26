"use client";

import { useState, useEffect } from "react";
import { X, Save, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { cn } from "@/lib/utils";
import { SANDBOX_TOOLS } from "@/types/agent";
import type { AgentRole, WorkflowAgent } from "@/types/agent";

interface Props {
  agent: WorkflowAgent | null;
  pattern: "ORCHESTRATOR" | "TEAM";
  onSave: (patch: Partial<WorkflowAgent> & { tools: string[] }) => Promise<void>;
  onDelete: () => Promise<void>;
  onClose: () => void;
}

const ROLE_OPTIONS: { value: AgentRole; label: string; desc: string }[] = [
  { value: "MAIN", label: "Main (Orchestrator)", desc: "Coordinates sub-agents" },
  { value: "SUB",  label: "Sub-Agent",           desc: "Receives delegated tasks" },
  { value: "PEER", label: "Peer",                 desc: "Works alongside other agents" },
];

export function AgentConfigPanel({ agent, pattern, onSave, onDelete, onClose }: Props) {
  const [name,         setName]         = useState("");
  const [role,         setRole]         = useState<AgentRole>("PEER");
  const [systemPrompt, setSystemPrompt] = useState("");
  const [tools,        setTools]        = useState<string[]>([]);
  const [saving,       setSaving]       = useState(false);
  const [deleting,     setDeleting]     = useState(false);

  useEffect(() => {
    if (!agent) return;
    setName(agent.name);
    setRole(agent.role);
    setSystemPrompt(agent.systemPrompt ?? "");
    try { setTools(JSON.parse(agent.toolsJson ?? "[]")); } catch { setTools([]); }
  }, [agent]);

  if (!agent) return null;

  const roleOptions = pattern === "ORCHESTRATOR"
    ? ROLE_OPTIONS
    : ROLE_OPTIONS.filter(r => r.value === "PEER");

  async function handleSave() {
    setSaving(true);
    try {
      await onSave({ name, role, systemPrompt, tools });
    } finally {
      setSaving(false);
    }
  }

  async function handleDelete() {
    setDeleting(true);
    try { await onDelete(); } finally { setDeleting(false); }
  }

  function toggleTool(tool: string) {
    setTools(prev => prev.includes(tool) ? prev.filter(t => t !== tool) : [...prev, tool]);
  }

  return (
    <aside className="flex h-full w-80 shrink-0 flex-col border-l border-[--color-border] bg-[--color-surface-raised]">
      {/* Header */}
      <div className="flex items-center justify-between border-b border-[--color-border] px-4 py-3">
        <span className="text-sm font-semibold">Configure Agent</span>
        <Button size="icon" variant="ghost" onClick={onClose}>
          <X className="h-4 w-4" />
        </Button>
      </div>

      <div className="flex-1 overflow-y-auto space-y-4 px-4 py-4">
        {/* Name */}
        <div className="space-y-1">
          <label className="text-xs font-medium text-[--color-muted]">Name</label>
          <input
            value={name}
            onChange={e => setName(e.target.value)}
            className="w-full rounded-md border border-[--color-border] bg-transparent px-3 py-1.5 text-sm focus:outline-none focus:ring-1 focus:ring-black dark:focus:ring-white"
          />
        </div>

        {/* Role (only for ORCHESTRATOR pattern) */}
        {pattern === "ORCHESTRATOR" && (
          <div className="space-y-1">
            <label className="text-xs font-medium text-[--color-muted]">Role</label>
            <div className="space-y-1">
              {roleOptions.map(r => (
                <button
                  key={r.value}
                  onClick={() => setRole(r.value)}
                  className={cn(
                    "flex w-full flex-col rounded-md border px-3 py-2 text-left transition-colors",
                    role === r.value
                      ? "border-black bg-black/5 dark:border-white dark:bg-white/10"
                      : "border-[--color-border] hover:bg-[--color-border]/30",
                  )}
                >
                  <span className="text-xs font-medium">{r.label}</span>
                  <span className="text-[10px] text-[--color-muted]">{r.desc}</span>
                </button>
              ))}
            </div>
          </div>
        )}

        {/* System Prompt */}
        <div className="space-y-1">
          <label className="text-xs font-medium text-[--color-muted]">System Prompt</label>
          <textarea
            value={systemPrompt}
            onChange={e => setSystemPrompt(e.target.value)}
            rows={8}
            placeholder="You are a helpful agent that…"
            className="w-full rounded-md border border-[--color-border] bg-transparent px-3 py-2 text-sm focus:outline-none focus:ring-1 focus:ring-black dark:focus:ring-white resize-none"
          />
        </div>

        {/* Sandbox Tools */}
        <div className="space-y-1.5">
          <label className="text-xs font-medium text-[--color-muted]">
            Sandbox Tools
            <span className="ml-1 text-[10px] font-normal">(runs in Docker)</span>
          </label>
          <div className="flex flex-wrap gap-1.5">
            {SANDBOX_TOOLS.map(tool => (
              <button
                key={tool}
                onClick={() => toggleTool(tool)}
                className={cn(
                  "rounded-full border px-2.5 py-0.5 text-xs font-medium transition-colors",
                  tools.includes(tool)
                    ? "border-black bg-black text-white dark:border-white dark:bg-white dark:text-black"
                    : "border-[--color-border] text-[--color-muted] hover:border-black dark:hover:border-white",
                )}
              >
                {tool.toLowerCase()}
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* Footer */}
      <div className="flex items-center gap-2 border-t border-[--color-border] px-4 py-3">
        <Button
          size="sm"
          variant="ghost"
          onClick={handleDelete}
          disabled={deleting}
          className="text-red-500 hover:bg-red-50 dark:hover:bg-red-900/20"
        >
          <Trash2 className="h-3.5 w-3.5" />
        </Button>
        <Button size="sm" onClick={handleSave} disabled={saving} className="ml-auto flex items-center gap-1.5">
          <Save className="h-3.5 w-3.5" />
          {saving ? "Saving…" : "Save"}
        </Button>
      </div>
    </aside>
  );
}
