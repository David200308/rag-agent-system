"use client";

import {
  useState,
  useRef,
  useEffect,
  useCallback,
  type KeyboardEvent,
} from "react";
import { Send, Database, Globe, Zap, X, ChevronDown } from "lucide-react";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/Button";
import { fetchSkills } from "@/lib/api";
import {
  SlashCommandMenu,
  buildSlashItems,
  type SlashItem,
} from "./SlashCommandMenu";
import type { Skill } from "@/types/agent";

interface MessageInputProps {
  onSend: (
    query: string,
    topK: number,
    useKnowledgeBase: boolean,
    useWebFetch: boolean,
    skillIds: string[],
  ) => void;
  disabled?: boolean;
}

// ── ToggleChip ────────────────────────────────────────────────────────────────

function ToggleChip({
  icon,
  label,
  active,
  onToggle,
}: {
  icon: React.ReactNode;
  label: string;
  active: boolean;
  onToggle: () => void;
}) {
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

// ── SkillChip ─────────────────────────────────────────────────────────────────

function SkillChip({
  skill,
  onRemove,
}: {
  skill: Skill;
  onRemove: () => void;
}) {
  return (
    <span className="inline-flex items-center gap-1 rounded-full border border-amber-300 bg-amber-50 px-2 py-0.5 text-xs font-medium text-amber-700 dark:border-amber-700 dark:bg-amber-950 dark:text-amber-300">
      <Zap className="h-3 w-3 shrink-0" />
      {skill.name}
      <button
        type="button"
        onClick={onRemove}
        className="ml-0.5 hover:text-red-500"
        aria-label={`Remove skill ${skill.name}`}
      >
        <X className="h-3 w-3" />
      </button>
    </span>
  );
}

// ── SkillPickerDropdown ───────────────────────────────────────────────────────

function SkillPickerDropdown({
  skills,
  selected,
  onToggle,
  onClose,
}: {
  skills: Skill[];
  selected: Set<string>;
  onToggle: (skill: Skill) => void;
  onClose: () => void;
}) {
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function handle(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) onClose();
    }
    document.addEventListener("mousedown", handle);
    return () => document.removeEventListener("mousedown", handle);
  }, [onClose]);

  return (
    <div
      ref={ref}
      className="absolute bottom-full right-0 z-50 mb-1 w-56 overflow-hidden rounded-xl border border-[--color-border] bg-[--color-surface] shadow-xl"
    >
      <p className="px-3 py-1.5 text-[10px] font-semibold uppercase tracking-wide text-[--color-muted]">
        Attach Skills
      </p>
      {skills.length === 0 ? (
        <p className="px-3 pb-3 text-xs text-[--color-muted]">
          No skills uploaded yet.
        </p>
      ) : (
        <div className="max-h-52 overflow-y-auto">
          {skills.map((skill) => {
            const checked = selected.has(skill.id);
            return (
              <button
                key={skill.id}
                type="button"
                onClick={() => onToggle(skill)}
                className={cn(
                  "flex w-full items-center gap-2.5 px-3 py-2 text-left text-sm transition-colors hover:bg-[--color-surface-raised]",
                  checked && "bg-[--color-surface-raised]",
                )}
              >
                <span
                  className={cn(
                    "flex h-4 w-4 shrink-0 items-center justify-center rounded border",
                    checked
                      ? "border-amber-500 bg-amber-500"
                      : "border-[--color-border]",
                  )}
                >
                  {checked && (
                    <svg
                      className="h-2.5 w-2.5 text-white"
                      fill="none"
                      viewBox="0 0 12 12"
                    >
                      <path
                        d="M2 6l3 3 5-5"
                        stroke="currentColor"
                        strokeWidth={2}
                        strokeLinecap="round"
                        strokeLinejoin="round"
                      />
                    </svg>
                  )}
                </span>
                <Zap
                  className={cn(
                    "h-3.5 w-3.5 shrink-0",
                    checked ? "text-amber-500" : "text-[--color-muted]",
                  )}
                />
                <span className="truncate">{skill.name}</span>
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}

// ── MessageInput (root) ───────────────────────────────────────────────────────

export function MessageInput({ onSend, disabled = false }: MessageInputProps) {
  const [text, setText] = useState("");
  const [topK, setTopK] = useState(5);
  const [useKnowledgeBase, setUseKnowledgeBase] = useState(true);
  const [useWebFetch, setUseWebFetch] = useState(true);

  // Skills state
  const [allSkills, setAllSkills] = useState<Skill[]>([]);
  const [selectedSkills, setSelectedSkills] = useState<Skill[]>([]);
  const [showSkillPicker, setShowSkillPicker] = useState(false);

  // Slash command state
  const [slashOpen, setSlashOpen] = useState(false);
  const [slashFilter, setSlashFilter] = useState("");
  const [slashActiveIdx, setSlashActiveIdx] = useState(0);

  const textareaRef = useRef<HTMLTextAreaElement>(null);

  // Load skills once
  useEffect(() => {
    fetchSkills().then(setAllSkills).catch(() => {});
  }, []);

  const selectedIds = new Set(selectedSkills.map((s) => s.id));

  // ── skill picker toggle ──────────────────────────────────────────────────────

  const toggleSkill = useCallback((skill: Skill) => {
    setSelectedSkills((prev) =>
      prev.some((s) => s.id === skill.id)
        ? prev.filter((s) => s.id !== skill.id)
        : [...prev, skill],
    );
  }, []);

  const removeSkill = useCallback((id: string) => {
    setSelectedSkills((prev) => prev.filter((s) => s.id !== id));
  }, []);

  // ── slash command detection ──────────────────────────────────────────────────

  const handleTextChange = (value: string) => {
    setText(value);

    if (value.startsWith("/")) {
      const filter = value.slice(1);
      // Only open if there are no spaces (still typing the command/skill name)
      if (!filter.includes(" ")) {
        setSlashFilter(filter);
        setSlashOpen(true);
        setSlashActiveIdx(0);
        return;
      }
    }
    setSlashOpen(false);
  };

  const handleSlashSelect = useCallback(
    (item: SlashItem) => {
      setSlashOpen(false);
      if (item.type === "skill") {
        // Attach skill as chip and clear the slash input
        if (!selectedIds.has(item.skill.id)) {
          setSelectedSkills((prev) => [...prev, item.skill]);
        }
        setText("");
        if (textareaRef.current) textareaRef.current.style.height = "auto";
      } else if (item.id === "workflow") {
        // Complete the text to "/workflow " for the user to add params
        setText("/workflow ");
        textareaRef.current?.focus();
      } else if (item.id === "knowledge") {
        // Toggle knowledge base
        setUseKnowledgeBase((v) => !v);
        setText("");
        if (textareaRef.current) textareaRef.current.style.height = "auto";
      }
    },
    [selectedIds],
  );

  // ── keyboard navigation ──────────────────────────────────────────────────────

  const onKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (slashOpen) {
      const items = buildSlashItems(slashFilter, allSkills);
      if (e.key === "ArrowDown") {
        e.preventDefault();
        setSlashActiveIdx((i) => Math.min(i + 1, items.length - 1));
        return;
      }
      if (e.key === "ArrowUp") {
        e.preventDefault();
        setSlashActiveIdx((i) => Math.max(i - 1, 0));
        return;
      }
      if (e.key === "Enter") {
        e.preventDefault();
        const item = items[slashActiveIdx];
        if (item) handleSlashSelect(item);
        return;
      }
      if (e.key === "Escape") {
        e.preventDefault();
        setSlashOpen(false);
        return;
      }
    }

    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      submit();
    }
  };

  // ── submit ───────────────────────────────────────────────────────────────────

  const submit = () => {
    const trimmed = text.trim();
    if (!trimmed || disabled) return;
    onSend(
      trimmed,
      topK,
      useKnowledgeBase,
      useWebFetch,
      selectedSkills.map((s) => s.id),
    );
    setText("");
    if (textareaRef.current) textareaRef.current.style.height = "auto";
  };

  const onInput = () => {
    const el = textareaRef.current;
    if (!el) return;
    el.style.height = "auto";
    el.style.height = `${Math.min(el.scrollHeight, 200)}px`;
  };

  // ── render ───────────────────────────────────────────────────────────────────

  return (
    <div className="border-t border-[--color-border] bg-[--color-surface] p-4">
      {/* Controls row */}
      <div className="mb-3 flex flex-wrap items-center gap-3 rounded-lg border border-[--color-border] bg-[--color-surface-raised] px-3 py-2 text-sm">
        {/* Top-K */}
        <label className="flex items-center gap-2 text-[--color-muted]">
          Top-K
          <input
            type="number"
            min={1}
            max={20}
            value={topK}
            onChange={(e) => setTopK(Number(e.target.value))}
            className="w-14 rounded border border-[--color-border] bg-[--color-surface] px-2 py-0.5 text-center text-sm focus:outline-none focus:ring-1 focus:ring-gray-900 dark:focus:ring-gray-100"
          />
        </label>

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

        <span className="hidden h-4 w-px bg-[--color-border] sm:block" />

        {/* Skill chips + picker */}
        <div className="flex flex-wrap items-center gap-1.5">
          <span className="text-xs text-[--color-muted]">Skills:</span>

          {selectedSkills.map((skill) => (
            <SkillChip
              key={skill.id}
              skill={skill}
              onRemove={() => removeSkill(skill.id)}
            />
          ))}

          {/* Skill picker button */}
          <div className="relative">
            <button
              type="button"
              onClick={() => setShowSkillPicker((v) => !v)}
              className={cn(
                "inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-xs font-medium transition-colors",
                showSkillPicker
                  ? "border-amber-500 bg-amber-50 text-amber-700 dark:bg-amber-950 dark:text-amber-300"
                  : "border-[--color-border] text-[--color-muted] hover:border-amber-400 hover:text-amber-600",
              )}
              title="Select skills to attach"
            >
              <Zap className="h-3 w-3" />
              {selectedSkills.length > 0 ? `${selectedSkills.length} attached` : "Attach"}
              <ChevronDown className="h-3 w-3" />
            </button>

            {showSkillPicker && (
              <SkillPickerDropdown
                skills={allSkills}
                selected={selectedIds}
                onToggle={toggleSkill}
                onClose={() => setShowSkillPicker(false)}
              />
            )}
          </div>
        </div>
      </div>

      {/* Textarea + slash menu wrapper */}
      <div className="relative">
        {slashOpen && (
          <SlashCommandMenu
            filter={slashFilter}
            skills={allSkills}
            activeIndex={slashActiveIdx}
            onSelect={handleSlashSelect}
            onActiveChange={setSlashActiveIdx}
          />
        )}

        <div className="flex items-end gap-2 rounded-xl border border-[--color-border] bg-[--color-surface-raised] px-3 py-2 focus-within:border-gray-900 dark:focus-within:border-gray-100">
          <textarea
            ref={textareaRef}
            value={text}
            onChange={(e) => handleTextChange(e.target.value)}
            onKeyDown={onKeyDown}
            onInput={onInput}
            placeholder="Ask a question… or type / for commands and skills"
            rows={1}
            disabled={disabled}
            className={cn(
              "flex-1 resize-none bg-transparent text-sm focus:outline-none disabled:opacity-50",
              "placeholder:text-[--color-muted]",
            )}
          />
          <div className="flex shrink-0 items-center gap-1">
            <Button
              size="icon"
              onClick={submit}
              disabled={!text.trim() || disabled}
            >
              <Send className="h-4 w-4" strokeWidth={2.5} />
            </Button>
          </div>
        </div>
      </div>

      <p className="mt-1.5 hidden text-center text-[10px] text-[--color-muted] sm:block">
        Enter to send · Shift+Enter for newline · / for commands
      </p>
    </div>
  );
}
