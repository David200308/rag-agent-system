import SwiftUI

struct SettingsView: View {
    @ObservedObject var authVM: AuthViewModel
    @State private var showLogoutConfirm = false
    @State private var showTimezoneSheet = false

    @State private var availableModels: [ModelConfig] = []
    @State private var selectedModel: String?

    @State private var timezone: String = TimeZone.current.identifier

    @State private var whitelist: [WebFetchWhitelistEntry] = []
    @State private var newDomain = ""
    @State private var addingDomain = false

    private let service = AgentService.shared

    var body: some View {
        NavigationStack {
            List {
                // MARK: Account
                Section {
                    HStack(spacing: 14) {
                        ZStack {
                            Circle()
                                .fill(Color(.secondarySystemBackground))
                                .frame(width: 48, height: 48)
                            Text(initials)
                                .font(.system(size: 18, weight: .semibold))
                                .foregroundStyle(Color(.secondaryLabel))
                        }
                        VStack(alignment: .leading, spacing: 2) {
                            Text(authVM.currentUserEmail ?? "—")
                                .font(.system(size: 15, weight: .medium))
                            Text("Signed in")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                    .padding(.vertical, 4)

                    Button(role: .destructive) {
                        showLogoutConfirm = true
                    } label: {
                        Label("Sign Out", systemImage: "rectangle.portrait.and.arrow.right")
                    }
                    .confirmationDialog("Sign out of your account?",
                                        isPresented: $showLogoutConfirm,
                                        titleVisibility: .visible) {
                        Button("Sign Out", role: .destructive) { authVM.logout() }
                    }
                } header: {
                    Text("Account")
                }

                // MARK: Preferences
                Section {
                    if availableModels.isEmpty {
                        LabeledContent("Default Model") {
                            Text("None configured")
                                .foregroundStyle(.secondary)
                        }
                    } else {
                        Picker("Default Model", selection: $selectedModel) {
                            Text("System default").tag(String?.none)
                            ForEach(availableModels) { m in
                                Text(m.displayName).tag(Optional(m.displayName))
                            }
                        }
                        .onChange(of: selectedModel) { _, v in
                            Task { try? await service.setUserDefaultModel(v) }
                        }
                    }

                    Button {
                        showTimezoneSheet = true
                    } label: {
                        LabeledContent("Timezone") {
                            Text(timezone.replacingOccurrences(of: "_", with: " "))
                                .foregroundStyle(.secondary)
                                .lineLimit(1)
                        }
                    }
                    .foregroundStyle(Color(.label))
                } header: {
                    Text("Preferences")
                }

                // MARK: Web Fetch Whitelist
                Section {
                    HStack(spacing: 10) {
                        Image(systemName: "globe")
                            .foregroundStyle(.secondary)
                            .frame(width: 20)
                        TextField("Add domain (e.g. example.com)", text: $newDomain)
                            .autocorrectionDisabled()
                            .textInputAutocapitalization(.never)
                            .onSubmit { Task { await addDomain() } }
                        if addingDomain {
                            ProgressView().scaleEffect(0.8)
                        } else {
                            Button {
                                Task { await addDomain() }
                            } label: {
                                Image(systemName: "plus.circle.fill")
                                    .foregroundStyle(newDomain.trimmingCharacters(in: .whitespaces).isEmpty
                                                     ? Color(.tertiaryLabel) : Color.accentColor)
                            }
                            .buttonStyle(.plain)
                            .disabled(newDomain.trimmingCharacters(in: .whitespaces).isEmpty)
                        }
                    }

                    if whitelist.isEmpty {
                        Text("No domains added yet")
                            .foregroundStyle(.secondary)
                            .font(.subheadline)
                    } else {
                        ForEach(whitelist) { entry in
                            HStack {
                                Text(entry.domain)
                                    .font(.system(.subheadline, design: .monospaced))
                                Spacer()
                                if let by = entry.addedBy {
                                    Text(by)
                                        .font(.caption2)
                                        .foregroundStyle(Color(.tertiaryLabel))
                                }
                            }
                            .swipeActions {
                                Button(role: .destructive) {
                                    Task { await removeDomain(entry.domain) }
                                } label: {
                                    Label("Remove", systemImage: "trash")
                                }
                            }
                        }
                    }
                } header: {
                    Text("Web Fetch Whitelist")
                } footer: {
                    Text("Subdomains are automatically included.")
                }
            }
            .listStyle(.insetGrouped)
            .navigationTitle("Settings")
            .task { await loadSettings() }
            .sheet(isPresented: $showTimezoneSheet) {
                TimezonePickerSheet(selected: $timezone) {
                    Task { try? await service.setUserTimezone(timezone) }
                }
            }
        }
    }

    private var initials: String {
        guard let email = authVM.currentUserEmail,
              let first = email.first else { return "?" }
        return String(first).uppercased()
    }

    private func loadSettings() async {
        do { availableModels = (try await service.listModels()).filter { $0.enabled == true } } catch {}
        do {
            let p = try await service.getUserPreferences()
            if let tz = p.timezone { timezone = tz }
            selectedModel = p.selectedModel
        } catch {}
        do { whitelist = try await service.listWebFetchWhitelist() } catch {}
    }

    private func addDomain() async {
        let d = newDomain.trimmingCharacters(in: .whitespaces)
        guard !d.isEmpty else { return }
        addingDomain = true
        do {
            let entry = try await service.addWebFetchDomain(d)
            whitelist.append(entry)
            whitelist.sort { $0.domain < $1.domain }
            newDomain = ""
        } catch {}
        addingDomain = false
    }

    private func removeDomain(_ domain: String) async {
        do {
            try await service.removeWebFetchDomain(domain)
            whitelist.removeAll { $0.domain == domain }
        } catch {}
    }
}

// MARK: – Timezone picker sheet

struct TimezonePickerSheet: View {
    @Binding var selected: String
    let onSelect: () -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var search = ""

    private var filtered: [String] {
        let all = TimeZone.knownTimeZoneIdentifiers
        guard !search.isEmpty else { return all }
        return all.filter { $0.localizedCaseInsensitiveContains(search) }
    }

    var body: some View {
        NavigationStack {
            List(filtered, id: \.self) { tz in
                Button {
                    selected = tz
                    onSelect()
                    dismiss()
                } label: {
                    HStack {
                        Text(tz.replacingOccurrences(of: "_", with: " "))
                            .foregroundStyle(Color(.label))
                        Spacer()
                        if tz == selected {
                            Image(systemName: "checkmark")
                                .foregroundStyle(Color.accentColor)
                                .font(.system(size: 13, weight: .semibold))
                        }
                    }
                }
                .buttonStyle(.plain)
            }
            .searchable(text: $search, prompt: "Search timezones")
            .navigationTitle("Timezone")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
    }
}
