import Foundation

struct AgentRequest: Codable {
    let query: String
    let conversationId: String?
    let topK: Int?
    let useKnowledgeBase: Bool?
    let useWebFetch: Bool?
    let skillIds: [String]?
}

struct AgentResponse: Codable {
    let answer: String
    let sources: [SourceDocument]
    let routeDecision: RouteDecision?
    let fallbackActivated: Bool
    let fallbackReason: String?
    let metadata: RunMetadata?
}

struct SourceDocument: Codable, Identifiable {
    let id: String
    let content: String
    let source: String
    let score: Double
    let category: String?
}

struct RouteDecision: Codable {
    let route: String
    let reasoning: String
    let confidence: Double
}

struct RunMetadata: Codable {
    let runId: String
    let durationMs: Int?
    let documentsRetrieved: Int
    let modelUsed: String?
    let conversationId: String?
}

struct ModelConfig: Codable, Identifiable, Hashable {
    var id: String { displayName }
    let displayName: String
    let platform: String
    let modelId: String
    let enabled: Bool?
}

struct KnowledgeSource: Codable, Identifiable {
    let id: Int?
    let source: String
    let label: String?
    let category: String?
    let chunkCount: Int
    let ownerEmail: String?
    let createdAt: String?

    var displayLabel: String { label ?? source }
}

struct Skill: Codable, Identifiable, Hashable {
    let id: String
    let name: String
    let fileName: String
    let fileType: String
    let size: Int
    let createdAt: String?
}

enum ShareMode: String, Codable, CaseIterable {
    case readOnly    = "READ_ONLY"
    case interactive = "INTERACTIVE"

    var displayName: String {
        switch self {
        case .readOnly:    return "Read Only"
        case .interactive: return "Interactive"
        }
    }
}

enum ShareAccessType: String, Codable, CaseIterable {
    case everyone  = "EVERYONE"
    case whitelist = "WHITELIST"

    var displayName: String {
        switch self {
        case .everyone:  return "Everyone"
        case .whitelist: return "Whitelist"
        }
    }
}

struct ConversationShare: Codable {
    let token: String
    let conversationId: String
    let expiresAt: String?
    let createdAt: String
    let shareMode: ShareMode
    let accessType: ShareAccessType
    let whitelist: [String]
}

struct WebFetchWhitelistEntry: Codable, Identifiable {
    let id: Int
    let domain: String
    let addedBy: String?
    let createdAt: String?
}

struct UserPreferences: Codable {
    let timezone: String?
    let selectedModel: String?
}

struct IngestionResult: Codable {
    let status: String
    let filename: String?
    let source: String?
    let chunkCount: Int
}

struct UrlIngestionResult: Codable {
    let status: String
    let url: String
    let title: String
    let chunkCount: Int
}

struct Workflow: Codable, Identifiable, Hashable {
    let id: String
    let name: String
    let description: String?
    let agentPattern: String
    let teamExecMode: String?
    let selectedModel: String?
    let createdAt: String
    let updatedAt: String
}

struct WorkflowRun: Codable, Identifiable, Hashable {
    let id: String
    let workflowId: String
    let userInput: String
    let status: String
    let finalOutput: String?
    let startedAt: String
    let finishedAt: String?
}
