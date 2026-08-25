"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";
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
  type EdgeChange,
  MarkerType,
  Handle,
  Position,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";

import { Plus, Play, Save, History, Download, Upload, FileJson, CalendarClock, Bot, GitBranch, FlagOff, GitCommitHorizontal, RotateCcw } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { PatternSelector } from "./PatternSelector";
import { AgentConfigPanel } from "./AgentConfigPanel";
import { WorkflowRunsPanel } from "./WorkflowRunsPanel";
import { WorkflowScheduleModal } from "./WorkflowScheduleModal";
import {
  fetchWorkflow,
  fetchWorkflowAgents,
  fetchSkills,
  upsertWorkflowAgent,
  deleteWorkflowAgent,
  updateWorkflow,
  startWorkflowRun,
  fetchWorkflowEdges,
  upsertWorkflowEdge,
  deleteWorkflowEdge,
  fetchWorkflowVersions,
  saveWorkflowVersion,
  restoreWorkflowVersion,
} from "@/lib/api";
import type { AgentPattern, AgentRole, NodeKind, RunStatus, TeamExecMode, Workflow, WorkflowAgent, WorkflowEdgeDto, WorkflowVersion, Skill } from "@/types/agent";
import { cn } from "@/lib/utils";

// ── Flow JSON schema ──────────────────────────────────────────────────────────

interface FlowAgentJson {
  name: string;
  role: AgentRole;
  nodeKind: NodeKind;
  conditionExpr: string | null;
  outputSchemaJson: string | null;
  systemPrompt: string;
  tools: string[];
  skillIds: string[];
  orderIndex: number;
  posX: number;
  posY: number;
}

interface FlowEdgeJson {
  sourceIndex: number;
  targetIndex: number;
  branchLabel: string | null;
}

interface FlowJson {
  name: string;
  agentPattern: AgentPattern;
  teamExecMode: TeamExecMode | null;
  agents: FlowAgentJson[];
  edges?: FlowEdgeJson[];
}

const GRAPH_EDGE_PREFIX = "e-graph-";

// ── Agent node visual ─────────────────────────────────────────────────────────

function AgentNodeCard({ data }: {
  data: { agent: WorkflowAgent; selected: boolean; onClick: () => void; skills: Skill[] }
}) {
  const { agent, selected, onClick, skills = [] } = data;

  const agentTools: string[] = (() => {
    try { return JSON.parse(agent.toolsJson ?? "[]"); } catch { return []; }
  })();

  const agentSkillIds: string[] = (() => {
    try { return JSON.parse(agent.skillIdsJson ?? "[]"); } catch { return []; }
  })();

  const agentSkills = skills.filter(s => agentSkillIds.includes(s.id));

  const roleColors: Record<string, string> = {
    MAIN: "border-purple-500 bg-purple-50 dark:bg-purple-950/30",
    SUB:  "border-blue-400  bg-blue-50   dark:bg-blue-950/30",
    PEER: "border-[--color-border] bg-[--color-surface-raised]",
  };

  return (
    <div
      onClick={onClick}
      className={cn(
        "rounded-lg border-2 px-2.5 py-2 cursor-pointer shadow-sm w-[160px] transition-all",
        roleColors[agent.role],
        selected && "ring-2 ring-black dark:ring-white ring-offset-1",
      )}
    >
      <Handle
        type="target"
        position={Position.Top}
        className="!w-2 !h-2 !border-[--color-border] !bg-[--color-surface-raised]"
      />

      <p className="text-[9px] font-semibold uppercase tracking-wide text-[--color-muted]">{agent.role}</p>
      <p className="text-xs font-bold truncate">{agent.name}</p>

      {agentTools.length > 0 && (
        <p className="text-[9px] text-[--color-muted] mt-0.5 truncate">
          {agentTools.map((t: string) => t.toLowerCase()).join(", ")}
        </p>
      )}

      {agentSkills.length > 0 && (
        <div className="mt-1.5 flex flex-wrap gap-0.5">
          {agentSkills.map(skill => (
            <span
              key={skill.id}
              className="flex items-center gap-0.5 rounded-full border border-amber-200 bg-amber-100 px-1.5 py-0.5 text-[8px] font-medium text-amber-700 dark:border-amber-800 dark:bg-amber-900/40 dark:text-amber-300"
            >
              ⚡ {skill.name}
            </span>
          ))}
        </div>
      )}

      <Handle
        type="source"
        position={Position.Bottom}
        className="!w-2 !h-2 !border-[--color-border] !bg-[--color-surface-raised]"
      />
    </div>
  );
}

