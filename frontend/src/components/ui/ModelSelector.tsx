"use client";

import { cn } from "@/lib/utils";
import type { ModelConfig } from "@/types/agent";

interface ModelSelectorProps {
  models: ModelConfig[];
  value: string | null;
  onChange: (value: string | null) => void;
  disabled?: boolean;
  className?: string;
}

export function ModelSelector({ models, value, onChange, disabled, className }: ModelSelectorProps) {
  return (
    <select
      value={value ?? ""}
      onChange={(e) => onChange(e.target.value || null)}
      disabled={disabled || models.length === 0}
      className={cn(
        "rounded-md border border-[--color-border] bg-[--color-surface-raised] px-2 py-1 text-xs",
        "focus:outline-none focus:ring-1 focus:ring-gray-900 dark:focus:ring-gray-100",
        "disabled:opacity-50 cursor-pointer",
        className,
      )}
    >
      <option value="">System default</option>
      {models.map((m) => (
        <option key={m.displayName} value={m.displayName}>{m.displayName}</option>
      ))}
    </select>
  );
}
