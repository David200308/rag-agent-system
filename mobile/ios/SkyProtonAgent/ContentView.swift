import SwiftUI

struct ContentView: View {
    @StateObject private var authVM = AuthViewModel()

    var body: some View {
        Group {
            if authVM.isAuthenticated {
                MainTabView(authVM: authVM)
            } else {
                LoginView(vm: authVM)
            }
        }
        .task {
            await authVM.checkAuth()
        }
    }
}
