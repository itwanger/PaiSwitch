import Foundation
import Security

enum KeychainError: LocalizedError {
    case itemNotFound
    case duplicateItem
    case invalidData
    case unexpectedStatus(OSStatus)

    var errorDescription: String? {
        switch self {
        case .itemNotFound:
            return "未找到 API Key"
        case .duplicateItem:
            return "API Key 已存在"
        case .invalidData:
            return "无效的数据"
        case .unexpectedStatus(let status):
            return "Keychain 错误: \(status)"
        }
    }
}

class KeychainManager {
    static let shared = KeychainManager()

    private let service = "com.paicoding.paiswitch.apikey"
    private let customServicePrefix = "com.paicoding.paiswitch.custom."

    private init() {}

    func saveAPIKey(_ key: String, for provider: ModelProvider) throws {
        let account = provider.rawValue

        // 先删除旧的（如果存在）
        try? deleteAPIKey(for: provider)

        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecValueData as String: key.data(using: .utf8)!,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlock
        ]

        let status = SecItemAdd(query as CFDictionary, nil)

        guard status == errSecSuccess else {
            throw KeychainError.unexpectedStatus(status)
        }
    }

    func getAPIKey(for provider: ModelProvider) throws -> String {
        let account = provider.rawValue

        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true
        ]

        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)

        guard status == errSecSuccess else {
            throw KeychainError.itemNotFound
        }

        guard let data = result as? Data,
              let key = String(data: data, encoding: .utf8) else {
            throw KeychainError.invalidData
        }

        return key
    }

    func deleteAPIKey(for provider: ModelProvider) throws {
        let account = provider.rawValue

        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]

        let status = SecItemDelete(query as CFDictionary)

        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw KeychainError.unexpectedStatus(status)
        }
    }

    func hasAPIKey(for provider: ModelProvider) -> Bool {
        do {
            _ = try getAPIKey(for: provider)
            return true
        } catch {
            return false
        }
    }

    // MARK: - Custom Provider Support

    /// Save API key for a custom provider
    func saveCustomAPIKey(_ key: String, for providerId: String) throws {
        let account = providerId

        // 先删除旧的（如果存在）
        try? deleteCustomAPIKey(for: providerId)

        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: customServicePrefix + providerId,
            kSecAttrAccount as String: account,
            kSecValueData as String: key.data(using: .utf8)!,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlock
        ]

        let status = SecItemAdd(query as CFDictionary, nil)

        guard status == errSecSuccess else {
            throw KeychainError.unexpectedStatus(status)
        }
    }

    /// Get API key for a custom provider
    func getCustomAPIKey(for providerId: String) throws -> String {
        let account = providerId

        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: customServicePrefix + providerId,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true
        ]

        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)

        guard status == errSecSuccess else {
            throw KeychainError.itemNotFound
        }

        guard let data = result as? Data,
              let key = String(data: data, encoding: .utf8) else {
            throw KeychainError.invalidData
        }

        return key
    }

    /// Delete API key for a custom provider
    func deleteCustomAPIKey(for providerId: String) throws {
        let account = providerId

        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: customServicePrefix + providerId,
            kSecAttrAccount as String: account
        ]

        let status = SecItemDelete(query as CFDictionary)

        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw KeychainError.unexpectedStatus(status)
        }
    }

    /// Check if custom provider has API key
    func hasCustomAPIKey(for providerId: String) -> Bool {
        do {
            _ = try getCustomAPIKey(for: providerId)
            return true
        } catch {
            return false
        }
    }
}
