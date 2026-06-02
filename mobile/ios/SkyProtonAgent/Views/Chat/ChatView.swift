import SwiftUI

struct ChatView: View {
    @StateObject private var vm = ChatViewModel()
    @State private var showSources = false
    @State private var showShare = false
    @State private var showSkillPicker = false
    let initialConversation: Conversation?
    let onOpenSidebar: (() -> Void)?

    init(conversation: Conversation? = nil, onOpenSidebar: (() -> Void)? = nil) {
        self.initialConversation = conversation
        self.onOpenSidebar = onOpenSidebar
    }

    var body: some View {
        VStack(spacing: 0) {
            messageList
            inputArea
        }
        .navigationTitle(initialConversation?.displayTitle ?? "New Chat")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar { navBarItems }
        .task {
            await vm.loadModels()
            await vm.loadSkills()
            if let conv = initialConversation {
                await vm.loadConversation(conv)
            }
        }
        .sheet(isPresented: $showSources) {
            SourcesSheet(sources: vm.lastSources, modelUsed: vm.lastModelUsed)
        }
        .sheet(isPresented: $showShare) {
            if let convId = vm.currentConversationId {
                ShareConversationView(conversationId: convId)
            }
        }
        .sheet(isPresented: $showSkillPicker) {
            SkillPickerSheet(skills: vm.availableSkills, selected: $vm.selectedSkillIds)
        }
    }

    // MARK: – Nav bar

    @ToolbarContentBuilder
    private var navBarItems: some ToolbarContent {
        ToolbarItem(placement: .navigationBarLeading) {
            if let onOpenSidebar {
                Button(action: onOpenSidebar) {
                    Image(systemName: "sidebar.left")
                        .font(.system(size: 16))
                }
            }
        }
        ToolbarItemGroup(placement: .navigationBarTrailing) {
            if vm.currentConversationId != nil && !vm.lastSources.isEmpty {
                Button { showSources = true } label: {
                    Label("\(vm.lastSources.count)", systemImage: "doc.text.magnifyingglass")
                        .font(.caption.weight(.medium))
                        .labelStyle(.titleAndIcon)
                }
                .buttonStyle(.bordered)
                .controlSize(.small)
            }
            if !vm.availableModels.isEmpty { modelMenu }
            if vm.currentConversationId != nil {
                Button { showShare = true } label: {
                    Image(systemName: "square.and.arrow.up")
                }
            }
        }
    }

    private var modelMenu: some View {
        Menu {
            Button {
                Task { await vm.setConversationModel(nil) }
            } label: {
                if vm.selectedModel == nil {
                    Label("Default", systemImage: "checkmark")
                } else {
                    Text("Default")
                }
            }
            Divider()
            ForEach(vm.availableModels) { model in
                Button {
                    Task { await vm.setConversationModel(model.displayName) }
                } label: {
                    if vm.selectedModel == model.displayName {
                        Label(model.displayName, systemImage: "checkmark")
                    } else {
                        Text(model.displayName)
                    }
                }
            }
        } label: {
            Image(systemName: "cpu")
                .foregroundStyle(vm.selectedModel != nil ? Color.accentColor : Color(.secondaryLabel))
        }
    }

    // MARK: – Message list

    private var messageList: some View {
        ScrollViewReader { proxy in
            ScrollView {
                if vm.messages.isEmpty {
                    emptyState
                } else {
                    LazyVStack(spacing: 4) {
                        ForEach(vm.messages) { msg in
                            MessageBubble(message: msg)
                                .id(msg.id)
                                .padding(.vertical, 2)
                        }
                        if vm.isLoading {
                            TypingIndicator()
                                .id("typing")
                                .padding(.vertical, 2)
                        }
                    }
                    .padding(.vertical, 16)
                }
            }
            .onChange(of: vm.messages.count) { _, _ in
                withAnimation(.easeOut(duration: 0.2)) {
                    if let last = vm.messages.last { proxy.scrollTo(last.id, anchor: .bottom) }
                }
            }
            .onChange(of: vm.isLoading) { _, loading in
                if loading { withAnimation { proxy.scrollTo("typing", anchor: .bottom) } }
            }
        }
    }

    private var emptyState: some View {
        VStack(spacing: 12) {
            Spacer()
            Image("Logo")
                .resizable()
                .scaledToFit()
                .frame(width: 80)
                .colorMultiply(.primary)
                .opacity(0.15)
            Text("Ask anything")
                .font(.headline)
                .foregroundStyle(.secondary)
            Spacer()
        }
        .frame(maxWidth: .infinity)
    }

    // MARK: – Input area

