import SwiftUI

// MARK: – Chat tab root (manages sidebar + active conversation)

struct ChatTabRoot: View {
    @StateObject private var listVM = ConversationListViewModel()
    @State private var activeConversation: Conversation?
    @State private var showSidebar = false
    @State private var newChatId = UUID()

    var body: some View {
        NavigationStack {
            ChatView(
                conversation: activeConversation,
                onOpenSidebar: { showSidebar = true }
            )
            .id(activeConversation?.id ?? newChatId.uuidString)
        }
        .sheet(isPresented: $showSidebar) {
            ConversationSidebar(vm: listVM) { conv in
                activeConversation = conv
                showSidebar = false
            } onNewChat: {
                activeConversation = nil
                newChatId = UUID()
                showSidebar = false
            }
        }
    }
}

// MARK: – Sidebar sheet

struct ConversationSidebar: View {
    @ObservedObject var vm: ConversationListViewModel
    let onSelect: (Conversation) -> Void
    let onNewChat: () -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Group {
                if vm.isLoading && vm.conversations.isEmpty {
                    ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if vm.conversations.isEmpty {
                    emptyState
                } else {
                    conversationList
                }
            }
            .navigationTitle("Conversations")
            .navigationBarTitleDisplayMode(.large)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button { dismiss() } label: {
                        Image(systemName: "xmark")
                            .font(.system(size: 13, weight: .semibold))
                    }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: onNewChat) {
                        Image(systemName: "square.and.pencil")
                    }
                }
            }
            .task { await vm.load() }
            .refreshable { await vm.load() }
        }
    }

    private var conversationList: some View {
        List {
            ForEach(vm.conversations) { conv in
                Button { onSelect(conv) } label: {
                    SidebarConversationRow(conversation: conv)
                }
                .buttonStyle(.plain)
                .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                    Button(role: .destructive) { Task { await vm.delete(id: conv.id) } } label: {
                        Label("Delete", systemImage: "trash")
                    }
                    Button { Task { await vm.archive(id: conv.id) } } label: {
                        Label("Archive", systemImage: "archivebox")
                    }
                    .tint(.orange)
                }
            }
        }
        .listStyle(.plain)
    }

    private var emptyState: some View {
        VStack(spacing: 16) {
            Image(systemName: "bubble.left.and.bubble.right")
                .font(.system(size: 40)).foregroundStyle(.tertiary)
            Text("No conversations").font(.headline).foregroundStyle(.secondary)
            Button("New Chat", action: onNewChat).buttonStyle(.bordered)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

// MARK: – Sidebar row

struct SidebarConversationRow: View {
    let conversation: Conversation

    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(conversation.displayTitle)
                .font(.system(size: 15, weight: .medium))
                .lineLimit(1)
                .foregroundStyle(Color(.label))
            HStack(spacing: 6) {
                if let model = conversation.selectedModel {
                    Text(model).font(.caption).foregroundStyle(Color.accentColor)
                }
                if let date = conversation.updatedAt {
                    Text(relativeDate(date)).font(.caption).foregroundStyle(Color(.tertiaryLabel))
                }
            }
        }
        .padding(.vertical, 2)
    }

    private func relativeDate(_ iso: String) -> String {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        guard let d = f.date(from: iso) else { return "" }
        let r = RelativeDateTimeFormatter(); r.unitsStyle = .abbreviated
        return r.localizedString(for: d, relativeTo: .now)
    }
}
