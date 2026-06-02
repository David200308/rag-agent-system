import SwiftUI

struct ShareConversationView: View {
    let conversationId: String
    @Environment(\.dismiss) private var dismiss

    @State private var share: ConversationShare?
    @State private var isLoading = false
    @State private var isCreating = false
    @State private var isRevoking = false
    @State private var error: String?
    @State private var copied = false

    // Create options
    @State private var shareMode: ShareMode = .readOnly
    @State private var accessType: ShareAccessType = .everyone
    @State private var expireDays: Int? = 7
    @State private var emailInput = ""
    @State private var whitelist: [String] = []

    private let service = AgentService.shared
    private let expiryOptions: [(label: String, value: Int?)] = [
        ("1 day", 1), ("7 days", 7), ("30 days", 30), ("Never", nil)
    ]

    var shareURL: String {
        let base = APIClient.shared.webFrontendURL
        guard let token = share?.token else { return "" }
        return "\(base)/share/\(token)"
    }

    var body: some View {
        NavigationStack {
            Group {
                if isLoading {
                    ProgressView()
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    ScrollView {
                        VStack(spacing: 20) {
                            if let share {
                                activeShareSection(share: share)
                            } else {
                                createShareSection
                            }
                        }
                        .padding()
                    }
                }
            }
            .navigationTitle("Share Conversation")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
            .task { await loadShare() }
            .alert("Error", isPresented: .constant(error != nil)) {
                Button("OK") { error = nil }
            } message: {
                Text(error ?? "")
            }
        }
    }

    // MARK: – Active share

    private func activeShareSection(share: ConversationShare) -> some View {
        VStack(spacing: 16) {
            // Status badge
            HStack {
                Image(systemName: "link.circle.fill")
                    .foregroundStyle(.green)
                Text("Active share link")
                    .font(.headline)
                Spacer()
            }

            // Share URL
            VStack(alignment: .leading, spacing: 6) {
                Text("Link")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                HStack {
                    Text(shareURL)
                        .font(.caption.monospaced())
                        .lineLimit(2)
                        .foregroundStyle(Color.accentColor)
                    Spacer()
                    Button {
                        UIPasteboard.general.string = shareURL
                        copied = true
                        DispatchQueue.main.asyncAfter(deadline: .now() + 2) { copied = false }
                    } label: {
                        Image(systemName: copied ? "checkmark" : "doc.on.doc")
                    }
                }
                .padding()
                .background(Color(.systemGray6))
                .clipShape(RoundedRectangle(cornerRadius: 10))
            }

            // Share info
            Grid(alignment: .leading, horizontalSpacing: 12, verticalSpacing: 8) {
                GridRow {
                    Text("Mode").font(.caption).foregroundStyle(.secondary)
                    Text(share.shareMode.displayName).font(.caption)
                }
                GridRow {
                    Text("Access").font(.caption).foregroundStyle(.secondary)
                    Text(share.accessType.displayName).font(.caption)
                }
                GridRow {
                    Text("Expires").font(.caption).foregroundStyle(.secondary)
                    Text(formatExpiry(share.expiresAt)).font(.caption)
                }
                if !share.whitelist.isEmpty {
                    GridRow {
                        Text("Whitelist").font(.caption).foregroundStyle(.secondary)
                        Text(share.whitelist.joined(separator: ", ")).font(.caption)
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding()
            .background(Color(.systemGray6))
            .clipShape(RoundedRectangle(cornerRadius: 10))

            // Share via system sheet
            ShareLink(item: shareURL) {
                Label("Share Link…", systemImage: "square.and.arrow.up")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)

            // Revoke
            Button(role: .destructive) {
                Task { await revokeShare() }
            } label: {
                Label(
                    isRevoking ? "Revoking…" : "Revoke Share",
                    systemImage: "trash"
                )
                .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
            .disabled(isRevoking)
        }
    }

    // MARK: – Create share

    private var createShareSection: some View {
        VStack(spacing: 16) {
            Text("No active share link")
                .font(.subheadline)
                .foregroundStyle(.secondary)

            // Mode picker
            VStack(alignment: .leading, spacing: 8) {
                Text("Mode")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Picker("Share Mode", selection: $shareMode) {
                    ForEach(ShareMode.allCases, id: \.self) { mode in
                        Text(mode.displayName).tag(mode)
                    }
                }
                .pickerStyle(.segmented)
            }

            // Access type
            VStack(alignment: .leading, spacing: 8) {
                Text("Who can access")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Picker("Access", selection: $accessType) {
                    ForEach(ShareAccessType.allCases, id: \.self) { type in
                        Text(type.displayName).tag(type)
                    }
                }
                .pickerStyle(.segmented)
            }

            // Whitelist emails (if WHITELIST selected)
            if accessType == .whitelist {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Allowed Emails")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    HStack {
                        TextField("user@example.com", text: $emailInput)
                            .keyboardType(.emailAddress)
                            .autocapitalization(.none)
                            .textFieldStyle(.roundedBorder)
                        Button("Add") {
                            let email = emailInput.trimmingCharacters(in: .whitespaces)
                            if !email.isEmpty && !whitelist.contains(email) {
                                whitelist.append(email)
                            }
                            emailInput = ""
                        }
                        .disabled(emailInput.trimmingCharacters(in: .whitespaces).isEmpty)
                    }
                    ForEach(whitelist, id: \.self) { email in
                        HStack {
                            Text(email).font(.caption)
                            Spacer()
                            Button {
                                whitelist.removeAll { $0 == email }
                            } label: {
                                Image(systemName: "xmark.circle.fill")
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                }
            }

            // Expiry picker
            VStack(alignment: .leading, spacing: 8) {
                Text("Expires")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Picker("Expiry", selection: $expireDays) {
                    ForEach(expiryOptions, id: \.label) { option in
                        Text(option.label).tag(option.value)
                    }
                }
                .pickerStyle(.segmented)
            }

            Button {
                Task { await createShare() }
            } label: {
                Label(
                    isCreating ? "Creating…" : "Create Share Link",
                    systemImage: "link.badge.plus"
                )
                .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .disabled(isCreating)
        }
    }

    // MARK: – Helpers

    private func loadShare() async {
        isLoading = true
        do {
            share = try await service.getShare(conversationId: conversationId)
        } catch {
            self.error = error.localizedDescription
        }
        isLoading = false
    }

    private func createShare() async {
        isCreating = true
        error = nil
        do {
            share = try await service.createShare(
                conversationId: conversationId,
                expireDays: expireDays,
                shareMode: shareMode,
                accessType: accessType,
                whitelist: whitelist
            )
        } catch {
            self.error = error.localizedDescription
        }
        isCreating = false
    }

    private func revokeShare() async {
        isRevoking = true
        error = nil
        do {
            try await service.revokeShare(conversationId: conversationId)
            share = nil
        } catch {
            self.error = error.localizedDescription
        }
        isRevoking = false
    }

    private func formatExpiry(_ iso: String?) -> String {
        guard let iso else { return "Never" }
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        guard let date = formatter.date(from: iso) else {
            let f2 = ISO8601DateFormatter()
            guard let d2 = f2.date(from: iso) else { return iso }
            let diff = d2.timeIntervalSinceNow
            if diff <= 0 { return "Expired" }
            let days = Int(diff / 86400)
            return "in \(days) day\(days != 1 ? "s" : "")"
        }
        let diff = date.timeIntervalSinceNow
        if diff <= 0 { return "Expired" }
        let days = Int(diff / 86400)
        return "in \(days) day\(days != 1 ? "s" : "")"
    }
}
