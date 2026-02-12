import SwiftUI

@main
struct PaiSwitchApp: App {
    @StateObject private var viewModel = MainViewModel()

    init() {
        // Run migration on app startup
        MigrationManager.shared.migrateIfNeeded()
    }

    var body: some Scene {
        WindowGroup {
            ContentView(viewModel: viewModel)
                .frame(minWidth: 800, minHeight: 500)
        }
        .commands {
            CommandGroup(replacing: .newItem) { }
        }

        Settings {
            SettingsView(viewModel: viewModel)
        }

        MenuBarExtra("PaiSwitch", systemImage: "arrow.triangle.2.circlepath") {
            TrayMenuView(viewModel: viewModel)
        }
    }
}
