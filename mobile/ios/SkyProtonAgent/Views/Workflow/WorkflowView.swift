import SwiftUI

struct WorkflowView: View {
    @State private var workflows: [Workflow] = []
    @State private var isLoading = false
    @State private var selectedWorkflow: Workflow?
    @State private var error: String?

    private let service = AgentService.shared

    var body: some View {
        NavigationStack {
            Group {
                if isLoading && workflows.isEmpty {
                    ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if workflows.isEmpty {
                    emptyState
                } else {
                    workflowList
                }
            }
            .background(Color(.secondarySystemBackground))
            .navigationTitle("Workflows")
            .task { await load() }
            .refreshable { await load() }
            .navigationDestination(item: $selectedWorkflow) { wf in
                WorkflowDetailView(workflow: wf)
            }
            .alert("Error", isPresented: .constant(error != nil)) {
                Button("OK") { error = nil }
            } message: { Text(error ?? "") }
        }
    }

    private var workflowList: some View {
        List {
            ForEach(workflows) { wf in
                Button { selectedWorkflow = wf } label: { WorkflowRow(workflow: wf) }
                    .buttonStyle(.plain)
                    .swipeActions {
                        Button(role: .destructive) { Task { await delete(wf) } } label: {
                            Label("Delete", systemImage: "trash")
                        }
                    }
            }
        }
        .listStyle(.insetGrouped)
    }

    private var emptyState: some View {
        VStack(spacing: 16) {
            Image(systemName: "flowchart")
                .font(.system(size: 44)).foregroundStyle(.tertiary)
            Text("No workflows").font(.headline).foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func load() async {
        isLoading = true
        do { workflows = try await service.listWorkflows() } catch { self.error = error.localizedDescription }
        isLoading = false
    }

    private func delete(_ wf: Workflow) async {
        do {
            try await service.deleteWorkflow(id: wf.id)
            workflows.removeAll { $0.id == wf.id }
        } catch { self.error = error.localizedDescription }
    }
}

// MARK: – Workflow row

struct WorkflowRow: View {
    let workflow: Workflow

    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text(workflow.name)
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(Color(.label))
                HStack(spacing: 6) {
                    PatternBadge(pattern: workflow.agentPattern)
                    if let mode = workflow.teamExecMode {
                        Text(mode)
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            Spacer()
            Image(systemName: "chevron.right")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(Color(.tertiaryLabel))
        }
        .padding(.vertical, 4)
    }
}

struct PatternBadge: View {
    let pattern: String
    var body: some View {
        Text(pattern == "TEAM" ? "Team" : "Orchestrator")
            .font(.caption2.weight(.semibold))
            .padding(.horizontal, 6).padding(.vertical, 2)
            .background(pattern == "TEAM" ? Color.purple.opacity(0.12) : Color.blue.opacity(0.12))
            .foregroundStyle(pattern == "TEAM" ? Color.purple : Color.blue)
            .clipShape(Capsule())
    }
}

// MARK: – Workflow detail

struct WorkflowDetailView: View {
    let workflow: Workflow
    @State private var runs: [WorkflowRun] = []
    @State private var isLoading = false
    @State private var inputText = ""
    @State private var isRunning = false
    @State private var error: String?
    @State private var selectedRun: WorkflowRun?

    private let service = AgentService.shared

    var body: some View {
        List {
            Section {
                HStack(spacing: 6) {
                    PatternBadge(pattern: workflow.agentPattern)
                    if let mode = workflow.teamExecMode { Text(mode).font(.caption).foregroundStyle(.secondary) }
                    if let model = workflow.selectedModel { Text(model).font(.caption).foregroundStyle(Color.accentColor) }
                }
            } header: { Text("Configuration") }

            Section {
                TextField("Enter input…", text: $inputText, axis: .vertical)
                    .lineLimit(3...6)
                Button {
                    Task { await run() }
                } label: {
                    Label(isRunning ? "Running…" : "Run Workflow", systemImage: "play.fill")
                }
                .disabled(inputText.trimmingCharacters(in: .whitespaces).isEmpty || isRunning)
            } header: { Text("Run") }

            Section {
                if isLoading {
                    ProgressView()
                } else if runs.isEmpty {
                    Text("No runs yet").foregroundStyle(.secondary).font(.subheadline)
                } else {
                    ForEach(runs) { run in
                        Button { selectedRun = run } label: {
                            WorkflowRunRow(run: run)
                        }
                        .buttonStyle(.plain)
                    }
                }
            } header: { Text("Run History") }
        }
        .listStyle(.insetGrouped)
        .navigationTitle(workflow.name)
        .navigationBarTitleDisplayMode(.inline)
        .task { await loadRuns() }
        .refreshable { await loadRuns() }
        .sheet(item: $selectedRun) { run in WorkflowRunDetailSheet(run: run) }
        .alert("Error", isPresented: .constant(error != nil)) {
            Button("OK") { error = nil }
        } message: { Text(error ?? "") }
    }

    private func loadRuns() async {
        isLoading = true
        do { runs = try await service.listWorkflowRuns(id: workflow.id) }
        catch { self.error = error.localizedDescription }
        isLoading = false
    }

    private func run() async {
        isRunning = true
        do {
            _ = try await service.runWorkflow(id: workflow.id, input: inputText)
            inputText = ""
            await loadRuns()
        } catch { self.error = error.localizedDescription }
        isRunning = false
    }
}

struct WorkflowRunRow: View {
    let run: WorkflowRun

    private var statusColor: Color {
        switch run.status {
        case "DONE": return .green
        case "FAILED": return .red
        case "RUNNING": return .orange
        default: return .secondary
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(run.status)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(statusColor)
                Spacer()
                Text(shortDate(run.startedAt))
                    .font(.caption2).foregroundStyle(.secondary)
            }
            Text(run.userInput)
                .font(.subheadline).lineLimit(2).foregroundStyle(Color(.label))
            if let output = run.finalOutput, !output.isEmpty {
                Text(output)
                    .font(.caption).foregroundStyle(.secondary).lineLimit(3)
            }
        }
        .padding(.vertical, 2)
    }

    private func shortDate(_ iso: String) -> String {
        let f = ISO8601DateFormatter(); f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        guard let d = f.date(from: iso) else { return "" }
        return d.formatted(.dateTime.month(.abbreviated).day().hour().minute())
    }
}

// MARK: – Run detail sheet

struct WorkflowRunDetailSheet: View {
    let run: WorkflowRun
    @Environment(\.dismiss) private var dismiss
    @State private var copied = false
    @State private var htmlPreviewSrc: String? = nil

    private var statusColor: Color {
        switch run.status {
        case "DONE": return .green
        case "FAILED": return .red
        case "RUNNING": return .orange
        default: return Color(.secondaryLabel)
        }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    // Status + time
                    HStack {
                        Text(run.status)
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(statusColor)
                            .padding(.horizontal, 10).padding(.vertical, 4)
                            .background(statusColor.opacity(0.12))
                            .clipShape(Capsule())
                        Spacer()
                        if let fin = run.finishedAt {
                            Text(shortDate(fin)).font(.caption).foregroundStyle(.secondary)
                        }
                    }

                    // Input
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Input").font(.caption.weight(.semibold)).foregroundStyle(.secondary)
                        Text(run.userInput)
                            .font(.subheadline)
                            .padding(12)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(Color(.secondarySystemBackground))
                            .clipShape(RoundedRectangle(cornerRadius: 10))
                    }

                    // Output
                    if let output = run.finalOutput, !output.isEmpty {
                        VStack(alignment: .leading, spacing: 6) {
                            HStack {
                                Text("Output").font(.caption.weight(.semibold)).foregroundStyle(.secondary)
                                Spacer()
                                Button {
                                    UIPasteboard.general.string = output
                                    copied = true
                                    DispatchQueue.main.asyncAfter(deadline: .now() + 2) { copied = false }
                                } label: {
                                    Label(copied ? "Copied" : "Copy", systemImage: copied ? "checkmark" : "doc.on.doc")
                                        .font(.caption)
                                }
                                .buttonStyle(.bordered)
                                .controlSize(.small)
                            }
                            MarkdownBubble(content: output, onHtmlPreview: { src in
                                htmlPreviewSrc = src
                            })
                            .padding(12)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(Color(.secondarySystemBackground))
                            .clipShape(RoundedRectangle(cornerRadius: 10))
                        }
                    } else if run.status == "RUNNING" {
                        HStack(spacing: 8) {
                            ProgressView()
                            Text("Running…").font(.subheadline).foregroundStyle(.secondary)
                        }
                    }
                }
                .padding(16)
            }
            .navigationTitle("Run Details")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
            .sheet(isPresented: Binding(
                get: { htmlPreviewSrc != nil },
                set: { if !$0 { htmlPreviewSrc = nil } }
            )) {
                if let src = htmlPreviewSrc {
                    HtmlPreviewSheet(source: src)
                }
            }
        }
    }

    private func shortDate(_ iso: String) -> String {
        let f = ISO8601DateFormatter(); f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        guard let d = f.date(from: iso) else { return "" }
        return d.formatted(.dateTime.month(.abbreviated).day().hour().minute())
    }
}
