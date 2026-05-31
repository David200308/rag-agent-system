"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import {
  ArrowRight,
  LayoutGrid,
  LayoutList,
  Menu,
  Network,
  Plus,
  Search,
  Trash2,
  Users,
} from "lucide-react";
import { Button } from "@/components/ui/Button";
import { PatternSelector } from "@/components/workflow/PatternSelector";
import { createWorkflow, deleteWorkflow, fetchWorkflows } from "@/lib/api";
import type { AgentPattern, TeamExecMode, Workflow } from "@/types/agent";
import { cn } from "@/lib/utils";
import { useWorkflowSidebar } from "./WorkflowSidebarContext";

type ViewMode = "list" | "grid";

export default function WorkflowListPage() {
  const router = useRouter();
  const openSidebar = useWorkflowSidebar();
  const [workflows,  setWorkflows]  = useState<Workflow[]>([]);
  const [creating,   setCreating]   = useState(false);
  const [showNew,    setShowNew]    = useState(false);
  const [newName,    setNewName]    = useState("");
  const [newPattern, setNewPattern] = useState<AgentPattern>("ORCHESTRATOR");
  const [newMode,    setNewMode]    = useState<TeamExecMode | null>(null);
  const [search,     setSearch]     = useState("");
  const [viewMode,   setViewMode]   = useState<ViewMode>("list");

  useEffect(() => { fetchWorkflows().then(setWorkflows); }, []);

  const filtered = workflows.filter(wf =>
    wf.name.toLowerCase().includes(search.toLowerCase()),
  );

  async function handleCreate() {
    if (!newName.trim()) return;
    setCreating(true);
    try {
      const wf = await createWorkflow({
        name: newName.trim(),
        agentPattern: newPattern,
        teamExecMode: newMode,
      });
      setWorkflows(prev => [wf, ...prev]);
      router.push(`/workflow/${wf.id}`);
    } finally {
      setCreating(false);
      setShowNew(false);
      setNewName("");
    }
  }

  async function handleDelete(id: string) {
    await deleteWorkflow(id);
    setWorkflows(prev => prev.filter(w => w.id !== id));
  }

  return (
    <div className="h-full overflow-y-auto">
      <div className={cn(
        "mx-auto px-4 py-8 transition-all",
        viewMode === "grid" ? "max-w-5xl" : "max-w-2xl",
      )}>
        {/* Header */}
        <div className="mb-6 flex items-center justify-between gap-3">
          <div className="flex items-center gap-2 min-w-0">
            <button
              onClick={openSidebar}
              className="rounded-md p-1 text-[--color-muted] hover:bg-[--color-border]/50 sm:hidden shrink-0"
              aria-label="Open menu"
            >
              <Menu className="h-5 w-5" />
            </button>
            <div className="min-w-0">
              <h1 className="text-lg font-bold">Workflows</h1>
              <p className="text-xs text-[--color-muted] mt-0.5 hidden sm:block">
                Multi-agent pipelines with sandbox execution
              </p>
            </div>
          </div>
          {/* Full button on sm+, icon-only on mobile */}
          <Button size="sm" onClick={() => setShowNew(true)} className="shrink-0">
            <Plus className="h-3.5 w-3.5 sm:mr-1" />
            <span className="hidden sm:inline">New Workflow</span>
          </Button>
        </div>

        {/* New workflow form */}
        {showNew && (
          <div className="mb-6 rounded-xl border border-[--color-border] bg-[--color-surface-raised] p-5 space-y-4">
            <p className="text-sm font-semibold">New Workflow</p>
            <div className="space-y-1">
              <label className="text-xs text-[--color-muted]">Name</label>
              <input
                autoFocus
                value={newName}
                onChange={e => setNewName(e.target.value)}
                onKeyDown={e => e.key === "Enter" && handleCreate()}
                placeholder="My agent pipeline"
                className="w-full rounded-md border border-[--color-border] bg-transparent px-3 py-1.5 text-sm focus:outline-none focus:ring-1 focus:ring-black dark:focus:ring-white"
              />
            </div>
            <PatternSelector
              pattern={newPattern}
              teamExecMode={newMode}
              onChange={(p, m) => { setNewPattern(p); setNewMode(m); }}
            />
            <div className="flex justify-end gap-2 pt-1">
              <Button size="sm" variant="ghost" onClick={() => setShowNew(false)}>Cancel</Button>
              <Button size="sm" onClick={handleCreate} disabled={creating || !newName.trim()}>
                {creating ? "Creating…" : "Create & Open"}
              </Button>
            </div>
          </div>
        )}

        {/* Search + view toggle toolbar */}
        {workflows.length > 0 && (
          <div className="mb-4 flex items-center gap-2">
            <div className="relative flex-1">
              <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-[--color-muted] pointer-events-none" />
              <input
                value={search}
                onChange={e => setSearch(e.target.value)}
                placeholder="Search workflows…"
                className="w-full rounded-lg border border-[--color-border] bg-transparent pl-8 pr-3 py-1.5 text-sm focus:outline-none focus:ring-1 focus:ring-black dark:focus:ring-white"
              />
            </div>
            {/* List / Grid toggle */}
            <div className="flex items-center rounded-lg border border-[--color-border] overflow-hidden shrink-0">
              <button
                onClick={() => setViewMode("list")}
                title="List view"
                className={cn(
                  "flex items-center justify-center px-2.5 py-1.5 transition-colors",
                  viewMode === "list"
                    ? "bg-[--color-border]/60 text-[--color-text]"
                    : "text-[--color-muted] hover:bg-[--color-border]/30",
                )}
              >
                <LayoutList className="h-4 w-4" />
              </button>
              <div className="w-px h-5 bg-[--color-border]" />
              <button
                onClick={() => setViewMode("grid")}
                title="Grid view"
                className={cn(
                  "flex items-center justify-center px-2.5 py-1.5 transition-colors",
                  viewMode === "grid"
                    ? "bg-[--color-border]/60 text-[--color-text]"
                    : "text-[--color-muted] hover:bg-[--color-border]/30",
                )}
              >
                <LayoutGrid className="h-4 w-4" />
              </button>
            </div>
          </div>
        )}

        {/* Empty states */}
        {workflows.length === 0 && !showNew && (
          <div className="rounded-xl border border-dashed border-[--color-border] py-16 text-center text-[--color-muted]">
            <p className="text-sm">No workflows yet</p>
            <p className="text-xs mt-1">Create one to build a multi-agent pipeline</p>
          </div>
        )}

        {workflows.length > 0 && filtered.length === 0 && (
          <div className="rounded-xl border border-dashed border-[--color-border] py-12 text-center text-[--color-muted]">
            <p className="text-sm">No workflows match &ldquo;{search}&rdquo;</p>
          </div>
        )}

        {/* List view */}
        {viewMode === "list" && filtered.length > 0 && (
          <div className="space-y-3">
            {filtered.map(wf => (
              <WorkflowListCard
                key={wf.id}
                workflow={wf}
                onOpen={() => router.push(`/workflow/${wf.id}`)}
                onDelete={() => handleDelete(wf.id)}
              />
            ))}
          </div>
        )}

        {/* Grid view */}
        {viewMode === "grid" && filtered.length > 0 && (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
            {filtered.map(wf => (
              <WorkflowGridCard
                key={wf.id}
                workflow={wf}
                onOpen={() => router.push(`/workflow/${wf.id}`)}
                onDelete={() => handleDelete(wf.id)}
              />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

/* ── shared helpers ── */

function PatternIcon({ pattern, size = 4 }: { pattern: AgentPattern; size?: number }) {
  return pattern === "ORCHESTRATOR"
    ? <Network className={`h-${size} w-${size}`} />
    : <Users   className={`h-${size} w-${size}`} />;
}

function patternLabel(wf: Workflow) {
  return wf.agentPattern === "ORCHESTRATOR"
    ? "Orchestrator"
    : `Team · ${wf.teamExecMode === "SEQUENTIAL" ? "Sequential" : "Parallel"}`;
}

/* ── List card (original style) ── */

function WorkflowListCard({ workflow, onOpen, onDelete }: {
  workflow: Workflow;
  onOpen: () => void;
  onDelete: () => void;
}) {
  return (
    <div
      className={cn(
        "group flex items-center gap-3 rounded-xl border border-[--color-border] px-4 py-3",
        "hover:bg-[--color-border]/20 transition-colors cursor-pointer",
      )}
      onClick={onOpen}
    >
      <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg border border-[--color-border]">
        <PatternIcon pattern={workflow.agentPattern} />
      </div>
      <div className="min-w-0 flex-1">
        <p className="text-sm font-medium truncate">{workflow.name}</p>
        <p className="text-xs text-[--color-muted]">{patternLabel(workflow)}</p>
      </div>
      <div className="flex items-center gap-1 sm:opacity-0 sm:group-hover:opacity-100 transition-opacity">
        <Button
          size="icon"
          variant="ghost"
          className="h-7 w-7"
          onClick={e => { e.stopPropagation(); onDelete(); }}
        >
          <Trash2 className="h-3.5 w-3.5 text-red-400" />
        </Button>
        <ArrowRight className="h-4 w-4 text-[--color-muted]" />
      </div>
    </div>
  );
}

/* ── Grid card ── */

function WorkflowGridCard({ workflow, onOpen, onDelete }: {
  workflow: Workflow;
  onOpen: () => void;
  onDelete: () => void;
}) {
  return (
    <div
      className={cn(
        "group relative flex flex-col gap-3 rounded-xl border border-[--color-border] p-4",
        "hover:bg-[--color-border]/20 transition-colors cursor-pointer",
      )}
      onClick={onOpen}
    >
      {/* Delete button — top-right on hover */}
      <button
        onClick={e => { e.stopPropagation(); onDelete(); }}
        className="absolute right-3 top-3 rounded-md p-1 opacity-0 group-hover:opacity-100 transition-opacity hover:bg-[--color-border]/50"
        title="Delete workflow"
      >
        <Trash2 className="h-3.5 w-3.5 text-red-400" />
      </button>

      {/* Icon */}
      <div className="flex h-10 w-10 items-center justify-center rounded-xl border border-[--color-border] bg-[--color-surface-raised]">
        <PatternIcon pattern={workflow.agentPattern} size={5} />
      </div>

      {/* Name + meta */}
      <div className="flex-1 min-w-0">
        <p className="text-sm font-semibold leading-snug line-clamp-2">{workflow.name}</p>
        <p className="text-xs text-[--color-muted] mt-1">{patternLabel(workflow)}</p>
      </div>

      {/* Footer */}
      <div className="flex items-center justify-end text-[--color-muted] group-hover:text-[--color-text] transition-colors">
        <span className="text-xs mr-1">Open</span>
        <ArrowRight className="h-3.5 w-3.5" />
      </div>
    </div>
  );
}
