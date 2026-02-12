import Foundation

// MARK: - Models

struct User: Codable {
    let id: Int
    let username: String
    let email: String
    let nickname: String?
    let avatarUrl: String?
    let status: String
    let createdAt: String
}

struct LoginResponse: Codable {
    let token: String
    let tokenType: String
    let expiresIn: Int
    let user: User
}

struct ProviderInfo: Codable, Identifiable, Hashable {
    let id: Int
    let code: String
    let name: String
    let description: String?
    let baseUrl: String
    let modelName: String
    let modelNameSmall: String?
    let isBuiltin: Bool
    let isActive: Bool
    let sortOrder: Int
    let iconUrl: String?
    let hasApiKey: Bool?
    let createdAt: String
}

struct ApiKeyInfo: Codable, Identifiable {
    let id: Int
    let providerId: Int
    let providerCode: String
    let providerName: String
    let keyHint: String
    let isValid: Bool
    let lastUsedAt: String?
    let expiresAt: String?
    let createdAt: String
}

struct ConfigInfo: Codable {
    let id: Int
    let userId: Int
    let currentProvider: ProviderInfo
    let apiTimeout: Int
    let extraConfig: [String: JSONValue]?
    let updatedAt: String
}

struct SwitchResult: Codable {
    let success: Bool
    let message: String
    let previousProvider: ProviderInfo?
    let currentProvider: ProviderInfo?
    let switchedAt: String
}

struct NaturalLanguageResponse: Codable {
    let aiResponse: String
    let switchTriggered: Bool?
    let switchResult: SwitchResult?
    let sessionId: String
}

// Helper for flexible JSON values
enum JSONValue: Codable, Hashable {
    case string(String)
    case int(Int)
    case double(Double)
    case bool(Bool)
    case array([JSONValue])
    case object([String: JSONValue])
    case null

    func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        switch self {
        case .string(let value): try container.encode(value)
        case .int(let value): try container.encode(value)
        case .double(let value): try container.encode(value)
        case .bool(let value): try container.encode(value)
        case .array(let value): try container.encode(value)
        case .object(let value): try container.encode(value)
        case .null: try container.encodeNil()
        }
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if let value = try? container.decode(String.self) { self = .string(value) }
        else if let value = try? container.decode(Int.self) { self = .int(value) }
        else if let value = try? container.decode(Double.self) { self = .double(value) }
        else if let value = try? container.decode(Bool.self) { self = .bool(value) }
        else if let value = try? container.decode([JSONValue].self) { self = .array(value) }
        else if let value = try? container.decode([String: JSONValue].self) { self = .object(value) }
        else { self = .null }
    }
}

// MARK: - Service

actor PaiSwitchService {
    static let shared = PaiSwitchService()
    private let client = APIClient.shared

    private init() {}

    // MARK: - Auth

    func login(username: String, password: String) async throws -> LoginResponse {
        let request = LoginRequest(username: username, password: password)
        return try await client.post("/auth/login", body: request)
    }

    func register(username: String, email: String, password: String) async throws -> LoginResponse {
        let request = RegisterRequest(username: username, email: email, password: password)
        return try await client.post("/auth/register", body: request)
    }

    // MARK: - Providers

    func getProviders() async throws -> [ProviderInfo] {
        return try await client.get("/providers/my")
    }

    // MARK: - API Keys

    func setApiKey(providerCode: String, apiKey: String) async throws -> ApiKeyInfo {
        let request = SetApiKeyRequest(providerCode: providerCode, apiKey: apiKey)
        return try await client.post("/api-keys", body: request)
    }

    func getApiKeys() async throws -> [ApiKeyInfo] {
        return try await client.get("/api-keys")
    }

    func deleteApiKey(providerCode: String) async throws {
        let _: EmptyResponse = try await client.delete("/api-keys/\(providerCode)")
    }

    // MARK: - Config

    func getConfig() async throws -> ConfigInfo {
        return try await client.get("/config")
    }

    // MARK: - Switch

    func switchTo(providerCode: String) async throws -> SwitchResult {
        let request = SwitchRequest(providerCode: providerCode, clientInfo: "macOS")
        return try await client.post("/switch", body: request)
    }

    // MARK: - AI

    func naturalLanguageSwitch(prompt: String, sessionId: String?) async throws -> NaturalLanguageResponse {
        var request = NaturalLanguageRequest(prompt: prompt, clientInfo: "macOS")
        if let sessionId = sessionId {
            request = NaturalLanguageRequest(prompt: prompt, sessionId: sessionId, clientInfo: "macOS")
        }
        return try await client.post("/ai/switch-by-nl", body: request)
    }
}

// MARK: - Request Models

private struct LoginRequest: Encodable {
    let username: String
    let password: String
}

private struct RegisterRequest: Encodable {
    let username: String
    let email: String
    let password: String
}

private struct SetApiKeyRequest: Encodable {
    let providerCode: String
    let apiKey: String
}

private struct SwitchRequest: Encodable {
    let providerCode: String
    let clientInfo: String?
}

private struct NaturalLanguageRequest: Encodable {
    let prompt: String
    var sessionId: String?
    let clientInfo: String?
}

private struct EmptyResponse: Decodable {}
