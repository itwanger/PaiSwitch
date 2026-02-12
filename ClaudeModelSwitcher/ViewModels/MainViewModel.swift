import Foundation
import SwiftUI
import Combine

@MainActor
class MainViewModel: ObservableObject {
    @Published var currentConfig: ClaudeConfig?
    @Published var currentProvider: ModelProvider = .claude
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published var successMessage: String?
    @Published var backups: [ConfigBackup] = []

    // Online mode
    @Published var isOnlineMode = false
    @Published var remoteProviders: [ProviderInfo] = []
    @Published var remoteConfig: ConfigInfo?

    private let configManager = ConfigManager.shared
    private let keychainManager = KeychainManager.shared
    private let backupManager = BackupManager.shared
    private let service = PaiSwitchService.shared
    private let authManager = AuthManager.shared

    private var cancellables = Set<AnyCancellable>()

    init() {
        loadCurrentConfig()
        loadBackups()
        setupObservers()
    }

    private func setupObservers() {
        // Observe auth state changes
        authManager.$isLoggedIn
            .sink { [weak self] isLoggedIn in
                self?.isOnlineMode = isLoggedIn
                if isLoggedIn {
                    Task { await self?.loadRemoteData() }
                }
            }
            .store(in: &cancellables)

        // Observe provider switch notifications from AI chat
        NotificationCenter.default.publisher(for: .providerSwitched)
            .sink { [weak self] _ in
                self?.loadCurrentConfig()
                Task { await self?.loadRemoteData() }
            }
            .store(in: &cancellables)

        // Check initial auth state
        if authManager.isLoggedIn {
            isOnlineMode = true
            Task { await loadRemoteData() }
        }
    }

    // MARK: - Remote Data

    func loadRemoteData() async {
        guard authManager.isLoggedIn else { return }

        do {
            async let providers = service.getProviders()
            async let config = service.getConfig()

            remoteProviders = try await providers
            remoteConfig = try await config
        } catch {
            print("Failed to load remote data: \(error)")
        }
    }

    // MARK: - Local Operations

    func loadCurrentConfig() {
        do {
            currentConfig = try configManager.loadConfig()
            currentProvider = currentConfig?.currentProvider ?? .claude
        } catch {
            errorMessage = "加载配置失败: \(error.localizedDescription)"
        }
    }

    func loadBackups() {
        backups = backupManager.listBackups()
    }

    // MARK: - Switch Operations

    func switchTo(_ provider: ModelProvider, apiKey: String? = nil) {
        isLoading = true
        errorMessage = nil
        successMessage = nil

        Task {
            do {
                // 1. 备份当前配置
                if let config = currentConfig {
                    _ = try backupManager.createBackup(provider: config.currentProvider)
                }

                // 2. 获取或保存 API Key
                let key: String
                if let apiKey = apiKey, !apiKey.isEmpty {
                    try keychainManager.saveAPIKey(apiKey, for: provider)
                    key = apiKey
                } else {
                    key = try keychainManager.getAPIKey(for: provider)
                }

                // 3. 更新配置
                var config = try configManager.loadConfig()

                if provider == .claude {
                    config.remove("ANTHROPIC_BASE_URL")
                    config.remove("ANTHROPIC_AUTH_TOKEN")
                    config.setString("ANTHROPIC_API_KEY", value: key)
                } else {
                    config.setString("ANTHROPIC_AUTH_TOKEN", value: key)
                    config.setString("ANTHROPIC_BASE_URL", value: provider.baseURL ?? "")
                    config.setString("ANTHROPIC_MODEL", value: provider.defaultModel)

                    if let fastModel = provider.fastModel {
                        config.setString("ANTHROPIC_SMALL_FAST_MODEL", value: fastModel)
                    } else {
                        config.remove("ANTHROPIC_SMALL_FAST_MODEL")
                    }
                }

                // 4. 保存配置
                try configManager.saveConfig(config)

                // 5. Sync to server if online
                if isOnlineMode {
                    try? await service.setApiKey(providerCode: provider.rawValue, apiKey: key)
                    try? await service.switchTo(providerCode: provider.rawValue)
                }

                currentConfig = config
                currentProvider = provider
                successMessage = "已切换到 \(provider.rawValue)"
                loadBackups()

            } catch {
                errorMessage = "切换失败: \(error.localizedDescription)"
            }

            isLoading = false
        }
    }

