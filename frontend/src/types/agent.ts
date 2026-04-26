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
  archived?: boolean;
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
  archived: boolean;
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

export interface ConversationShare {
  token: string;
  conversationId: string;
  expiresAt: string | null;
  createdAt: string;
}

// ── Scheduled messages ────────────────────────────────────────────────────────

export interface ScheduledMessage {
  id: number;
  conversationId: string;
  ownerEmail: string;
  message: string;
  cronExpr: string;
  topK: number;
  useKnowledgeBase: boolean;
  useWebFetch: boolean;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateScheduleRequest {
  conversationId: string;
  message: string;
  cronMinute: string;
  cronHour: string;
  cronDay: string;
  cronMonth: string;
  cronWeekday: string;
  topK: number;
  useKnowledgeBase: boolean;
  useWebFetch: boolean;
}

export interface UpdateScheduleRequest {
  message?: string;
  cronMinute?: string;
  cronHour?: string;
  cronDay?: string;
  cronMonth?: string;
  cronWeekday?: string;
  topK?: number;
  useKnowledgeBase?: boolean;
  useWebFetch?: boolean;
  enabled?: boolean;
}

export interface WebFetchWhitelistEntry {
  id: number;
  domain: string;
  addedBy: string | null;
  createdAt: string;
}

// ── Workflow engine types ──────────────────────────────────────────────────

export type AgentPattern = "ORCHESTRATOR" | "TEAM";
export type TeamExecMode = "PARALLEL" | "SEQUENTIAL";
export type AgentRole    = "MAIN" | "SUB" | "PEER";
export type RunStatus    = "PENDING" | "RUNNING" | "DONE" | "FAILED";
export type LogType      = "TOOL_CALL" | "TOOL_RESULT" | "LLM_RESPONSE" | "DELEGATION" | "ERROR" | "SYSTEM";

export const SANDBOX_TOOLS = ["BASH", "CURL", "GIT", "GREP", "PYTHON", "NODE"] as const;
export type SandboxTool = typeof SANDBOX_TOOLS[number];

export interface Workflow {
  id: string;
  name: string;
  description: string | null;
  ownerEmail: string | null;
  agentPattern: AgentPattern;
  teamExecMode: TeamExecMode | null;
  createdAt: string;
  updatedAt: string;
}

export interface WorkflowAgent {
  id: number;
  workflowId: string;
  role: AgentRole;
  name: string;
  systemPrompt: string | null;
  toolsJson: string;       // JSON array string, parse client-side
  orderIndex: number;
  posX: number;
  posY: number;
  createdAt: string;
}

export interface WorkflowRun {
  id: string;
  workflowId: string;
  ownerEmail: string | null;
  userInput: string;
  status: RunStatus;
  sandboxContainer: string | null;
  finalOutput: string | null;
  startedAt: string;
  finishedAt: string | null;
}

export interface WorkflowRunLog {
  id: number;
  runId: string;
  agentId: number | null;
  agentName: string | null;
  logType: LogType;
  content: string;
  createdAt: string;
}

export interface WorkflowRunEvent {
  agentName: string;
  logType: LogType;
  content: string;
  createdAt: string;
}

export interface WorkflowDoneEvent {
  status: RunStatus;
  output: string;
}

// ── Skills ────────────────────────────────────────────────────────────────────

export interface Skill {
  id: string;
  name: string;
  fileName: string;
  fileType: "txt" | "md" | "zip";
  size: number;
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
