"use client";

import { useEffect, useState, useCallback } from "react";
import { X, RefreshCw, CheckCircle2, XCircle, CircleDot, Clock } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { WorkflowRunViewer } from "./WorkflowRunViewer";
import { fetchWorkflowRuns } from "@/lib/api";
import type { WorkflowRun } from "@/types/agent";
import { cn } from "@/lib/utils";

interface Props {
  workflowId: string;
  liveRunId: string | null;
  onClose: () => void;
  onRunComplete?: (output: string, status: WorkflowRun["status"]) => void;
}

function statusIcon(status: WorkflowRun["status"]) {
  if (status === "DONE")    return <CheckCircle2 className="h-3.5 w-3.5 text-green-500 shrink-0" />;
  if (status === "FAILED")  return <XCircle      className="h-3.5 w-3.5 text-red-500 shrink-0" />;
  if (status === "RUNNING") return <CircleDot    className="h-3.5 w-3.5 text-blue-500 animate-pulse shrink-0" />;
  return                           <Clock        className="h-3.5 w-3.5 text-[--color-muted] shrink-0" />;
}

function duration(run: WorkflowRun) {
  if (!run.finishedAt) return null;
  const ms = new Date(run.finishedAt).getTime() - new Date(run.startedAt).getTime();
  const s = Math.round(ms / 1000);
  return s < 60 ? `${s}s` : `${Math.round(s / 60)}m ${s % 60}s`;
}

function timeAgo(iso: string) {
  const diff = Date.now() - new Date(iso).getTime();
  const mins = Math.floor(diff / 60000);
  if (mins < 1)  return "just now";
  if (mins < 60) return `${mins}m ago`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24)  return `${hrs}h ago`;
  return `${Math.floor(hrs / 24)}d ago`;
}

export function WorkflowRunsPanel({ workflowId, liveRunId, onClose, onRunComplete }: Props) {
  const [runs,        setRuns]        = useState<WorkflowRun[]>([]);
  const [selectedId,  setSelectedId]  = useState<string | null>(liveRunId);
  const [loading,     setLoading]     = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = await fetchWorkflowRuns(workflowId);
      setRuns(data);
    } catch {
      // ignore fetch errors
    } finally {
      setLoading(false);
    }
  }, [workflowId]);

  useEffect(() => { load(); }, [load]);

  // Auto-select the live run and refresh the list when it changes
  useEffect(() => {
    if (!liveRunId) return;
    setSelectedId(liveRunId);
    load();
  }, [liveRunId, load]);

  // Refresh list when a run finishes
  function handleRunDone(output: string, status: WorkflowRun["status"]) {
    setTimeout(load, 500);
    onRunComplete?.(output, status);
  }

  return (
    <div className="flex w-80 flex-col border-l border-[--color-border] bg-[--color-surface]">
      {/* Header */}
      <div className="flex items-center gap-2 border-b border-[--color-border] px-4 py-3">
        <span className="text-sm font-semibold flex-1">Runs</span>
        <button
          onClick={load}
          disabled={loading}
          className="text-[--color-muted] hover:text-[--color-fg] disabled:opacity-40"
          title="Refresh"
        >
          <RefreshCw className={cn("h-3.5 w-3.5", loading && "animate-spin")} />
        </button>
        <button
          onClick={onClose}
          className="text-[--color-muted] hover:text-[--color-fg]"
        >
          <X className="h-4 w-4" />
        </button>
      </div>

      {/* Run list */}
      <div className="flex flex-col overflow-y-auto" style={{ maxHeight: selectedId ? "40%" : "100%" }}>
        {runs.length === 0 && !loading && (
          <p className="px-4 py-6 text-center text-xs text-[--color-muted]">No runs yet</p>
        )}
        {runs.map(run => (
          <button
            key={run.id}
            onClick={() => setSelectedId(run.id === selectedId ? null : run.id)}
            className={cn(
              "flex flex-col gap-1 px-4 py-3 text-left border-b border-[--color-border] hover:bg-[--color-surface-raised] transition-colors",
              selectedId === run.id && "bg-[--color-surface-raised]",
            )}
          >
            <div className="flex items-center gap-2">
              {statusIcon(run.status)}
              <span className="text-[11px] font-semibold capitalize text-[--color-fg]">
                {run.status.toLowerCase()}
              </span>
              {duration(run) && (
                <span className="ml-auto text-[10px] text-[--color-muted]">{duration(run)}</span>
              )}
            </div>
            <p className="text-[11px] text-[--color-muted] line-clamp-2 pl-5">
              {run.userInput}
            </p>
            <p className="text-[10px] text-[--color-muted]/70 pl-5">
              {timeAgo(run.startedAt)}
            </p>
          </button>
        ))}
      </div>

      {/* Log detail */}
      {selectedId && (
        <div className="flex flex-1 flex-col overflow-hidden border-t border-[--color-border]">
          <WorkflowRunViewer
          runId={selectedId}
          initialStatus={runs.find(r => r.id === selectedId)?.status}
          onDone={handleRunDone}
          fill
        />
        </div>
      )}
    </div>
  );
}