    func switchToRemoteProvider(_ provider: ProviderInfo) async {
        isLoading = true
        errorMessage = nil

        do {
            let result = try await service.switchTo(providerCode: provider.code)

            if result.success {
                // Also update local config
                if let localKey = try? keychainManager.getAPIKey(for: ModelProvider.from(provider.code)) {
                    var config = try configManager.loadConfig()
                    config.setString("ANTHROPIC_AUTH_TOKEN", value: localKey)
                    config.setString("ANTHROPIC_BASE_URL", value: provider.baseUrl)
                    config.setString("ANTHROPIC_MODEL", value: provider.modelName)
                    if let small = provider.modelNameSmall {
                        config.setString("ANTHROPIC_SMALL_FAST_MODEL", value: small)
                    }
                    try configManager.saveConfig(config)
                    currentConfig = config
                }

                currentProvider = ModelProvider.from(provider.code)
                successMessage = "已切换到 \(provider.name)"
                await loadRemoteData()
            } else {
                errorMessage = result.message
            }
        } catch {
            errorMessage = "切换失败: \(error.localizedDescription)"
        }

        isLoading = false
    }

    func restoreBackup(_ backup: ConfigBackup) {
        isLoading = true

        Task {
            do {
                try backupManager.restoreBackup(backup)
                loadCurrentConfig()
                loadBackups()
                successMessage = "已恢复备份: \(backup.formattedDate)"
            } catch {
                errorMessage = "恢复失败: \(error.localizedDescription)"
            }

            isLoading = false
        }
    }

    func deleteBackup(_ backup: ConfigBackup) {
        do {
            try backupManager.deleteBackup(backup)
            loadBackups()
            successMessage = "已删除备份"
        } catch {
            errorMessage = "删除失败: \(error.localizedDescription)"
        }
    }

    func getAPIKey(for provider: ModelProvider) -> String? {
        try? keychainManager.getAPIKey(for: provider)
    }

    func hasAPIKey(for provider: ModelProvider) -> Bool {
        keychainManager.hasAPIKey(for: provider)
    }

    func clearMessages() {
        errorMessage = nil
        successMessage = nil
    }

    // MARK: - Custom Provider Support

    func switchToCustomProvider(_ provider: CustomProviderConfiguration, apiKey: String? = nil) {
        isLoading = true
        errorMessage = nil
        successMessage = nil

        Task {
            do {
                if let config = currentConfig {
                    _ = try backupManager.createBackup(provider: config.currentProvider)
                }

                let key: String
                if let apiKey = apiKey, !apiKey.isEmpty {
                    try keychainManager.saveCustomAPIKey(apiKey, for: provider.id.uuidString)
                    key = apiKey
                } else {
                    key = try keychainManager.getCustomAPIKey(for: provider.id.uuidString)
                }

                var config = try configManager.loadConfig()
                config.setString("ANTHROPIC_AUTH_TOKEN", value: key)
                config.setString("ANTHROPIC_BASE_URL", value: provider.baseURL)
                config.setString("ANTHROPIC_MODEL", value: provider.defaultModel)

                if let fastModel = provider.fastModel, !fastModel.isEmpty {
                    config.setString("ANTHROPIC_SMALL_FAST_MODEL", value: fastModel)
                } else {
                    config.remove("ANTHROPIC_SMALL_FAST_MODEL")
                }

                try configManager.saveConfig(config)

                currentConfig = config
                currentProvider = .custom
                successMessage = "已切换到 \(provider.name)"
                loadBackups()

            } catch {
                errorMessage = "切换失败: \(error.localizedDescription)"
            }

            isLoading = false
        }
    }

    func getCustomAPIKey(for provider: CustomProviderConfiguration) -> String? {
        try? keychainManager.getCustomAPIKey(for: provider.id.uuidString)
    }

    func hasCustomAPIKey(for provider: CustomProviderConfiguration) -> Bool {
        keychainManager.hasCustomAPIKey(for: provider.id.uuidString)
    }

    // MARK: - Remote API Key

    func setRemoteApiKey(providerCode: String, apiKey: String) async {
        do {
            _ = try await service.setApiKey(providerCode: providerCode, apiKey: apiKey)
            await loadRemoteData()
            successMessage = "API Key 已保存"
        } catch {
            errorMessage = "保存失败: \(error.localizedDescription)"
        }
    }
}

// MARK: - ModelProvider Extension

extension ModelProvider {
    static func from(_ code: String) -> ModelProvider {
        switch code {
        case "claude": return .claude
        case "deepseek": return .deepseek
        case "zhipu": return .zhipu
        case "openrouter": return .openrouter
        default: return .custom
        }
    }
}
