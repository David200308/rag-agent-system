import SwiftUI

enum AppTab {
    case finance, travel
}

struct MainTabView: View {
    @ObservedObject var authVM: AuthViewModel
    @State private var selectedTab: AppTab = .finance
    @State private var showSettings = false

    var body: some View {
        ZStack(alignment: .bottom) {
            Theme.background.ignoresSafeArea()

            Group {
                switch selectedTab {
                case .finance: FinancialView(onProfileTap: { showSettings = true })
                case .travel:  TravelListView()
                }
            }

            FloatingTabBar(selected: $selectedTab)
                .padding(.horizontal, 20)
                .padding(.bottom, 8)
        }
        .sheet(isPresented: $showSettings) {
            SettingsView(authVM: authVM)
        }
    }
}

private struct FloatingTabBar: View {
    @Binding var selected: AppTab

    var body: some View {
        HStack(spacing: 0) {
            tabItem(.finance, label: "Finance") {
                Image(systemName: "chart.bar.fill")
            }
            tabItem(.travel, label: "Travel") {
                Image(systemName: "safari.fill")
            }
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 12)
        .background(Theme.graphite)
        .clipShape(RoundedRectangle(cornerRadius: 26, style: .continuous))
        .shadow(color: .black.opacity(0.35), radius: 20, y: 10)
    }

    private func tabItem<Icon: View>(_ tab: AppTab, label: String, @ViewBuilder icon: () -> Icon) -> some View {
        let isActive = selected == tab
        return Button {
            selected = tab
        } label: {
            VStack(spacing: 4) {
                icon()
                    .font(.system(size: 20))
                Text(label)
                    .font(.system(size: 11, weight: .semibold))
            }
            .foregroundStyle(isActive ? Color.white : Color.white.opacity(0.5))
            .frame(maxWidth: .infinity)
        }
        .buttonStyle(.plain)
    }
}

/// Bottom inset so scrollable content clears the floating tab bar.
struct TabBarSafeArea: ViewModifier {
    func body(content: Content) -> some View {
        content.safeAreaInset(edge: .bottom) {
            Color.clear.frame(height: 76)
        }
    }
}

extension View {
    func tabBarSafeArea() -> some View { modifier(TabBarSafeArea()) }
}
