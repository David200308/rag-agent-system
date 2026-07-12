"use client";

import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { Users, Network, GitBranch, ChevronDown, Check } from "lucide-react";
import { cn } from "@/lib/utils";
import type { AgentPattern, TeamExecMode } from "@/types/agent";

interface Props {
  pattern: AgentPattern;
  teamExecMode: TeamExecMode | null;
  onChange: (pattern: AgentPattern, mode: TeamExecMode | null) => void;
  disabled?: boolean;
}

const PATTERNS: { value: AgentPattern; icon: React.ReactNode; label: string; desc: string }[] = [
  { value: "ORCHESTRATOR", icon: <Network className="h-4 w-4" />, label: "Orchestrator", desc: "Main agent delegates to sub-agents" },
  { value: "TEAM", icon: <Users className="h-4 w-4" />, label: "Agent Team", desc: "Peer agents work together" },
  { value: "GRAPH", icon: <GitBranch className="h-4 w-4" />, label: "Graph", desc: "Wire agents, conditions & end nodes" },
];

export function PatternSelector({ pattern, teamExecMode, onChange, disabled }: Props) {
  const [open, setOpen] = useState(false);
  const [menuPos, setMenuPos] = useState<{ top: number; left: number; width: number } | null>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const menuRef   = useRef<HTMLDivElement>(null);

  const current = PATTERNS.find(p => p.value === pattern) ?? PATTERNS[0]!;

  // Position the portaled menu (toolbar containers upstream use overflow-x-auto,
  // which per the CSS spec implicitly clips overflow-y too — a portal avoids that),
  // and close it on outside click / scroll / resize.
  useEffect(() => {
    if (!open) return;
    const rect = buttonRef.current?.getBoundingClientRect();
    if (rect) setMenuPos({ top: rect.bottom + 4, left: rect.left, width: Math.max(rect.width, 220) });

    const onClick = (e: MouseEvent) => {
      const target = e.target as globalThis.Node;
      if (buttonRef.current?.contains(target)) return;
      if (menuRef.current?.contains(target)) return;
      setOpen(false);
    };
    const onDismiss = () => setOpen(false);
    document.addEventListener("mousedown", onClick);
    window.addEventListener("scroll", onDismiss, true);
    window.addEventListener("resize", onDismiss);
    return () => {
      document.removeEventListener("mousedown", onClick);
      window.removeEventListener("scroll", onDismiss, true);
      window.removeEventListener("resize", onDismiss);
    };
  }, [open]);

  function select(next: AgentPattern) {
    setOpen(false);
    if (next === "TEAM") onChange("TEAM", teamExecMode ?? "PARALLEL");
    else onChange(next, null);
  }

  return (
    <div className="flex flex-col gap-1.5">
      <p className="text-xs font-medium text-[--color-muted]">Agent Pattern</p>
      <div className="flex items-center gap-2">
        <button
          ref={buttonRef}
          onClick={() => setOpen(v => !v)}
          disabled={disabled}
          className={cn(
            "flex items-center gap-2 rounded-lg border border-[--color-border] px-3 py-1.5 text-left transition-colors hover:bg-[--color-border]/30",
            disabled && "opacity-50 cursor-not-allowed",
          )}
        >
          {current.icon}
          <span className="text-xs font-semibold">{current.label}</span>
          <ChevronDown className={cn("h-3.5 w-3.5 text-[--color-muted] transition-transform", open && "rotate-180")} />
        </button>

        {pattern === "TEAM" && (
          <div className="flex gap-2">
            <ModeChip
              active={teamExecMode === "PARALLEL"}
              label="Parallel"
              onClick={() => onChange("TEAM", "PARALLEL")}
              disabled={disabled}
            />
            <ModeChip
              active={teamExecMode === "SEQUENTIAL"}
              label="Sequential"
              onClick={() => onChange("TEAM", "SEQUENTIAL")}
              disabled={disabled}
            />
          </div>
        )}
      </div>

      {open && menuPos && typeof document !== "undefined" && createPortal(
        <div
          ref={menuRef}
          style={{ top: menuPos.top, left: menuPos.left, width: menuPos.width }}
          className="fixed z-50 overflow-hidden rounded-lg border border-[--color-border] bg-white dark:bg-zinc-900 shadow-lg py-1"
        >
          {PATTERNS.map(p => {
            const active = p.value === pattern;
            return (
              <button
                key={p.value}
                onClick={() => select(p.value)}
                className={cn(
                  "flex w-full items-start gap-2.5 px-3 py-2 text-left transition-colors hover:bg-[--color-border]/30",
                  active && "bg-[--color-border]/20",
                )}
              >
                <span className="mt-0.5">{p.icon}</span>
                <span className="flex-1 min-w-0">
                  <span className="flex items-center gap-1.5 text-xs font-semibold">{p.label}</span>
                  <span className="block text-[10px] text-[--color-muted]">{p.desc}</span>
                </span>
                {active && <Check className="h-3.5 w-3.5 shrink-0 text-black dark:text-white" />}
              </button>
            );
          })}
        </div>,
        document.body,
      )}
    </div>
  );
}

function ModeChip({ active, label, onClick, disabled }: {
  active: boolean; label: string; onClick: () => void; disabled?: boolean;
}) {
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      className={cn(
        "rounded-full border px-3 py-0.5 text-xs font-medium transition-colors",
        active
          ? "border-black bg-black text-white dark:border-white dark:bg-white dark:text-black"
          : "border-[--color-border] text-[--color-muted] hover:border-black dark:hover:border-white",
        disabled && "opacity-50 cursor-not-allowed",
      )}
    >
      {label}
    </button>
  );
}
