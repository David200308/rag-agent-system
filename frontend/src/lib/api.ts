/**
 * Client-side API layer.
 *
 * Architecture:
 *   Browser  ──native fetch──►  Next.js API routes  ──undici──►  Spring Boot :8081
 *
 * This file:
 *  - Uses the native Fetch API (no external HTTP library in the browser)
 *  - Exports TanStack Query option factories (queryOptions / mutationOptions)
 *    for full type-safety and centralised cache-key management
 */
import { queryOptions, type MutationOptions } from "@tanstack/react-query";
import type {
  AgentRequest,
  AgentResponse,
  BackendConversation,
  BackendMessage,
  IngestionResult,
  UrlIngestionResult,
} from "@/types/agent";

// ── Fetch helpers ─────────────────────────────────────────────────────────────

async function postJson<T>(url: string, body: unknown): Promise<T> {
  const res = await fetch(url, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`${res.status} ${res.statusText}: ${text}`);
  }
  return res.json() as Promise<T>;
}

async function postForm<T>(url: string, form: FormData): Promise<T> {
  const res = await fetch(url, { method: "POST", body: form });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`${res.status} ${res.statusText}: ${text}`);
  }
  return res.json() as Promise<T>;
}

// ── Raw API functions ─────────────────────────────────────────────────────────

export async function queryAgent(payload: AgentRequest): Promise<AgentResponse> {
  return postJson<AgentResponse>("/api/agent/query", payload);
}

export async function fetchConversations(): Promise<BackendConversation[]> {
  const res = await fetch("/api/agent/conversations");
  if (!res.ok) return [];
  return res.json() as Promise<BackendConversation[]>;
}

export async function fetchConversationMessages(backendId: string): Promise<BackendMessage[]> {
  const res = await fetch(`/api/agent/conversations/${backendId}`);
  if (!res.ok) return [];
  return res.json() as Promise<BackendMessage[]>;
}

export async function ingestFile(
  file: File,
  source?: string,
  category?: string,
): Promise<IngestionResult> {
  const form = new FormData();
  form.append("file", file);
  if (source)   form.append("source", source);
  if (category) form.append("category", category);
  return postForm<IngestionResult>("/api/agent/ingest", form);
}

export async function ingestText(
  text: string,
  source: string,
): Promise<IngestionResult> {
  return postJson<IngestionResult>("/api/agent/ingest-text", { text, source });
}

export async function ingestUrl(
  url: string,
  category?: string,
): Promise<UrlIngestionResult> {
  return postJson<UrlIngestionResult>("/api/agent/ingest-url", { url, category });
}

// ── TanStack Query option factories ───────────────────────────────────────────
// Use these with useQuery / useMutation / prefetchQuery for consistent cache keys.

/** Cache keys namespace */
export const agentKeys = {
  all:   ["agent"]            as const,
  query: (q: string) => ["agent", "query", q] as const,
} as const;

/**
 * queryOptions factory for a single agent query result.
 * Useful for prefetching or Server Component usage.
 */
export function agentQueryOptions(payload: AgentRequest) {
  return queryOptions({
    queryKey: agentKeys.query(payload.query),
    queryFn: () => queryAgent(payload),
    staleTime: 60_000,    // cache result for 1 min — RAG answers don't change often
    gcTime: 5 * 60_000,
  });
}

/**
 * mutationOptions factory for file ingestion.
 * Pass to useMutation({ ...ingestFileMutationOptions() }).
 */
export function ingestFileMutationOptions(): MutationOptions<
  IngestionResult,
  Error,
  { file: File; source?: string; category?: string }
> {
  return {
    mutationFn: ({ file, source, category }) => ingestFile(file, source, category),
  };
}

/**
 * mutationOptions factory for plain-text ingestion.
 */
export function ingestTextMutationOptions(): MutationOptions<
  IngestionResult,
  Error,
  { text: string; source: string }
> {
  return {
    mutationFn: ({ text, source }) => ingestText(text, source),
  };
}

/**
 * mutationOptions factory for URL ingestion.
 */
export function ingestUrlMutationOptions(): MutationOptions<
  UrlIngestionResult,
  Error,
  { url: string; category?: string }
> {
  return {
    mutationFn: ({ url, category }) => ingestUrl(url, category),
  };
}
