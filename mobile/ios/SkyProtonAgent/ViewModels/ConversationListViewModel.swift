import Foundation

@MainActor
final class ConversationListViewModel: ObservableObject {
    @Published var conversations: [Conversation] = []
    @Published var archivedConversations: [Conversation] = []
    @Published var isLoading = false
    @Published var errorMessage: String?

    private let service = AgentService.shared

    func load() async {
        isLoading = true
        errorMessage = nil
        do {
            conversations = try await service.listConversations()
        } catch {
            errorMessage = error.localizedDescription
        }
        isLoading = false
    }

    func loadArchived() async {
        isLoading = true
        errorMessage = nil
        do {
            archivedConversations = try await service.listArchivedConversations()
        } catch {
            errorMessage = error.localizedDescription
        }
        isLoading = false
    }

    func delete(id: String) async {
        do {
            try await service.deleteConversation(id: id)
            conversations.removeAll { $0.id == id }
            archivedConversations.removeAll { $0.id == id }
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func archive(id: String) async {
        do {
            try await service.archiveConversation(id: id)
            conversations.removeAll { $0.id == id }
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func unarchive(id: String) async {
        do {
            try await service.unarchiveConversation(id: id)
            archivedConversations.removeAll { $0.id == id }
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
