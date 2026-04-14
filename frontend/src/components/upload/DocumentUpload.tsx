"use client";

import { useState, useCallback } from "react";
import { useMutation } from "@tanstack/react-query";
import { Upload, FileText, CheckCircle2, XCircle, X } from "lucide-react";
import { cn } from "@/lib/utils";
import { ingestFileMutationOptions, ingestTextMutationOptions } from "@/lib/api";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
import type { IngestionResult } from "@/types/agent";

interface FileEntry {
  file: File;
  status: "pending" | "uploading" | "done" | "error";
  result?: IngestionResult;
  error?: string;
}

export function DocumentUpload() {
  const [files, setFiles] = useState<FileEntry[]>([]);
  const [textInput, setTextInput] = useState("");
  const [textSource, setTextSource] = useState("");
  const [dragging, setDragging] = useState(false);
  const [replaceFiles, setReplaceFiles] = useState(false);
  const [replaceText, setReplaceText] = useState(false);

  const fileMutation = useMutation({
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

  const textMutation = useMutation({
    ...ingestTextMutationOptions(),
    onSuccess: () => {
      setTextInput("");
      setTextSource("");
    },
  });

  const addFiles = useCallback((incoming: FileList | File[]) => {
    const newEntries: FileEntry[] = Array.from(incoming).map((file) => ({
      file,
      status: "pending",
    }));
    setFiles((prev) => [...prev, ...newEntries]);
  }, []);

  const onDrop = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault();
      setDragging(false);
      if (e.dataTransfer.files.length) addFiles(e.dataTransfer.files);
    },
    [addFiles],
  );

  const uploadAll = () => {
    files
      .filter((f) => f.status === "pending")
      .forEach((f) => fileMutation.mutate({ file: f.file, replace: replaceFiles }));
  };

  const removeFile = (file: File) =>
    setFiles((prev) => prev.filter((f) => f.file !== file));

  const pendingCount = files.filter((f) => f.status === "pending").length;

  return (
    <div className="mx-auto max-w-2xl space-y-8 p-6">
      <div>
        <h1 className="text-xl font-semibold">Upload Documents</h1>
        <p className="mt-1 text-sm text-[--color-muted]">
          Ingest PDF, DOCX, HTML, or plain text into Weaviate for RAG retrieval.
        </p>
      </div>

      {/* ── Drop zone ── */}
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

      {/* ── Replace toggle ── */}
      <label className="flex cursor-pointer items-center gap-2 text-sm">
        <input
          type="checkbox"
          checked={replaceFiles}
          onChange={(e) => setReplaceFiles(e.target.checked)}
          className="h-4 w-4 rounded border-[--color-border] accent-gray-900 dark:accent-white"
        />
        <span>Replace existing chunks for the same source</span>
        <span className="text-xs text-[--color-muted]">(deletes old vectors before re-ingesting)</span>
      </label>

      {/* ── File list ── */}
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
                  {error && ` · ${error}`}
                </p>
              </div>
              <Badge
                variant={
                  status === "done" ? "success" :
                  status === "error" ? "danger" :
                  status === "uploading" ? "info" : "default"
                }
              >
                {status === "uploading" ? (
                  <span className="flex items-center gap-1">
                    <span className="h-2.5 w-2.5 animate-spin rounded-full border border-current border-t-transparent" />
                    uploading
                  </span>
                ) : status === "done" ? (
                  <span className="flex items-center gap-1"><CheckCircle2 className="h-3 w-3" /> done</span>
                ) : status === "error" ? (
                  <span className="flex items-center gap-1"><XCircle className="h-3 w-3" /> error</span>
                ) : "pending"}
              </Badge>
              {status === "pending" && (
                <button onClick={() => removeFile(file)} className="text-[--color-muted] hover:text-red-500">
                  <X className="h-3.5 w-3.5" />
                </button>
              )}
            </div>
          ))}

          {pendingCount > 0 && (
            <Button onClick={uploadAll} className="w-full">
              Upload {pendingCount} file{pendingCount > 1 ? "s" : ""}
            </Button>
          )}
        </div>
      )}

      {/* ── Plain text ingestion ── */}
      <div className="space-y-3 rounded-xl border border-[--color-border] p-4">
        <h2 className="text-sm font-semibold">Paste text directly</h2>
        <input
          type="text"
          placeholder="Source label (e.g. company-docs)"
          value={textSource}
          onChange={(e) => setTextSource(e.target.value)}
          className="w-full rounded-lg border border-[--color-border] bg-[--color-surface] px-3 py-1.5 text-sm focus:outline-none focus:ring-1 focus:ring-gray-900 dark:focus:ring-gray-100"
        />
        <textarea
          rows={6}
          placeholder="Paste document text here…"
          value={textInput}
          onChange={(e) => setTextInput(e.target.value)}
          className="w-full resize-none rounded-lg border border-[--color-border] bg-[--color-surface] px-3 py-2 text-sm focus:outline-none focus:ring-1 focus:ring-gray-900 dark:focus:ring-gray-100"
        />
        <label className="flex cursor-pointer items-center gap-2 text-sm">
          <input
            type="checkbox"
            checked={replaceText}
            onChange={(e) => setReplaceText(e.target.checked)}
            className="h-4 w-4 rounded border-[--color-border] accent-gray-900 dark:accent-white"
          />
          <span>Replace existing chunks for this source</span>
        </label>
        <Button
          onClick={() => textMutation.mutate({ text: textInput, source: textSource || "manual-input", replace: replaceText })}
          loading={textMutation.isPending}
          disabled={!textInput.trim()}
          className="w-full"
        >
          Ingest text
        </Button>
        {textMutation.isSuccess && (
          <p className="text-center text-sm text-emerald-600">
            ✓ Ingested {textMutation.data?.chunkCount} chunks
          </p>
        )}
      </div>
    </div>
  );
}
