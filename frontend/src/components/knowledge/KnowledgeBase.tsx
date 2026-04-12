"use client";

import { useState, useCallback } from "react";
import { useMutation } from "@tanstack/react-query";
import {
  Upload, FileText, CheckCircle2, XCircle, X,
  Link2, Globe, Trash2, Type,
} from "lucide-react";
import { cn } from "@/lib/utils";
import {
  ingestFileMutationOptions,
  ingestTextMutationOptions,
  ingestUrlMutationOptions,
} from "@/lib/api";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
import type { IngestionResult, UrlIngestionResult } from "@/types/agent";

// ── types ─────────────────────────────────────────────────────────────────────

type Tab = "files" | "text" | "url";

interface FileEntry {
  file: File;
  status: "pending" | "uploading" | "done" | "error";
  result?: IngestionResult;
  error?: string;
}

interface UrlEntry {
  id: string;
  name: string;
  url: string;
  category: string;
  status: "pending" | "fetching" | "done" | "error";
  result?: UrlIngestionResult;
  error?: string;
}

// ── sub-panels ────────────────────────────────────────────────────────────────

function FilesPanel() {
  const [files, setFiles] = useState<FileEntry[]>([]);
  const [dragging, setDragging] = useState(false);

  const mutation = useMutation({
    ...ingestFileMutationOptions(),
    onMutate: ({ file }) =>
      setFiles((prev) =>
        prev.map((f) => (f.file === file ? { ...f, status: "uploading" } : f)),
      ),
    onSuccess: (result, { file }) =>
      setFiles((prev) =>
        prev.map((f) => (f.file === file ? { ...f, status: "done", result } : f)),
      ),
    onError: (err: Error, { file }) =>
      setFiles((prev) =>
        prev.map((f) => (f.file === file ? { ...f, status: "error", error: err.message } : f)),
      ),
  });

  const addFiles = useCallback((incoming: FileList | File[]) => {
    const next: FileEntry[] = Array.from(incoming).map((file) => ({ file, status: "pending" }));
    setFiles((prev) => [...prev, ...next]);
  }, []);

  const onDrop = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault();
      setDragging(false);
      if (e.dataTransfer.files.length) addFiles(e.dataTransfer.files);
    },
    [addFiles],
  );

  const uploadAll = () =>
    files.filter((f) => f.status === "pending").forEach((f) => mutation.mutate({ file: f.file }));

  const remove = (file: File) => setFiles((prev) => prev.filter((f) => f.file !== file));
  const pending = files.filter((f) => f.status === "pending").length;

  return (
    <div className="space-y-5">
      {/* Drop zone */}
      <div
        onDragOver={(e) => { e.preventDefault(); setDragging(true); }}
        onDragLeave={() => setDragging(false)}
        onDrop={onDrop}
        className={cn(
          "flex flex-col items-center justify-center gap-3 rounded-xl border-2 border-dashed px-6 py-12 text-center transition-colors",
          dragging
            ? "border-gray-900 bg-gray-50 dark:border-gray-100 dark:bg-gray-900"
            : "border-[--color-border] hover:border-gray-400",
        )}
      >
        <Upload className="h-8 w-8 text-[--color-muted]" />
        <p className="text-sm font-medium">
          Drag &amp; drop files here, or{" "}
          <label className="cursor-pointer underline hover:underline">
            browse
            <input
              type="file"
              multiple
              accept=".pdf,.docx,.doc,.html,.txt,.md"
              className="sr-only"
              onChange={(e) => e.target.files && addFiles(e.target.files)}
            />
          </label>
        </p>
        <p className="text-xs text-[--color-muted]">PDF, DOCX, HTML, TXT up to 50 MB each</p>
      </div>

      {/* File list */}
      {files.length > 0 && (
        <div className="space-y-2">
          {files.map(({ file, status, result, error }) => (
            <div
              key={file.name + file.size}
              className="flex items-center gap-3 rounded-lg border border-[--color-border] bg-[--color-surface-raised] px-3 py-2"
            >
              <FileText className="h-4 w-4 shrink-0 text-[--color-muted]" />
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium">{file.name}</p>
                <p className="text-xs text-[--color-muted]">
                  {(file.size / 1024).toFixed(1)} KB
                  {result && ` · ${result.chunkCount} chunks ingested`}
                  {error && <span className="text-red-400"> · {error}</span>}
                </p>
              </div>
              <StatusBadge status={status} />
              {status === "pending" && (
                <button onClick={() => remove(file)} className="text-[--color-muted] hover:text-red-500">
                  <X className="h-3.5 w-3.5" />
                </button>
              )}
            </div>
          ))}
          {pending > 0 && (
            <Button onClick={uploadAll} className="w-full">
              Upload {pending} file{pending > 1 ? "s" : ""}
            </Button>
          )}
        </div>
      )}
    </div>
  );
}

