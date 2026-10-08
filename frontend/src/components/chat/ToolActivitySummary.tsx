"use client";

import { useState } from "react";
import { ChevronDown, ChevronRight } from "lucide-react";
import { cn } from "@/lib/utils";
import type { ToolActivity } from "@/types/agent";

interface ToolActivitySummaryProps {
  activity: ToolActivity[];
}

/** "Searched the web ×3, read web page ×2 · 1 blocked" — labels grouped in first-call order. */
export function summarizeToolActivity(activity: ToolActivity[]): string {
  const counts = new Map<string, number>();
  for (const a of activity) counts.set(a.label, (counts.get(a.label) ?? 0) + 1);

  const parts = [...counts].map(([label, n], i) => {
    const text = i === 0 ? label : label.charAt(0).toLowerCase() + label.slice(1);
    return n > 1 ? `${text} ×${n}` : text;
  });

  const blocked = activity.filter((a) => a.status === "BLOCKED").length;
  const failed  = activity.filter((a) => a.status === "FAILED").length;
  const issues = [blocked && `${blocked} blocked`, failed && `${failed} failed`].filter(Boolean);
  return parts.join(", ") + (issues.length ? ` · ${issues.join(", ")}` : "");
}

const statusText: Record<ToolActivity["status"], string | null> = {
  OK: null,
  BLOCKED: "blocked",
  FAILED: "failed",
};

/** Collapsed-by-default list of the tool calls behind an assistant answer. */
export function ToolActivitySummary({ activity }: ToolActivitySummaryProps) {
  const [expanded, setExpanded] = useState(false);
  if (activity.length === 0) return null;

  return (
    <div className="w-full text-xs">
      <button
        type="button"
        onClick={() => setExpanded((v) => !v)}
        aria-expanded={expanded}
        className="flex max-w-full items-center gap-1 text-left text-[--color-muted] hover:text-inherit transition-colors"
      >
        <span className="truncate">{summarizeToolActivity(activity)}</span>
        {expanded ? (
          <ChevronDown className="h-3.5 w-3.5 shrink-0" />
        ) : (
          <ChevronRight className="h-3.5 w-3.5 shrink-0" />
        )}
      </button>

      {expanded && (
        <ul className="mt-1.5 overflow-hidden rounded-lg border border-[--color-border] bg-[--color-surface-raised] divide-y divide-[--color-border]">
          {activity.map((a, i) => (
            <li key={i} className="flex items-baseline gap-2 px-3 py-2">
              <span className="shrink-0 text-[--color-muted]">{a.label}</span>
              {a.detail && <span className="min-w-0 break-words">{a.detail}</span>}
              {statusText[a.status] && (
                <span
                  className={cn(
                    "ml-auto shrink-0",
                    a.status === "FAILED" ? "text-red-600 dark:text-red-400" : "text-[--color-muted]",
                  )}
                >
                  {statusText[a.status]}
                </span>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
