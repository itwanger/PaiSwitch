import Foundation

enum ModelProvider: String, CaseIterable, Identifiable, Codable {
    case claude = "Claude 官方"
    case deepseek = "DeepSeek"
    case zhipu = "智谱 AI"
    case openrouter = "OpenRouter"
    case siliconflow = "硅基流动"
    case volcano = "火山引擎"
    case custom = "自定义"

    var id: String { rawValue }

    var baseURL: String? {
        switch self {
        case .claude:
            return nil // 移除 BASE_URL 使用官方 API
        case .deepseek:
            return "https://api.deepseek.com/anthropic"
        case .zhipu:
            return "https://open.bigmodel.cn/api/anthropic"
        case .openrouter:
            return "https://openrouter.ai/api"
        case .siliconflow:
            return "https://api.siliconflow.cn/v1"
        case .volcano:
            return "https://ark.cn-beijing.volces.com/api/v3"
        case .custom:
            return nil
        }
    }

    /// Default model name - reads from UserDefaults or returns builtin default
    var defaultModel: String {
        let key = "\(self.rawValue)_default_model"
        if let customModel = UserDefaults.standard.string(forKey: key) {
            return customModel
        }
        return builtinDefaultModel
    }

    /// Built-in default model name (used when no custom value is set)
    private var builtinDefaultModel: String {
        switch self {
        case .claude:
            return "claude-sonnet-4"
        case .deepseek:
            return "deepseek-chat"
        case .zhipu:
            return "glm-4.7"
        case .openrouter:
            return "openrouter/pony-alpha"
        case .siliconflow:
            return "Qwen/Qwen2.5-72B-Instruct"
        case .volcano:
            return "doubao-pro-32k"
        case .custom:
            return ""
        }
    }

    /// Fast model name - reads from UserDefaults or returns builtin default
    var fastModel: String? {
        let key = "\(self.rawValue)_fast_model"
        if let customModel = UserDefaults.standard.string(forKey: key), !customModel.isEmpty {
            return customModel
        }
        return builtinFastModel
    }

    /// Built-in fast model name
    private var builtinFastModel: String? {
        switch self {
        case .zhipu:
            return "glm-4.7-air"
        case .deepseek:
            return "deepseek-chat"
        default:
            return nil
        }
    }

    /// Set a custom default model name
    func setDefaultModel(_ model: String) {
        let key = "\(self.rawValue)_default_model"
        if model == builtinDefaultModel {
            UserDefaults.standard.removeObject(forKey: key)
        } else {
            UserDefaults.standard.set(model, forKey: key)
        }
    }

    /// Set a custom fast model name
    func setFastModel(_ model: String?) {
        let key = "\(self.rawValue)_fast_model"
        if let model = model, !model.isEmpty {
            UserDefaults.standard.set(model, forKey: key)
        } else {
            UserDefaults.standard.removeObject(forKey: key)
        }
    }

    /// Check if this provider supports fast models
    var supportsFastModel: Bool {
        return builtinFastModel != nil
    }

    /// Check if default model is using custom value
    var isUsingCustomDefaultModel: Bool {
        let key = "\(self.rawValue)_default_model"
        return UserDefaults.standard.string(forKey: key) != nil
    }

    /// Check if fast model is using custom value
    var isUsingCustomFastModel: Bool {
        let key = "\(self.rawValue)_fast_model"
        return UserDefaults.standard.string(forKey: key) != nil
    }

    var iconName: String {
        switch self {
        case .claude:
            return "brain"
        case .deepseek:
            return "waveform.path"
        case .zhipu:
            return "sparkles"
        case .openrouter:
            return "arrow.triangle.branch"
        case .siliconflow:
            return "cpu.fill"
        case .volcano:
            return "flame"
        case .custom:
            return "gearshape.2"
        }
    }

    var description: String {
        switch self {
        case .claude:
            return "Anthropic 官方 API"
        case .deepseek:
            return "DeepSeek V3 - 高性价比"
        case .zhipu:
            return "智谱 \(defaultModel) - 国产大模型"
        case .openrouter:
            return "OpenRouter - 多模型聚合"
        case .siliconflow:
            return "硅基流动 - 国内推理平台"
        case .volcano:
            return "火山引擎 - 字节跳动"
        case .custom:
            return "自定义服务商配置"
        }
    }
}
