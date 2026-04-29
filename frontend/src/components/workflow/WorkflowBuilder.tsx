"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  addEdge,
  useNodesState,
  useEdgesState,
  type Connection,
  type Node,
  type Edge,
  MarkerType,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";

import { Plus, Play, Save, History, Download, Upload, FileJson } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { PatternSelector } from "./PatternSelector";
import { AgentConfigPanel } from "./AgentConfigPanel";
import { WorkflowRunsPanel } from "./WorkflowRunsPanel";
import {
  fetchWorkflowAgents,
  upsertWorkflowAgent,
  deleteWorkflowAgent,
  updateWorkflow,
  startWorkflowRun,
} from "@/lib/api";
import type { AgentPattern, AgentRole, RunStatus, TeamExecMode, Workflow, WorkflowAgent } from "@/types/agent";
import { cn } from "@/lib/utils";

// ── Flow JSON schema ──────────────────────────────────────────────────────────

interface FlowAgentJson {
  name: string;
  role: AgentRole;
  systemPrompt: string;
  tools: string[];
  skillIds: string[];
  orderIndex: number;
  posX: number;
  posY: number;
}

interface FlowJson {
  name: string;
  agentPattern: AgentPattern;
  teamExecMode: TeamExecMode | null;
  agents: FlowAgentJson[];
}

// ── Agent node visual ─────────────────────────────────────────────────────────

function AgentNodeCard({ data }: { data: { agent: WorkflowAgent; selected: boolean; onClick: () => void } }) {
  const { agent, selected, onClick } = data;
  const roleColors: Record<string, string> = {
    MAIN: "border-purple-500 bg-purple-50 dark:bg-purple-950/30",
    SUB:  "border-blue-400  bg-blue-50   dark:bg-blue-950/30",
    PEER: "border-[--color-border] bg-[--color-surface-raised]",
  };
  return (
    <div
      onClick={onClick}
      className={cn(
        "rounded-lg border-2 px-2.5 py-1.5 cursor-pointer shadow-sm w-[120px] transition-all",
        roleColors[agent.role],
        selected && "ring-2 ring-black dark:ring-white ring-offset-1",
      )}
    >
      <p className="text-[9px] font-semibold uppercase tracking-wide text-[--color-muted]">{agent.role}</p>
      <p className="text-xs font-bold truncate">{agent.name}</p>
      {agent.toolsJson && JSON.parse(agent.toolsJson).length > 0 && (
        <p className="text-[9px] text-[--color-muted] mt-0.5 truncate">
          {JSON.parse(agent.toolsJson).map((t: string) => t.toLowerCase()).join(", ")}
        </p>
      )}
    </div>
  );
}

const nodeTypes = { agent: AgentNodeCard };

// ── Main component ────────────────────────────────────────────────────────────

interface Props { workflow: Workflow }