function TextPanel() {
  const [text, setText] = useState("");
  const [source, setSource] = useState("");

  const mutation = useMutation({
    ...ingestTextMutationOptions(),
    onSuccess: () => { setText(""); setSource(""); },
  });

  return (
    <div className="space-y-3">
      <p className="text-sm text-[--color-muted]">
        Paste any text directly — it will be chunked and added to the vector store.
      </p>
      <input
        type="text"
        placeholder="Source label (e.g. company-docs)"
        value={source}
        onChange={(e) => setSource(e.target.value)}
        className="w-full rounded-lg border border-[--color-border] bg-[--color-surface] px-3 py-1.5 text-sm focus:outline-none focus:ring-1 focus:ring-gray-900 dark:focus:ring-gray-100"
      />
      <textarea
        rows={8}
        placeholder="Paste document text here…"
        value={text}
        onChange={(e) => setText(e.target.value)}
        className="w-full resize-none rounded-lg border border-[--color-border] bg-[--color-surface] px-3 py-2 text-sm focus:outline-none focus:ring-1 focus:ring-gray-900 dark:focus:ring-gray-100"
      />
      <Button
        onClick={() => mutation.mutate({ text, source: source || "manual-input" })}
        loading={mutation.isPending}
        disabled={!text.trim()}
        className="w-full"
      >
        Ingest text
      </Button>
      {mutation.isSuccess && (
        <p className="text-center text-sm text-emerald-600">
          ✓ Ingested {mutation.data?.chunkCount} chunks
        </p>
      )}
      {mutation.isError && (
        <p className="text-center text-sm text-red-500">{mutation.error.message}</p>
      )}
    </div>
  );
}

