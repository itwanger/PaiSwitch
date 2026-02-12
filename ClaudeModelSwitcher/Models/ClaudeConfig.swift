import Foundation

struct ClaudeConfig: Codable {
    var env: [String: EnvValue]

    init(env: [String: EnvValue] = [:]) {
        self.env = env
    }

    var currentProvider: ModelProvider {
        guard let baseURL = getString("ANTHROPIC_BASE_URL") else {
            return .claude
        }

        for provider in ModelProvider.allCases where provider != .custom {
            if provider.baseURL == baseURL {
                return provider
            }
        }

        return .custom
    }

    var currentModel: String {
        getString("ANTHROPIC_MODEL") ?? "claude-sonnet-4"
    }

    var apiToken: String? {
        getString("ANTHROPIC_AUTH_TOKEN") ?? getString("ANTHROPIC_API_KEY")
    }

    var timeout: Int {
        getInt("API_TIMEOUT_MS") ?? 120000
    }

    func getString(_ key: String) -> String? {
        env[key]?.stringValue
    }

    func getInt(_ key: String) -> Int? {
        env[key]?.intValue
    }

    mutating func setString(_ key: String, value: String?) {
        if let value = value, !value.isEmpty {
            env[key] = .string(value)
        } else {
            env.removeValue(forKey: key)
        }
    }

    mutating func setInt(_ key: String, value: Int?) {
        if let value = value {
            env[key] = .int(value)
        } else {
            env.removeValue(forKey: key)
        }
    }

    mutating func remove(_ key: String) {
        env.removeValue(forKey: key)
    }
}

enum EnvValue: Codable {
    case string(String)
    case int(Int)

    var stringValue: String? {
        switch self {
        case .string(let s): return s
        case .int(let i): return String(i)
        }
    }

    var intValue: Int? {
        switch self {
        case .int(let i): return i
        case .string(let s): return Int(s)
        }
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()

        if let intValue = try? container.decode(Int.self) {
            self = .int(intValue)
        } else if let stringValue = try? container.decode(String.self) {
            self = .string(stringValue)
        } else {
            throw DecodingError.dataCorruptedError(
                in: container,
                debugDescription: "EnvValue must be String or Int"
            )
        }
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        switch self {
        case .string(let s):
            try container.encode(s)
        case .int(let i):
            try container.encode(i)
        }
    }
}
