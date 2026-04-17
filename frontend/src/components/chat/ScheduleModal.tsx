"use client";

import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { X, Plus, Trash2, Power, PowerOff, Clock } from "lucide-react";
import {
  fetchSchedules,
  createSchedule,
  updateSchedule,
  deleteSchedule,
} from "@/lib/api";
import { Button } from "@/components/ui/Button";
import { Spinner } from "@/components/ui/Spinner";
import { cn } from "@/lib/utils";
import type { CreateScheduleRequest } from "@/types/agent";

// ── Cron field helpers ────────────────────────────────────────────────────────

const PRESETS = [
  { label: "Every minute",   cron: { m: "*",    h: "*",  d: "*", mo: "*", w: "*" } },
  { label: "Every hour",     cron: { m: "0",    h: "*",  d: "*", mo: "*", w: "*" } },
  { label: "Daily 8 AM",     cron: { m: "0",    h: "8",  d: "*", mo: "*", w: "*" } },
  { label: "Daily 9 PM",     cron: { m: "0",    h: "21", d: "*", mo: "*", w: "*" } },
  { label: "Mon–Fri 9 AM",   cron: { m: "0",    h: "9",  d: "*", mo: "*", w: "1-5" } },
  { label: "Weekly Sunday",  cron: { m: "0",    h: "8",  d: "*", mo: "*", w: "0" } },
  { label: "1st of month",   cron: { m: "0",    h: "8",  d: "1", mo: "*", w: "*" } },
  { label: "Every 10 min",   cron: { m: "*/10", h: "*",  d: "*", mo: "*", w: "*" } },
];

interface CronFields {
  m: string; h: string; d: string; mo: string; w: string;
}

function parseCronExpr(expr: string): CronFields {
  const parts = expr.split(" ");
  return {
    m:  parts[0] ?? "*",
    h:  parts[1] ?? "*",
    d:  parts[2] ?? "*",
    mo: parts[3] ?? "*",
    w:  parts[4] ?? "*",
  };
}

