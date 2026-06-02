import SwiftUI

struct MainTabView: View {
    @ObservedObject var authVM: AuthViewModel

    var body: some View {
        TabView {
            ChatTabRoot()
                .tabItem { Image(systemName: "bubble.left.and.bubble.right.fill") }

            WorkflowView()
                .tabItem { Image(systemName: "flowchart.fill") }

            FinancialView()
                .tabItem { Image(systemName: "chart.bar.fill") }

            SettingsView(authVM: authVM)
                .tabItem { Image(systemName: "gearshape.fill") }
        }
    }
}
