"use client";

import { useState, useRef, type KeyboardEvent } from "react";
import { Send, Settings2 } from "lucide-react";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/Button";

interface MessageInputProps {
  onSend: (query: string, topK: number) => void;
  disabled?: boolean;
}

export function MessageInput({ onSend, disabled = false }: MessageInputProps) {
  const [text, setText] = useState("");
  const [topK, setTopK] = useState(5);
  const [showOptions, setShowOptions] = useState(false);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  const submit = () => {
    const trimmed = text.trim();
    if (!trimmed || disabled) return;
    onSend(trimmed, topK);
    setText("");
    if (textareaRef.current) textareaRef.current.style.height = "auto";
  };

  const onKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      submit();
    }
  };

  // Auto-grow textarea
  const onInput = () => {
    const el = textareaRef.current;
    if (!el) return;
    el.style.height = "auto";
    el.style.height = `${Math.min(el.scrollHeight, 200)}px`;
  };

  return (
    <div className="border-t border-[--color-border] bg-[--color-surface] p-4">
      {showOptions && (
        <div className="mb-3 flex items-center gap-3 rounded-lg border border-[--color-border] bg-[--color-surface-raised] px-3 py-2 text-sm">
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
      <p className="mt-1.5 text-center text-[10px] text-[--color-muted]">
        Enter to send · Shift+Enter for newline
      </p>
    </div>
  );
}
