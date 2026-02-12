import Foundation

struct ConfigBackup: Identifiable, Codable {
    let id: UUID
    let timestamp: Date
    let provider: String
    let filename: String

    var formattedDate: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd HH:mm:ss"
        return formatter.string(from: timestamp)
    }
}

class BackupManager {
    static let shared = BackupManager()

    private let fileManager = FileManager.default
    private let backupsDirectory: URL

    private init() {
        let home = fileManager.homeDirectoryForCurrentUser
        backupsDirectory = home.appendingPathComponent(".claude/backups")

        // 创建备份目录
        try? fileManager.createDirectory(at: backupsDirectory, withIntermediateDirectories: true)
    }

    func createBackup(provider: ModelProvider) throws -> ConfigBackup {
        let configManager = ConfigManager.shared
        let timestamp = Date()
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyyMMdd_HHmmss"

        let filename = "settings.json.backup.\(formatter.string(from: timestamp))"
        let backupURL = backupsDirectory.appendingPathComponent(filename)

        // 复制当前配置
        if configManager.configExists() {
            try fileManager.copyItem(at: configManager.configURL, to: backupURL)
        } else {
            // 创建空备份
            let emptyConfig = ClaudeConfig(env: [:])
            let data = try JSONEncoder().encode(emptyConfig)
            try data.write(to: backupURL)
        }

        let backup = ConfigBackup(
            id: UUID(),
            timestamp: timestamp,
            provider: provider.rawValue,
            filename: filename
        )

        // 保存备份元数据
        saveBackupMetadata(backup)

        return backup
    }

    func restoreBackup(_ backup: ConfigBackup) throws {
        let backupURL = backupsDirectory.appendingPathComponent(backup.filename)
        let configManager = ConfigManager.shared

        guard fileManager.fileExists(atPath: backupURL.path) else {
            throw ConfigError.fileNotFound
        }

        // 先备份当前配置
        let currentProvider = try? ConfigManager.shared.loadConfig().currentProvider
        if let provider = currentProvider {
            _ = try createBackup(provider: provider)
        }

        // 恢复备份
        try fileManager.removeItem(at: configManager.configURL)
        try fileManager.copyItem(at: backupURL, to: configManager.configURL)
    }

    func listBackups() -> [ConfigBackup] {
        let metadataURL = backupsDirectory.appendingPathComponent("backups_metadata.json")

        guard let data = try? Data(contentsOf: metadataURL),
              let backups = try? JSONDecoder().decode([ConfigBackup].self, from: data) else {
            return []
        }

        return backups.sorted { $0.timestamp > $1.timestamp }
    }

    func deleteBackup(_ backup: ConfigBackup) throws {
        let backupURL = backupsDirectory.appendingPathComponent(backup.filename)
        try? fileManager.removeItem(at: backupURL)

        // 更新元数据
        var backups = listBackups()
        backups.removeAll { $0.id == backup.id }
        saveBackupsMetadata(backups)
    }

    private func saveBackupMetadata(_ backup: ConfigBackup) {
        var backups = listBackups()
        backups.insert(backup, at: 0)

        // 只保留最近 20 个备份
        if backups.count > 20 {
            for oldBackup in backups[20...] {
                try? deleteBackup(oldBackup)
            }
            backups = Array(backups.prefix(20))
        }

        saveBackupsMetadata(backups)
    }

    private func saveBackupsMetadata(_ backups: [ConfigBackup]) {
        let metadataURL = backupsDirectory.appendingPathComponent("backups_metadata.json")

        guard let data = try? JSONEncoder().encode(backups) else { return }
        try? data.write(to: metadataURL)
    }
}
