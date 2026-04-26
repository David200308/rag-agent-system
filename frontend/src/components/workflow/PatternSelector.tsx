"use client";

import { Users, Network } from "lucide-react";
import { cn } from "@/lib/utils";
import type { AgentPattern, TeamExecMode } from "@/types/agent";

interface Props {
  pattern: AgentPattern;
  teamExecMode: TeamExecMode | null;
  onChange: (pattern: AgentPattern, mode: TeamExecMode | null) => void;
  disabled?: boolean;
}

export function PatternSelector({ pattern, teamExecMode, onChange, disabled }: Props) {
  return (
    <div className="flex flex-col gap-2">
      <p className="text-xs font-medium text-[--color-muted]">Agent Pattern</p>
      <div className="flex gap-2">
        <PatternCard
          active={pattern === "ORCHESTRATOR"}
          icon={<Network className="h-4 w-4" />}
          label="Orchestrator"
          desc="Main agent delegates to sub-agents"
          onClick={() => onChange("ORCHESTRATOR", null)}
          disabled={disabled}
        />
        <PatternCard
          active={pattern === "TEAM"}
          icon={<Users className="h-4 w-4" />}
          label="Agent Team"
          desc="Peer agents work together"
          onClick={() => onChange("TEAM", teamExecMode ?? "PARALLEL")}
          disabled={disabled}
        />
      </div>

      {pattern === "TEAM" && (
        <div className="flex gap-2 mt-1">
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
  );
}

function PatternCard({ active, icon, label, desc, onClick, disabled }: {
  active: boolean; icon: React.ReactNode; label: string;
  desc: string; onClick: () => void; disabled?: boolean;
}) {
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      className={cn(
        "flex flex-1 flex-col gap-1 rounded-lg border p-3 text-left transition-colors",
        active
          ? "border-black bg-black text-white dark:border-white dark:bg-white dark:text-black"
          : "border-[--color-border] hover:bg-[--color-border]/30",
        disabled && "opacity-50 cursor-not-allowed",
      )}
    >
      <span className="flex items-center gap-1.5 text-xs font-semibold">
        {icon} {label}
      </span>
      <span className={cn("text-[10px]", active ? "opacity-70" : "text-[--color-muted]")}>
        {desc}
      </span>
    </button>
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
