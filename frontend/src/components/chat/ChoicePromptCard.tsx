"use client";

import { useState } from "react";
import { Check } from "lucide-react";
import { cn } from "@/lib/utils";
import type { ChoicePrompt } from "@/types/agent";

interface ChoicePromptCardProps {
  prompt: ChoicePrompt;
  /** Whether the user can still answer — only the latest message's prompt, while idle. */
  active: boolean;
  /** The user's reply that followed this prompt, used to highlight what they picked. */
  answer?: string;
  onSelect?: (text: string) => void;
}

/**
 * Appends the offered question and options to an assistant turn's text for conversation
 * history, so the model knows what a reply like "Pro" was answering. Mirrors
 * ChoicePrompt.withHistoryNote on the backend.
 */
export function withChoiceNote(content: string, prompt: ChoicePrompt | undefined): string {
  if (!prompt) return content;
  const note = `[Offered the user options — ${prompt.question} ${prompt.options.join(" | ")}${
    prompt.multiSelect ? " (multiple allowed)" : ""
  }]`;
  return content.trim() ? `${content}\n\n${note}` : note;
}

/** Joins/splits a multi-select answer — the picks are sent as one plain-text message. */
const SEPARATOR = ", ";

function pickedOptions(prompt: ChoicePrompt, answer: string | undefined): Set<string> {
  if (!answer) return new Set();
  const reply = answer.trim();
  if (prompt.options.includes(reply)) return new Set([reply]);
  if (!prompt.multiSelect) return new Set();
  const parts = reply.split(SEPARATOR).map((p) => p.trim());
  return new Set(parts.every((p) => prompt.options.includes(p)) ? parts : []);
}

/** The question the assistant asked with clickable options; picking one sends it as the next message. */
export function ChoicePromptCard({ prompt, active, answer, onSelect }: ChoicePromptCardProps) {
  const [checked, setChecked] = useState<string[]>([]);
  const picked = pickedOptions(prompt, answer);
  const interactive = active && !!onSelect;

  const toggle = (option: string) =>
    setChecked((prev) =>
      prev.includes(option) ? prev.filter((o) => o !== option) : [...prev, option],
    );

  const handleClick = (option: string) => {
    if (!interactive) return;
    if (prompt.multiSelect) toggle(option);
    else onSelect(option);
  };

  const submitMulti = () => {
    if (!interactive || checked.length === 0) return;
    // Keep the assistant's option order regardless of click order.
    onSelect(prompt.options.filter((o) => checked.includes(o)).join(SEPARATOR));
  };

  return (
    <div className="w-full rounded-xl border border-[--color-border] bg-[--color-surface-raised] p-3 text-sm">
      <p className="mb-2 font-medium">{prompt.question}</p>

      <div className="flex flex-wrap gap-2">
        {prompt.options.map((option) => {
          const isPicked = picked.has(option) || (interactive && checked.includes(option));
          return (
            <button
              key={option}
              type="button"
              onClick={() => handleClick(option)}
              disabled={!interactive}
              aria-pressed={prompt.multiSelect ? isPicked : undefined}
              className={cn(
                "flex items-center gap-1.5 rounded-lg border px-3 py-1.5 text-left transition-colors",
                isPicked
                  ? "border-black bg-black text-white dark:border-white dark:bg-white dark:text-black"
                  : "border-[--color-border] bg-[--color-surface]",
                interactive && !isPicked && "hover:border-gray-400 dark:hover:border-gray-500",
                !interactive && !isPicked && "opacity-50",
                !interactive && "cursor-default",
              )}
            >
              {prompt.multiSelect && isPicked && <Check className="h-3.5 w-3.5 shrink-0" />}
              <span>{option}</span>
            </button>
          );
        })}
      </div>

      {interactive && (
        <div className="mt-2 flex items-center justify-between gap-2 text-xs text-[--color-muted]">
          <span>{prompt.multiSelect ? "Pick one or more, " : "Pick one, "}or type your own answer below.</span>
          {prompt.multiSelect && (
            <button
              type="button"
              onClick={submitMulti}
              disabled={checked.length === 0}
              className="shrink-0 rounded-lg bg-black px-3 py-1 font-medium text-white disabled:opacity-40 dark:bg-white dark:text-black"
            >
              Send
            </button>
          )}
        </div>
      )}
    </div>
  );
}
