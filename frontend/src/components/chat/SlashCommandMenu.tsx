"use client";

import { useEffect, useRef } from "react";
import { GitFork, Database, Zap } from "lucide-react";
import { cn } from "@/lib/utils";
import type { Skill } from "@/types/agent";

const BUILTINS = [
  {
    id: "workflow",
    label: "workflow",
    description: "Create and open a new workflow",
    icon: <GitFork className="h-3.5 w-3.5" />,
  },
  {
    id: "knowledge",
    label: "knowledge",
    description: "Toggle knowledge base on/off",
    icon: <Database className="h-3.5 w-3.5" />,
  },
] as const;

export type BuiltinId = (typeof BUILTINS)[number]["id"];

export type SlashItem =
  | { type: "builtin"; id: BuiltinId; label: string; description: string }
  | { type: "skill"; skill: Skill };

interface SlashCommandMenuProps {
  filter: string;
  skills: Skill[];
  activeIndex: number;
  onSelect: (item: SlashItem) => void;
  onActiveChange: (index: number) => void;
}

export function SlashCommandMenu({
  filter,
  skills,
  activeIndex,
  onSelect,
  onActiveChange,
}: SlashCommandMenuProps) {
  const lower = filter.toLowerCase();
  const listRef = useRef<HTMLDivElement>(null);

  const matchedBuiltins = BUILTINS.filter((b) =>
    b.label.toLowerCase().includes(lower),
  );
  const matchedSkills = skills.filter((s) =>
    s.name.toLowerCase().includes(lower),
  );

  const totalItems = matchedBuiltins.length + matchedSkills.length;

  useEffect(() => {
    const el = listRef.current?.children[activeIndex] as HTMLElement | undefined;
    el?.scrollIntoView({ block: "nearest" });
  }, [activeIndex]);

  if (totalItems === 0) return null;

  return (
    <div
      ref={listRef}
      className="absolute bottom-full left-0 right-0 z-50 mb-1 max-h-64 overflow-y-auto rounded-xl border border-[--color-border] bg-white shadow-xl dark:bg-zinc-900"
    >
      {matchedBuiltins.length > 0 && (
        <>
          <p className="sticky top-0 bg-white dark:bg-zinc-900 px-3 py-1.5 text-[10px] font-semibold uppercase tracking-wide text-[--color-muted]">
            Commands
          </p>
          {matchedBuiltins.map((item, i) => (
            <button
              key={item.id}
              onClick={() =>
                onSelect({ type: "builtin", id: item.id, label: item.label, description: item.description })
              }
              onMouseEnter={() => onActiveChange(i)}
              className={cn(
                "flex w-full items-center gap-3 px-3 py-2 text-left text-sm transition-colors",
                activeIndex === i
                  ? "bg-[--color-surface-raised]"
                  : "hover:bg-[--color-surface-raised]",
              )}
            >
              <span className="shrink-0 text-[--color-muted]">{item.icon}</span>
              <span className="font-mono font-medium text-[--color-fg]">
                /{item.label}
              </span>
              <span className="text-xs text-[--color-muted]">
                {item.description}
              </span>
            </button>
          ))}
        </>
      )}

      {matchedSkills.length > 0 && (
        <>
          <p
            className={cn(
              "sticky top-0 bg-white dark:bg-zinc-900 px-3 py-1.5 text-[10px] font-semibold uppercase tracking-wide text-[--color-muted]",
              matchedBuiltins.length > 0 && "border-t border-[--color-border]",
            )}
          >
            Skills
          </p>
          {matchedSkills.map((skill, i) => {
            const globalIdx = matchedBuiltins.length + i;
            return (
              <button
                key={skill.id}
                onClick={() => onSelect({ type: "skill", skill })}
                onMouseEnter={() => onActiveChange(globalIdx)}
                className={cn(
                  "flex w-full items-center gap-3 px-3 py-2 text-left text-sm transition-colors",
                  activeIndex === globalIdx
                    ? "bg-[--color-surface-raised]"
                    : "hover:bg-[--color-surface-raised]",
                )}
              >
                <Zap className="h-3.5 w-3.5 shrink-0 text-amber-500" />
                <span className="font-mono font-medium text-[--color-fg]">
                  /{skill.name}
                </span>
                <span className="text-xs text-[--color-muted]">Attach skill</span>
              </button>
            );
          })}
        </>
      )}
    </div>
  );
}

export function buildSlashItems(
  filter: string,
  skills: Skill[],
): SlashItem[] {
  const lower = filter.toLowerCase();
  const builtins: SlashItem[] = BUILTINS.filter((b) =>
    b.label.toLowerCase().includes(lower),
  ).map((b) => ({ type: "builtin", id: b.id, label: b.label, description: b.description }));
  const skillItems: SlashItem[] = skills
    .filter((s) => s.name.toLowerCase().includes(lower))
    .map((s) => ({ type: "skill", skill: s }));
  return [...builtins, ...skillItems];
}
