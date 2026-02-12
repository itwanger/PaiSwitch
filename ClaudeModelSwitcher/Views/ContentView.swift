import SwiftUI

struct ContentView: View {
    @StateObject var viewModel: MainViewModel
    @StateObject private var authManager = AuthManager.shared
    @State private var selectedProvider: ModelProvider?
    @State private var selectedCustomProvider: CustomProviderConfiguration?
    @State private var showingAPIKeyInput = false
    @State private var inputAPIKey = ""
    @State private var showLoginSheet = false
    @State private var showAIChat = false

    var body: some View {
        NavigationSplitView {
            List {
                // Online Status Section
                Section {
                    if authManager.isLoggedIn {
                        HStack {
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundColor(.green)
                            VStack(alignment: .leading) {
                                Text("在线模式")
                                    .font(.subheadline)
                                Text(authManager.currentUser?.username ?? "用户")
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            }
                            Spacer()
                            Button("退出") {
                                authManager.logout()
                            }
                            .buttonStyle(.plain)
                            .foregroundColor(.red)
                        }
                    } else {
                        Button {
                            showLoginSheet = true
                        } label: {
                            HStack {
                                Image(systemName: "person.badge.key")
                                Text("登录以启用在线功能")
                            }
                        }
                    }
                } header: {
                    Text("账户")
                }

                // AI Chat Section (online only)
                if authManager.isLoggedIn {
                    Section {
                        Button {
                            showAIChat = true
                        } label: {
                            HStack {
                                Image(systemName: "bubble.left.and.bubble.right")
                                    .foregroundColor(.accentColor)
                                Text("AI 助手")
                                Spacer()
                                Image(systemName: "chevron.right")
                                    .foregroundColor(.secondary)
                            }
                        }
                        .buttonStyle(.plain)
                    } header: {
                        Text("AI 功能")
                    }
                }

                // Providers Section
                Section {
                    ForEach(ModelProvider.allCases, id: \.self) { provider in
                        ProviderRow(
                            provider: provider,
                            isSelected: viewModel.currentProvider == provider,
                            hasAPIKey: viewModel.hasAPIKey(for: provider)
                        )
                        .contentShape(Rectangle())
                        .onTapGesture {
                            selectedProvider = provider
                            selectedCustomProvider = nil
                        }
                    }
                } header: {
                    Text("内置提供商")
                }

                // Custom Providers Section
                let customProviders = CustomProviderManager.shared.getAllProviders()
                if !customProviders.isEmpty {
                    Section {
                        ForEach(customProviders) { provider in
                            HStack {
                                VStack(alignment: .leading) {
                                    Text(provider.name)
                                    Text(provider.baseURL)
                                        .font(.caption)
                                        .foregroundColor(.secondary)
                                }
                                Spacer()
                                if viewModel.currentProvider == .custom {
                                    Image(systemName: "checkmark")
                                        .foregroundColor(.accentColor)
                                }
                            }
                            .contentShape(Rectangle())
                            .onTapGesture {
                                selectedCustomProvider = provider
                                selectedProvider = nil
                            }
                        }
                    } header: {
                        Text("自定义提供商")
                    }
                }
            }
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
                WelcomeView(
                    currentProvider: viewModel.currentProvider,
                    isOnline: authManager.isLoggedIn
                )
            }
        }
        .toolbar {
            ToolbarItem(placement: .automatic) {
                Button(action: {
                    viewModel.loadCurrentConfig()
                    Task { await viewModel.loadRemoteData() }
                }) {
                    Label("刷新", systemImage: "arrow.clockwise")
                }
            }

            ToolbarItem(placement: .automatic) {
                NavigationLink(destination: ConfigHistoryView(viewModel: viewModel)) {
                    Label("历史记录", systemImage: "clock.arrow.circlepath")
                }
            }
        }
        .sheet(isPresented: $showLoginSheet) {
            LoginView()
                .frame(width: 400)
        }
        .sheet(isPresented: $showAIChat) {
            AIChatView()
                .frame(width: 500, height: 600)
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

struct ProviderRow: View {
    let provider: ModelProvider
    let isSelected: Bool
    let hasAPIKey: Bool

    var body: some View {
        HStack {
            VStack(alignment: .leading) {
                Text(provider.displayName)
                Text(provider.defaultModel)
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            Spacer()
            if isSelected {
                Image(systemName: "checkmark")
                    .foregroundColor(.accentColor)
            }
            if hasAPIKey {
                Image(systemName: "key.fill")
                    .foregroundColor(.green)
                    .font(.caption)
            }
        }
    }
}

struct WelcomeView: View {
    let currentProvider: ModelProvider
    let isOnline: Bool

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

            if isOnline {
                Label("在线模式已启用", systemImage: "checkmark.circle.fill")
                    .foregroundColor(.green)
                    .font(.subheadline)
            } else {
                Text("从左侧选择服务商进行切换")
                    .font(.subheadline)
                    .foregroundStyle(.tertiary)
            }
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

#Preview {
    ContentView(viewModel: MainViewModel())
        .frame(width: 800, height: 600)
}
