import SwiftUI

struct SettingsView: View {
    @ObservedObject var viewModel: MainViewModel

    var body: some View {
        TabView {
            GeneralSettingsView(viewModel: viewModel)
                .tabItem {
                    Label("通用", systemImage: "gear")
                }

            AboutView()
                .tabItem {
                    Label("关于", systemImage: "info.circle")
                }
        }
        .frame(width: 400, height: 300)
    }
}

struct GeneralSettingsView: View {
    @ObservedObject var viewModel: MainViewModel
    @AppStorage("launchAtLogin") private var launchAtLogin = false

    var body: some View {
        Form {
            Section("启动") {
                Toggle("开机自动启动", isOn: $launchAtLogin)
            }

            Section("当前状态") {
                LabeledContent("当前服务商") {
                    Text(viewModel.currentProvider.rawValue)
                        .fontWeight(.medium)
                }

                LabeledContent("当前模型") {
                    Text(viewModel.currentConfig?.currentModel ?? "-")
                        .font(.system(.body, design: .monospaced))
                }
            }

            Section("配置文件") {
                LabeledContent("位置") {
                    Text("~/.claude/settings.json")
                        .font(.system(.body, design: .monospaced))
                        .textSelection(.enabled)
                }
            }
        }
        .formStyle(.grouped)
        .padding()
    }
}

struct AboutView: View {
    var body: some View {
        VStack(spacing: 20) {
            Text("π")
                .font(.system(size: 64, weight: .bold, design: .rounded))
                .foregroundStyle(Color.accentColor)

            Text("PaiSwitch")
                .font(.title)
                .fontWeight(.bold)

            Text("版本 1.0.0")
                .font(.subheadline)
                .foregroundStyle(.secondary)

            Text("快速切换 Claude Code 使用的 AI 模型服务商")
                .font(.body)
                .multilineTextAlignment(.center)
                .foregroundStyle(.secondary)

            Spacer()

            VStack(spacing: 8) {
                Link("GitHub", destination: URL(string: "https://github.com")!)
                Text("© 2024")
                    .font(.caption)
                    .foregroundStyle(.tertiary)
            }
        }
        .padding()
    }
}
