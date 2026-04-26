"use client";

import { useState, useCallback, useEffect } from "react";
import { Upload, Trash2, FileText, CheckCircle2, XCircle, RefreshCw, Zap } from "lucide-react";
import { cn } from "@/lib/utils";
import { fetchSkills, uploadSkill, deleteSkill } from "@/lib/api";
import { Button } from "@/components/ui/Button";
import type { Skill } from "@/types/agent";

type Tab = "upload" | "manage";

interface UploadEntry {
  file: File;
  name: string;
  status: "pending" | "uploading" | "done" | "error";
  error?: string;
}

function UploadPanel({ onUploaded }: { onUploaded: () => void }) {
  const [entries, setEntries] = useState<UploadEntry[]>([]);
  const [dragging, setDragging] = useState(false);

  const addFiles = useCallback((incoming: FileList | File[]) => {
    const accepted = Array.from(incoming).filter((f) => {
      const ext = f.name.split(".").pop()?.toLowerCase();
      return ext === "txt" || ext === "md" || ext === "zip";
    });
    setEntries((prev) => [
      ...prev,
      ...accepted.map((file) => ({
        file,
        name: file.name.replace(/\.[^.]+$/, ""),
        status: "pending" as const,
      })),
    ]);
  }, []);

  const onDrop = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault();
      setDragging(false);
      if (e.dataTransfer.files.length) addFiles(e.dataTransfer.files);
    },
    [addFiles],
  );

  async function uploadAll() {
    const pending = entries.filter((e) => e.status === "pending");
    for (const entry of pending) {
      setEntries((prev) =>
        prev.map((e) => (e.file === entry.file ? { ...e, status: "uploading" } : e))
      );
      try {
        await uploadSkill(entry.file, entry.name || undefined);
        setEntries((prev) =>
          prev.map((e) => (e.file === entry.file ? { ...e, status: "done" } : e))
        );
        onUploaded();
      } catch (err) {
        setEntries((prev) =>
          prev.map((e) =>
            e.file === entry.file
              ? { ...e, status: "error", error: (err as Error).message }
              : e
          )
        );
      }
    }
  }

  const hasPending = entries.some((e) => e.status === "pending");

  return (
    <div className="space-y-4">
      {/* Drop zone */}
      <div
        onDragOver={(e) => { e.preventDefault(); setDragging(true); }}
        onDragLeave={() => setDragging(false)}
        onDrop={onDrop}
        className={cn(
          "flex flex-col items-center justify-center gap-2 rounded-lg border-2 border-dashed px-6 py-10 text-center transition-colors",
          dragging
            ? "border-black bg-black/5 dark:border-white dark:bg-white/5"
            : "border-[--color-border] hover:border-[--color-muted]",
        )}
      >
        <Upload className="h-8 w-8 text-[--color-muted]" />
        <p className="text-sm font-medium">Drop skill files here</p>
        <p className="text-xs text-[--color-muted]">.txt, .md, .zip supported</p>
        <label className="mt-2 cursor-pointer rounded-md border border-[--color-border] px-3 py-1.5 text-xs font-medium hover:bg-[--color-border]/40 transition-colors">
          Browse files
          <input
            type="file"
            accept=".txt,.md,.zip"
            multiple
            className="sr-only"
            onChange={(e) => e.target.files && addFiles(e.target.files)}
          />
        </label>
      </div>

      {/* File list */}
      {entries.length > 0 && (
        <div className="space-y-2">
          {entries.map((entry, i) => (
            <div key={i} className="flex items-center gap-3 rounded-md border border-[--color-border] px-3 py-2">
              <FileText className="h-4 w-4 shrink-0 text-[--color-muted]" />
              <div className="min-w-0 flex-1">
                <input
                  value={entry.name}
                  onChange={(ev) =>
                    setEntries((prev) =>
                      prev.map((e, j) => (j === i ? { ...e, name: ev.target.value } : e))
                    )
                  }
                  disabled={entry.status !== "pending"}
                  placeholder="Skill name"
                  className="w-full bg-transparent text-xs font-medium focus:outline-none disabled:opacity-60"
                />
                <p className="text-[10px] text-[--color-muted]">{entry.file.name}</p>
              </div>
              <div className="shrink-0">
                {entry.status === "pending" && (
                  <span className="text-[10px] text-[--color-muted]">Pending</span>
                )}
                {entry.status === "uploading" && (
                  <RefreshCw className="h-3.5 w-3.5 animate-spin text-[--color-muted]" />
                )}
                {entry.status === "done" && (
                  <CheckCircle2 className="h-3.5 w-3.5 text-green-500" />
                )}
                {entry.status === "error" && (
                  <XCircle className="h-3.5 w-3.5 text-red-500" />
                )}
              </div>
              {entry.status === "pending" && (
                <button
                  onClick={() => setEntries((prev) => prev.filter((_, j) => j !== i))}
                  className="shrink-0 text-[--color-muted] hover:text-red-500"
                >
                  <Trash2 className="h-3.5 w-3.5" />
                </button>
              )}
            </div>
          ))}

          {hasPending && (
            <Button size="sm" onClick={uploadAll} className="w-full">
              Upload all
            </Button>
          )}
        </div>
      )}
    </div>
  );
}