function UrlPanel() {
  const [entries, setEntries] = useState<UrlEntry[]>([]);
  const [name, setName]       = useState("");
  const [url, setUrl]         = useState("");
  const [category, setCategory] = useState("");

  const mutation = useMutation({
    ...ingestUrlMutationOptions(),
    onSuccess: (result, { url: u }) =>
      setEntries((prev) =>
        prev.map((e) => (e.url === u && e.status === "fetching" ? { ...e, status: "done", result } : e)),
      ),
    onError: (err: Error, { url: u }) =>
      setEntries((prev) =>
        prev.map((e) =>
          e.url === u && e.status === "fetching" ? { ...e, status: "error", error: err.message } : e,
        ),
      ),
  });

  const addAndFetch = () => {
    const trimmed = url.trim();
    if (!trimmed) return;
    const entry: UrlEntry = {
      id: crypto.randomUUID(),
      name: name.trim() || trimmed,
      url: trimmed,
      category: category.trim(),
      status: "fetching",
    };
    setEntries((prev) => [entry, ...prev]);
    setName(""); setUrl(""); setCategory("");
    mutation.mutate({ url: trimmed, category: category.trim() || undefined });
  };

  return (
    <div className="space-y-5">
      <p className="text-sm text-[--color-muted]">
        Paste any public URL — the page will be fetched, parsed, and added to Weaviate.
      </p>

      <div className="space-y-3">
        <input
          type="text"
          placeholder="Name (e.g. Company Docs)"
          value={name}
          onChange={(e) => setName(e.target.value)}
          className="w-full rounded-lg border border-[--color-border] bg-[--color-surface] px-3 py-1.5 text-sm focus:outline-none focus:ring-1 focus:ring-gray-900 dark:focus:ring-gray-100"
        />
        <div className="flex gap-2">
          <div className="relative flex-1">
            <Globe className="absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-[--color-muted]" />
            <input
              type="url"
              placeholder="https://example.com/docs/page"
              value={url}
              onChange={(e) => setUrl(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && addAndFetch()}
              className="w-full rounded-lg border border-[--color-border] bg-[--color-surface] py-1.5 pl-8 pr-3 text-sm focus:outline-none focus:ring-1 focus:ring-gray-900 dark:focus:ring-gray-100"
            />
          </div>
          <input
            type="text"
            placeholder="Tag"
            value={category}
            onChange={(e) => setCategory(e.target.value)}
            title="Short label stored as metadata — filter by it when querying"
            className="w-28 shrink-0 rounded-lg border border-[--color-border] bg-[--color-surface] px-3 py-1.5 text-sm focus:outline-none focus:ring-1 focus:ring-gray-900 dark:focus:ring-gray-100"
          />
        </div>
        <Button onClick={addAndFetch} loading={mutation.isPending} disabled={!url.trim()} className="w-full">
          <Link2 className="mr-1.5 h-3.5 w-3.5" strokeWidth={2.5} />
          Fetch &amp; Ingest URL
        </Button>
      </div>

      {entries.length > 0 && (
        <div className="space-y-2">
          <p className="text-xs font-semibold uppercase tracking-wide text-[--color-muted]">Ingested sources</p>
          {entries.map((e) => (
            <div
              key={e.id}
              className="flex items-start gap-3 rounded-lg border border-[--color-border] bg-[--color-surface-raised] px-3 py-2"
            >
              <Link2
                className={cn(
                  "mt-0.5 h-4 w-4 shrink-0",
                  e.status === "done"  ? "text-emerald-500" :
                  e.status === "error" ? "text-red-400"     : "text-[--color-muted]",
                )}
              />
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium">{e.name}</p>
                <p className="truncate text-xs text-[--color-muted]">{e.url}</p>
                <p className="text-xs text-[--color-muted]">
                  {e.result && `${e.result.chunkCount} chunks`}
                  {e.category && ` · #${e.category}`}
                  {e.error && <span className="text-red-400"> · {e.error}</span>}
                </p>
              </div>
              <UrlStatusBadge status={e.status} />
              <button
                onClick={() => setEntries((prev) => prev.filter((x) => x.id !== e.id))}
                className="mt-0.5 text-[--color-muted] hover:text-red-500"
              >
                <Trash2 className="h-3.5 w-3.5" />
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

// ── shared helpers ────────────────────────────────────────────────────────────

function StatusBadge({ status }: { status: FileEntry["status"] }) {
  if (status === "uploading") return (
    <Badge variant="info">
      <span className="flex items-center gap-1">
        <span className="h-2.5 w-2.5 animate-spin rounded-full border border-current border-t-transparent" />
        uploading
      </span>
    </Badge>
  );
  if (status === "done") return (
    <Badge variant="success"><span className="flex items-center gap-1"><CheckCircle2 className="h-3 w-3" /> done</span></Badge>
  );
  if (status === "error") return (
    <Badge variant="danger"><span className="flex items-center gap-1"><XCircle className="h-3 w-3" /> error</span></Badge>
  );
  return <Badge variant="default">pending</Badge>;
}

function UrlStatusBadge({ status }: { status: UrlEntry["status"] }) {
  if (status === "fetching") return (
    <Badge variant="info">
      <span className="flex items-center gap-1">
        <span className="h-2.5 w-2.5 animate-spin rounded-full border border-current border-t-transparent" />
        fetching
      </span>
    </Badge>
  );
  if (status === "done") return (
    <Badge variant="success"><span className="flex items-center gap-1"><CheckCircle2 className="h-3 w-3" /> done</span></Badge>
  );
  if (status === "error") return (
    <Badge variant="danger"><span className="flex items-center gap-1"><XCircle className="h-3 w-3" /> error</span></Badge>
  );
  return <Badge variant="default">pending</Badge>;
}

// ── main component ────────────────────────────────────────────────────────────

const TABS: { id: Tab; label: string; icon: React.ReactNode }[] = [
  { id: "files", label: "Files",  icon: <Upload className="h-3.5 w-3.5" /> },
  { id: "text",  label: "Text",   icon: <Type   className="h-3.5 w-3.5" /> },
  { id: "url",   label: "URL",    icon: <Globe  className="h-3.5 w-3.5" /> },
];

export function KnowledgeBase() {
  const [tab, setTab] = useState<Tab>("files");

  return (
    <div className="mx-auto max-w-2xl space-y-6 p-6">
      <div>
        <h1 className="text-xl font-semibold">Knowledge Base</h1>
        <p className="mt-1 text-sm text-[--color-muted]">
          Add content to the vector store — upload files, paste text, or ingest a URL.
        </p>
      </div>

      {/* Tabs */}
      <div className="flex gap-1 rounded-xl border border-[--color-border] bg-[--color-surface-raised] p-1">
        {TABS.map(({ id, label, icon }) => (
          <button
            key={id}
            onClick={() => setTab(id)}
            className={cn(
              "flex flex-1 items-center justify-center gap-1.5 rounded-lg py-2 text-sm font-medium transition-colors",
              tab === id
                ? "bg-white text-black shadow-sm dark:bg-black dark:text-white"
                : "text-[--color-muted] hover:text-inherit",
            )}
          >
            {icon}
            {label}
          </button>
        ))}
      </div>

      {/* Panel */}
      <div>
        {tab === "files" && <FilesPanel />}
        {tab === "text"  && <TextPanel />}
        {tab === "url"   && <UrlPanel />}
      </div>
    </div>
  );
}
