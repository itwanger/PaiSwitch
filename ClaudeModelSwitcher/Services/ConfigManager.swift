import Foundation

enum ConfigError: LocalizedError {
    case fileNotFound
    case invalidJSON
    case writeFailed
    case backupFailed

    var errorDescription: String? {
        switch self {
        case .fileNotFound:
            return "配置文件不存在"
        case .invalidJSON:
            return "配置文件格式无效"
        case .writeFailed:
            return "写入配置文件失败"
        case .backupFailed:
            return "创建备份失败"
        }
    }
}

class ConfigManager {
    static let shared = ConfigManager()

    private let fileManager = FileManager.default
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    var configURL: URL {
        let home = fileManager.homeDirectoryForCurrentUser
        return home.appendingPathComponent(".claude/settings.json")
    }

    private init() {
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
    }

    func loadConfig() throws -> ClaudeConfig {
        guard fileManager.fileExists(atPath: configURL.path) else {
            // 创建默认配置
            let defaultConfig = ClaudeConfig(env: [:])
            try saveConfig(defaultConfig)
            return defaultConfig
        }

        let data = try Data(contentsOf: configURL)
        let config = try decoder.decode(ClaudeConfig.self, from: data)
        return config
    }

    func saveConfig(_ config: ClaudeConfig) throws {
        let data = try encoder.encode(config)

        // 确保目录存在
        let directory = configURL.deletingLastPathComponent()
        if !fileManager.fileExists(atPath: directory.path) {
            try fileManager.createDirectory(at: directory, withIntermediateDirectories: true)
        }

        try data.write(to: configURL, options: [.atomic])
    }

    func configExists() -> Bool {
        fileManager.fileExists(atPath: configURL.path)
    }
}
