import Foundation
import SwiftUI

@MainActor
class MainViewModel: ObservableObject {
    @Published var currentConfig: ClaudeConfig?
    @Published var currentProvider: ModelProvider = .claude
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published var successMessage: String?
    @Published var backups: [ConfigBackup] = []

    private let configManager = ConfigManager.shared
    private let keychainManager = KeychainManager.shared
    private let backupManager = BackupManager.shared

    init() {
        loadCurrentConfig()
        loadBackups()
    }

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
                    // Claude 官方: 移除第三方配置
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

                // 更新 UI
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
                // 1. Backup current config
                if let config = currentConfig {
                    _ = try backupManager.createBackup(provider: config.currentProvider)
                }

                // 2. Get or save API Key
                let key: String
                if let apiKey = apiKey, !apiKey.isEmpty {
                    try keychainManager.saveCustomAPIKey(apiKey, for: provider.id.uuidString)
                    key = apiKey
                } else {
                    key = try keychainManager.getCustomAPIKey(for: provider.id.uuidString)
                }

                // 3. Update configuration
                var config = try configManager.loadConfig()
                config.setString("ANTHROPIC_AUTH_TOKEN", value: key)
                config.setString("ANTHROPIC_BASE_URL", value: provider.baseURL)
                config.setString("ANTHROPIC_MODEL", value: provider.defaultModel)

                if let fastModel = provider.fastModel, !fastModel.isEmpty {
                    config.setString("ANTHROPIC_SMALL_FAST_MODEL", value: fastModel)
                } else {
                    config.remove("ANTHROPIC_SMALL_FAST_MODEL")
                }

                // 4. Save config
                try configManager.saveConfig(config)

                // Update UI
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
}
