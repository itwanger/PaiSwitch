import SwiftUI

struct CustomProviderListView: View {
    @ObservedObject var viewModel: MainViewModel
    @State private var providers: [CustomProviderConfiguration]
    @State private var showingAddProvider = false
    @State private var editingProvider: CustomProviderConfiguration?

    init(viewModel: MainViewModel) {
        self.viewModel = viewModel
        self._providers = State(initialValue: CustomProviderManager.shared.loadProviders())
    }

    var body: some View {
        Group {
            if providers.isEmpty {
                emptyState
            } else {
                List {
                    ForEach(providers) { provider in
                        CustomProviderRow(
                            provider: provider,
                            hasAPIKey: hasAPIKey(for: provider),
                            onEdit: { editingProvider = provider },
                            onDelete: { deleteProvider(provider) }
                        )
                    }
                }
            }
        }
        .navigationTitle("自定义服务商")
        .toolbar {
            ToolbarItem(placement: .automatic) {
                Button(action: { showingAddProvider = true }) {
                    Label("添加", systemImage: "plus")
                }
            }
        }
        .sheet(isPresented: $showingAddProvider) {
            AddCustomProviderView(viewModel: viewModel) { newProvider in
                addProvider(newProvider)
            }
        }
        .sheet(item: $editingProvider) { provider in
            AddCustomProviderView(
                viewModel: viewModel,
                editingProvider: provider
            ) { updatedProvider in
                updateProvider(updatedProvider)
            }
        }
        .onAppear {
            reloadProviders()
        }
    }

    private var emptyState: some View {
        VStack(spacing: 12) {
            Image(systemName: "gearshape.2")
                .font(.system(size: 48))
                .foregroundStyle(.secondary)

            Text("暂无自定义服务商")
                .font(.headline)

            Text("点击右上角「添加」按钮创建自定义配置")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 100)
    }

    private func reloadProviders() {
        providers = CustomProviderManager.shared.loadProviders()
    }

    private func addProvider(_ provider: CustomProviderConfiguration) {
        CustomProviderManager.shared.saveProvider(provider)
        reloadProviders()
    }

    private func updateProvider(_ provider: CustomProviderConfiguration) {
        CustomProviderManager.shared.saveProvider(provider)
        reloadProviders()
    }

    private func deleteProvider(_ provider: CustomProviderConfiguration) {
        try? KeychainManager.shared.deleteCustomAPIKey(for: provider.id.uuidString)
        CustomProviderManager.shared.deleteProvider(provider)
        reloadProviders()
    }

    private func hasAPIKey(for provider: CustomProviderConfiguration) -> Bool {
        KeychainManager.shared.hasCustomAPIKey(for: provider.id.uuidString)
    }
}

struct CustomProviderRow: View {
    let provider: CustomProviderConfiguration
    let hasAPIKey: Bool
    let onEdit: () -> Void
    let onDelete: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: provider.iconName)
                .font(.title2)
                .foregroundStyle(.secondary)
                .frame(width: 28)

            VStack(alignment: .leading, spacing: 2) {
                Text(provider.name)
                    .fontWeight(.medium)

                Text(provider.baseURL)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }

            Spacer()

            if !hasAPIKey {
                Image(systemName: "key.fill")
                    .foregroundStyle(.orange)
                    .font(.caption)
            }

            HStack(spacing: 4) {
                Button(action: onEdit) {
                    Image(systemName: "pencil")
                        .font(.caption)
                }
                .buttonStyle(.borderless)

                Button(action: onDelete) {
                    Image(systemName: "trash")
                        .font(.caption)
                        .foregroundStyle(.red)
                }
                .buttonStyle(.borderless)
            }
        }
        .padding(.vertical, 4)
    }
}
