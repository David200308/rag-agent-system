import SwiftUI

@main
struct SkyProtonAgentApp: App {
    @AppStorage(appearanceModeKey) private var appearanceMode: AppearanceMode = .system

    var body: some Scene {
        WindowGroup {
            ContentView()
                .preferredColorScheme(appearanceMode.colorScheme)
        }
    }
}
