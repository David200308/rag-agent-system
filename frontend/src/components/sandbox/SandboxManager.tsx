"use client";

import { useCallback, useEffect, useState } from "react";
import {
  Box, Play, Square, RotateCcw, Eraser, Trash2, RefreshCw, Network, ShieldOff,
} from "lucide-react";
import { cn } from "@/lib/utils";
import {
  fetchSandboxes, fetchSandboxQuota, createSandbox,
  stopSandbox, restartSandbox, clearSandbox, removeSandbox,
} from "@/lib/api";
import { Button } from "@/components/ui/Button";
import type { PersistentSandbox, SandboxQuota } from "@/types/agent";

function StatusBadge({ status }: { status: PersistentSandbox["status"] }) {
  const running = status === "RUNNING";
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full px-2 py-0.5 text-[10px] font-semibold",
        running
          ? "bg-green-100 text-green-700 dark:bg-green-900/40 dark:text-green-400"
          : "bg-zinc-100 text-zinc-600 dark:bg-zinc-800 dark:text-zinc-400",
      )}
    >
      <span className={cn("h-1.5 w-1.5 rounded-full", running ? "bg-green-500" : "bg-zinc-400")} />
      {running ? "Running" : "Stopped"}
    </span>
  );
}

function LaunchForm({ disabled, onLaunched }: { disabled: boolean; onLaunched: () => void }) {
  const [name, setName] = useState("");
  const [network, setNetwork] = useState(false);
  const [launching, setLaunching] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleLaunch() {
    setLaunching(true);
    setError(null);
    try {
      await createSandbox(name.trim(), network);
      setName("");
      setNetwork(false);
      onLaunched();
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setLaunching(false);
    }
  }

  return (
    <div className={cn(
      "rounded-lg border border-dashed border-[--color-border] p-4",
      disabled && "opacity-50 pointer-events-none",
    )}>
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <input
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="Sandbox name (optional)"
          disabled={launching}
          className="flex-1 rounded-md border border-[--color-border] bg-transparent px-3 py-1.5 text-sm focus:outline-none focus:ring-1 focus:ring-black dark:focus:ring-white"
        />
        <label className="flex shrink-0 items-center gap-1.5 text-xs text-[--color-muted]">
          <input
            type="checkbox"
            checked={network}
            onChange={(e) => setNetwork(e.target.checked)}
            disabled={launching}
          />
          <Network className="h-3.5 w-3.5" />
          Enable network
        </label>
        <Button size="sm" onClick={handleLaunch} disabled={launching || disabled} className="shrink-0">
          {launching ? <RefreshCw className="h-3.5 w-3.5 animate-spin" /> : <Play className="h-3.5 w-3.5" />}
          Launch sandbox
        </Button>
      </div>
      {error && <p className="mt-2 text-xs text-red-500">{error}</p>}
    </div>
  );
}

