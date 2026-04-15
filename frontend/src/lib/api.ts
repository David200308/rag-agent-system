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
  ConversationShare,
  IngestionResult,
  KnowledgeSourceEntry,
  UrlIngestionResult,
  WebFetchWhitelistEntry,
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

export async function fetchKnowledgeSources(): Promise<KnowledgeSourceEntry[]> {
  const res = await fetch("/api/agent/knowledge");
  if (!res.ok) return [];
  return res.json() as Promise<KnowledgeSourceEntry[]>;
}

export async function deleteKnowledgeSource(source: string): Promise<void> {
  await fetch(`/api/agent/knowledge?source=${encodeURIComponent(source)}`, { method: "DELETE" });
}

export async function updateKnowledgeSource(
  source: string,
  label: string,
  category: string,
): Promise<void> {
  await fetch("/api/agent/knowledge", {
    method: "PATCH",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ source, label, category }),
  });
}

export async function updateKnowledgeSharing(source: string, emails: string[]): Promise<void> {
  await fetch("/api/agent/knowledge", {
    method: "PUT",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ source, emails }),
  });
}

export async function deleteConversation(backendId: string): Promise<void> {
  await fetch(`/api/agent/conversations/${backendId}`, { method: "DELETE" });
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
  replace = false,
): Promise<IngestionResult> {
  const form = new FormData();
  form.append("file", file);
  if (source)   form.append("source", source);
  if (category) form.append("category", category);
  if (replace)  form.append("replace", "true");
  return postForm<IngestionResult>("/api/agent/ingest", form);
}

export async function ingestText(
  text: string,
  source: string,
  replace = false,
): Promise<IngestionResult> {
  return postJson<IngestionResult>("/api/agent/ingest-text", { text, source, replace: String(replace) });
}

export async function ingestUrl(
  url: string,
  category?: string,
): Promise<UrlIngestionResult> {
  return postJson<UrlIngestionResult>("/api/agent/ingest-url", { url, category });
}

// ── Share link ────────────────────────────────────────────────────────────────

export async function createShare(
  conversationId: string,
  expireDays: number | null,
): Promise<ConversationShare> {
  return postJson<ConversationShare>(
    `/api/agent/conversations/${conversationId}/share`,
    { expireDays },
  );
}

export async function getShare(conversationId: string): Promise<ConversationShare | null> {
  const res = await fetch(`/api/agent/conversations/${conversationId}/share`);
  if (res.status === 404) return null;
  if (!res.ok) throw new Error(`${res.status}`);
  return res.json() as Promise<ConversationShare>;
}

export async function revokeShare(conversationId: string): Promise<void> {
  await fetch(`/api/agent/conversations/${conversationId}/share`, { method: "DELETE" });
}

// ── Web-fetch whitelist ───────────────────────────────────────────────────────

export async function fetchWebFetchWhitelist(): Promise<WebFetchWhitelistEntry[]> {
  const res = await fetch("/api/agent/web-fetch/whitelist");
  if (!res.ok) return [];
  return res.json() as Promise<WebFetchWhitelistEntry[]>;
}

export async function addWebFetchDomain(domain: string): Promise<WebFetchWhitelistEntry> {
  return postJson<WebFetchWhitelistEntry>("/api/agent/web-fetch/whitelist", { domain });
}

export async function removeWebFetchDomain(domain: string): Promise<void> {
  await fetch(`/api/agent/web-fetch/whitelist/${encodeURIComponent(domain)}`, { method: "DELETE" });
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
  { file: File; source?: string; category?: string; replace?: boolean }
> {
  return {
    mutationFn: ({ file, source, category, replace }) => ingestFile(file, source, category, replace),
  };
}

/**
 * mutationOptions factory for plain-text ingestion.
 */
export function ingestTextMutationOptions(): MutationOptions<
  IngestionResult,
  Error,
  { text: string; source: string; replace?: boolean }
> {
  return {
    mutationFn: ({ text, source, replace }) => ingestText(text, source, replace),
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
