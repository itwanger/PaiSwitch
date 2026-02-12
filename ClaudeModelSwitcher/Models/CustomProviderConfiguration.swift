import Foundation

/// Configuration for custom model providers
struct CustomProviderConfiguration: Identifiable, Codable, Equatable {
    let id: UUID
    var name: String
    var baseURL: String
    var defaultModel: String
    var fastModel: String?
    var iconName: String

    init(
        id: UUID = UUID(),
        name: String,
        baseURL: String,
        defaultModel: String,
        fastModel: String? = nil,
        iconName: String = "gearshape.2"
    ) {
        self.id = id
        self.name = name
        self.baseURL = baseURL
        self.defaultModel = defaultModel
        self.fastModel = fastModel
        self.iconName = iconName
    }

    var hasFastModel: Bool {
        fastModel != nil && !fastModel!.isEmpty
    }
}
