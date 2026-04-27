"use client";

import { useEffect, useRef, useState } from "react";
import { ChevronDown, ChevronUp, CircleDot, CheckCircle2, XCircle } from "lucide-react";
import { cn } from "@/lib/utils";
import type { LogType, RunStatus, WorkflowRunLog } from "@/types/agent";

interface Props {
  runId: string | null;
  onDone?: (output: string, status: RunStatus) => void;
  /** Seed the status display so completed runs don't flash "Running…" on open */
  initialStatus?: RunStatus;
  /** Fill available height instead of capping at 16rem */
  fill?: boolean;
}

const LOG_COLORS: Record<LogType, string> = {
  TOOL_CALL:    "text-blue-500",
  TOOL_RESULT:  "text-green-600 dark:text-green-400",
  LLM_RESPONSE: "text-[--color-fg]",
  DELEGATION:   "text-purple-500",
  ERROR:        "text-red-500",
  SYSTEM:       "text-[--color-muted]",
};

const LOG_LABELS: Record<LogType, string> = {
  TOOL_CALL:    "⚙ Tool",
  TOOL_RESULT:  "✓ Result",
  LLM_RESPONSE: "◎ Agent",
  DELEGATION:   "→ Delegate",
  ERROR:        "✗ Error",
  SYSTEM:       "· System",
};

export function WorkflowRunViewer({ runId, onDone, initialStatus, fill }: Props) {
  const [logs,     setLogs]     = useState<WorkflowRunLog[]>([]);
  const [status,   setStatus]   = useState<RunStatus | null>(initialStatus ?? null);
  const [output,   setOutput]   = useState<string | null>(null);
  const [expanded, setExpanded] = useState(true);
  const bottomRef  = useRef<HTMLDivElement>(null);

  // Keep onDone in a ref so it never appears in the SSE effect's dependency array.
  // Without this, every parent re-render produces a new function reference, which
  // tears down and restarts the SSE connection and causes the status to flicker
  // between "Running…" and "Done/Failed" on every render cycle.
  const onDoneRef = useRef(onDone);
  useEffect(() => { onDoneRef.current = onDone; }, [onDone]);

  // Seed status from prop when the selected run changes.
  useEffect(() => {
    if (initialStatus) setStatus(initialStatus);
  }, [runId, initialStatus]);

  useEffect(() => {
    if (!runId) return;
    setLogs([]);
    // Only default to RUNNING if we don't already know the status.
    // Completed runs receive their status via initialStatus, so we avoid the flash.
    setStatus(s => s === "DONE" || s === "FAILED" ? s : "RUNNING");
    setOutput(null);

    const es = new EventSource(`/api/workflow/runs/${runId}/stream`);

    es.addEventListener("log", (e: MessageEvent) => {
      const data = JSON.parse(e.data) as WorkflowRunLog;
      setLogs(prev => [...prev, data]);
    });

    es.addEventListener("done", (e: MessageEvent) => {
      const data = JSON.parse(e.data) as { status: RunStatus; output: string };
      setStatus(data.status);
      setOutput(data.output);
      onDoneRef.current?.(data.output, data.status);
      es.close();
    });

    es.onerror = () => {
      // Only flip to FAILED if still waiting — don't overwrite a known terminal state.
      setStatus(prev => prev === "RUNNING" ? "FAILED" : prev);
      es.close();
    };

    return () => es.close();
  }, [runId]); // onDone intentionally excluded — accessed via ref above

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [logs]);

  if (!runId) return null;

  return (
    <div className={cn("flex flex-col", fill ? "flex-1 overflow-hidden" : "border-t border-[--color-border]")}>
      {/* Toolbar */}
      <div className="flex items-center gap-2 px-4 py-2 shrink-0">
        <StatusIcon status={status} />
        <span className="text-xs font-medium">
          {status === "RUNNING" ? "Running…" : status === "DONE" ? "Done" : status === "FAILED" ? "Failed" : "Pending"}
        </span>
        <span className="text-[10px] text-[--color-muted]">{logs.length} events</span>
        {!fill && (
          <button
            onClick={() => setExpanded(v => !v)}
            className="ml-auto text-[--color-muted] hover:text-[--color-fg]"
          >
            {expanded ? <ChevronDown className="h-4 w-4" /> : <ChevronUp className="h-4 w-4" />}
          </button>
        )}
      </div>

      {(fill || expanded) && (
        <div className={cn("overflow-y-auto px-4 pb-4 space-y-1.5", fill ? "flex-1" : "max-h-64")}>
          {logs.map((log, i) => (
            <LogEntry key={i} log={log} />
          ))}
          {status === "DONE" && output && (
            <div className="mt-3 rounded-lg border border-[--color-border] bg-[--color-surface-raised] p-3">
              <p className="text-[10px] font-semibold text-[--color-muted] mb-1">Final Output</p>
              <p className="text-xs whitespace-pre-wrap">{output}</p>
            </div>
          )}
          <div ref={bottomRef} />
        </div>
      )}
    </div>
  );
}

function LogEntry({ log }: { log: WorkflowRunLog }) {
  const [expanded, setExpanded] = useState(log.logType === "LLM_RESPONSE");
  const isLong = log.content.length > 200;

  return (
    <div className="font-mono text-[11px]">
      <div className="flex items-start gap-2">
        <span className={cn("shrink-0 font-semibold", LOG_COLORS[log.logType])}>
          {LOG_LABELS[log.logType]}
        </span>
        {log.agentName && (
          <span className="shrink-0 rounded bg-[--color-border]/60 px-1 text-[10px] text-[--color-muted]">
            {log.agentName}
          </span>
        )}
        <span className={cn("flex-1 break-all", LOG_COLORS[log.logType])}>
          {isLong && !expanded
            ? log.content.slice(0, 200) + "…"
            : log.content}
        </span>
        {isLong && (
          <button
            onClick={() => setExpanded(v => !v)}
            className="shrink-0 text-[10px] text-[--color-muted] underline"
          >
            {expanded ? "less" : "more"}
          </button>
        )}
      </div>
    </div>
  );
}

function StatusIcon({ status }: { status: RunStatus | null }) {
  if (status === "DONE")    return <CheckCircle2 className="h-3.5 w-3.5 text-green-500" />;
  if (status === "FAILED")  return <XCircle      className="h-3.5 w-3.5 text-red-500" />;
  if (status === "RUNNING") return <CircleDot    className="h-3.5 w-3.5 text-blue-500 animate-pulse" />;
  return <CircleDot className="h-3.5 w-3.5 text-[--color-muted]" />;
}
