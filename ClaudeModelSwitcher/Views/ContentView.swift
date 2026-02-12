import SwiftUI

struct ContentView: View {
    @StateObject var viewModel: MainViewModel
    @State private var selectedProvider: ModelProvider?
    @State private var selectedCustomProvider: CustomProviderConfiguration?
    @State private var showingAPIKeyInput = false
    @State private var inputAPIKey = ""

    var body: some View {
        NavigationSplitView {
            ProviderListView(
                viewModel: viewModel,
                selectedProvider: $selectedProvider,
                selectedCustomProvider: $selectedCustomProvider
            )
            .frame(minWidth: 200)
        } detail: {
            if let customProvider = selectedCustomProvider {
                CustomProviderDetailView(
                    viewModel: viewModel,
                    provider: customProvider,
                    onSwitch: { key in
                        viewModel.switchToCustomProvider(customProvider, apiKey: key)
                        selectedCustomProvider = nil
                    }
                )
            } else if let provider = selectedProvider {
                ProviderDetailView(
                    viewModel: viewModel,
                    provider: provider,
                    onSwitch: { key in
                        viewModel.switchTo(provider, apiKey: key.isEmpty ? nil : key)
                    }
                )
            } else {
                WelcomeView(currentProvider: viewModel.currentProvider)
            }
        }
        .toolbar {
            ToolbarItem(placement: .automatic) {
                Button(action: { viewModel.loadCurrentConfig() }) {
                    Label("刷新", systemImage: "arrow.clockwise")
                }
            }

            ToolbarItem(placement: .automatic) {
                NavigationLink(destination: ConfigHistoryView(viewModel: viewModel)) {
                    Label("历史记录", systemImage: "clock.arrow.circlepath")
                }
            }
        }
        .overlay(alignment: .top) {
            if let message = viewModel.successMessage {
                ToastView(message: message, type: .success) {
                    viewModel.clearMessages()
                }
                .transition(.move(edge: .top).combined(with: .opacity))
            }

            if let message = viewModel.errorMessage {
                ToastView(message: message, type: .error) {
                    viewModel.clearMessages()
                }
                .transition(.move(edge: .top).combined(with: .opacity))
            }
        }
        .animation(.easeInOut, value: viewModel.successMessage)
        .animation(.easeInOut, value: viewModel.errorMessage)
    }
}

struct WelcomeView: View {
    let currentProvider: ModelProvider

    var body: some View {
        VStack(spacing: 20) {
            Text("π")
                .font(.system(size: 64, weight: .bold, design: .rounded))
                .foregroundStyle(Color.accentColor)

            Text("PaiSwitch")
                .font(.title)
                .fontWeight(.bold)

            Text("当前模型: \(currentProvider.rawValue)")
                .font(.headline)
                .foregroundStyle(.secondary)

            Text("从左侧选择服务商进行切换")
                .font(.subheadline)
                .foregroundStyle(.tertiary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

struct ToastView: View {
    let message: String
    let type: ToastType
    let onDismiss: () -> Void

    enum ToastType {
        case success, error

        var color: Color {
            switch self {
            case .success: .green
            case .error: .red
            }
        }

        var icon: String {
            switch self {
            case .success: "checkmark.circle.fill"
            case .error: "xmark.circle.fill"
            }
        }
    }

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: type.icon)
                .foregroundStyle(type.color)

            Text(message)
                .font(.subheadline)

            Button(action: onDismiss) {
                Image(systemName: "xmark")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(.regularMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 10))
        .shadow(radius: 4)
        .padding()
        .onAppear {
            DispatchQueue.main.asyncAfter(deadline: .now() + 3) {
                onDismiss()
            }
        }
    }
}
