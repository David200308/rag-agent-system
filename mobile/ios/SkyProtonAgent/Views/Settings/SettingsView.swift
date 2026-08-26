import SwiftUI

struct SettingsView: View {
    @ObservedObject var authVM: AuthViewModel
    @State private var showLogoutConfirm = false

    var body: some View {
        NavigationStack {
            List {
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
            }
            .listStyle(.insetGrouped)
            .navigationTitle("Settings")
        }
    }

    private var initials: String {
        guard let email = authVM.currentUserEmail,
              let first = email.first else { return "?" }
        return String(first).uppercased()
    }
}
