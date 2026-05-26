"use client";

import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  X, Plus, Trash2, Power, PowerOff, Clock,
  ChevronDown, ChevronRight, CheckCircle2, XCircle, Loader2,
} from "lucide-react";
import {
  fetchWorkflowSchedules,
  createWorkflowSchedule,
  updateWorkflowSchedule,
  deleteWorkflowSchedule,
  fetchScheduleRuns,
} from "@/lib/api";
import { Button } from "@/components/ui/Button";
import { Spinner } from "@/components/ui/Spinner";
import { cn } from "@/lib/utils";
import type { CreateWorkflowScheduleRequest, ScheduleRun, UpdateScheduleRequest, WorkflowSchedule } from "@/types/agent";

// ── Cron helpers ──────────────────────────────────────────────────────────────

const PRESETS = [
  { label: "Every minute",  cron: { m: "*",    h: "*",  d: "*", mo: "*", w: "*" } },
  { label: "Every hour",    cron: { m: "0",    h: "*",  d: "*", mo: "*", w: "*" } },
  { label: "Daily 8 AM",    cron: { m: "0",    h: "8",  d: "*", mo: "*", w: "*" } },
  { label: "Daily 9 PM",    cron: { m: "0",    h: "21", d: "*", mo: "*", w: "*" } },
  { label: "Mon–Fri 9 AM",  cron: { m: "0",    h: "9",  d: "*", mo: "*", w: "1-5" } },
  { label: "Weekly Sunday", cron: { m: "0",    h: "8",  d: "*", mo: "*", w: "0" } },
  { label: "1st of month",  cron: { m: "0",    h: "8",  d: "1", mo: "*", w: "*" } },
  { label: "Every 10 min",  cron: { m: "*/10", h: "*",  d: "*", mo: "*", w: "*" } },
];

const TIMEZONES = [
  "UTC", "America/New_York", "America/Chicago", "America/Denver", "America/Los_Angeles",
  "Europe/London", "Europe/Paris", "Europe/Berlin",
  "Asia/Tokyo", "Asia/Shanghai", "Asia/Singapore", "Australia/Sydney",
];

interface CronFields { m: string; h: string; d: string; mo: string; w: string; }

function CronInput({ label, value, onChange, placeholder }: {
  label: string; value: string; onChange: (v: string) => void; placeholder: string;
}) {
  return (
    <div className="flex flex-col gap-1">
      <label className="text-[10px] font-medium uppercase tracking-wider text-[--color-muted]">{label}</label>
      <input
        type="text"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        className="w-full rounded border border-[--color-border] bg-[--color-surface] px-2 py-1 text-sm focus:outline-none focus:ring-1 focus:ring-gray-900 dark:focus:ring-gray-100"
      />
    </div>
  );
}

function RunStatusIcon({ status }: { status: string }) {
  if (status === "COMPLETED") return <CheckCircle2 className="h-3.5 w-3.5 text-emerald-500" />;
  if (status === "FAILED" || status === "TIMED_OUT") return <XCircle className="h-3.5 w-3.5 text-red-500" />;
  return <Loader2 className="h-3.5 w-3.5 animate-spin text-blue-500" />;
}

function RunHistory({ scheduleId }: { scheduleId: string }) {
  const { data: runs = [], isLoading } = useQuery<ScheduleRun[]>({
    queryKey: ["scheduleRuns", scheduleId],
    queryFn: () => fetchScheduleRuns(scheduleId),
    staleTime: 30_000,
  });

  if (isLoading) return <div className="flex justify-center py-2"><Spinner className="h-4 w-4" /></div>;
  if (runs.length === 0) return <p className="text-xs text-[--color-muted] py-1">No runs yet.</p>;

  return (
    <ul className="space-y-1">
      {runs.map((run) => (
        <li key={run.workflowId} className="flex items-center gap-2 text-xs">
          <RunStatusIcon status={run.status} />
          <span className="font-mono text-[--color-muted]">
            {run.startTime ? new Date(run.startTime).toLocaleString() : "—"}
          </span>
          <span className={cn(
            "ml-auto rounded px-1.5 py-0.5 text-[10px] font-medium",
            run.status === "COMPLETED" ? "bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-400" :
            run.status === "FAILED" || run.status === "TIMED_OUT" ? "bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400" :
            "bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400",
          )}>
            {run.status}
          </span>
        </li>
      ))}
    </ul>
  );
}

// ── Main component ────────────────────────────────────────────────────────────

interface Props {
  workflowId: string;
  onClose: () => void;
}

