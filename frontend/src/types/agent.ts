// Mirror of the Java schema/* records — kept in sync manually.

export interface ConversationTurn {
  role: "user" | "assistant";
  content: string;
}

export interface AgentRequest {
  query: string;
  filters?: Record<string, string>;
  topK?: number;
  conversationHistory?: ConversationTurn[];
  stream: boolean;
}

export interface SourceDocument {
  id: string;
  content: string;
  source: string;
  score: number;
  category: string | null;
}

export type Route = "RETRIEVE" | "DIRECT" | "FALLBACK" | "ERROR";

export interface RouteDecision {
  route: Route;
  reasoning: string;
  confidence: number;
}

export interface RunMetadata {
  runId: string;
  startedAt: string; // ISO-8601
  durationMs: number;
  documentsRetrieved: number;
  modelUsed: string;
}

export interface AgentResponse {
  answer: string;
  sources: SourceDocument[];
  routeDecision: RouteDecision;
  fallbackActivated: boolean;
  fallbackReason: string | null;
  metadata: RunMetadata;
}

// ── UI-only types ──────────────────────────────────────────────────────────

export type MessageRole = "user" | "assistant" | "error";

export interface ChatMessage {
  id: string;
  role: MessageRole;
  content: string;
  sources?: SourceDocument[];
  routeDecision?: RouteDecision;
  fallbackActivated?: boolean;
  metadata?: RunMetadata;
  timestamp: Date;
}

export interface Conversation {
  id: string;
  title: string;
  messages: ChatMessage[];
  createdAt: Date;
  updatedAt: Date;
}

export interface IngestionResult {
  status: string;
  filename?: string;
  source?: string;
  chunkCount: number;
}

export interface UrlIngestionResult {
  status: string;
  url: string;
  title: string;
  chunkCount: number;
}
