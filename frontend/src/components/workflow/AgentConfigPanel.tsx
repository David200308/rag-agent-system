"use client";

import { useState, useEffect, useRef } from "react";
import { X, Save, Trash2, Zap } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { cn } from "@/lib/utils";
import { SANDBOX_TOOLS } from "@/types/agent";
import type { AgentRole, Skill, WorkflowAgent } from "@/types/agent";
import { fetchSkills, fetchConnectorStatus } from "@/lib/api";
import { useSkillsStore } from "@/store/skillsStore";

// ── Connector tool definitions ────────────────────────────────────────────────

interface ConnectorTool {
  id: string;       // stored in toolsJson, e.g. "CONNECTOR_GOOGLE_DOCS"
  provider: string; // must match connector status key, e.g. "google"
  name: string;
  icon: string;
}

const CONNECTOR_TOOLS: ConnectorTool[] = [
  { id: "CONNECTOR_GOOGLE_DOCS",   provider: "google",   name: "Google Docs",   icon: "📄" },
  { id: "CONNECTOR_GOOGLE_SHEETS", provider: "google",   name: "Google Sheets", icon: "📊" },
  { id: "CONNECTOR_GOOGLE_SLIDES", provider: "google",   name: "Google Slides", icon: "📑" },
  { id: "CONNECTOR_TELEGRAM",      provider: "telegram", name: "Telegram",      icon: "✈️" },
];

interface Props {
  agent: WorkflowAgent | null;
  pattern: "ORCHESTRATOR" | "TEAM" | "GRAPH";
  onSave: (patch: Partial<WorkflowAgent> & { tools: string[]; skillIds: string[] }) => Promise<void>;
  onDelete: () => Promise<void>;
  onClose: () => void;
}

const ROLE_OPTIONS: { value: AgentRole; label: string; desc: string }[] = [
  { value: "MAIN", label: "Main (Orchestrator)", desc: "Coordinates sub-agents" },
  { value: "SUB",  label: "Sub-Agent",           desc: "Receives delegated tasks" },
  { value: "PEER", label: "Peer",                 desc: "Works alongside other agents" },
];

const PANEL_MIN = 260;
const PANEL_MAX = 560;
const PANEL_DEFAULT = 320;