function SandboxCard({ sandbox, onChanged }: { sandbox: PersistentSandbox; onChanged: () => void }) {
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const running = sandbox.status === "RUNNING";

  async function run(action: string, fn: () => Promise<unknown>) {
    setBusy(action);
    setError(null);
    try {
      await fn();
      onChanged();
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(null);
    }
  }

  return (
    <div className="rounded-md border border-[--color-border] px-3 py-2.5">
      <div className="flex items-center gap-3">
        <Box className="h-4 w-4 shrink-0 text-[--color-muted]" />
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2">
            <p className="truncate text-sm font-medium">{sandbox.name}</p>
            <StatusBadge status={sandbox.status} />
            {sandbox.networkEnabled ? (
              <span className="inline-flex items-center gap-1 text-[10px] text-[--color-muted]" title="Network enabled">
                <Network className="h-2.5 w-2.5" />
              </span>
            ) : (
              <span className="inline-flex items-center gap-1 text-[10px] text-[--color-muted]" title="Network disabled">
                <ShieldOff className="h-2.5 w-2.5" />
              </span>
            )}
          </div>
          <p className="text-[10px] text-[--color-muted]">
            {sandbox.containerId ? sandbox.containerId.slice(0, 12) : "no container"} ·{" "}
            created {new Date(sandbox.createdAt).toLocaleString()}
          </p>
        </div>

        {running ? (
          <Button
            size="icon" variant="ghost" className="h-7 w-7 shrink-0"
            onClick={() => run("stop", () => stopSandbox(sandbox.id))}
            disabled={busy !== null}
            title="Stop"
          >
            {busy === "stop" ? <RefreshCw className="h-3.5 w-3.5 animate-spin" /> : <Square className="h-3.5 w-3.5" />}
          </Button>
        ) : (
          <Button
            size="icon" variant="ghost" className="h-7 w-7 shrink-0"
            onClick={() => run("start", () => restartSandbox(sandbox.id))}
            disabled={busy !== null}
            title="Start"
          >
            {busy === "start" ? <RefreshCw className="h-3.5 w-3.5 animate-spin" /> : <Play className="h-3.5 w-3.5" />}
          </Button>
        )}
        <Button
          size="icon" variant="ghost" className="h-7 w-7 shrink-0"
          onClick={() => run("restart", () => restartSandbox(sandbox.id))}
          disabled={busy !== null}
          title="Restart"
        >
          {busy === "restart" ? <RefreshCw className="h-3.5 w-3.5 animate-spin" /> : <RotateCcw className="h-3.5 w-3.5" />}
        </Button>
        <Button
          size="icon" variant="ghost" className="h-7 w-7 shrink-0"
          onClick={() => run("clear", () => clearSandbox(sandbox.id))}
          disabled={busy !== null || !running}
          title={running ? "Clear workspace" : "Sandbox must be running to clear"}
        >
          {busy === "clear" ? <RefreshCw className="h-3.5 w-3.5 animate-spin" /> : <Eraser className="h-3.5 w-3.5" />}
        </Button>
        <Button
          size="icon" variant="ghost" className="h-7 w-7 shrink-0 hover:text-red-500"
          onClick={() => {
            if (confirm(`Remove sandbox "${sandbox.name}"? This destroys its container permanently.`)) {
              run("remove", () => removeSandbox(sandbox.id));
            }
          }}
          disabled={busy !== null}
          title="Remove"
        >
          {busy === "remove" ? <RefreshCw className="h-3.5 w-3.5 animate-spin" /> : <Trash2 className="h-3.5 w-3.5" />}
        </Button>
      </div>
      {error && <p className="mt-2 text-xs text-red-500">{error}</p>}
    </div>
  );
}

export function SandboxManager() {
  const [sandboxes, setSandboxes] = useState<PersistentSandbox[]>([]);
  const [quota, setQuota] = useState<SandboxQuota>({ used: 0, max: 1 });
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [s, q] = await Promise.all([fetchSandboxes(), fetchSandboxQuota()]);
      setSandboxes(s);
      setQuota(q);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  const atQuota = quota.used >= quota.max;

  return (
    <div className="mx-auto max-w-2xl px-6 py-8">
      <div className="mb-6">
        <div className="mb-1 flex items-center gap-2">
          <Box className="h-5 w-5 text-amber-500" />
          <h1 className="text-xl font-semibold">Sandboxes</h1>
        </div>
        <p className="text-sm text-[--color-muted]">
          Launch a permanent sandbox you fully control — only you can start, stop, restart, clear
          or remove it. Attach one to a workflow (in the workflow builder) so its runs reuse this
          container instead of creating a new one each time, and finishing a run won&apos;t close it.
        </p>
      </div>

      <div className="mb-4 flex items-center justify-between">
        <p className="text-xs font-medium text-[--color-muted]">
          {quota.used} / {quota.max} sandbox{quota.max === 1 ? "" : "es"} used
        </p>
        {!loading && (
          <button
            onClick={load}
            className="flex items-center gap-1 text-xs text-[--color-muted] hover:text-[--color-fg]"
          >
            <RefreshCw className="h-3 w-3" /> Refresh
          </button>
        )}
      </div>

      <div className="mb-6">
        <LaunchForm disabled={atQuota} onLaunched={load} />
        {atQuota && (
          <p className="mt-2 text-xs text-[--color-muted]">
            Quota reached — remove an existing sandbox to launch a new one.
          </p>
        )}
      </div>

      {loading ? (
        <div className="flex items-center justify-center py-12 text-[--color-muted]">
          <RefreshCw className="h-4 w-4 animate-spin" />
        </div>
      ) : sandboxes.length === 0 ? (
        <p className="py-12 text-center text-sm text-[--color-muted]">
          No sandboxes yet. Launch one above.
        </p>
      ) : (
        <div className="space-y-2">
          {sandboxes.map((s) => (
            <SandboxCard key={s.id} sandbox={s} onChanged={load} />
          ))}
        </div>
      )}
    </div>
  );
}
