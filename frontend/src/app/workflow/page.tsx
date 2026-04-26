"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Plus, Network, Users, Trash2, ArrowRight } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { PatternSelector } from "@/components/workflow/PatternSelector";
import { createWorkflow, deleteWorkflow, fetchWorkflows } from "@/lib/api";
import type { AgentPattern, TeamExecMode, Workflow } from "@/types/agent";
import { cn } from "@/lib/utils";

export default function WorkflowListPage() {
  const router = useRouter();
  const [workflows,   setWorkflows]   = useState<Workflow[]>([]);
  const [creating,    setCreating]    = useState(false);
  const [showNew,     setShowNew]     = useState(false);
  const [newName,     setNewName]     = useState("");
  const [newPattern,  setNewPattern]  = useState<AgentPattern>("ORCHESTRATOR");
  const [newMode,     setNewMode]     = useState<TeamExecMode | null>(null);

  useEffect(() => { fetchWorkflows().then(setWorkflows); }, []);

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
    <div className="mx-auto max-w-2xl px-4 py-8">
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-lg font-bold">Workflows</h1>
          <p className="text-xs text-[--color-muted] mt-0.5">Multi-agent pipelines with sandbox execution</p>
        </div>
        <Button size="sm" onClick={() => setShowNew(true)}>
          <Plus className="h-3.5 w-3.5 mr-1" /> New Workflow
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

      {/* List */}
      {workflows.length === 0 && !showNew ? (
        <div className="rounded-xl border border-dashed border-[--color-border] py-16 text-center text-[--color-muted]">
          <p className="text-sm">No workflows yet</p>
          <p className="text-xs mt-1">Create one to build a multi-agent pipeline</p>
        </div>
      ) : (
        <div className="space-y-3">
          {workflows.map(wf => (
            <WorkflowCard
              key={wf.id}
              workflow={wf}
              onOpen={() => router.push(`/workflow/${wf.id}`)}
              onDelete={() => handleDelete(wf.id)}
            />
          ))}
        </div>
      )}
    </div>
  );
}

function WorkflowCard({ workflow, onOpen, onDelete }: {
  workflow: Workflow;
  onOpen: () => void;
  onDelete: () => void;
}) {
  return (
    <div className={cn(
      "group flex items-center gap-3 rounded-xl border border-[--color-border] px-4 py-3",
      "hover:bg-[--color-border]/20 transition-colors cursor-pointer",
    )} onClick={onOpen}>
      <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg border border-[--color-border]">
        {workflow.agentPattern === "ORCHESTRATOR"
          ? <Network className="h-4 w-4" />
          : <Users   className="h-4 w-4" />
        }
      </div>
      <div className="min-w-0 flex-1">
        <p className="text-sm font-medium truncate">{workflow.name}</p>
        <p className="text-xs text-[--color-muted]">
          {workflow.agentPattern === "ORCHESTRATOR"
            ? "Orchestrator"
            : `Team · ${workflow.teamExecMode === "SEQUENTIAL" ? "Sequential" : "Parallel"}`}
        </p>
      </div>
      <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
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
