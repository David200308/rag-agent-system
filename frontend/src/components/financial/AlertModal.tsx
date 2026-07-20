"use client";

import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { X, Plus, Trash2, Power, PowerOff, Bell } from "lucide-react";
import { fetchAlerts, createPriceAlert, updateAlert, deleteAlert } from "@/lib/api";
import { Button } from "@/components/ui/Button";
import { Spinner } from "@/components/ui/Spinner";
import { cn } from "@/lib/utils";
import type { AlertFrequency, PriceAlert } from "@/types/alerts";

const DIRECTIONS: { value: PriceAlert["direction"]; label: string }[] = [
  { value: ">=", label: "≥ (at or above)" },
  { value: ">",  label: "> (above)" },
  { value: "=",  label: "= (equals)" },
  { value: "<=", label: "≤ (at or below)" },
  { value: "<",  label: "< (below)" },
];

const FREQUENCY_UNITS: { value: AlertFrequency["unit"]; label: string }[] = [
  { value: "HOUR",  label: "Every N hours" },
  { value: "DAY",   label: "Every N days" },
  { value: "ONCE",  label: "Once, then disable" },
  { value: "NEVER", label: "Never re-fire" },
];

function formatFrequency(freq?: AlertFrequency | null): string {
  if (!freq) return "Default (at most once/hour)";
  switch (freq.unit) {
    case "ONCE": return "Once, then disabled";
    case "NEVER": return "Never re-fires";
    case "HOUR": return `Every ${freq.number ?? 1}h`;
    case "DAY": return `Every ${freq.number ?? 1}d`;
    default: return "";
  }
}

interface AlertModalProps {
  symbol: string;
  assetType: "CRYPTO" | "STOCK";
  onClose: () => void;
}