// ── Condition / End node visuals ──────────────────────────────────────────────

function ConditionNodeCard({ data }: {
  data: { agent: WorkflowAgent; selected: boolean; onClick: () => void }
}) {
  const { agent, selected, onClick } = data;
  return (
    <div
      onClick={onClick}
      className={cn(
        "rounded-lg border-2 border-amber-500 bg-amber-50 dark:bg-amber-950/30 px-2.5 py-2 cursor-pointer shadow-sm w-[160px] transition-all",
        selected && "ring-2 ring-black dark:ring-white ring-offset-1",
      )}
    >
      <Handle type="target" position={Position.Top} className="!w-2 !h-2 !border-[--color-border] !bg-[--color-surface-raised]" />
      <p className="flex items-center gap-1 text-[9px] font-semibold uppercase tracking-wide text-amber-700 dark:text-amber-400">
        <GitBranch className="h-2.5 w-2.5" /> Condition
      </p>
      <p className="text-xs font-bold truncate">{agent.name}</p>
      {agent.conditionExpr && (
        <p className="text-[9px] text-[--color-muted] mt-0.5 line-clamp-2">{agent.conditionExpr}</p>
      )}
      <Handle type="source" position={Position.Bottom} className="!w-2 !h-2 !border-[--color-border] !bg-[--color-surface-raised]" />
    </div>
  );
}

function EndNodeCard({ data }: {
  data: { agent: WorkflowAgent; selected: boolean; onClick: () => void }
}) {
  const { agent, selected, onClick } = data;
  return (
    <div
      onClick={onClick}
      className={cn(
        "rounded-lg border-2 border-zinc-500 bg-zinc-100 dark:bg-zinc-800/60 px-2.5 py-2 cursor-pointer shadow-sm w-[130px] transition-all",
        selected && "ring-2 ring-black dark:ring-white ring-offset-1",
      )}
    >
      <Handle type="target" position={Position.Top} className="!w-2 !h-2 !border-[--color-border] !bg-[--color-surface-raised]" />
      <p className="flex items-center gap-1 text-[9px] font-semibold uppercase tracking-wide text-[--color-muted]">
        <FlagOff className="h-2.5 w-2.5" /> End
      </p>
      <p className="text-xs font-bold truncate">{agent.name}</p>
    </div>
  );
}

const nodeTypes = { agent: AgentNodeCard, condition: ConditionNodeCard, end: EndNodeCard };

const NODE_TYPE_BY_KIND: Record<NodeKind, "agent" | "condition" | "end"> = {
  AGENT: "agent",
  CONDITION: "condition",
  END: "end",
};

// ── Main component ────────────────────────────────────────────────────────────

interface Props { workflow: Workflow }

