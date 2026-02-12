import Foundation

/// Manager for custom provider configurations
class CustomProviderManager {
    static let shared = CustomProviderManager()

    private let userDefaultsKey = "custom_providers"

    private init() {}

    /// Load all custom providers from UserDefaults
    func loadProviders() -> [CustomProviderConfiguration] {
        guard let data = UserDefaults.standard.data(forKey: userDefaultsKey),
              let providers = try? JSONDecoder().decode([CustomProviderConfiguration].self, from: data) else {
            return []
        }
        return providers
    }

    /// Save a custom provider configuration
    func saveProvider(_ provider: CustomProviderConfiguration) {
        var providers = loadProviders()

        // Update existing or add new
        if let index = providers.firstIndex(where: { $0.id == provider.id }) {
            providers[index] = provider
        } else {
            providers.append(provider)
        }

        saveProviders(providers)
    }

    /// Delete a custom provider
    func deleteProvider(_ provider: CustomProviderConfiguration) {
        var providers = loadProviders()
        providers.removeAll { $0.id == provider.id }
        saveProviders(providers)
    }

    /// Delete a custom provider by ID
    func deleteProvider(id: UUID) {
        var providers = loadProviders()
        providers.removeAll { $0.id == id }
        saveProviders(providers)
    }

    /// Get a provider by ID
    func getProvider(id: UUID) -> CustomProviderConfiguration? {
        loadProviders().first { $0.id == id }
    }

    private func saveProviders(_ providers: [CustomProviderConfiguration]) {
        if let data = try? JSONEncoder().encode(providers) {
            UserDefaults.standard.set(data, forKey: userDefaultsKey)
        }
    }
}
