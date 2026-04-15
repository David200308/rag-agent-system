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
  conversationId?: string;
  fetchUrls?: string[];
  useKnowledgeBase?: boolean;
  useWebFetch?: boolean;
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
  conversationId?: string;
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
  /** UUID assigned by the Spring Boot backend — set after the first successful query. */
  backendConversationId?: string;
  title: string;
  messages: ChatMessage[];
  createdAt: Date;
  updatedAt: Date;
}

/** Mirror of Java KnowledgeSource entity returned by GET /knowledge */
export interface KnowledgeSourceEntry {
  id: number;
  source: string;
  label: string | null;
  category: string | null;
  chunkCount: number;
  ownerEmail: string | null;
  ingestedAt: string;
  shares: { id: number; sharedEmail: string }[];
}

/** Mirror of Java Conversation entity returned by GET /conversations */
export interface BackendConversation {
  id: string;
  userEmail: string | null;
  createdAt: string;
  updatedAt: string;
}

/** Mirror of Java ConversationMessage entity */
export interface BackendMessage {
  id: number;
  conversationId: string;
  role: "user" | "assistant";
  content: string;
  runId: string | null;
  createdAt: string;
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