export function WorkflowBuilder({ workflow }: Props) {
  const [agents,       setAgents]       = useState<WorkflowAgent[]>([]);
  const [skills,       setSkills]       = useState<Skill[]>([]);
  const [nodes,        setNodes, onNodesChange] = useNodesState<Node>([]);
  const [edges,        setEdges, onEdgesChange] = useEdgesState<Edge>([]);
  const manualEdgesRef = useRef<Edge[]>([]);
  const [graphEdges,   setGraphEdges]   = useState<WorkflowEdgeDto[]>([]);
  const graphEdgesRef  = useRef<WorkflowEdgeDto[]>([]);
  graphEdgesRef.current = graphEdges;
  const [showAddMenu,  setShowAddMenu]  = useState(false);
  const [addMenuPos,   setAddMenuPos]   = useState<{ top: number; left: number } | null>(null);
  const addMenuButtonRef = useRef<HTMLDivElement>(null);
  const addMenuPopupRef  = useRef<HTMLDivElement>(null);
  const [selectedId,   setSelectedId]   = useState<number | null>(null);
  const [pattern,      setPattern]      = useState<AgentPattern>(workflow.agentPattern);
  const [teamExecMode, setTeamExecMode] = useState<TeamExecMode | null>(workflow.teamExecMode);
  const [runId,          setRunId]          = useState<string | null>(null);
  const [runInput,       setRunInput]       = useState("");
  const [showRunInput,      setShowRunInput]      = useState(false);
  const [showRunsPanel,     setShowRunsPanel]     = useState(false);
  const [showScheduleModal, setShowScheduleModal] = useState(false);
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

  const [showVersionsPanel, setShowVersionsPanel] = useState(false);
  const [versions,          setVersions]          = useState<WorkflowVersion[]>([]);
  const [loadingVersions,   setLoadingVersions]   = useState(false);
  const [savingVersion,     setSavingVersion]     = useState(false);
  const [restoringVersion,  setRestoringVersion]  = useState<number | null>(null);

  const RUNS_MIN = 260;
  const RUNS_MAX = 720;
  const RUNS_DEFAULT = 320;
  const [runsWidth,    setRunsWidth]    = useState(RUNS_DEFAULT);
  const [runsDragging, setRunsDragging] = useState(false);
  const runsWidthRef  = useRef(RUNS_DEFAULT);
  const runsStartRef  = useRef<{ x: number; w: number } | null>(null);
  runsWidthRef.current = runsWidth;

  const syncNodes = useCallback((
    agentList: WorkflowAgent[], skillList: Skill[], edgeList?: WorkflowEdgeDto[],
    patternOverride?: AgentPattern, teamExecModeOverride?: TeamExecMode | null,
  ) => {
    // Overrides let callers pass the just-fetched pattern/mode instead of the (stale,
    // pre-re-render) closed-over state right after calling setPattern/setTeamExecMode.
    const effectivePattern      = patternOverride ?? pattern;
    const effectiveTeamExecMode = teamExecModeOverride !== undefined ? teamExecModeOverride : teamExecMode;

    const newNodes: Node[] = agentList.map(agent => ({
      id:       String(agent.id),
      type:     effectivePattern === "GRAPH" ? NODE_TYPE_BY_KIND[agent.nodeKind] : "agent",
      position: { x: agent.posX, y: agent.posY },
      data:     { agent, selected: false, onClick: () => setSelectedId(agent.id), skills: skillList },
    }));
    setNodes(newNodes);

    if (effectivePattern === "GRAPH") {
      const nodeIds = new Set(agentList.map(a => String(a.id)));
      const graph = (edgeList ?? graphEdgesRef.current).filter(
        e => nodeIds.has(String(e.sourceNodeId)) && nodeIds.has(String(e.targetNodeId)),
      );
      setEdges(graph.map(e => ({
        id:        `${GRAPH_EDGE_PREFIX}${e.id}`,
        source:    String(e.sourceNodeId),
        target:    String(e.targetNodeId),
        markerEnd: { type: MarkerType.ArrowClosed },
        style:     { stroke: e.branchLabel ? "#f59e0b" : "#94a3b8" },
        label:     e.branchLabel ?? undefined,
        labelStyle: { fontSize: 8, fill: "#f59e0b" },
      })));
      return;
    }

    const autoEdges: Edge[] = [];

    // Sequential team: chain agents in order
    if (effectiveTeamExecMode === "SEQUENTIAL" && agentList.length > 1) {
      const sorted = [...agentList].sort((a, b) => a.orderIndex - b.orderIndex);
      sorted.slice(0, -1).forEach((a, i) => {
        autoEdges.push({
          id:        `e-seq-${a.id}-${sorted[i + 1]!.id}`,
          source:    String(a.id),
          target:    String(sorted[i + 1]!.id),
          markerEnd: { type: MarkerType.ArrowClosed },
          style:     { stroke: "#6366f1" },
          label:     "next",
          labelStyle: { fontSize: 8, fill: "#6366f1" },
        });
      });
    }

    // Orchestrator: draw MAIN → each SUB
    if (effectivePattern === "ORCHESTRATOR") {
      const mainAgent = agentList.find(a => a.role === "MAIN");
      const subAgents = agentList.filter(a => a.role === "SUB");
      if (mainAgent) {
        subAgents.forEach(sub => {
          autoEdges.push({
            id:        `e-orch-${mainAgent.id}-${sub.id}`,
            source:    String(mainAgent.id),
            target:    String(sub.id),
            markerEnd: { type: MarkerType.ArrowClosed },
            style:     { stroke: "#a855f7" },
            label:     "delegates",
            labelStyle: { fontSize: 8, fill: "#a855f7" },
          });
        });
      }
    }

    // Re-apply manual edges, filtering out any whose nodes were deleted
    const nodeIds = new Set(agentList.map(a => String(a.id)));
    const autoEdgeIds = new Set(autoEdges.map(e => e.id));
    const validManual = manualEdgesRef.current.filter(
      e => nodeIds.has(e.source) && nodeIds.has(e.target) && !autoEdgeIds.has(e.id),
    );

    setEdges([...autoEdges, ...validManual]);
  }, [teamExecMode, pattern, setNodes, setEdges]);

  // Load agents, skills, and (for GRAPH pattern) persisted edges from backend
  useEffect(() => {
    Promise.all([fetchWorkflowAgents(workflow.id), fetchSkills(), fetchWorkflowEdges(workflow.id)]).then(([a, s, e]) => {
      setAgents(a);
      setSkills(s);
      setGraphEdges(e);
      syncNodes(a, s, e);
    });
  }, [workflow.id, syncNodes]);

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
  }, [selectedId, setNodes]);

  const selectedAgent = useMemo(
    () => agents.find(a => a.id === selectedId) ?? null,
    [agents, selectedId],
  );

  // Position the "Add Component" dropdown (portaled to <body> so the toolbar's
  // overflow-x-auto — which implicitly clips overflow-y too — can't cut it off),
  // close it on outside click, and close it on scroll/resize since its position is fixed.
  useEffect(() => {
    if (!showAddMenu) return;
    const rect = addMenuButtonRef.current?.getBoundingClientRect();
    if (rect) setAddMenuPos({ top: rect.bottom + 4, left: rect.left });

    const onClick = (e: MouseEvent) => {
      const target = e.target as globalThis.Node;
      if (addMenuButtonRef.current?.contains(target)) return;
      if (addMenuPopupRef.current?.contains(target)) return;
      setShowAddMenu(false);
    };
    const onDismiss = () => setShowAddMenu(false);
    document.addEventListener("mousedown", onClick);
    window.addEventListener("scroll", onDismiss, true);
    window.addEventListener("resize", onDismiss);
    return () => {
      document.removeEventListener("mousedown", onClick);
      window.removeEventListener("scroll", onDismiss, true);
      window.removeEventListener("resize", onDismiss);
    };
  }, [showAddMenu]);

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
    (params: Connection) => {
      if (pattern === "GRAPH") {
        const sourceAgent = agents.find(a => String(a.id) === params.source);
        let branchLabel: string | null = null;
        if (sourceAgent?.nodeKind === "CONDITION") {
          const input = window.prompt("Branch label for this edge (e.g. \"yes\" / \"no\"):", "");
          if (input === null) return; // cancelled
          branchLabel = input.trim() || null;
        }
        upsertWorkflowEdge(workflow.id, {
          sourceNodeId: Number(params.source),
          targetNodeId: Number(params.target),
          branchLabel,
        }).then(saved => {
          const updated = [...graphEdgesRef.current, saved];
          setGraphEdges(updated);
          syncNodes(agents, skills, updated);
        });
        return;
      }
      const newEdge: Edge = {
        ...params,
        id:        `e-manual-${params.source}-${params.target}-${Date.now()}`,
        markerEnd: { type: MarkerType.ArrowClosed },
        style:     { stroke: "#94a3b8" },
      };
      manualEdgesRef.current = [...manualEdgesRef.current, newEdge];
      setEdges(eds => addEdge(newEdge, eds));
    },
    [setEdges, pattern, agents, skills, workflow.id, syncNodes],
  );

  const handleEdgesChange = useCallback(
    (changes: EdgeChange[]) => {
      onEdgesChange(changes);
      changes.forEach(change => {
        if (change.type !== "remove") return;
        if (pattern === "GRAPH" && change.id.startsWith(GRAPH_EDGE_PREFIX)) {
          const edgeId = Number(change.id.slice(GRAPH_EDGE_PREFIX.length));
          deleteWorkflowEdge(workflow.id, edgeId);
          setGraphEdges(prev => prev.filter(e => e.id !== edgeId));
        } else {
          manualEdgesRef.current = manualEdgesRef.current.filter(e => e.id !== change.id);
        }
      });
    },
    [onEdgesChange, pattern, workflow.id],
  );

  async function addComponent(kind: NodeKind) {
    const role = pattern === "ORCHESTRATOR"
      ? (agents.some(a => a.role === "MAIN") ? "SUB" : "MAIN")
      : "PEER";

    const nameByKind: Record<NodeKind, string> = {
      AGENT:     role === "MAIN" ? "Main Agent" : `Agent ${agents.length + 1}`,
      CONDITION: `Condition ${agents.length + 1}`,
      END:       "End",
    };

    const offsetX = 100 + agents.length * 220;
    const saved = await upsertWorkflowAgent(workflow.id, {
      name: nameByKind[kind],
      role,
      nodeKind: kind,
      systemPrompt: "",
      tools: [],
      orderIndex: agents.length,
      posX: offsetX,
      posY: 200,
    });
    const updated = [...agents, saved];
    setAgents(updated);
    syncNodes(updated, skills);
    setSelectedId(saved.id);
  }

  async function handleSaveAgent(patch: Partial<WorkflowAgent> & { tools: string[]; skillIds: string[] }) {
    if (!selectedAgent) return;
    const saved = await upsertWorkflowAgent(workflow.id, {
      id:               selectedAgent.id,
      name:             patch.name ?? selectedAgent.name,
      role:             patch.role ?? selectedAgent.role,
      nodeKind:         selectedAgent.nodeKind,
      conditionExpr:    patch.conditionExpr !== undefined ? patch.conditionExpr : selectedAgent.conditionExpr,
      outputSchemaJson: patch.outputSchemaJson !== undefined ? patch.outputSchemaJson : selectedAgent.outputSchemaJson,
      systemPrompt:     patch.systemPrompt ?? selectedAgent.systemPrompt ?? "",
      tools:            patch.tools,
      skillIds:         patch.skillIds,
      orderIndex:       selectedAgent.orderIndex,
      posX:             selectedAgent.posX,
      posY:             selectedAgent.posY,
    });
    const updated = agents.map(a => a.id === saved.id ? saved : a);
    setAgents(updated);
    syncNodes(updated, skills);
  }

  async function handleDeleteAgent() {
    if (!selectedAgent) return;
    await deleteWorkflowAgent(workflow.id, selectedAgent.id);
    const updated = agents.filter(a => a.id !== selectedAgent.id);
    setAgents(updated);
    syncNodes(updated, skills);
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
            nodeKind: agent.nodeKind,
            conditionExpr: agent.conditionExpr,
            outputSchemaJson: agent.outputSchemaJson,
            systemPrompt: agent.systemPrompt ?? "",
            tools: JSON.parse(agent.toolsJson ?? "[]"),
            skillIds: JSON.parse(agent.skillIdsJson ?? "[]"),
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
    const indexById = new Map(agents.map((a, i) => [a.id, i]));
    return {
      name: workflow.name,
      agentPattern: pattern,
      teamExecMode: teamExecMode,
      agents: agents.map(a => ({
        name: a.name,
        role: a.role,
        nodeKind: a.nodeKind,
        conditionExpr: a.conditionExpr,
        outputSchemaJson: a.outputSchemaJson,
        systemPrompt: a.systemPrompt ?? "",
        tools: JSON.parse(a.toolsJson ?? "[]"),
        skillIds: JSON.parse(a.skillIdsJson ?? "[]"),
        orderIndex: a.orderIndex,
        posX: a.posX,
        posY: a.posY,
      })),
      edges: pattern === "GRAPH"
        ? graphEdges
            .filter(e => indexById.has(e.sourceNodeId) && indexById.has(e.targetNodeId))
            .map(e => ({
              sourceIndex: indexById.get(e.sourceNodeId)!,
              targetIndex: indexById.get(e.targetNodeId)!,
              branchLabel: e.branchLabel,
            }))
        : undefined,
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
          nodeKind: agentDef.nodeKind ?? "AGENT",
          conditionExpr: agentDef.conditionExpr ?? null,
          outputSchemaJson: agentDef.outputSchemaJson ?? null,
          systemPrompt: agentDef.systemPrompt,
          tools: agentDef.tools,
          skillIds: agentDef.skillIds ?? [],
          orderIndex: agentDef.orderIndex,
          posX: agentDef.posX,
          posY: agentDef.posY,
        });
        created.push(saved);
      }
      // Re-create edges (GRAPH pattern) using array-index references resolved against the newly created agent ids
      const createdEdges: WorkflowEdgeDto[] = [];
      if (parsed.agentPattern === "GRAPH" && Array.isArray(parsed.edges)) {
        for (const edgeDef of parsed.edges) {
          const source = created[edgeDef.sourceIndex];
          const target = created[edgeDef.targetIndex];
          if (!source || !target) continue;
          const savedEdge = await upsertWorkflowEdge(workflow.id, {
            sourceNodeId: source.id,
            targetNodeId: target.id,
            branchLabel: edgeDef.branchLabel,
          });
          createdEdges.push(savedEdge);
        }
      }
      setAgents(created);
      setGraphEdges(createdEdges);
      syncNodes(created, skills, createdEdges, parsed.agentPattern, parsed.teamExecMode ?? null);
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

  function loadVersions() {
    setLoadingVersions(true);
    fetchWorkflowVersions(workflow.id)
      .then(setVersions)
      .finally(() => setLoadingVersions(false));
  }

  function handleOpenVersionsPanel() {
    setShowVersionsPanel(true);
    loadVersions();
  }

  async function handleSaveVersion() {
    const label = window.prompt("Label this version (optional):", "");
    if (label === null) return; // cancelled
    setSavingVersion(true);
    try {
      await saveWorkflowVersion(workflow.id, label.trim() || undefined);
      loadVersions();
    } finally {
      setSavingVersion(false);
    }
  }

  async function handleRestoreVersion(versionNumber: number) {
    if (!window.confirm(`Restore v${versionNumber}? This replaces the current agents and connections (the restore itself is saved as a new version, so nothing is lost).`)) {
      return;
    }
    setRestoringVersion(versionNumber);
    try {
      await restoreWorkflowVersion(workflow.id, versionNumber);
      const [wf, a, s, e] = await Promise.all([
        fetchWorkflow(workflow.id),
        fetchWorkflowAgents(workflow.id),
        fetchSkills(),
        fetchWorkflowEdges(workflow.id),
      ]);
      if (wf) {
        setPattern(wf.agentPattern);
        setTeamExecMode(wf.teamExecMode);
      }
      setAgents(a);
      setSkills(s);
      setGraphEdges(e);
      syncNodes(a, s, e, wf?.agentPattern, wf?.teamExecMode ?? null);
      setSelectedId(null);
      loadVersions();
      setShowVersionsPanel(false);
    } finally {
      setRestoringVersion(null);
    }
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
            <div ref={addMenuButtonRef}>
              <Button
                size="sm"
                variant="ghost"
                onClick={() => pattern === "GRAPH" ? setShowAddMenu(v => !v) : addComponent("AGENT")}
              >
                <Plus className="h-3.5 w-3.5 mr-1" /> {pattern === "GRAPH" ? "Add Component" : "Add Agent"}
              </Button>
            </div>
            {showAddMenu && pattern === "GRAPH" && addMenuPos && typeof document !== "undefined" && createPortal(
              <div
                ref={addMenuPopupRef}
                style={{ top: addMenuPos.top, left: addMenuPos.left }}
                className="fixed z-50 w-40 overflow-hidden rounded-md border border-[--color-border] bg-white dark:bg-zinc-900 shadow-lg py-1"
              >
                <button
                  onClick={() => { addComponent("AGENT"); setShowAddMenu(false); }}
                  className="flex w-full items-center gap-2 px-3 py-1.5 text-left text-xs hover:bg-[--color-border]/30"
                >
                  <Bot className="h-3.5 w-3.5" /> Agent
                </button>
                <button
                  onClick={() => { addComponent("CONDITION"); setShowAddMenu(false); }}
                  className="flex w-full items-center gap-2 px-3 py-1.5 text-left text-xs hover:bg-[--color-border]/30"
                >
                  <GitBranch className="h-3.5 w-3.5" /> Condition
                </button>
                <button
                  onClick={() => { addComponent("END"); setShowAddMenu(false); }}
                  className="flex w-full items-center gap-2 px-3 py-1.5 text-left text-xs hover:bg-[--color-border]/30"
                >
                  <FlagOff className="h-3.5 w-3.5" /> End
                </button>
              </div>,
              document.body,
            )}
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
            <Button size="sm" variant="ghost" onClick={() => setShowScheduleModal(true)}>
              <CalendarClock className="h-3.5 w-3.5 mr-1" /> Schedule
            </Button>
            <Button size="sm" variant="ghost" onClick={handleOpenVersionsPanel}>
              <GitCommitHorizontal className="h-3.5 w-3.5 mr-1" /> Versions
            </Button>
            <Button size="sm" variant="ghost" onClick={() => setShowRunsPanel(v => !v)}>
              <History className="h-3.5 w-3.5 mr-1" /> Runs
            </Button>
            <Button size="sm" onClick={() => setShowRunInput(true)}>
              <Play className="h-3.5 w-3.5 mr-1" /> Run
            </Button>
          </div>
        </div>
      </div>

      {/* Schedule modal */}
      {showScheduleModal && (
        <WorkflowScheduleModal
          workflowId={workflow.id}
          onClose={() => setShowScheduleModal(false)}
        />
      )}

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

      {/* Versions modal */}
      {showVersionsPanel && (
        <div className="absolute inset-0 z-50 flex items-center justify-center bg-black/30">
          <div className="flex flex-col w-full max-w-md max-h-[80vh] rounded-xl border border-[--color-border] bg-white dark:bg-zinc-900 shadow-xl mx-4">
            <div className="flex items-center justify-between px-5 py-3 border-b border-[--color-border]">
              <p className="text-sm font-semibold">Workflow Versions</p>
              <Button size="sm" variant="ghost" onClick={() => setShowVersionsPanel(false)}>Close</Button>
            </div>
            <div className="px-5 py-3 border-b border-[--color-border]">
              <Button size="sm" onClick={handleSaveVersion} disabled={savingVersion} className="w-full flex items-center justify-center gap-1.5">
                <Save className="h-3.5 w-3.5" />
                {savingVersion ? "Saving…" : "Save Current as Version"}
              </Button>
            </div>
            <div className="flex-1 overflow-y-auto">
              {loadingVersions && (
                <p className="px-5 py-6 text-center text-xs text-[--color-muted]">Loading…</p>
              )}
              {!loadingVersions && versions.length === 0 && (
                <p className="px-5 py-6 text-center text-xs text-[--color-muted]">No versions saved yet</p>
              )}
              {versions.map(v => (
                <div
                  key={v.id}
                  className="flex items-center gap-3 px-5 py-3 border-b border-[--color-border] last:border-b-0"
                >
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <span className="rounded-full border border-[--color-border] px-1.5 py-0 text-[10px] font-medium text-[--color-muted]">
                        v{v.versionNumber}
                      </span>
                      {v.label && <span className="text-xs font-medium truncate">{v.label}</span>}
                    </div>
                    <p className="text-[10px] text-[--color-muted] mt-0.5">
                      {new Date(v.createdAt).toLocaleString()}
                    </p>
                  </div>
                  <Button
                    size="sm"
                    variant="ghost"
                    onClick={() => handleRestoreVersion(v.versionNumber)}
                    disabled={restoringVersion !== null}
                    className="flex items-center gap-1 shrink-0"
                  >
                    <RotateCcw className="h-3.5 w-3.5" />
                    {restoringVersion === v.versionNumber ? "Restoring…" : "Restore"}
                  </Button>
                </div>
              ))}
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
            onEdgesChange={handleEdgesChange}
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
                <p className="text-sm font-medium">No components yet</p>
                <p className="text-xs mt-1">
                  Click &quot;{pattern === "GRAPH" ? "Add Component" : "Add Agent"}&quot; to get started
                </p>
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