export function AgentConfigPanel({ agent, pattern, onSave, onDelete, onClose }: Props) {
  const [name,         setName]         = useState("");
  const [role,         setRole]         = useState<AgentRole>("PEER");
  const [systemPrompt, setSystemPrompt] = useState("");
  const [tools,        setTools]        = useState<string[]>([]);
  const [conditionExpr,    setConditionExpr]    = useState("");
  const [outputSchemaJson, setOutputSchemaJson] = useState("");
  const [saving,       setSaving]       = useState(false);
  const [deleting,     setDeleting]     = useState(false);
  const [skills,          setSkills]          = useState<Skill[]>([]);
  const [selectedSkillIds, setSelectedSkillIds] = useState<string[]>([]);
  const [connectorStatus, setConnectorStatus] = useState<Record<string, boolean>>({});

  const { setAgentSkills } = useSkillsStore();

  // ── Resizable panel width ────────────────────────────────────────────────
  const [panelWidth, setPanelWidth] = useState(PANEL_DEFAULT);
  const [dragging,   setDragging]   = useState(false);
  const widthRef     = useRef(PANEL_DEFAULT);
  const dragStartRef = useRef<{ x: number; w: number } | null>(null);
  widthRef.current = panelWidth;

  useEffect(() => {
    const stored = localStorage.getItem("workflow-config-panel-width");
    if (stored) {
      const n = parseInt(stored, 10);
      if (n >= PANEL_MIN && n <= PANEL_MAX) setPanelWidth(n);
    }
  }, []);

  useEffect(() => {
    if (!dragging) return;
    const move = (clientX: number) => {
      if (!dragStartRef.current) return;
      const next = Math.min(PANEL_MAX, Math.max(PANEL_MIN,
        dragStartRef.current.w + (dragStartRef.current.x - clientX),
      ));
      setPanelWidth(next);
    };
    const stop = () => {
      setDragging(false);
      localStorage.setItem("workflow-config-panel-width", String(widthRef.current));
      dragStartRef.current = null;
    };
    const onMouseMove = (e: MouseEvent) => move(e.clientX);
    const onTouchMove = (e: TouchEvent) => { if (e.touches[0]) move(e.touches[0].clientX); };
    window.addEventListener("mousemove", onMouseMove);
    window.addEventListener("mouseup", stop);
    window.addEventListener("touchmove", onTouchMove, { passive: true });
    window.addEventListener("touchend", stop);
    return () => {
      window.removeEventListener("mousemove", onMouseMove);
      window.removeEventListener("mouseup", stop);
      window.removeEventListener("touchmove", onTouchMove);
      window.removeEventListener("touchend", stop);
    };
  }, [dragging]);

  useEffect(() => {
    fetchSkills()
      .then(skills => setSkills(skills.filter(s => !s.status || s.status === "APPROVED")))
      .catch(() => {});
    fetchConnectorStatus().then(setConnectorStatus).catch(() => {});
  }, []);

  useEffect(() => {
    if (!agent) return;
    setName(agent.name);
    setRole(agent.role);
    setSystemPrompt(agent.systemPrompt ?? "");
    setConditionExpr(agent.conditionExpr ?? "");
    setOutputSchemaJson(agent.outputSchemaJson ?? "");
    try { setTools(JSON.parse(agent.toolsJson ?? "[]")); } catch { setTools([]); }
    try { setSelectedSkillIds(JSON.parse(agent.skillIdsJson ?? "[]")); } catch { setSelectedSkillIds([]); }
  }, [agent]);

  if (!agent) return null;

  const roleOptions = pattern === "ORCHESTRATOR"
    ? ROLE_OPTIONS
    : ROLE_OPTIONS.filter(r => r.value === "PEER");

  const isCondition = agent.nodeKind === "CONDITION";
  const isEnd       = agent.nodeKind === "END";
  const isAgentNode = !isCondition && !isEnd;

  let schemaJsonError: string | null = null;
  if (outputSchemaJson.trim()) {
    try { JSON.parse(outputSchemaJson); } catch (e) {
      schemaJsonError = e instanceof Error ? e.message : "Invalid JSON";
    }
  }

  async function handleSave() {
    setSaving(true);
    try {
      if (agent) setAgentSkills(agent.id, selectedSkillIds);
      await onSave({
        name, role, systemPrompt, tools, skillIds: selectedSkillIds,
        conditionExpr: isCondition ? conditionExpr : null,
        outputSchemaJson: isAgentNode && outputSchemaJson.trim() ? outputSchemaJson : null,
      });
    } finally {
      setSaving(false);
    }
  }

  function toggleSkill(id: string) {
    setSelectedSkillIds((prev) =>
      prev.includes(id) ? prev.filter((s) => s !== id) : [...prev, id]
    );
  }

  async function handleDelete() {
    setDeleting(true);
    try { await onDelete(); } finally { setDeleting(false); }
  }

  function toggleTool(tool: string) {
    setTools(prev => prev.includes(tool) ? prev.filter(t => t !== tool) : [...prev, tool]);
  }

  return (
    <>
      {/* Resize divider */}
      <div
        className="group relative hidden w-1 shrink-0 cursor-col-resize sm:block"
        onMouseDown={e => { e.preventDefault(); dragStartRef.current = { x: e.clientX, w: widthRef.current }; setDragging(true); }}
        onTouchStart={e => { if (e.touches[0]) { dragStartRef.current = { x: e.touches[0].clientX, w: widthRef.current }; setDragging(true); } }}
        aria-hidden
      >
        <div className={cn(
          "absolute inset-y-0 left-1/2 w-0.5 -translate-x-1/2 rounded-full transition-colors duration-150",
          dragging ? "bg-blue-500" : "bg-[--color-border] group-hover:bg-blue-400",
        )} />
      </div>

      <aside
        style={{ backgroundColor: "var(--color-surface-raised)", "--panel-w": `${panelWidth}px` } as React.CSSProperties}
        className={cn(
          "flex flex-col",
          // Mobile: fixed bottom sheet
          "fixed inset-x-0 bottom-0 z-50 max-h-[70vh] rounded-t-xl border-t border-[--color-border]",
          // Desktop: inline right panel, width driven by --panel-w (resizable)
          "sm:relative sm:inset-auto sm:z-auto sm:h-full sm:max-h-none sm:w-[var(--panel-w)] sm:shrink-0 sm:rounded-none sm:border-l sm:border-t-0",
        )}
      >
      {/* Header */}
      <div className="flex items-center justify-between border-b border-[--color-border] px-4 py-3">
        <span className="text-sm font-semibold">
          {isCondition ? "Configure Condition" : isEnd ? "Configure End" : "Configure Agent"}
        </span>
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

        {/* Condition node: just the branch-selection description */}
        {isCondition && (
          <div className="space-y-1">
            <label className="text-xs font-medium text-[--color-muted]">Condition</label>
            <textarea
              value={conditionExpr}
              onChange={e => setConditionExpr(e.target.value)}
              rows={5}
              placeholder={'Describe how to pick a branch, e.g. "yes if the output mentions urgent, otherwise no".'}
              className="w-full rounded-md border border-[--color-border] bg-transparent px-3 py-2 text-sm focus:outline-none focus:ring-1 focus:ring-black dark:focus:ring-white resize-none"
            />
            <p className="text-[10px] text-[--color-muted]">
              Draw an edge from this node for each possible outcome and label it (e.g. &quot;yes&quot; / &quot;no&quot;) — the run engine picks the matching edge based on this description.
            </p>
          </div>
        )}

        {/* End node: nothing beyond the name — it terminates the run */}
        {isEnd && (
          <p className="text-[10px] text-[--color-muted]">
            Reaching this node ends the workflow run and returns whatever output flowed into it.
          </p>
        )}

        {/* Role (only for ORCHESTRATOR pattern) */}
        {isAgentNode && pattern === "ORCHESTRATOR" && (
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

        {/* Skills */}
        {isAgentNode && (
        <div className="space-y-1.5">
          <label className="flex items-center gap-1.5 text-xs font-medium text-[--color-muted]">
            <Zap className="h-3.5 w-3.5 text-amber-500" />
            Skills
          </label>
          {skills.length === 0 ? (
            <p className="text-[10px] text-[--color-muted]">
              No skills uploaded. Add skills in the{" "}
              <a href="/skills" className="underline hover:text-[--color-fg]">
                Skills
              </a>{" "}
              section.
            </p>
          ) : (
            <div className="flex flex-wrap gap-1.5">
              {skills.map((skill) => (
                <button
                  key={skill.id}
                  onClick={() => toggleSkill(skill.id)}
                  className={cn(
                    "flex items-center gap-1 rounded-full border px-2.5 py-0.5 text-xs font-medium transition-colors",
                    selectedSkillIds.includes(skill.id)
                      ? "border-amber-500 bg-amber-500/10 text-amber-600 dark:text-amber-400"
                      : "border-[--color-border] text-[--color-muted] hover:border-amber-400",
                  )}
                >
                  <Zap className="h-2.5 w-2.5" />
                  {skill.name}
                </button>
              ))}
            </div>
          )}
        </div>
        )}

        {/* System Prompt */}
        {isAgentNode && (
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
        )}

        {/* Connected Tools */}
        {isAgentNode && CONNECTOR_TOOLS.some(ct => connectorStatus[ct.provider]) && (
          <div className="space-y-1.5">
            <label className="text-xs font-medium text-[--color-muted]">
              Connected Tools
              <span className="ml-1 text-[10px] font-normal">(your integrations)</span>
            </label>
            <div className="flex flex-wrap gap-1.5">
              {CONNECTOR_TOOLS.filter(ct => connectorStatus[ct.provider]).map(ct => (
                <button
                  key={ct.id}
                  onClick={() => toggleTool(ct.id)}
                  className={cn(
                    "flex items-center gap-1 rounded-full border px-2.5 py-0.5 text-xs font-medium transition-colors",
                    tools.includes(ct.id)
                      ? "border-sky-500 bg-sky-500/10 text-sky-600 dark:text-sky-400"
                      : "border-[--color-border] text-[--color-muted] hover:border-sky-400",
                  )}
                >
                  <span>{ct.icon}</span>
                  {ct.name}
                </button>
              ))}
            </div>
            {CONNECTOR_TOOLS.some(ct => !connectorStatus[ct.provider]) && (
              <p className="text-[10px] text-[--color-muted]">
                More integrations available in{" "}
                <a href="/mcp" className="underline hover:text-[--color-fg]">Integrations</a>.
              </p>
            )}
          </div>
        )}

        {/* Sandbox Tools */}
        {isAgentNode && (
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
        )}

        {/* Output Schema */}
        {isAgentNode && (
        <div className="space-y-1">
          <label className="text-xs font-medium text-[--color-muted]">
            Output Schema
            <span className="ml-1 text-[10px] font-normal">(optional — validates the final answer)</span>
          </label>
          <textarea
            value={outputSchemaJson}
            onChange={e => setOutputSchemaJson(e.target.value)}
            rows={6}
            spellCheck={false}
            placeholder={'{"type":"object","properties":{"summary":{"type":"string"}},"required":["summary"]}'}
            className={cn(
              "w-full rounded-md border bg-transparent px-3 py-2 font-mono text-xs focus:outline-none focus:ring-1 focus:ring-black dark:focus:ring-white resize-none",
              schemaJsonError ? "border-red-400" : "border-[--color-border]",
            )}
          />
          {schemaJsonError ? (
            <p className="text-[10px] text-red-500">Invalid JSON: {schemaJsonError}</p>
          ) : (
            <p className="text-[10px] text-[--color-muted]">
              If set, the agent&apos;s final answer must be JSON matching this schema — it gets one retry with the validation errors before falling back to its raw answer.
            </p>
          )}
        </div>
        )}
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
    </>
  );
}
