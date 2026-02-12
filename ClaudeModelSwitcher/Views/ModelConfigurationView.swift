import SwiftUI

struct ModelConfigurationView: View {
    @ObservedObject var viewModel: MainViewModel
    let provider: ModelProvider
    @Environment(\.dismiss) private var dismiss

    @State private var defaultModel: String
    @State private var fastModel: String
    @State private var hasChanges = false

    init(viewModel: MainViewModel, provider: ModelProvider) {
        self.viewModel = viewModel
        self.provider = provider
        self._defaultModel = State(initialValue: provider.defaultModel)
        self._fastModel = State(initialValue: provider.fastModel ?? "")
    }

    var body: some View {
        VStack(spacing: 0) {
            Form {
                GroupBox {
                    VStack(alignment: .leading, spacing: 12) {
                        // Provider info
                        HStack {
                            Image(systemName: provider.iconName)
                                .font(.title2)
                                .foregroundStyle(Color.accentColor)

                            VStack(alignment: .leading, spacing: 2) {
                                Text(provider.rawValue)
                                    .font(.headline)
                                Text(provider.description)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }

                        Divider()

                        // Default model
                        VStack(alignment: .leading, spacing: 4) {
                            Text("默认模型")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                            TextField("例如: glm-4.7", text: $defaultModel)
                                .textFieldStyle(.roundedBorder)
                                .onChange(of: defaultModel) { _ in
                                    hasChanges = true
                                }

                            if provider.isUsingCustomDefaultModel {
                                HStack(spacing: 4) {
                                    Image(systemName: "checkmark.circle.fill")
                                        .font(.caption)
                                        .foregroundStyle(.orange)
                                    Text("使用自定义模型名称")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                            }
                        }

                        if provider.supportsFastModel {
                            VStack(alignment: .leading, spacing: 4) {
                                Text("快速模型")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                TextField("例如: glm-4.7-air", text: $fastModel)
                                    .textFieldStyle(.roundedBorder)
                                    .onChange(of: fastModel) { _ in
                                        hasChanges = true
                                    }

                                if provider.isUsingCustomFastModel {
                                    HStack(spacing: 4) {
                                        Image(systemName: "checkmark.circle.fill")
                                            .font(.caption)
                                            .foregroundStyle(.orange)
                                        Text("使用自定义快速模型名称")
                                            .font(.caption)
                                            .foregroundStyle(.secondary)
                                    }
                                }
                            }
                        }

                        Divider()

                        // API URL (read-only)
                        VStack(alignment: .leading, spacing: 4) {
                            Text("API 地址")
                                .font(.caption)
                                .foregroundStyle(.secondary)

                            if let baseURL = provider.baseURL {
                                Text(baseURL)
                                    .font(.system(.body, design: .monospaced))
                                    .textSelection(.enabled)
                                    .foregroundStyle(.secondary)
                            } else {
                                Text("(使用官方 API)")
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(4)
                }
            }
            .formStyle(.grouped)
            .scrollDisabled(true)

            HStack {
                Spacer()
                Button("取消") {
                    dismiss()
                }
                .keyboardShortcut(.cancelAction)

                Button("保存") {
                    saveConfiguration()
                    dismiss()
                }
                .keyboardShortcut(.defaultAction)
                .disabled(!hasChanges || defaultModel.isEmpty)
                .buttonStyle(.borderedProminent)
                Spacer()
            }
            .padding()
        }
        .frame(width: 400, height: 320)
    }

    private func saveConfiguration() {
        provider.setDefaultModel(defaultModel)

        if provider.supportsFastModel {
            provider.setFastModel(fastModel.isEmpty ? nil : fastModel)
        }

        viewModel.successMessage = "模型配置已更新"
    }
}

#Preview {
    ModelConfigurationView(
        viewModel: MainViewModel(),
        provider: .zhipu
    )
}
