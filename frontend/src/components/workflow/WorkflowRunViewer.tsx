"use client";

import { useEffect, useRef, useState } from "react";
import { ChevronDown, ChevronUp, CircleDot, CheckCircle2, XCircle } from "lucide-react";
import { cn } from "@/lib/utils";
import type { LogType, RunStatus, WorkflowRunLog } from "@/types/agent";

interface Props {
  runId: string | null;
  onDone?: (output: string, status: RunStatus) => void;
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

export function WorkflowRunViewer({ runId, onDone }: Props) {
  const [logs,      setLogs]      = useState<WorkflowRunLog[]>([]);
  const [status,    setStatus]    = useState<RunStatus | null>(null);
  const [output,    setOutput]    = useState<string | null>(null);
  const [expanded,  setExpanded]  = useState(true);
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!runId) return;
    setLogs([]);
    setStatus("RUNNING");
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
      onDone?.(data.output, data.status);
      es.close();
    });

    es.onerror = () => {
      setStatus(prev => prev === "RUNNING" ? "FAILED" : prev);
      es.close();
    };

    return () => es.close();
  }, [runId, onDone]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [logs]);

  if (!runId) return null;

  return (
    <div className="flex flex-col border-t border-[--color-border]">
      {/* Toolbar */}
      <div className="flex items-center gap-2 px-4 py-2">
        <StatusIcon status={status} />
        <span className="text-xs font-medium">
          {status === "RUNNING" ? "Running…" : status === "DONE" ? "Done" : status === "FAILED" ? "Failed" : "Pending"}
        </span>
        <span className="text-[10px] text-[--color-muted]">{logs.length} events</span>
        <button
          onClick={() => setExpanded(v => !v)}
          className="ml-auto text-[--color-muted] hover:text-[--color-fg]"
        >
          {expanded ? <ChevronDown className="h-4 w-4" /> : <ChevronUp className="h-4 w-4" />}
        </button>
      </div>

      {expanded && (
        <div className="max-h-64 overflow-y-auto px-4 pb-4 space-y-1.5">
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
