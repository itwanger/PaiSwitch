import SwiftUI

struct TrayMenuView: View {
    @ObservedObject var viewModel: MainViewModel

    private let providers: [ModelProvider] = {
        ModelProvider.allCases.filter { $0 != .custom }
    }()

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            // 当前状态
            Text("当前: \(viewModel.currentProvider.rawValue)")
                .font(.headline)
                .padding(.bottom, 4)

            Divider()

            // 快速切换菜单
            ForEach(providers, id: \.rawValue) { provider in
                providerButton(for: provider)
            }

            Divider()

            // 打开主窗口
            Button("打开主窗口...") {
                NSApplication.shared.activate(ignoringOtherApps: true)
                if let window = NSApplication.shared.windows.first {
                    window.makeKeyAndOrderFront(nil)
                }
            }

            Divider()

            Button("退出") {
                NSApplication.shared.terminate(nil)
            }
        }
        .onAppear {
            viewModel.loadCurrentConfig()
        }
    }

    private func providerButton(for provider: ModelProvider) -> some View {
        Button(action: {
            if viewModel.hasAPIKey(for: provider) || provider == .claude {
                viewModel.switchTo(provider)
            }
        }) {
            Label {
                Text(provider.rawValue)
            } icon: {
                Image(systemName: provider.iconName)
            }
        }
        .disabled(!viewModel.hasAPIKey(for: provider) && provider != .claude)
    }
}
