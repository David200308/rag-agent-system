"use client";

import { useEffect, useRef, useState } from "react";
import { ChevronDown, Check } from "lucide-react";
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
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  const selected = models.find((m) => m.displayName === value) ?? null;
  const label = selected?.displayName ?? "System default";

  useEffect(() => {
    function handleClick(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) {
        setOpen(false);
      }
    }
    if (open) document.addEventListener("mousedown", handleClick);
    return () => document.removeEventListener("mousedown", handleClick);
  }, [open]);

  const isDisabled = disabled || models.length === 0;

  const options = [
    { label: "System default", value: null },
    ...models.map((m) => ({ label: m.displayName, value: m.displayName })),
  ];

  return (
    <div ref={ref} className={cn("relative shrink-0", className)}>
      <button
        type="button"
        disabled={isDisabled}
        onClick={() => setOpen((o) => !o)}
        className={cn(
          "flex max-w-[160px] items-center gap-1 rounded-md border border-[--color-border]",
          "bg-[--color-surface-raised] px-2 py-1 text-xs",
          "hover:bg-[--color-border] focus:outline-none focus:ring-1 focus:ring-gray-900 dark:focus:ring-gray-100",
          "disabled:cursor-not-allowed disabled:opacity-50",
        )}
      >
        <span className="truncate">{label}</span>
        <ChevronDown className={cn("h-3 w-3 shrink-0 text-[--color-muted] transition-transform", open && "rotate-180")} />
      </button>

      {open && !isDisabled && (
        <div className="absolute right-0 top-full z-50 mt-1 min-w-[160px] max-w-[220px] rounded-md border border-[--color-border] bg-[var(--color-surface-raised)] py-1 shadow-lg">
          {options.map((opt) => {
            const isSelected = opt.value === value;
            return (
              <button
                key={opt.label}
                type="button"
                onClick={() => {
                  onChange(opt.value);
                  setOpen(false);
                }}
                className={cn(
                  "flex w-full items-center gap-2 px-3 py-1.5 text-left text-xs",
                  "hover:bg-[--color-border]",
                  isSelected && "font-medium",
                )}
              >
                <Check className={cn("h-3 w-3 shrink-0", isSelected ? "opacity-100" : "opacity-0")} />
                <span className="truncate">{opt.label}</span>
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}