export function WorkflowScheduleModal({ workflowId, onClose }: Props) {
  const qc = useQueryClient();

  const { data: schedules = [], isLoading } = useQuery<WorkflowSchedule[]>({
    queryKey: ["workflowSchedules", workflowId],
    queryFn: () => fetchWorkflowSchedules(workflowId),
  });

  const [showForm,   setShowForm]   = useState(false);
  const [input,      setInput]      = useState("");
  const [cron,       setCron]       = useState<CronFields>({ m: "0", h: "8", d: "*", mo: "*", w: "*" });
  const [timezone,   setTimezone]   = useState("UTC");
  const [expandedRuns, setExpandedRuns] = useState<Set<string>>(new Set());

  const createMutation = useMutation({
    mutationFn: (req: CreateWorkflowScheduleRequest) => createWorkflowSchedule(req),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["workflowSchedules", workflowId] });
      setShowForm(false);
      setInput("");
      setCron({ m: "0", h: "8", d: "*", mo: "*", w: "*" });
      setTimezone("UTC");
    },
  });

  const toggleMutation = useMutation({
    mutationFn: ({ id, enabled }: { id: string; enabled: boolean }) =>
      updateWorkflowSchedule(id, { enabled } as UpdateScheduleRequest),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["workflowSchedules", workflowId] }),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteWorkflowSchedule(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["workflowSchedules", workflowId] }),
  });

  const handleCreate = () => {
    if (!input.trim()) return;
    createMutation.mutate({
      workflowId,
      workflowInput: input.trim(),
      cronMinute:  cron.m,
      cronHour:    cron.h,
      cronDay:     cron.d,
      cronMonth:   cron.mo,
      cronWeekday: cron.w,
      timezone,
    });
  };

  const toggleRuns = (id: string) => {
    setExpandedRuns(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4"
      onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}
    >
      <div className="relative flex max-h-[90vh] w-full max-w-lg flex-col overflow-hidden rounded-xl border border-[--color-border] bg-white shadow-2xl dark:bg-neutral-900">

        {/* Header */}
        <div className="flex items-center justify-between border-b border-[--color-border] px-4 py-3">
          <div className="flex items-center gap-2">
            <Clock className="h-4 w-4 text-[--color-muted]" />
            <h2 className="text-sm font-semibold">Workflow Schedules</h2>
          </div>
          <button onClick={onClose} className="rounded p-1 text-[--color-muted] hover:bg-[--color-border]/50">
            <X className="h-4 w-4" />
          </button>
        </div>

        {/* Body */}
        <div className="flex-1 overflow-y-auto px-4 py-3 space-y-4">

          {isLoading ? (
            <div className="flex justify-center py-8"><Spinner className="h-5 w-5" /></div>
          ) : schedules.length === 0 ? (
            <p className="py-4 text-center text-sm text-[--color-muted]">No scheduled runs yet.</p>
          ) : (
            <ul className="space-y-2">
              {schedules.map((sc) => (
                <li
                  key={sc.id}
                  className={cn(
                    "rounded-lg border p-3 text-sm",
                    sc.enabled
                      ? "border-[--color-border] bg-[--color-surface-raised]"
                      : "border-[--color-border] bg-[--color-surface] opacity-60",
                  )}
                >
                  <div className="flex items-start gap-3">
                    <div className="flex-1 min-w-0">
                      <p className="truncate font-medium">{sc.workflowInput}</p>
                      <div className="mt-0.5 flex flex-wrap items-center gap-x-3 gap-y-0.5">
                        <span className="font-mono text-xs text-[--color-muted]">{sc.cronExpr}</span>
                        {sc.timezone && sc.timezone !== "UTC" && (
                          <span className="text-xs text-[--color-muted]">{sc.timezone}</span>
                        )}
                      </div>
                      {sc.nextRunAt && (
                        <p className="mt-0.5 text-[11px] text-[--color-muted]">
                          Next: {new Date(sc.nextRunAt).toLocaleString()}
                        </p>
                      )}
                      {sc.lastRunAt && (
                        <p className="text-[11px] text-[--color-muted]">
                          Last: {new Date(sc.lastRunAt).toLocaleString()}
                        </p>
                      )}
                    </div>
                    <div className="flex shrink-0 items-center gap-1">
                      <button
                        title={sc.enabled ? "Disable" : "Enable"}
                        onClick={() => toggleMutation.mutate({ id: sc.id, enabled: !sc.enabled })}
                        className="rounded p-1 text-[--color-muted] hover:bg-[--color-border]/50"
                      >
                        {sc.enabled
                          ? <Power className="h-4 w-4 text-emerald-500" />
                          : <PowerOff className="h-4 w-4" />}
                      </button>
                      <button
                        title="Delete"
                        onClick={() => deleteMutation.mutate(sc.id)}
                        className="rounded p-1 text-[--color-muted] hover:bg-[--color-border]/50"
                      >
                        <Trash2 className="h-4 w-4 hover:text-red-500" />
                      </button>
                    </div>
                  </div>

                  <button
                    onClick={() => toggleRuns(sc.id)}
                    className="mt-2 flex items-center gap-1 text-[11px] text-[--color-muted] hover:text-[--color-foreground]"
                  >
                    {expandedRuns.has(sc.id)
                      ? <ChevronDown className="h-3 w-3" />
                      : <ChevronRight className="h-3 w-3" />}
                    Run history
                  </button>
                  {expandedRuns.has(sc.id) && (
                    <div className="mt-2 border-t border-[--color-border] pt-2">
                      <RunHistory scheduleId={sc.id} />
                    </div>
                  )}
                </li>
              ))}
            </ul>
          )}

          {/* New schedule form */}
          {showForm && (
            <div className="rounded-lg border border-[--color-border] bg-[--color-surface-raised] p-3 space-y-3">
              <p className="text-xs font-medium text-[--color-muted]">New Schedule</p>

              <div>
                <label className="text-[10px] font-medium uppercase tracking-wider text-[--color-muted]">
                  Workflow Input
                </label>
                <textarea
                  rows={2}
                  value={input}
                  onChange={(e) => setInput(e.target.value)}
                  placeholder="Input text to pass to the workflow on each run…"
                  className="mt-1 w-full resize-none rounded border border-[--color-border] bg-[--color-surface] px-2 py-1.5 text-sm focus:outline-none focus:ring-1 focus:ring-gray-900 dark:focus:ring-gray-100"
                />
              </div>

              {/* Presets */}
              <div>
                <p className="mb-1.5 text-[10px] font-medium uppercase tracking-wider text-[--color-muted]">
                  Quick Presets
                </p>
                <div className="flex flex-wrap gap-1.5">
                  {PRESETS.map((p) => (
                    <button
                      key={p.label}
                      type="button"
                      onClick={() => setCron(p.cron)}
                      className={cn(
                        "rounded-full border px-2 py-0.5 text-xs transition-colors",
                        cron.m === p.cron.m && cron.h === p.cron.h && cron.d === p.cron.d &&
                          cron.mo === p.cron.mo && cron.w === p.cron.w
                          ? "border-gray-900 bg-gray-900 text-white dark:border-gray-100 dark:bg-gray-100 dark:text-black"
                          : "border-[--color-border] text-[--color-muted] hover:border-gray-400",
                      )}
                    >
                      {p.label}
                    </button>
                  ))}
                </div>
              </div>

              {/* Cron fields */}
              <div className="grid grid-cols-5 gap-2">
                <CronInput label="Minute"  value={cron.m}  onChange={(v) => setCron((c) => ({ ...c, m: v }))}  placeholder="0-59" />
                <CronInput label="Hour"    value={cron.h}  onChange={(v) => setCron((c) => ({ ...c, h: v }))}  placeholder="0-23" />
                <CronInput label="Day"     value={cron.d}  onChange={(v) => setCron((c) => ({ ...c, d: v }))}  placeholder="1-31" />
                <CronInput label="Month"   value={cron.mo} onChange={(v) => setCron((c) => ({ ...c, mo: v }))} placeholder="1-12" />
                <CronInput label="Weekday" value={cron.w}  onChange={(v) => setCron((c) => ({ ...c, w: v }))}  placeholder="0-6" />
              </div>

              {/* Cron preview + timezone */}
              <div className="flex items-center gap-3">
                <p className="flex-1 rounded bg-[--color-surface] px-2 py-1 font-mono text-xs text-[--color-muted]">
                  {cron.m} {cron.h} {cron.d} {cron.mo} {cron.w}
                </p>
                <div className="flex flex-col gap-0.5">
                  <label className="text-[10px] font-medium uppercase tracking-wider text-[--color-muted]">Timezone</label>
                  <select
                    value={timezone}
                    onChange={(e) => setTimezone(e.target.value)}
                    className="rounded border border-[--color-border] bg-[--color-surface] px-2 py-1 text-xs focus:outline-none focus:ring-1 focus:ring-gray-900 dark:focus:ring-gray-100"
                  >
                    {TIMEZONES.map((tz) => (
                      <option key={tz} value={tz}>{tz}</option>
                    ))}
                  </select>
                </div>
              </div>

              {createMutation.isError && (
                <p className="text-xs text-red-500">
                  {(createMutation.error as Error).message}
                </p>
              )}
              <div className="flex justify-end gap-2">
                <Button variant="ghost" size="sm" onClick={() => setShowForm(false)}>Cancel</Button>
                <Button size="sm" onClick={handleCreate} disabled={!input.trim() || createMutation.isPending}>
                  {createMutation.isPending ? <Spinner className="h-3 w-3" /> : "Save Schedule"}
                </Button>
              </div>
            </div>
          )}
        </div>

        {/* Footer */}
        {!showForm && (
          <div className="border-t border-[--color-border] px-4 py-3">
            <Button size="sm" className="w-full" onClick={() => setShowForm(true)}>
              <Plus className="mr-1.5 h-3.5 w-3.5" />
              Add Schedule
            </Button>
          </div>
        )}
      </div>
    </div>
  );
}
