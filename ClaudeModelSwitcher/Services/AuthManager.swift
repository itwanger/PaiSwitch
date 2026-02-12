import Foundation
import Combine

@MainActor
class AuthManager: ObservableObject {
    static let shared = AuthManager()

    @Published var isLoggedIn: Bool = false
    @Published var currentUser: User?
    @Published var isLoading: Bool = false
    @Published var errorMessage: String?

    private let keychain = KeychainManager.shared
    private let service = PaiSwitchService.shared

    private init() {
        checkAuthStatus()
    }

    func checkAuthStatus() {
        if let token = keychain.getAPIToken() {
            isLoggedIn = true
            Task {
                await fetchUserInfo()
            }
        }
    }

    func login(username: String, password: String) async {
        isLoading = true
        errorMessage = nil

        do {
            let response = try await service.login(username: username, password: password)
            try keychain.saveAPIToken(response.token)
            currentUser = response.user
            isLoggedIn = true
        } catch {
            errorMessage = error.localizedDescription
        }

        isLoading = false
    }

    func register(username: String, email: String, password: String) async {
        isLoading = true
        errorMessage = nil

        do {
            let response = try await service.register(username: username, email: email, password: password)
            try keychain.saveAPIToken(response.token)
            currentUser = response.user
            isLoggedIn = true
        } catch {
            errorMessage = error.localizedDescription
        }

        isLoading = false
    }

    func logout() {
        try? keychain.deleteAPIToken()
        currentUser = nil
        isLoggedIn = false
    }

    private func fetchUserInfo() async {
        do {
            let config = try await service.getConfig()
            // Extract user info from config if needed
        } catch {
            if case APIError.unauthorized = error {
                logout()
            }
        }
    }

    func setServerURL(_ url: String) {
        UserDefaults.standard.set(url, forKey: "api_base_url")
    }

    func getServerURL() -> String {
        UserDefaults.standard.string(forKey: "api_base_url") ?? "http://localhost:8080/api/v1"
    }
}
