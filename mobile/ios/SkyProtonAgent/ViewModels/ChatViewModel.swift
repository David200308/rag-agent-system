import Foundation
import SwiftUI

@MainActor
final class ChatViewModel: ObservableObject {
    @Published var messages: [ChatMessage] = []
    @Published var inputText: String = ""
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published var currentConversationId: String?
    @Published var lastSources: [SourceDocument] = []
    @Published var lastModelUsed: String?
    @Published var useKnowledgeBase: Bool = true
    @Published var useWebFetch: Bool = true
    @Published var selectedModel: String?
    @Published var availableModels: [ModelConfig] = []
    @Published var availableSkills: [Skill] = []
    @Published var selectedSkillIds: Set<String> = []

    private let service = AgentService.shared

    struct ChatMessage: Identifiable {
        let id = UUID()
        let role: String
        let content: String
        var isUser: Bool { role == "user" }
    }

    func loadModels() async {
        do {
            let models = try await service.listModels()
            availableModels = models.filter { $0.enabled == true }
        } catch {}
    }

    func loadSkills() async {
        do {
            availableSkills = try await service.listSkills()
        } catch {}
    }

    func send() async {
        let text = inputText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        inputText = ""
        errorMessage = nil

        messages.append(ChatMessage(role: "user", content: text))
        isLoading = true

        let req = AgentRequest(
            query: text,
            conversationId: currentConversationId,
            topK: 5,
            useKnowledgeBase: useKnowledgeBase,
            useWebFetch: useWebFetch,
            skillIds: selectedSkillIds.isEmpty ? nil : Array(selectedSkillIds)
        )

        do {
            let resp = try await service.query(req)
            messages.append(ChatMessage(role: "assistant", content: resp.answer))
            lastSources = resp.sources
            lastModelUsed = resp.metadata?.modelUsed
            if currentConversationId == nil, let convId = resp.metadata?.conversationId {
                currentConversationId = convId
                // Persist the model selection after conversation is created
                if let model = selectedModel {
                    try? await service.setConversationModel(conversationId: convId, displayName: model)
                }
            }
        } catch {
            errorMessage = error.localizedDescription
            messages.append(ChatMessage(role: "assistant", content: "Error: \(error.localizedDescription)"))
        }
        isLoading = false
    }

    func loadConversation(_ conversation: Conversation) async {
        isLoading = true
        errorMessage = nil
        do {
            let msgs = try await service.getMessages(conversationId: conversation.id)
            messages = msgs.map { ChatMessage(role: $0.role, content: $0.content) }
            currentConversationId = conversation.id
            selectedModel = conversation.selectedModel
        } catch {
            errorMessage = error.localizedDescription
        }
        isLoading = false
    }

    func setConversationModel(_ displayName: String?) async {
        selectedModel = displayName
        guard let convId = currentConversationId else { return }
        try? await service.setConversationModel(conversationId: convId, displayName: displayName)
    }

    func newConversation() {
        messages = []
        currentConversationId = nil
        lastSources = []
        lastModelUsed = nil
        errorMessage = nil
        selectedSkillIds = []
    }
}
