import Foundation

/// Model configuration data structure
struct ModelConfiguration: Codable {
    var provider: ModelProvider
    var defaultModel: String
    var fastModel: String?

    var hasFastModel: Bool {
        fastModel != nil && !fastModel!.isEmpty
    }
}
