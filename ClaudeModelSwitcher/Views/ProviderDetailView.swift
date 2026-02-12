import SwiftUI

struct ProviderDetailView: View {
    @ObservedObject var viewModel: MainViewModel
    let provider: ModelProvider
    let onSwitch: (String) -> Void

    @State private var apiKey: String = ""
    @State private var showAPIKey: Bool = false
    @State private var showingModelConfig = false

    var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                // Header
                VStack(spacing: 12) {
                    Image(systemName: provider.iconName)
                        .font(.system(size: 48))
                        .foregroundStyle(Color.accentColor)

                    Text(provider.rawValue)
                        .font(.title)
                        .fontWeight(.bold)

                    Text(provider.description)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                .padding(.top, 40)

                // Configuration Info
                VStack(alignment: .leading, spacing: 16) {
                    HStack {
                        GroupBox("配置信息") {
                            VStack(alignment: .leading, spacing: 12) {
                                if let baseURL = provider.baseURL {
                                    ConfigRow(label: "API 地址", value: baseURL)
                                }

                                ConfigRow(label: "默认模型", value: provider.defaultModel)

                                if let fastModel = provider.fastModel {
                                    ConfigRow(label: "快速模型", value: fastModel)
                                }
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                        }

                        Button(action: { showingModelConfig = true }) {
                            Image(systemName: "pencil.circle")
                                .font(.title2)
                                .foregroundStyle(.secondary)
                        }
                        .buttonStyle(.borderless)
                        .help("编辑模型配置")
                    }

                    // API Key Section
                    GroupBox("API Key") {
                        VStack(alignment: .leading, spacing: 12) {
                            HStack {
                                if showAPIKey {
                                    TextField("输入 API Key", text: $apiKey)
                                        .textFieldStyle(.roundedBorder)
                                } else {
                                    SecureField("输入 API Key", text: $apiKey)
                                        .textFieldStyle(.roundedBorder)
                                }

                                Button(action: { showAPIKey.toggle() }) {
                                    Image(systemName: showAPIKey ? "eye.slash" : "eye")
                                }
                                .buttonStyle(.borderless)
                            }

                            if viewModel.hasAPIKey(for: provider) {
                                HStack {
                                    Image(systemName: "checkmark.circle.fill")
                                        .foregroundStyle(.green)
                                    Text("已保存 API Key（留空使用已保存的密钥）")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                            }
                        }
                    }

                    // Switch Button
                    Button(action: {
                        onSwitch(apiKey)
                        apiKey = ""
                    }) {
                        HStack {
                            if viewModel.isLoading {
                                ProgressView()
                                    .controlSize(.small)
                            }

                            Text(viewModel.currentProvider == provider ? "重新配置" : "切换到此服务商")
                                .fontWeight(.semibold)
                        }
                        .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .controlSize(.large)
                    .disabled(viewModel.isLoading)
                }
                .padding(.horizontal, 40)

                Spacer()
            }
        }
        .navigationTitle(provider.rawValue)
        .sheet(isPresented: $showingModelConfig) {
            ModelConfigurationView(viewModel: viewModel, provider: provider)
        }
        .onAppear {
            // 加载已保存的 API Key（仅显示占位符）
            if let savedKey = viewModel.getAPIKey(for: provider) {
                apiKey = String(savedKey.prefix(8)) + "..."
            }
        }
    }
}

struct ConfigRow: View {
    let label: String
    let value: String

    var body: some View {
        HStack {
            Text(label)
                .foregroundStyle(.secondary)
                .frame(width: 80, alignment: .leading)

            Text(value)
                .textSelection(.enabled)

            Spacer()

            Button(action: {
                NSPasteboard.general.clearContents()
                NSPasteboard.general.setString(value, forType: .string)
            }) {
                Image(systemName: "doc.on.doc")
            }
            .buttonStyle(.borderless)
            .help("复制")
        }
    }
}
