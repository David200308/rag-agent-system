"use client";

import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { Box, ChevronDown, Check, Zap } from "lucide-react";
import { cn } from "@/lib/utils";
import type { PersistentSandbox } from "@/types/agent";

interface Props {
  attachedSandboxId: string | null;
  sandboxes: PersistentSandbox[];
  onChange: (id: string | null) => void;
  disabled?: boolean;
}

/**
 * Lets a workflow reuse one of the user's permanent sandboxes (see the Sandboxes page)
 * instead of getting a fresh ephemeral one on every run. Modeled on PatternSelector.
 */
export function SandboxAttachSelector({ attachedSandboxId, sandboxes, onChange, disabled }: Props) {
  const [open, setOpen] = useState(false);
  const [menuPos, setMenuPos] = useState<{ top: number; left: number; width: number } | null>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const menuRef   = useRef<HTMLDivElement>(null);

  const attached = sandboxes.find(s => s.id === attachedSandboxId) ?? null;

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

  function select(id: string | null) {
    setOpen(false);
    onChange(id);
  }

  return (
    <div className="flex flex-col gap-1.5">
      <p className="text-xs font-medium text-[--color-muted]">Sandbox</p>
      <button
        ref={buttonRef}
        onClick={() => setOpen(v => !v)}
        disabled={disabled}
        className={cn(
          "flex items-center gap-2 rounded-lg border border-[--color-border] px-3 py-1.5 text-left transition-colors hover:bg-[--color-border]/30",
          disabled && "opacity-50 cursor-not-allowed",
        )}
      >
        <Box className="h-4 w-4" />
        <span className="text-xs font-semibold">{attached ? attached.name : "Ephemeral (default)"}</span>
        <ChevronDown className={cn("h-3.5 w-3.5 text-[--color-muted] transition-transform", open && "rotate-180")} />
      </button>

      {open && menuPos && typeof document !== "undefined" && createPortal(
        <div
          ref={menuRef}
          style={{ top: menuPos.top, left: menuPos.left, width: menuPos.width }}
          className="fixed z-50 overflow-hidden rounded-lg border border-[--color-border] bg-white dark:bg-zinc-900 shadow-lg py-1"
        >
          <button
            onClick={() => select(null)}
            className={cn(
              "flex w-full items-start gap-2.5 px-3 py-2 text-left transition-colors hover:bg-[--color-border]/30",
              attachedSandboxId === null && "bg-[--color-border]/20",
            )}
          >
            <Zap className="mt-0.5 h-3.5 w-3.5" />
            <span className="flex-1 min-w-0">
              <span className="block text-xs font-semibold">Ephemeral (default)</span>
              <span className="block text-[10px] text-[--color-muted]">Fresh sandbox per run, destroyed when it finishes</span>
            </span>
            {attachedSandboxId === null && <Check className="h-3.5 w-3.5 shrink-0 text-black dark:text-white" />}
          </button>

          {sandboxes.length === 0 ? (
            <p className="px-3 py-2 text-[10px] text-[--color-muted]">
              No persistent sandboxes yet — launch one from the Sandboxes page.
            </p>
          ) : sandboxes.map(s => {
            const active = s.id === attachedSandboxId;
            return (
              <button
                key={s.id}
                onClick={() => select(s.id)}
                className={cn(
                  "flex w-full items-start gap-2.5 px-3 py-2 text-left transition-colors hover:bg-[--color-border]/30",
                  active && "bg-[--color-border]/20",
                )}
              >
                <Box className="mt-0.5 h-3.5 w-3.5" />
                <span className="flex-1 min-w-0">
                  <span className="block text-xs font-semibold truncate">{s.name}</span>
                  <span className="block text-[10px] text-[--color-muted]">
                    {s.status === "RUNNING" ? "Running" : "Stopped"} · reused across runs
                  </span>
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
