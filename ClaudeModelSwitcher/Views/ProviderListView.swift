import SwiftUI

struct ProviderListView: View {
    @ObservedObject var viewModel: MainViewModel
    @Binding var selectedProvider: ModelProvider?
    @Binding var selectedCustomProvider: CustomProviderConfiguration?
    @State private var customProviders: [CustomProviderConfiguration] = []
    @State private var showingAddProvider = false

    var body: some View {
        List {
            builtInProvidersSection
            customProvidersSection
        }
        .listStyle(.sidebar)
        .navigationTitle("模型服务商")
        .sheet(isPresented: $showingAddProvider) {
            AddCustomProviderView(viewModel: viewModel) { newProvider in
                CustomProviderManager.shared.saveProvider(newProvider)
                loadCustomProviders()
                viewModel.successMessage = "自定义服务商已添加"
            }
        }
        .onAppear {
            loadCustomProviders()
        }
    }

    private var builtInProvidersSection: some View {
        Group {
            sectionHeader("内置服务商")
            ForEach(ModelProvider.allCases.filter { $0 != .custom }, id: \.rawValue) { provider in
                providerRow(for: provider)
            }
        }
    }

    private var customProvidersSection: some View {
        Group {
            sectionHeader("自定义服务商")
                .padding(.top, 8)

            ForEach(customProviders) { provider in
                customProviderRow(for: provider)
            }

            addCustomProviderButton
        }
    }

    private func sectionHeader(_ title: String) -> some View {
        Text(title)
            .font(.caption)
            .foregroundStyle(.secondary)
            .listRowSeparator(.hidden)
    }

    private func providerRow(for provider: ModelProvider) -> some View {
        ProviderRow(
            provider: provider,
            isActive: viewModel.currentProvider == provider,
            hasAPIKey: viewModel.hasAPIKey(for: provider)
        )
        .tag(provider)
        .onTapGesture {
            selectedProvider = provider
            selectedCustomProvider = nil
        }
        .listRowSeparator(.hidden)
    }

    private func customProviderRow(for provider: CustomProviderConfiguration) -> some View {
        CustomProviderListRow(
            provider: provider,
            isActive: viewModel.currentProvider == .custom,
            hasAPIKey: viewModel.hasCustomAPIKey(for: provider)
        )
        .onTapGesture {
            selectedCustomProvider = provider
            selectedProvider = nil
        }
        .listRowSeparator(.hidden)
    }

    private var addCustomProviderButton: some View {
        Button(action: { showingAddProvider = true }) {
            HStack(spacing: 8) {
                Image(systemName: "plus.circle.fill")
                    .foregroundStyle(Color.accentColor)
                Text("添加自定义服务商")
                    .fontWeight(.medium)
            }
        }
        .buttonStyle(.plain)
    }

    private func loadCustomProviders() {
        customProviders = CustomProviderManager.shared.loadProviders()
    }
}

struct ProviderRow: View {
    let provider: ModelProvider
    let isActive: Bool
    let hasAPIKey: Bool

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: provider.iconName)
                .font(.title2)
                .foregroundStyle(isActive ? Color.accentColor : .secondary)
                .frame(width: 28)

            VStack(alignment: .leading, spacing: 2) {
                HStack {
                    Text(provider.rawValue)
                        .fontWeight(.medium)

                    if isActive {
                        Image(systemName: "checkmark.circle.fill")
                            .foregroundStyle(.green)
                            .font(.caption)
                    }
                }

                Text(provider.description)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }

            Spacer()

            if !hasAPIKey && provider != .claude {
                Image(systemName: "key.fill")
                    .foregroundStyle(.orange)
                    .font(.caption)
            }
        }
        .padding(.vertical, 4)
    }
}

struct CustomProviderListRow: View {
    let provider: CustomProviderConfiguration
    let isActive: Bool
    let hasAPIKey: Bool

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: provider.iconName)
                .font(.title2)
                .foregroundStyle(isActive ? Color.accentColor : .secondary)
                .frame(width: 28)

            VStack(alignment: .leading, spacing: 2) {
                HStack {
                    Text(provider.name)
                        .fontWeight(.medium)

                    if isActive {
                        Image(systemName: "checkmark.circle.fill")
                            .foregroundStyle(.green)
                            .font(.caption)
                    }
                }

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
        }
        .padding(.vertical, 4)
    }
}
