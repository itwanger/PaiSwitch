import Foundation
import Security

/// Manages data migrations between versions
class MigrationManager {
    static let shared = MigrationManager()

    private let migrationVersionKey = "paiswitch_migration_version"
    private let currentVersion = 2

    // Old service name from ClaudeModelSwitcher
    private let oldServiceName = "com.claudemodelswitcher.apikey"
    // New service name for PaiSwitch
    private let newServiceName = "com.paicoding.paiswitch.apikey"

    private init() {}

    /// Run migration if needed
    func migrateIfNeeded() {
        let lastVersion = UserDefaults.standard.integer(forKey: migrationVersionKey)

        if lastVersion < currentVersion {
            performMigration(from: lastVersion, to: currentVersion)
            UserDefaults.standard.set(currentVersion, forKey: migrationVersionKey)
        }
    }

    private func performMigration(from oldVersion: Int, to newVersion: Int) {
        print("PaiSwitch: Migrating from version \(oldVersion) to \(newVersion)")

        // Version 1 -> 2: Migrate keychain entries
        if oldVersion < 2 {
            migrateKeychainEntries()
        }

        print("PaiSwitch: Migration completed")
    }

    // MARK: - Keychain Migration

    private func migrateKeychainEntries() {
        let providers: [ModelProvider] = [.claude, .deepseek, .zhipu, .openrouter, .siliconflow, .volcano]

        for provider in providers {
            migrateKeychainEntry(for: provider)
        }
    }

    private func migrateKeychainEntry(for provider: ModelProvider) {
        let account = provider.rawValue

        // Try to read from old service
        let oldQuery: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: oldServiceName,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true
        ]

        var result: AnyObject?
        let status = SecItemCopyMatching(oldQuery as CFDictionary, &result)

        guard status == errSecSuccess,
              let data = result as? Data,
              let apiKey = String(data: data, encoding: .utf8) else {
            // No old entry found, skip
            return
        }

        // Check if new entry already exists
        let newCheckQuery: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: newServiceName,
            kSecAttrAccount as String: account
        ]

        let checkStatus = SecItemCopyMatching(newCheckQuery as CFDictionary, nil)

        if checkStatus == errSecItemNotFound {
            // Write to new service
            let newWriteQuery: [String: Any] = [
                kSecClass as String: kSecClassGenericPassword,
                kSecAttrService as String: newServiceName,
                kSecAttrAccount as String: account,
                kSecValueData as String: apiKey.data(using: .utf8)!
            ]

            let writeStatus = SecItemAdd(newWriteQuery as CFDictionary, nil)

            if writeStatus == errSecSuccess {
                print("PaiSwitch: Migrated API key for \(provider.rawValue)")
            }
        }

        // Optionally delete old entry after successful migration
        // Uncomment if you want to clean up old entries:
        // SecItemDelete(oldQuery as CFDictionary)
    }

    // MARK: - Manual Migration Trigger

    /// Force re-run migration (useful for testing)
    func forceMigration() {
        UserDefaults.standard.set(0, forKey: migrationVersionKey)
        migrateIfNeeded()
    }
}