export function WorkflowBuilder({ workflow }: Props) {
  const [agents,       setAgents]       = useState<WorkflowAgent[]>([]);
  const [nodes,        setNodes, onNodesChange] = useNodesState<Node>([]);
  const [edges,        setEdges, onEdgesChange] = useEdgesState<Edge>([]);
  const [selectedId,   setSelectedId]   = useState<number | null>(null);
  const [pattern,      setPattern]      = useState<AgentPattern>(workflow.agentPattern);
  const [teamExecMode, setTeamExecMode] = useState<TeamExecMode | null>(workflow.teamExecMode);
  const [runId,          setRunId]          = useState<string | null>(null);
  const [runInput,       setRunInput]       = useState("");
  const [showRunInput,   setShowRunInput]   = useState(false);
  const [showRunsPanel,  setShowRunsPanel]  = useState(false);
  const [saving,         setSaving]         = useState(false);
  const [browserNotify,  setBrowserNotify]  = useState(() =>
    typeof window !== "undefined" && localStorage.getItem("workflow:notify:browser") === "true");
  const [emailNotify,    setEmailNotify]    = useState(() =>
    typeof window !== "undefined" && localStorage.getItem("workflow:notify:email") === "true");
  const browserNotifyRef = useRef(browserNotify);
  const [showJsonEditor, setShowJsonEditor] = useState(false);
  const [jsonText,       setJsonText]       = useState("");
  const [jsonError,      setJsonError]      = useState<string | null>(null);
  const [importing,      setImporting]      = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const RUNS_MIN = 260;
  const RUNS_MAX = 720;
  const RUNS_DEFAULT = 320;
  const [runsWidth,    setRunsWidth]    = useState(RUNS_DEFAULT);
  const [runsDragging, setRunsDragging] = useState(false);
  const runsWidthRef  = useRef(RUNS_DEFAULT);
  const runsStartRef  = useRef<{ x: number; w: number } | null>(null);
  runsWidthRef.current = runsWidth;

  // Load agents from backend
  useEffect(() => {
    fetchWorkflowAgents(workflow.id).then(a => {
      setAgents(a);
      syncNodes(a);
    });
  }, [workflow.id]);

  function syncNodes(agentList: WorkflowAgent[]) {
    const newNodes: Node[] = agentList.map(agent => ({
      id:       String(agent.id),
      type:     "agent",
      position: { x: agent.posX, y: agent.posY },
      data:     { agent, selected: false, onClick: () => setSelectedId(agent.id) },
    }));
    setNodes(newNodes);

    // Auto-wire edges for sequential team
    if (workflow.teamExecMode === "SEQUENTIAL" && agentList.length > 1) {
      const sorted = [...agentList].sort((a, b) => a.orderIndex - b.orderIndex);
      const autoEdges: Edge[] = sorted.slice(0, -1).map((a, i) => ({
        id:           `e-${a.id}-${sorted[i + 1]!.id}`,
        source:       String(a.id),
        target:       String(sorted[i + 1]!.id),
        markerEnd:    { type: MarkerType.ArrowClosed },
        style:        { stroke: "#6366f1" },
      }));
      setEdges(autoEdges);
    }
  }

  // Sync nodes data when selection changes
  useEffect(() => {
    setNodes(prev => prev.map(n => ({
      ...n,
      data: {
        ...n.data,
        selected: Number(n.id) === selectedId,
        onClick: () => setSelectedId(Number(n.id)),
      },
    })));
  }, [selectedId]);

  const selectedAgent = useMemo(
    () => agents.find(a => a.id === selectedId) ?? null,
    [agents, selectedId],
  );

  // ── Runs panel resize ─────────────────────────────────────────────────────

  useEffect(() => {
    const stored = localStorage.getItem("workflow-runs-width");
    if (stored) {
      const n = parseInt(stored, 10);
      if (n >= RUNS_MIN && n <= RUNS_MAX) {
        setRunsWidth(n);
        runsWidthRef.current = n;
      }
    }
  }, []);

  useEffect(() => {
    if (!runsDragging) return;
    const move = (clientX: number) => {
      if (!runsStartRef.current) return;
      const next = Math.min(RUNS_MAX, Math.max(RUNS_MIN,
        runsStartRef.current.w + (runsStartRef.current.x - clientX),
      ));
      setRunsWidth(next);
    };
    const stop = () => {
      setRunsDragging(false);
      localStorage.setItem("workflow-runs-width", String(runsWidthRef.current));
      runsStartRef.current = null;
    };
    const onMouseMove = (e: MouseEvent) => move(e.clientX);
    const onTouchMove = (e: TouchEvent) => { if (e.touches[0]) move(e.touches[0].clientX); };
    window.addEventListener("mousemove",  onMouseMove);
    window.addEventListener("mouseup",    stop);
    window.addEventListener("touchmove",  onTouchMove, { passive: true });
    window.addEventListener("touchend",   stop);
    return () => {
      window.removeEventListener("mousemove",  onMouseMove);
      window.removeEventListener("mouseup",    stop);
      window.removeEventListener("touchmove",  onTouchMove);
      window.removeEventListener("touchend",   stop);
    };
  }, [runsDragging]);

  // ── Handlers ──────────────────────────────────────────────────────────────

  const onConnect = useCallback(
    (params: Connection) =>
      setEdges(eds => addEdge({ ...params, markerEnd: { type: MarkerType.ArrowClosed } }, eds)),
    [setEdges],
  );

  async function addAgent() {
    const role = pattern === "ORCHESTRATOR"
      ? (agents.some(a => a.role === "MAIN") ? "SUB" : "MAIN")
      : "PEER";

    const offsetX = 100 + agents.length * 220;
    const saved = await upsertWorkflowAgent(workflow.id, {
      name: role === "MAIN" ? "Main Agent" : `Agent ${agents.length + 1}`,
      role,
      systemPrompt: "",
      tools: [],
      orderIndex: agents.length,
      posX: offsetX,
      posY: 200,
    });
    const updated = [...agents, saved];
    setAgents(updated);
    syncNodes(updated);
    setSelectedId(saved.id);
  }

  async function handleSaveAgent(patch: Partial<WorkflowAgent> & { tools: string[]; skillIds: string[] }) {
    if (!selectedAgent) return;
    const saved = await upsertWorkflowAgent(workflow.id, {
      id:           selectedAgent.id,
      name:         patch.name ?? selectedAgent.name,
      role:         patch.role ?? selectedAgent.role,
      systemPrompt: patch.systemPrompt ?? selectedAgent.systemPrompt ?? "",
      tools:        patch.tools,
      skillIds:     patch.skillIds,
      orderIndex:   selectedAgent.orderIndex,
      posX:         selectedAgent.posX,
      posY:         selectedAgent.posY,
    });
    const updated = agents.map(a => a.id === saved.id ? saved : a);
    setAgents(updated);
    syncNodes(updated);
  }

  async function handleDeleteAgent() {
    if (!selectedAgent) return;
    await deleteWorkflowAgent(workflow.id, selectedAgent.id);
    const updated = agents.filter(a => a.id !== selectedAgent.id);
    setAgents(updated);
    syncNodes(updated);
    setSelectedId(null);
  }

  async function handlePatternChange(newPattern: AgentPattern, newMode: TeamExecMode | null) {
    setPattern(newPattern);
    setTeamExecMode(newMode);
    await updateWorkflow(workflow.id, { agentPattern: newPattern, teamExecMode: newMode });
  }

  async function handleSavePositions() {
    setSaving(true);
    try {
      for (const node of nodes) {
        const agent = agents.find(a => String(a.id) === node.id);
        if (!agent) continue;
        if (Math.abs(agent.posX - node.position.x) > 1 || Math.abs(agent.posY - node.position.y) > 1) {
          await upsertWorkflowAgent(workflow.id, {
            id: agent.id, name: agent.name, role: agent.role,
            systemPrompt: agent.systemPrompt ?? "",
            tools: JSON.parse(agent.toolsJson ?? "[]"),
            orderIndex: agent.orderIndex,
            posX: node.position.x, posY: node.position.y,
          });
        }
      }
    } finally {
      setSaving(false);
    }
  }

  function toggleBrowserNotify(enabled: boolean) {
    if (enabled && typeof window !== "undefined" && Notification.permission !== "granted") {
      Notification.requestPermission().then(p => {
        const granted = p === "granted";
        setBrowserNotify(granted);
        browserNotifyRef.current = granted;
        localStorage.setItem("workflow:notify:browser", String(granted));
      });
    } else {
      setBrowserNotify(enabled);
      browserNotifyRef.current = enabled;
      localStorage.setItem("workflow:notify:browser", String(enabled));
    }
  }

  function toggleEmailNotify(enabled: boolean) {
    setEmailNotify(enabled);
    localStorage.setItem("workflow:notify:email", String(enabled));
  }

  function handleRunComplete(output: string, status: RunStatus) {
    if (!browserNotifyRef.current) return;
    if (typeof window === "undefined" || Notification.permission !== "granted") return;
    const title = status === "DONE" ? "Workflow completed" : "Workflow failed";
    const body  = output ? output.slice(0, 100) + (output.length > 100 ? "…" : "") : status;
    new Notification(title, { body, icon: "/favicon.ico" });
  }

  function buildFlowJson(): FlowJson {
    return {
      name: workflow.name,
      agentPattern: pattern,
      teamExecMode: teamExecMode,
      agents: agents.map(a => ({
        name: a.name,
        role: a.role,
        systemPrompt: a.systemPrompt ?? "",
        tools: JSON.parse(a.toolsJson ?? "[]"),
        skillIds: JSON.parse(a.skillIdsJson ?? "[]"),
        orderIndex: a.orderIndex,
        posX: a.posX,
        posY: a.posY,
      })),
    };
  }

  function handleDownloadJson() {
    const json = buildFlowJson();
    const blob = new Blob([JSON.stringify(json, null, 2)], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `${workflow.name.replace(/\s+/g, "-").toLowerCase()}-flow.json`;
    a.click();
    URL.revokeObjectURL(url);
  }

  function handleOpenJsonEditor() {
    setJsonText(JSON.stringify(buildFlowJson(), null, 2));
    setJsonError(null);
    setShowJsonEditor(true);
  }

  async function applyFlowJson(raw: string) {
    let parsed: FlowJson;
    try {
      parsed = JSON.parse(raw) as FlowJson;
    } catch {
      setJsonError("Invalid JSON — could not parse.");
      return;
    }
    if (!parsed.agentPattern || !Array.isArray(parsed.agents)) {
      setJsonError("Missing required fields: agentPattern, agents[]");
      return;
    }
    setImporting(true);
    setJsonError(null);
    try {
      // Delete existing agents
      for (const a of agents) {
        await deleteWorkflowAgent(workflow.id, a.id);
      }
      // Update workflow pattern
      await updateWorkflow(workflow.id, {
        agentPattern: parsed.agentPattern,
        teamExecMode: parsed.teamExecMode ?? null,
      });
      setPattern(parsed.agentPattern);
      setTeamExecMode(parsed.teamExecMode ?? null);
      // Create new agents
      const created: WorkflowAgent[] = [];
      for (const agentDef of parsed.agents) {
        const saved = await upsertWorkflowAgent(workflow.id, {
          name: agentDef.name,
          role: agentDef.role,
          systemPrompt: agentDef.systemPrompt,
          tools: agentDef.tools,
          skillIds: agentDef.skillIds ?? [],
          orderIndex: agentDef.orderIndex,
          posX: agentDef.posX,
          posY: agentDef.posY,
        });
        created.push(saved);
      }
      setAgents(created);
      syncNodes(created);
      setSelectedId(null);
      setShowJsonEditor(false);
    } catch (err) {
      setJsonError(err instanceof Error ? err.message : "Import failed");
    } finally {
      setImporting(false);
    }
  }

  function handleFileImport(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = ev => {
      const text = ev.target?.result as string;
      setJsonText(text);
      setJsonError(null);
      setShowJsonEditor(true);
    };
    reader.readAsText(file);
    e.target.value = "";
  }

  async function handleRun() {
    if (!runInput.trim()) return;
    const { runId: id } = await startWorkflowRun(workflow.id, runInput, emailNotify);
    setRunId(id);
    setShowRunInput(false);
    setRunInput("");
    setShowRunsPanel(true);
  }

  // ── Render ────────────────────────────────────────────────────────────────

  return (
    <div className="flex h-full flex-col">
      {/* Toolbar */}
      <div className="overflow-x-auto border-b border-[--color-border]">
        <div className="flex min-w-max items-center gap-3 px-4 py-3">
          <PatternSelector
            pattern={pattern}
            teamExecMode={teamExecMode}
            onChange={handlePatternChange}
          />
          <div className="ml-auto flex items-center gap-2 pl-2">
            <Button size="sm" variant="ghost" onClick={addAgent}>
              <Plus className="h-3.5 w-3.5 mr-1" /> Add Agent
            </Button>
            <Button size="sm" variant="ghost" onClick={handleSavePositions} disabled={saving}>
              <Save className="h-3.5 w-3.5 mr-1" /> {saving ? "Saving…" : "Save Layout"}
            </Button>
            <Button size="sm" variant="ghost" onClick={handleDownloadJson} title="Download flow as JSON">
              <Download className="h-3.5 w-3.5 mr-1" /> JSON
            </Button>
            <Button size="sm" variant="ghost" onClick={() => fileInputRef.current?.click()} title="Import flow from JSON file">
              <Upload className="h-3.5 w-3.5 mr-1" /> Import
            </Button>
            <Button size="sm" variant="ghost" onClick={handleOpenJsonEditor} title="View / edit flow JSON">
              <FileJson className="h-3.5 w-3.5" />
            </Button>
            <input
              ref={fileInputRef}
              type="file"
              accept=".json,application/json"
              className="hidden"
              onChange={handleFileImport}
            />
            <Button size="sm" variant="ghost" onClick={() => setShowRunsPanel(v => !v)}>
              <History className="h-3.5 w-3.5 mr-1" /> Runs
            </Button>
            <Button size="sm" onClick={() => setShowRunInput(true)}>
              <Play className="h-3.5 w-3.5 mr-1" /> Run
            </Button>
          </div>
        </div>
      </div>

      {/* Run input overlay */}
      {showRunInput && (
        <div className="absolute inset-0 z-50 flex items-center justify-center bg-black/30">
          <div className="w-full max-w-md rounded-xl border border-[--color-border] bg-white dark:bg-zinc-900 p-6 shadow-xl mx-4">
            <p className="mb-3 text-sm font-semibold">Enter workflow input</p>
            <textarea
              autoFocus
              value={runInput}
              onChange={e => setRunInput(e.target.value)}
              rows={4}
              placeholder="Describe the task for the agents…"
              className="w-full rounded-md border border-[--color-border] bg-transparent px-3 py-2 text-sm focus:outline-none focus:ring-1 focus:ring-black dark:focus:ring-white resize-none"
            />

            {/* Notification preferences */}
            <div className="mt-4 rounded-md border border-[--color-border] divide-y divide-[--color-border]">
              <NotifyToggle
                icon="🔔"
                label="Browser notification"
                checked={browserNotify}
                onChange={toggleBrowserNotify}
              />
              <NotifyToggle
                icon="✉"
                label="Email notification"
                checked={emailNotify}
                onChange={toggleEmailNotify}
              />
            </div>

            <div className="mt-4 flex justify-end gap-2">
              <Button size="sm" variant="ghost" onClick={() => setShowRunInput(false)}>Cancel</Button>
              <Button size="sm" onClick={handleRun} disabled={!runInput.trim()}>
                <Play className="h-3.5 w-3.5 mr-1" /> Start Run
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* JSON editor modal */}
      {showJsonEditor && (
        <div className="absolute inset-0 z-50 flex items-center justify-center bg-black/30">
          <div className="flex flex-col w-full max-w-2xl max-h-[80vh] rounded-xl border border-[--color-border] bg-white dark:bg-zinc-900 shadow-xl mx-4">
            <div className="flex items-center justify-between px-5 py-3 border-b border-[--color-border]">
              <p className="text-sm font-semibold">Flow JSON</p>
              <div className="flex items-center gap-2">
                <Button size="sm" variant="ghost" onClick={handleDownloadJson}>
                  <Download className="h-3.5 w-3.5 mr-1" /> Download
                </Button>
                <Button size="sm" variant="ghost" onClick={() => setShowJsonEditor(false)}>Close</Button>
              </div>
            </div>
            <div className="flex-1 overflow-hidden p-4">
              <textarea
                value={jsonText}
                onChange={e => { setJsonText(e.target.value); setJsonError(null); }}
                spellCheck={false}
                className="w-full h-full min-h-[320px] font-mono text-xs rounded-md border border-[--color-border] bg-transparent px-3 py-2 focus:outline-none focus:ring-1 focus:ring-black dark:focus:ring-white resize-none"
              />
            </div>
            {jsonError && (
              <p className="px-5 pb-2 text-xs text-red-500">{jsonError}</p>
            )}
            <div className="px-5 py-3 border-t border-[--color-border] flex items-center justify-between gap-2">
              <p className="text-xs text-[--color-muted]">Importing will replace all current agents.</p>
              <div className="flex gap-2">
                <Button size="sm" variant="ghost" onClick={() => setShowJsonEditor(false)}>Cancel</Button>
                <Button size="sm" onClick={() => applyFlowJson(jsonText)} disabled={importing}>
                  {importing ? "Importing…" : "Import & Apply"}
                </Button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Canvas + Config */}
      <div className={cn("flex flex-1 overflow-hidden", runsDragging && "select-none cursor-col-resize")}>
        <div className="relative flex-1">
          <ReactFlow
            nodes={nodes}
            edges={edges}
            nodeTypes={nodeTypes}
            onNodesChange={onNodesChange}
            onEdgesChange={onEdgesChange}
            onConnect={onConnect}
            fitView
          >
            <Background />
            <Controls />
            <MiniMap />
          </ReactFlow>

          {agents.length === 0 && (
            <div className="pointer-events-none absolute inset-0 flex items-center justify-center">
              <div className="text-center text-[--color-muted]">
                <p className="text-sm font-medium">No agents yet</p>
                <p className="text-xs mt-1">Click "Add Agent" to get started</p>
              </div>
            </div>
          )}
        </div>

        {selectedAgent && !showRunsPanel && (
          <AgentConfigPanel
            agent={selectedAgent}
            pattern={pattern}
            onSave={handleSaveAgent}
            onDelete={handleDeleteAgent}
            onClose={() => setSelectedId(null)}
          />
        )}

        {showRunsPanel && (
          <>
            {/* Draggable divider — same visual style as the sidebar handle */}
            <div
              className="group relative w-1 shrink-0 cursor-col-resize"
              onMouseDown={e => { e.preventDefault(); runsStartRef.current = { x: e.clientX, w: runsWidthRef.current }; setRunsDragging(true); }}
              onTouchStart={e => { if (e.touches[0]) { runsStartRef.current = { x: e.touches[0].clientX, w: runsWidthRef.current }; setRunsDragging(true); } }}
              aria-hidden
            >
              <div className={cn(
                "absolute inset-y-0 left-1/2 w-0.5 -translate-x-1/2 rounded-full transition-colors duration-150",
                runsDragging ? "bg-blue-500" : "bg-[--color-border] group-hover:bg-blue-400",
              )} />
            </div>
            <WorkflowRunsPanel
              workflowId={workflow.id}
              liveRunId={runId}
              onClose={() => setShowRunsPanel(false)}
              onRunComplete={handleRunComplete}
              width={runsWidth}
            />
          </>
        )}
      </div>
    </div>
  );
}

// ── Notification toggle row ───────────────────────────────────────────────────

function NotifyToggle({
  icon,
  label,
  checked,
  onChange,
}: {
  icon: string;
  label: string;
  checked: boolean;
  onChange: (v: boolean) => void;
}) {
  return (
    <label className="flex items-center justify-between px-3 py-2 cursor-pointer select-none">
      <span className="flex items-center gap-2 text-xs text-[--color-fg]">
        <span>{icon}</span>
        <span>{label}</span>
      </span>
      {/* pill toggle */}
      <span
        role="switch"
        aria-checked={checked}
        onClick={() => onChange(!checked)}
        className={cn(
          "relative inline-flex h-5 w-9 shrink-0 rounded-full border-2 border-transparent transition-colors cursor-pointer",
          checked ? "bg-black dark:bg-white" : "bg-[--color-border]",
        )}
      >
        <span
          className={cn(
            "pointer-events-none inline-block h-4 w-4 rounded-full bg-white dark:bg-zinc-900 shadow transition-transform",
            checked ? "translate-x-4" : "translate-x-0",
          )}
        />
      </span>
    </label>
  );
}
