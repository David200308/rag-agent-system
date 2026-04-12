"use client";

import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { Link2, CheckCircle2, XCircle, Trash2, Globe, ArrowLeft } from "lucide-react";
import Link from "next/link";
import { cn } from "@/lib/utils";
import { ingestUrlMutationOptions } from "@/lib/api";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
import type { UrlIngestionResult } from "@/types/agent";

interface ConnectorEntry {
  id: string;
  name: string;
  url: string;
  category: string;
  status: "pending" | "fetching" | "done" | "error";
  result?: UrlIngestionResult;
  error?: string;
}

export function McpConnector() {
  const [entries, setEntries]   = useState<ConnectorEntry[]>([]);
  const [name, setName]         = useState("");
  const [url, setUrl]           = useState("");
  const [category, setCategory] = useState("");

  const mutation = useMutation({
    ...ingestUrlMutationOptions(),
    onSuccess: (result, { url: u }) => {
      setEntries((prev) =>
        prev.map((e) => (e.url === u && e.status === "fetching" ? { ...e, status: "done", result } : e)),
      );
    },
    onError: (err: Error, { url: u }) => {
      setEntries((prev) =>
        prev.map((e) =>
          e.url === u && e.status === "fetching"
            ? { ...e, status: "error", error: err.message }
            : e,
        ),
      );
    },
  });

  const addAndFetch = () => {
    const trimmed = url.trim();
    if (!trimmed) return;

    const entry: ConnectorEntry = {
      id: crypto.randomUUID(),
      name: name.trim() || trimmed,
      url: trimmed,
      category: category.trim(),
      status: "fetching",
    };
    setEntries((prev) => [entry, ...prev]);
    setName("");
    setUrl("");
    setCategory("");

    mutation.mutate({ url: trimmed, category: category.trim() || undefined });
  };

  const remove = (id: string) =>
    setEntries((prev) => prev.filter((e) => e.id !== id));

  return (
    <div className="mx-auto max-w-2xl space-y-8 p-6">
      {/* Back button */}
      <Link
        href="/"
        className="inline-flex items-center gap-1.5 text-sm text-[--color-muted] hover:text-inherit transition-colors"
      >
        <ArrowLeft className="h-4 w-4" />
        Back to Chat
      </Link>

      <div>
        <h1 className="text-xl font-semibold">MCP Connectors</h1>
        <p className="mt-1 text-sm text-[--color-muted]">
          Connect external data sources to the RAG knowledge base. Enter a URL to fetch and ingest its content.
        </p>
      </div>

      {/* ── MCP server info ── */}
      <div className="rounded-xl border border-[--color-border] bg-[--color-surface-raised] p-4 space-y-1">
        <p className="text-xs font-semibold uppercase tracking-wide text-[--color-muted]">MCP Server</p>
        <p className="text-sm font-mono">http://localhost:8081/mcp/sse</p>
        <p className="text-xs text-[--color-muted]">
          Connect Claude Desktop or Claude Code by pointing an MCP client at this SSE endpoint.
          Tools available: <code className="rounded bg-[--color-border]/60 px-1 py-0.5">search_knowledge</code>{" "}
          <code className="rounded bg-[--color-border]/60 px-1 py-0.5">ingest_url</code>
        </p>
      </div>

      {/* ── URL connector form ── */}
      <div className="space-y-3 rounded-xl border border-[--color-border] p-4">
        <h2 className="text-sm font-semibold">URL Connector</h2>
        <p className="text-xs text-[--color-muted]">
          Paste any public URL — the page will be fetched, parsed, and added to Weaviate.
          Add an optional tag (e.g. <code className="rounded bg-[--color-border]/60 px-1">docs</code>,{" "}
          <code className="rounded bg-[--color-border]/60 px-1">blog</code>) to filter by source when querying.
        </p>

        <input
          type="text"
          placeholder="Connector name (e.g. Company Docs)"
          value={name}
          onChange={(e) => setName(e.target.value)}
          className="w-full rounded-lg border border-[--color-border] bg-[--color-surface] px-3 py-1.5 text-sm focus:outline-none focus:ring-1 focus:ring-indigo-500"
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
              className="w-full rounded-lg border border-[--color-border] bg-[--color-surface] py-1.5 pl-8 pr-3 text-sm focus:outline-none focus:ring-1 focus:ring-indigo-500"
            />
          </div>
          <input
            type="text"
            placeholder="Tag (optional)"
            value={category}
            onChange={(e) => setCategory(e.target.value)}
            title="A short label stored as metadata — use it to filter results later (e.g. 'docs', 'blog')"
            className="w-32 shrink-0 rounded-lg border border-[--color-border] bg-[--color-surface] px-3 py-1.5 text-sm focus:outline-none focus:ring-1 focus:ring-indigo-500"
          />
        </div>

        <Button
          onClick={addAndFetch}
          loading={mutation.isPending}
          disabled={!url.trim()}
          className="w-full"
        >
          <Link2 className="mr-1.5 h-3.5 w-3.5" strokeWidth={2.5} />
          Fetch &amp; Ingest URL
        </Button>
      </div>

      {/* ── Ingested URL list ── */}
      {entries.length > 0 && (
        <div className="space-y-2">
          <h2 className="text-sm font-semibold">Ingested sources</h2>
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
                  {e.error && <span className="text-red-400">{e.error}</span>}
                </p>
              </div>
              <Badge
                variant={
                  e.status === "done"     ? "success" :
                  e.status === "error"    ? "danger"  :
                  e.status === "fetching" ? "info"    : "default"
                }
              >
                {e.status === "fetching" ? (
                  <span className="flex items-center gap-1">
                    <span className="h-2.5 w-2.5 animate-spin rounded-full border border-current border-t-transparent" />
                    fetching
                  </span>
                ) : e.status === "done" ? (
                  <span className="flex items-center gap-1"><CheckCircle2 className="h-3 w-3" /> done</span>
                ) : e.status === "error" ? (
                  <span className="flex items-center gap-1"><XCircle className="h-3 w-3" /> error</span>
                ) : "pending"}
              </Badge>
              <button
                onClick={() => remove(e.id)}
                className="mt-0.5 text-[--color-muted] hover:text-red-500"
                title="Remove"
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
