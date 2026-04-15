"use client";

import { useState, useRef, type KeyboardEvent } from "react";
import { Send, Settings2, Database, Globe } from "lucide-react";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/Button";

interface MessageInputProps {
  onSend: (query: string, topK: number, useKnowledgeBase: boolean, useWebFetch: boolean) => void;
  disabled?: boolean;
}

interface ToggleChipProps {
  icon: React.ReactNode;
  label: string;
  active: boolean;
  onToggle: () => void;
}

function ToggleChip({ icon, label, active, onToggle }: ToggleChipProps) {
  return (
    <button
      type="button"
      onClick={onToggle}
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-medium transition-colors",
        active
          ? "border-gray-900 bg-gray-900 text-white dark:border-gray-100 dark:bg-gray-100 dark:text-black"
          : "border-[--color-border] bg-transparent text-[--color-muted] hover:border-gray-400 hover:text-inherit",
      )}
    >
      {icon}
      {label}
    </button>
  );
}

export function MessageInput({ onSend, disabled = false }: MessageInputProps) {
  const [text, setText] = useState("");
  const [topK, setTopK] = useState(5);
  const [useKnowledgeBase, setUseKnowledgeBase] = useState(true);
  const [useWebFetch, setUseWebFetch] = useState(true);
  const [showOptions, setShowOptions] = useState(false);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  const submit = () => {
    const trimmed = text.trim();
    if (!trimmed || disabled) return;
    onSend(trimmed, topK, useKnowledgeBase, useWebFetch);
    setText("");
    if (textareaRef.current) textareaRef.current.style.height = "auto";
  };

  const onKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      submit();
    }
  };

  const onInput = () => {
    const el = textareaRef.current;
    if (!el) return;
    el.style.height = "auto";
    el.style.height = `${Math.min(el.scrollHeight, 200)}px`;
  };

  return (
    <div className="border-t border-[--color-border] bg-[--color-surface] p-4">
      {showOptions && (
        <div className="mb-3 flex flex-wrap items-center gap-3 rounded-lg border border-[--color-border] bg-[--color-surface-raised] px-3 py-2 text-sm">
          {/* Top-K */}
          <label className="flex items-center gap-2 text-[--color-muted]">
            Top-K sources
            <input
              type="number"
              min={1}
              max={20}
              value={topK}
              onChange={(e) => setTopK(Number(e.target.value))}
              className="w-16 rounded border border-[--color-border] bg-[--color-surface] px-2 py-0.5 text-center text-sm focus:outline-none focus:ring-1 focus:ring-gray-900 dark:focus:ring-gray-100"
            />
          </label>

          {/* Divider */}
          <span className="hidden h-4 w-px bg-[--color-border] sm:block" />

          {/* Source toggles */}
          <div className="flex items-center gap-2">
            <span className="text-xs text-[--color-muted]">Sources:</span>
            <ToggleChip
              icon={<Database className="h-3 w-3" />}
              label="Knowledge Base"
              active={useKnowledgeBase}
              onToggle={() => setUseKnowledgeBase((v) => !v)}
            />
            <ToggleChip
              icon={<Globe className="h-3 w-3" />}
              label="Web Fetch"
              active={useWebFetch}
              onToggle={() => setUseWebFetch((v) => !v)}
            />
          </div>
        </div>
      )}

      <div className="flex items-end gap-2 rounded-xl border border-[--color-border] bg-[--color-surface-raised] px-3 py-2 focus-within:border-gray-900 dark:focus-within:border-gray-100">
        <textarea
          ref={textareaRef}
          value={text}
          onChange={(e) => setText(e.target.value)}
          onKeyDown={onKeyDown}
          onInput={onInput}
          placeholder="Ask a question… (Shift+Enter for new line)"
          rows={1}
          disabled={disabled}
          className={cn(
            "flex-1 resize-none bg-transparent text-sm focus:outline-none disabled:opacity-50",
            "placeholder:text-[--color-muted]",
          )}
        />
        <div className="flex shrink-0 items-center gap-1">
          <Button
            variant="ghost"
            size="icon"
            onClick={() => setShowOptions((v) => !v)}
            className={cn(showOptions && "text-[--color-brand-500]")}
            title="Query options"
          >
            <Settings2 className="h-4 w-4" />
          </Button>
          <Button size="icon" onClick={submit} disabled={!text.trim() || disabled}>
            <Send className="h-4 w-4" strokeWidth={2.5} />
          </Button>
        </div>
      </div>
      <p className="mt-1.5 hidden text-center text-[10px] text-[--color-muted] sm:block">
        Enter to send · Shift+Enter for newline
      </p>
    </div>
  );
}