function CronInput({
  label, value, onChange, placeholder,
}: {
  label: string; value: string; onChange: (v: string) => void; placeholder: string;
}) {
  return (
    <div className="flex flex-col gap-1">
      <label className="text-[10px] font-medium uppercase tracking-wider text-[--color-muted]">
        {label}
      </label>
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

// ── Main component ────────────────────────────────────────────────────────────

interface ScheduleModalProps {
  conversationId: string; // backend conversation UUID
  onClose: () => void;
}

export function ScheduleModal({ conversationId, onClose }: ScheduleModalProps) {
  const qc = useQueryClient();

  const { data: schedules = [], isLoading } = useQuery({
    queryKey: ["schedules", conversationId],
    queryFn: () => fetchSchedules(conversationId),
  });

  // ── Create form state ──────────────────────────────────────────────────────
  const [showForm, setShowForm] = useState(false);
  const [message, setMessage] = useState("");
  const [cron, setCron] = useState<CronFields>({ m: "0", h: "8", d: "*", mo: "*", w: "*" });
  const [topK, setTopK] = useState(5);
  const [useKb, setUseKb] = useState(true);
  const [useWf, setUseWf] = useState(true);

  const createMutation = useMutation({
    mutationFn: (req: CreateScheduleRequest) => createSchedule(req),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["schedules", conversationId] });
      setShowForm(false);
      setMessage("");
      setCron({ m: "0", h: "8", d: "*", mo: "*", w: "*" });
    },
  });

  const toggleMutation = useMutation({
    mutationFn: ({ id, enabled }: { id: number; enabled: boolean }) =>
      updateSchedule(id, { enabled }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["schedules", conversationId] }),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: number) => deleteSchedule(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["schedules", conversationId] }),
  });

  const handleCreate = () => {
    if (!message.trim()) return;
    createMutation.mutate({
      conversationId,
      message: message.trim(),
      cronMinute:  cron.m,
      cronHour:    cron.h,
      cronDay:     cron.d,
      cronMonth:   cron.mo,
      cronWeekday: cron.w,
      topK,
      useKnowledgeBase: useKb,
      useWebFetch:      useWf,
    });
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div className="relative flex max-h-[90vh] w-full max-w-lg flex-col overflow-hidden rounded-xl border border-[--color-border] bg-[--color-surface] shadow-2xl">

        {/* Header */}
        <div className="flex items-center justify-between border-b border-[--color-border] px-4 py-3">
          <div className="flex items-center gap-2">
            <Clock className="h-4 w-4 text-[--color-muted]" />
            <h2 className="text-sm font-semibold">Scheduled Messages</h2>
          </div>
          <button
            onClick={onClose}
            className="rounded p-1 text-[--color-muted] hover:bg-[--color-border]/50"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        {/* Body */}
        <div className="flex-1 overflow-y-auto px-4 py-3 space-y-4">

          {/* Existing schedules */}
          {isLoading ? (
            <div className="flex justify-center py-8">
              <Spinner className="h-5 w-5" />
            </div>
          ) : schedules.length === 0 ? (
            <p className="py-4 text-center text-sm text-[--color-muted]">
              No scheduled messages yet.
            </p>
          ) : (
            <ul className="space-y-2">
              {schedules.map((sc) => (
                <li
                  key={sc.id}
                  className={cn(
                    "flex items-start gap-3 rounded-lg border p-3 text-sm",
                    sc.enabled
                      ? "border-[--color-border] bg-[--color-surface-raised]"
                      : "border-[--color-border] bg-[--color-surface] opacity-60",
                  )}
                >
                  <div className="flex-1 min-w-0">
                    <p className="truncate font-medium">{sc.message}</p>
                    <p className="mt-0.5 font-mono text-xs text-[--color-muted]">
                      {sc.cronExpr}
                    </p>
                  </div>
                  <div className="flex shrink-0 items-center gap-1">
                    <button
                      title={sc.enabled ? "Disable" : "Enable"}
                      onClick={() => toggleMutation.mutate({ id: sc.id, enabled: !sc.enabled })}
                      className="rounded p-1 text-[--color-muted] hover:bg-[--color-border]/50"
                    >
                      {sc.enabled ? (
                        <Power className="h-4 w-4 text-emerald-500" />
                      ) : (
                        <PowerOff className="h-4 w-4" />
                      )}
                    </button>
                    <button
                      title="Delete"
                      onClick={() => deleteMutation.mutate(sc.id)}
                      className="rounded p-1 text-[--color-muted] hover:bg-[--color-border]/50"
                    >
                      <Trash2 className="h-4 w-4 hover:text-red-500" />
                    </button>
                  </div>
                </li>
              ))}
            </ul>
          )}

          {/* New schedule form */}
          {showForm && (
            <div className="rounded-lg border border-[--color-border] bg-[--color-surface-raised] p-3 space-y-3">
              <p className="text-xs font-medium text-[--color-muted]">New Schedule</p>

              {/* Message */}
              <div>
                <label className="text-[10px] font-medium uppercase tracking-wider text-[--color-muted]">
                  Message
                </label>
                <textarea
                  rows={2}
                  value={message}
                  onChange={(e) => setMessage(e.target.value)}
                  placeholder="What question should be sent on schedule?"
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
                        cron.m === p.cron.m &&
                          cron.h === p.cron.h &&
                          cron.d === p.cron.d &&
                          cron.mo === p.cron.mo &&
                          cron.w === p.cron.w
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

              {/* Preview */}
              <p className="rounded bg-[--color-surface] px-2 py-1 font-mono text-xs text-[--color-muted]">
                {cron.m} {cron.h} {cron.d} {cron.mo} {cron.w}
              </p>

              {/* Options row */}
              <div className="flex flex-wrap items-center gap-3 text-xs text-[--color-muted]">
                <label className="flex items-center gap-1.5">
                  Top-K
                  <input
                    type="number"
                    min={1} max={20}
                    value={topK}
                    onChange={(e) => setTopK(Number(e.target.value))}
                    className="w-14 rounded border border-[--color-border] bg-[--color-surface] px-1.5 py-0.5 text-center focus:outline-none focus:ring-1 focus:ring-gray-900 dark:focus:ring-gray-100"
                  />
                </label>
                <label className="flex items-center gap-1.5 cursor-pointer">
                  <input type="checkbox" checked={useKb} onChange={(e) => setUseKb(e.target.checked)} />
                  Knowledge Base
                </label>
                <label className="flex items-center gap-1.5 cursor-pointer">
                  <input type="checkbox" checked={useWf} onChange={(e) => setUseWf(e.target.checked)} />
                  Web Fetch
                </label>
              </div>

              {/* Create button row */}
              {createMutation.isError && (
                <p className="text-xs text-red-500">
                  {(createMutation.error as Error).message}
                </p>
              )}
              <div className="flex justify-end gap-2">
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => setShowForm(false)}
                >
                  Cancel
                </Button>
                <Button
                  size="sm"
                  onClick={handleCreate}
                  disabled={!message.trim() || createMutation.isPending}
                >
                  {createMutation.isPending ? <Spinner className="h-3 w-3" /> : "Save Schedule"}
                </Button>
              </div>
            </div>
          )}
        </div>

        {/* Footer */}
        {!showForm && (
          <div className="border-t border-[--color-border] px-4 py-3">
            <Button
              size="sm"
              className="w-full"
              onClick={() => setShowForm(true)}
            >
              <Plus className="mr-1.5 h-3.5 w-3.5" />
              Add Schedule
            </Button>
          </div>
        )}
      </div>
    </div>
  );
}