export function AlertModal({ symbol, assetType, onClose }: AlertModalProps) {
  const qc = useQueryClient();

  const { data, isLoading } = useQuery({
    queryKey: ["alerts"],
    queryFn: fetchAlerts,
  });
  const alerts = (data?.price ?? []).filter((a) => a.symbol === symbol);

  const [showForm, setShowForm] = useState(false);
  const [threshold, setThreshold] = useState("");
  const [direction, setDirection] = useState<PriceAlert["direction"]>(">=");
  const [freqUnit, setFreqUnit] = useState<AlertFrequency["unit"]>("HOUR");
  const [freqNumber, setFreqNumber] = useState("1");

  const needsFreqNumber = freqUnit === "HOUR" || freqUnit === "DAY";

  const createMutation = useMutation({
    mutationFn: () => createPriceAlert({
      symbol,
      assetType,
      direction,
      threshold: Number(threshold),
      frequency: needsFreqNumber
        ? { unit: freqUnit, number: Number(freqNumber) }
        : { unit: freqUnit },
    }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["alerts"] });
      setShowForm(false);
      setThreshold("");
      setDirection(">=");
      setFreqUnit("HOUR");
      setFreqNumber("1");
    },
  });

  const toggleMutation = useMutation({
    mutationFn: ({ id, enabled }: { id: string; enabled: boolean }) =>
      updateAlert("price", id, { enabled }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["alerts"] }),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteAlert("price", id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["alerts"] }),
  });

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4"
      onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}
    >
      <div className="relative flex max-h-[90vh] w-full max-w-md flex-col overflow-hidden rounded-xl border border-[--color-border] bg-white shadow-2xl dark:bg-neutral-900">

        {/* Header */}
        <div className="flex items-center justify-between border-b border-[--color-border] px-4 py-3">
          <div className="flex items-center gap-2">
            <Bell className="h-4 w-4 text-[--color-muted]" />
            <h2 className="text-sm font-semibold">Price Alerts — {symbol}</h2>
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
          {isLoading ? (
            <div className="flex justify-center py-8">
              <Spinner className="h-5 w-5" />
            </div>
          ) : alerts.length === 0 ? (
            <p className="py-4 text-center text-sm text-[--color-muted]">
              No alerts set for {symbol} yet.
            </p>
          ) : (
            <ul className="space-y-2">
              {alerts.map((a) => (
                <li
                  key={a.id}
                  className={cn(
                    "rounded-lg border p-3 text-sm",
                    a.enabled
                      ? "border-[--color-border] bg-[--color-surface-raised]"
                      : "border-[--color-border] bg-[--color-surface] opacity-60",
                  )}
                >
                  <div className="flex items-start gap-3">
                    <div className="flex-1 min-w-0">
                      <p className="font-medium">
                        {symbol} {a.direction} {a.threshold}
                      </p>
                      <p className="text-xs text-[--color-muted]">{formatFrequency(a.frequency)}</p>
                    </div>
                    <div className="flex shrink-0 items-center gap-1">
                      <button
                        title={a.enabled ? "Disable" : "Enable"}
                        onClick={() => toggleMutation.mutate({ id: a.id, enabled: !a.enabled })}
                        className="rounded p-1 text-[--color-muted] hover:bg-[--color-border]/50"
                      >
                        {a.enabled ? (
                          <Power className="h-4 w-4 text-emerald-500" />
                        ) : (
                          <PowerOff className="h-4 w-4" />
                        )}
                      </button>
                      <button
                        title="Delete"
                        onClick={() => deleteMutation.mutate(a.id)}
                        className="rounded p-1 text-[--color-muted] hover:bg-[--color-border]/50"
                      >
                        <Trash2 className="h-4 w-4 hover:text-red-500" />
                      </button>
                    </div>
                  </div>
                </li>
              ))}
            </ul>
          )}

          {showForm && (
            <div className="rounded-lg border border-[--color-border] bg-[--color-surface-raised] p-3 space-y-3">
              <p className="text-xs font-medium text-[--color-muted]">New Alert</p>

              <div className="grid grid-cols-2 gap-2">
                <div>
                  <label className="text-[10px] font-medium uppercase tracking-wider text-[--color-muted]">
                    Direction
                  </label>
                  <select
                    value={direction}
                    onChange={(e) => setDirection(e.target.value as PriceAlert["direction"])}
                    className="mt-1 w-full rounded border border-[--color-border] bg-[--color-surface] px-2 py-1 text-sm focus:outline-none focus:ring-1 focus:ring-gray-900 dark:focus:ring-gray-100"
                  >
                    {DIRECTIONS.map((d) => (
                      <option key={d.value} value={d.value}>{d.label}</option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="text-[10px] font-medium uppercase tracking-wider text-[--color-muted]">
                    Threshold (USD)
                  </label>
                  <input
                    type="number"
                    step="any"
                    value={threshold}
                    onChange={(e) => setThreshold(e.target.value)}
                    placeholder="0.00"
                    className="mt-1 w-full rounded border border-[--color-border] bg-[--color-surface] px-2 py-1 text-sm focus:outline-none focus:ring-1 focus:ring-gray-900 dark:focus:ring-gray-100"
                  />
                </div>
              </div>

              <div className={cn("grid gap-2", needsFreqNumber ? "grid-cols-2" : "grid-cols-1")}>
                <div>
                  <label className="text-[10px] font-medium uppercase tracking-wider text-[--color-muted]">
                    Frequency
                  </label>
                  <select
                    value={freqUnit}
                    onChange={(e) => setFreqUnit(e.target.value as AlertFrequency["unit"])}
                    className="mt-1 w-full rounded border border-[--color-border] bg-[--color-surface] px-2 py-1 text-sm focus:outline-none focus:ring-1 focus:ring-gray-900 dark:focus:ring-gray-100"
                  >
                    {FREQUENCY_UNITS.map((f) => (
                      <option key={f.value} value={f.value}>{f.label}</option>
                    ))}
                  </select>
                </div>
                {needsFreqNumber && (
                  <div>
                    <label className="text-[10px] font-medium uppercase tracking-wider text-[--color-muted]">
                      Every N {freqUnit === "HOUR" ? "hours" : "days"}
                    </label>
                    <input
                      type="number"
                      min={1}
                      step={1}
                      value={freqNumber}
                      onChange={(e) => setFreqNumber(e.target.value)}
                      className="mt-1 w-full rounded border border-[--color-border] bg-[--color-surface] px-2 py-1 text-sm focus:outline-none focus:ring-1 focus:ring-gray-900 dark:focus:ring-gray-100"
                    />
                  </div>
                )}
              </div>

              {createMutation.isError && (
                <p className="text-xs text-red-500">
                  {(createMutation.error as Error).message}
                </p>
              )}

              <div className="flex justify-end gap-2">
                <Button variant="ghost" size="sm" onClick={() => setShowForm(false)}>
                  Cancel
                </Button>
                <Button
                  size="sm"
                  onClick={() => createMutation.mutate()}
                  disabled={
                    !threshold.trim() ||
                    (needsFreqNumber && Number(freqNumber) <= 0) ||
                    createMutation.isPending
                  }
                >
                  {createMutation.isPending ? <Spinner className="h-3 w-3" /> : "Save Alert"}
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
              Add Alert
            </Button>
          </div>
        )}
      </div>
    </div>
  );
}
