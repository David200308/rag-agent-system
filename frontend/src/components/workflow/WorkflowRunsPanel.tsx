"use client";

import { useEffect, useState, useCallback } from "react";
import { X, RefreshCw, CheckCircle2, XCircle, CircleDot, Clock, ChevronLeft, ChevronRight } from "lucide-react";
import { WorkflowRunViewer } from "./WorkflowRunViewer";
import { fetchWorkflowRuns } from "@/lib/api";
import type { WorkflowRun } from "@/types/agent";
import { cn } from "@/lib/utils";

interface Props {
  workflowId: string;
  liveRunId: string | null;
  onClose: () => void;
  onRunComplete?: (output: string, status: WorkflowRun["status"]) => void;
  width?: number;
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

export function WorkflowRunsPanel({ workflowId, liveRunId, onClose, onRunComplete, width }: Props) {
  const [runs,         setRuns]        = useState<WorkflowRun[]>([]);
  const [selectedId,   setSelectedId]  = useState<string | null>(liveRunId);
  const [loading,      setLoading]     = useState(false);
  const [page,         setPage]        = useState(0);
  const [pageSize,     setPageSize]    = useState(10);
  const [totalElements, setTotal]      = useState(0);
  const [totalPages,   setTotalPages]  = useState(0);

  const load = useCallback(async (p = page, ps = pageSize) => {
    setLoading(true);
    try {
      const data = await fetchWorkflowRuns(workflowId, p, ps);
      setRuns(data.content);
      setTotal(data.totalElements);
      setTotalPages(data.totalPages);
    } catch {
      // ignore fetch errors
    } finally {
      setLoading(false);
    }
  }, [workflowId, page, pageSize]);

  useEffect(() => { load(page, pageSize); }, [workflowId, page, pageSize]); // eslint-disable-line react-hooks/exhaustive-deps

  // Auto-select the live run and refresh the list when it changes
  useEffect(() => {
    if (!liveRunId) return;
    setSelectedId(liveRunId);
    load(0, pageSize);
    setPage(0);
  }, [liveRunId]); // eslint-disable-line react-hooks/exhaustive-deps

  // Reset to first page when page size changes
  function handlePageSizeChange(newSize: number) {
    setPageSize(newSize);
    setPage(0);
  }

  // Refresh list when a run finishes
  function handleRunDone(output: string, status: WorkflowRun["status"]) {
    setTimeout(() => load(page, pageSize), 500);
    onRunComplete?.(output, status);
  }

  return (
    <div
      className="flex h-full flex-col border-[--color-border] bg-[--color-surface]"
      style={{ width: width ?? 320, minWidth: width ?? 320 }}
    >
      {/* Header */}
      <div className="flex shrink-0 items-center gap-2 border-b border-[--color-border] px-4 py-3">
        <span className="text-sm font-semibold flex-1">Runs</span>
        <button
          onClick={() => load(page, pageSize)}
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

      {/* Run list — shrinks to make room for the detail pane */}
      <div className={cn(
        "flex flex-col overflow-y-auto shrink-0",
        selectedId ? "max-h-[45%]" : "flex-1",
      )}>
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

      {/* Pagination controls */}
      {totalElements > 0 && (
        <div className="shrink-0 flex items-center justify-between border-t border-[--color-border] px-3 py-2">
          <div className="flex items-center gap-1 text-[10px] text-[--color-muted]">
            <span>Rows:</span>
            {[5, 10, 20, 50].map(s => (
              <button
                key={s}
                onClick={() => handlePageSizeChange(s)}
                className={cn(
                  "px-1.5 py-0.5 rounded transition-colors",
                  pageSize === s
                    ? "bg-[--color-surface-raised] text-[--color-fg] font-semibold"
                    : "hover:text-[--color-fg]",
                )}
              >
                {s}
              </button>
            ))}
          </div>
          <div className="flex items-center gap-1">
            <span className="text-[10px] text-[--color-muted]">
              {page * pageSize + 1}–{Math.min((page + 1) * pageSize, totalElements)} of {totalElements}
            </span>
            <button
              onClick={() => setPage(p => p - 1)}
              disabled={page === 0}
              className="text-[--color-muted] hover:text-[--color-fg] disabled:opacity-30"
            >
              <ChevronLeft className="h-3.5 w-3.5" />
            </button>
            <button
              onClick={() => setPage(p => p + 1)}
              disabled={page >= totalPages - 1}
              className="text-[--color-muted] hover:text-[--color-fg] disabled:opacity-30"
            >
              <ChevronRight className="h-3.5 w-3.5" />
            </button>
          </div>
        </div>
      )}


      {/* Log detail — fills remaining height */}
      {selectedId && (
        <div className="flex min-h-0 flex-1 flex-col overflow-hidden border-t border-[--color-border]">
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