function ManagePanel({
  skills,
  loading,
  onRefresh,
}: {
  skills: Skill[];
  loading: boolean;
  onRefresh: () => void;
}) {
  async function handleDelete(id: string) {
    await deleteSkill(id);
    onRefresh();
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center py-12 text-[--color-muted]">
        <RefreshCw className="h-4 w-4 animate-spin" />
      </div>
    );
  }

  if (skills.length === 0) {
    return (
      <p className="py-12 text-center text-sm text-[--color-muted]">
        No skills uploaded yet. Use the Upload tab to add your first skill.
      </p>
    );
  }

  return (
    <div className="space-y-2">
      {skills.map((skill) => (
        <div
          key={skill.id}
          className="flex items-center gap-3 rounded-md border border-[--color-border] px-3 py-2.5"
        >
          <Zap className="h-4 w-4 shrink-0 text-amber-500" />
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-medium">{skill.name}</p>
            <p className="text-[10px] text-[--color-muted]">
              {skill.fileName} · {skill.fileType.toUpperCase()} ·{" "}
              {new Date(skill.createdAt).toLocaleDateString()}
            </p>
          </div>
          <Button
            size="icon"
            variant="ghost"
            className="h-7 w-7 shrink-0 text-[--color-muted] hover:text-red-500"
            onClick={() => handleDelete(skill.id)}
            title="Delete skill"
          >
            <Trash2 className="h-3.5 w-3.5" />
          </Button>
        </div>
      ))}
    </div>
  );
}

export function SkillsManager() {
  const [tab, setTab] = useState<Tab>("upload");
  const [skills, setSkills] = useState<Skill[]>([]);
  const [loading, setLoading] = useState(true);

  async function loadSkills() {
    setLoading(true);
    try {
      const data = await fetchSkills();
      setSkills(data);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { loadSkills(); }, []);

  const tabs: { key: Tab; label: string }[] = [
    { key: "upload", label: "Upload" },
    { key: "manage", label: `Manage (${skills.length})` },
  ];

  return (
    <div className="mx-auto max-w-2xl px-6 py-8">
      {/* Header */}
      <div className="mb-6">
        <div className="flex items-center gap-2 mb-1">
          <Zap className="h-5 w-5 text-amber-500" />
          <h1 className="text-xl font-semibold">Skills</h1>
        </div>
        <p className="text-sm text-[--color-muted]">
          Upload reusable skill files that agents can leverage. Skills can be text, markdown, or zip archives containing multiple files.
        </p>
      </div>

      {/* Tabs */}
      <div className="mb-6 flex gap-1 border-b border-[--color-border]">
        {tabs.map((t) => (
          <button
            key={t.key}
            onClick={() => setTab(t.key)}
            className={cn(
              "border-b-2 px-4 py-2 text-sm font-medium transition-colors",
              tab === t.key
                ? "border-black text-black dark:border-white dark:text-white"
                : "border-transparent text-[--color-muted] hover:text-[--color-fg]",
            )}
          >
            {t.label}
          </button>
        ))}
      </div>

      {tab === "upload" && (
        <UploadPanel onUploaded={() => { loadSkills(); setTab("manage"); }} />
      )}
      {tab === "manage" && (
        <ManagePanel skills={skills} loading={loading} onRefresh={loadSkills} />
      )}
    </div>
  );
}
