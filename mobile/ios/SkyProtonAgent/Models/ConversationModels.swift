import Foundation

struct Conversation: Codable, Identifiable, Hashable {
    let id: String
    let title: String?
    let ownerEmail: String?
    let createdAt: String?
    let updatedAt: String?
    let archived: Bool?
    let selectedModel: String?

    var displayTitle: String {
        if let t = title, !t.isEmpty { return t }
        return "Conversation \(id.prefix(8))"
    }
}

struct ConversationMessage: Codable, Identifiable {
    let id: Int?
    let conversationId: String
    let role: String        // "user" | "assistant"
    let content: String
    let createdAt: String?
    let runId: String?

    var isUser: Bool { role == "user" }
}
