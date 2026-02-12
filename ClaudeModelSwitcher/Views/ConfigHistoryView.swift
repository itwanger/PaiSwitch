import SwiftUI

struct ConfigHistoryView: View {
    @ObservedObject var viewModel: MainViewModel

    var body: some View {
        List {
            if viewModel.backups.isEmpty {
                VStack(spacing: 12) {
                    Image(systemName: "clock.arrow.circlepath")
                        .font(.system(size: 48))
                        .foregroundStyle(.secondary)

                    Text("暂无备份记录")
                        .font(.headline)

                    Text("切换模型时会自动创建备份")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity)
                .padding(.top, 100)
            } else {
                ForEach(viewModel.backups) { backup in
                    BackupRow(
                        backup: backup,
                        onRestore: { viewModel.restoreBackup(backup) },
                        onDelete: { viewModel.deleteBackup(backup) }
                    )
                }
            }
        }
        .navigationTitle("配置历史")
        .toolbar {
            ToolbarItem(placement: .automatic) {
                Button("刷新") {
                    viewModel.loadBackups()
                }
            }
        }
    }
}

struct BackupRow: View {
    let backup: ConfigBackup
    let onRestore: () -> Void
    let onDelete: () -> Void

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text(backup.provider)
                    .fontWeight(.medium)

                Text(backup.formattedDate)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            Spacer()

            HStack(spacing: 8) {
                Button("恢复") {
                    onRestore()
                }
                .buttonStyle(.bordered)

                Button(action: onDelete) {
                    Image(systemName: "trash")
                        .foregroundStyle(.red)
                }
                .buttonStyle(.borderless)
            }
        }
        .padding(.vertical, 4)
    }
}