    private var inputArea: some View {
        VStack(spacing: 0) {
            Divider()
            VStack(spacing: 8) {
                // Toggles row
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        ToggleChip(icon: "books.vertical", label: "Knowledge",
                                   active: vm.useKnowledgeBase) { vm.useKnowledgeBase.toggle() }
                        ToggleChip(icon: "globe", label: "Web",
                                   active: vm.useWebFetch) { vm.useWebFetch.toggle() }
                        if !vm.availableSkills.isEmpty {
                            SkillsChip(count: vm.selectedSkillIds.count) { showSkillPicker = true }
                        }
                    }
                    .padding(.horizontal, 16)
                }

                // Text field + send button
                HStack(alignment: .bottom, spacing: 10) {
                    TextField("Message…", text: $vm.inputText, axis: .vertical)
                        .font(.subheadline)
                        .lineLimit(1...6)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 10)
                        .background(Color(.secondarySystemBackground))
                        .clipShape(RoundedRectangle(cornerRadius: 20))

                    Button {
                        Task { await vm.send() }
                    } label: {
                        Image(systemName: "arrow.up")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundStyle(.white)
                            .frame(width: 32, height: 32)
                            .background(vm.inputText.isEmpty ? Color(.tertiaryLabel) : Color(.label))
                            .clipShape(Circle())
                    }
                    .disabled(vm.inputText.isEmpty || vm.isLoading)
                    .animation(.easeInOut(duration: 0.15), value: vm.inputText.isEmpty)
                }
                .padding(.horizontal, 16)
            }
            .padding(.vertical, 10)
            .background(Color(.systemBackground))
        }
    }
}

// MARK: – Toggle chip

struct ToggleChip: View {
    let icon: String
    let label: String
    let active: Bool
    let onToggle: () -> Void

    var body: some View {
        Button(action: onToggle) {
            HStack(spacing: 4) {
                Image(systemName: icon)
                    .font(.system(size: 10, weight: .semibold))
                Text(label)
                    .font(.system(size: 12, weight: .medium))
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .foregroundStyle(active ? Color(.systemBackground) : Color(.secondaryLabel))
            .background(active ? Color(.label) : Color(.secondarySystemBackground))
            .clipShape(Capsule())
        }
        .buttonStyle(.plain)
    }
}

// MARK: – Skills chip

struct SkillsChip: View {
    let count: Int
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 4) {
                Image(systemName: "bolt.fill")
                    .font(.system(size: 10, weight: .semibold))
                Text(count > 0 ? "Skills (\(count))" : "Skills")
                    .font(.system(size: 12, weight: .medium))
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .foregroundStyle(count > 0 ? .orange : Color(.secondaryLabel))
            .background(count > 0 ? Color.orange.opacity(0.12) : Color(.secondarySystemBackground))
            .clipShape(Capsule())
        }
        .buttonStyle(.plain)
    }
}

// MARK: – Skill picker sheet

struct SkillPickerSheet: View {
    let skills: [Skill]
    @Binding var selected: Set<String>
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List(skills) { skill in
                Button {
                    if selected.contains(skill.id) { selected.remove(skill.id) }
                    else { selected.insert(skill.id) }
                } label: {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(skill.name).font(.headline).foregroundStyle(Color(.label))
                            Text(skill.fileName).font(.caption).foregroundStyle(.secondary)
                        }
                        Spacer()
                        Image(systemName: selected.contains(skill.id) ? "checkmark.circle.fill" : "circle")
                            .foregroundStyle(selected.contains(skill.id) ? .orange : Color(.tertiaryLabel))
                    }
                }
                .buttonStyle(.plain)
            }
            .navigationTitle("Attach Skills")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } }
                ToolbarItem(placement: .cancellationAction) { Button("Clear") { selected.removeAll() } }
            }
        }
    }
}

// MARK: – Sources sheet

struct SourcesSheet: View {
    let sources: [SourceDocument]
    let modelUsed: String?
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                if let model = modelUsed {
                    Section("Model") {
                        Label(model, systemImage: "cpu").font(.subheadline)
                    }
                }
                Section("Sources (\(sources.count))") {
                    ForEach(sources) { src in
                        VStack(alignment: .leading, spacing: 6) {
                            HStack {
                                Label(src.source, systemImage: "doc.text")
                                    .font(.caption.weight(.semibold)).lineLimit(1)
                                Spacer()
                                Text(String(format: "%.2f", src.score))
                                    .font(.caption2).foregroundStyle(.secondary)
                                    .padding(.horizontal, 6).padding(.vertical, 2)
                                    .background(Color(.secondarySystemBackground))
                                    .clipShape(Capsule())
                            }
                            Text(src.content)
                                .font(.caption).foregroundStyle(.secondary).lineLimit(4)
                        }
                        .padding(.vertical, 4)
                    }
                }
            }
            .navigationTitle("Response Details")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } }
            }
        }
    }
}
