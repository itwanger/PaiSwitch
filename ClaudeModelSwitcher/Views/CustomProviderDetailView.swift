import SwiftUI

struct CustomProviderDetailView: View {
    @ObservedObject var viewModel: MainViewModel
    let provider: CustomProviderConfiguration
    let onSwitch: (String?) -> Void

    @State private var apiKey: String = ""
    @State private var showAPIKey: Bool = false
    @State private var showingEdit = false

    var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                // Header
                VStack(spacing: 12) {
                    Image(systemName: provider.iconName)
                        .font(.system(size: 48))
                        .foregroundStyle(Color.accentColor)

                    Text(provider.name)
                        .font(.title)
                        .fontWeight(.bold)

                    Text(provider.baseURL)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .textSelection(.enabled)
                }
                .padding(.top, 40)

                // Configuration Info
                VStack(alignment: .leading, spacing: 16) {
                    HStack {
                        GroupBox("配置信息") {
                            VStack(alignment: .leading, spacing: 12) {
                                ConfigRow(label: "默认模型", value: provider.defaultModel)

                                if let fastModel = provider.fastModel, !fastModel.isEmpty {
                                    ConfigRow(label: "快速模型", value: fastModel)
                                }
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                        }

                        Button(action: { showingEdit = true }) {
                            Image(systemName: "pencil.circle")
                                .font(.title2)
                                .foregroundStyle(.secondary)
                        }
                        .buttonStyle(.borderless)
                        .help("编辑服务商")
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

                            if viewModel.hasCustomAPIKey(for: provider) {
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
                        onSwitch(apiKey.isEmpty ? nil : apiKey)
                        apiKey = ""
                    }) {
                        HStack {
                            if viewModel.isLoading {
                                ProgressView()
                                    .controlSize(.small)
                            }

                            Text("切换到此服务商")
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

                // Delete Button
                Button(action: deleteProvider) {
                    HStack {
                        Image(systemName: "trash")
                        Text("删除此服务商")
                    }
                    .foregroundStyle(.red)
                }
                .buttonStyle(.borderless)
                .padding(.bottom, 20)
            }
        }
        .navigationTitle(provider.name)
        .sheet(isPresented: $showingEdit) {
            AddCustomProviderView(
                viewModel: viewModel,
                editingProvider: provider
            ) { updatedProvider in
                CustomProviderManager.shared.saveProvider(updatedProvider)
                viewModel.successMessage = "服务商已更新"
            }
        }
        .onAppear {
            if let savedKey = viewModel.getCustomAPIKey(for: provider) {
                apiKey = String(savedKey.prefix(8)) + "..."
            }
        }
    }

    private func deleteProvider() {
        let alert = NSAlert()
        alert.messageText = "删除服务商"
        alert.informativeText = "确定要删除「\(provider.name)」吗？此操作无法撤销。"
        alert.alertStyle = .warning
        alert.addButton(withTitle: "删除")
        alert.addButton(withTitle: "取消")

        if alert.runModal() == .alertFirstButtonReturn {
            try? KeychainManager.shared.deleteCustomAPIKey(for: provider.id.uuidString)
            CustomProviderManager.shared.deleteProvider(provider)
            viewModel.successMessage = "服务商已删除"
        }
    }
}
