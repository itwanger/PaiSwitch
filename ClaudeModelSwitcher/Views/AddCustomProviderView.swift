import SwiftUI

struct AddCustomProviderView: View {
    @ObservedObject var viewModel: MainViewModel
    let editingProvider: CustomProviderConfiguration?
    let onSave: (CustomProviderConfiguration) -> Void

    @Environment(\.dismiss) private var dismiss

    @State private var name: String
    @State private var baseURL: String
    @State private var defaultModel: String
    @State private var fastModel: String
    @State private var apiKey: String
    @State private var showAPIKey: Bool = false
    @State private var hasAPIKeySaved: Bool

    private let keychainManager = KeychainManager.shared
    private var isEditing: Bool { editingProvider != nil }

    init(viewModel: MainViewModel, editingProvider: CustomProviderConfiguration? = nil, onSave: @escaping (CustomProviderConfiguration) -> Void) {
        self.viewModel = viewModel
        self.editingProvider = editingProvider
        self.onSave = onSave

        if let provider = editingProvider {
            self._name = State(initialValue: provider.name)
            self._baseURL = State(initialValue: provider.baseURL)
            self._defaultModel = State(initialValue: provider.defaultModel)
            self._fastModel = State(initialValue: provider.fastModel ?? "")
            self._apiKey = State(initialValue: "")
            self._hasAPIKeySaved = State(initialValue: keychainManager.hasCustomAPIKey(for: provider.id.uuidString))
        } else {
            self._name = State(initialValue: "")
            self._baseURL = State(initialValue: "")
            self._defaultModel = State(initialValue: "")
            self._fastModel = State(initialValue: "")
            self._apiKey = State(initialValue: "")
            self._hasAPIKeySaved = State(initialValue: false)
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            Form {
                GroupBox {
                    VStack(alignment: .leading, spacing: 12) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("服务商名称")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                            TextField("例如: My AI Service", text: $name)
                                .textFieldStyle(.roundedBorder)
                        }

                        VStack(alignment: .leading, spacing: 4) {
                            Text("API 地址")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                            TextField("https://api.example.com/v1", text: $baseURL)
                                .textFieldStyle(.roundedBorder)
                        }

                        VStack(alignment: .leading, spacing: 4) {
                            Text("默认模型")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                            TextField("例如: gpt-4", text: $defaultModel)
                                .textFieldStyle(.roundedBorder)
                        }

                        VStack(alignment: .leading, spacing: 4) {
                            HStack {
                                Text("快速模型")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)

                                Text("(可选)")
                                    .font(.caption2)
                                    .foregroundStyle(.tertiary)
                            }

                            TextField("例如: gpt-3.5-turbo", text: $fastModel)
                                .textFieldStyle(.roundedBorder)
                        }

                        Divider()

                        VStack(alignment: .leading, spacing: 4) {
                            Text("API Key")
                                .font(.caption)
                                .foregroundStyle(.secondary)

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

                            if hasAPIKeySaved {
                                HStack(spacing: 4) {
                                    Image(systemName: "checkmark.circle.fill")
                                        .foregroundStyle(.green)
                                        .font(.caption)
                                    Text("已保存 API Key（留空使用已保存的密钥）")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
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

                Button(isEditing ? "更新" : "添加") {
                    saveProvider()
                }
                .keyboardShortcut(.defaultAction)
                .disabled(!isValid)
                .buttonStyle(.borderedProminent)
                Spacer()
            }
            .padding()
        }
        .frame(width: 450, height: 450)
    }

    private var isValid: Bool {
        !name.isEmpty && !baseURL.isEmpty && !defaultModel.isEmpty
    }

    private func saveProvider() {
        let provider: CustomProviderConfiguration

        if let existing = editingProvider {
            provider = CustomProviderConfiguration(
                id: existing.id,
                name: name,
                baseURL: baseURL,
                defaultModel: defaultModel,
                fastModel: fastModel.isEmpty ? nil : fastModel,
                iconName: existing.iconName
            )
        } else {
            provider = CustomProviderConfiguration(
                name: name,
                baseURL: baseURL,
                defaultModel: defaultModel,
                fastModel: fastModel.isEmpty ? nil : fastModel
            )
        }

        // Save API key if provided
        if !apiKey.isEmpty {
            try? keychainManager.saveCustomAPIKey(apiKey, for: provider.id.uuidString)
        }

        onSave(provider)
        dismiss()
    }
}

#Preview {
    AddCustomProviderView(viewModel: MainViewModel()) { _ in }
}
