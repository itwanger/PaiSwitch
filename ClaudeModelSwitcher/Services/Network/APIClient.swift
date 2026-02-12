import Foundation

enum APIError: Error, LocalizedError {
    case invalidURL
    case noData
    case decodingError(Error)
    case serverError(Int, String)
    case unauthorized
    case networkError(Error)

    var errorDescription: String? {
        switch self {
        case .invalidURL:
            return "无效的 URL"
        case .noData:
            return "无数据返回"
        case .decodingError(let error):
            return "数据解析失败: \(error.localizedDescription)"
        case .serverError(let code, let message):
            return "服务器错误 (\(code)): \(message)"
        case .unauthorized:
            return "未授权，请重新登录"
        case .networkError(let error):
            return "网络错误: \(error.localizedDescription)"
        }
    }
}

struct APIResponse<T: Decodable>: Decodable {
    let code: Int
    let message: String
    let data: T?
}

class APIClient {
    static let shared = APIClient()

    var baseURL: String {
        UserDefaults.standard.string(forKey: "api_base_url") ?? "http://localhost:8080/api/v1"
    }

    var token: String? {
        KeychainManager.shared.getAPIToken()
    }

    private init() {}

    func get<T: Decodable>(_ endpoint: String) async throws -> T {
        try await request(endpoint, method: "GET", body: nil as EmptyBody?)
    }

    func post<T: Decodable, B: Encodable>(_ endpoint: String, body: B) async throws -> T {
        try await request(endpoint, method: "POST", body: body)
    }

    func put<T: Decodable, B: Encodable>(_ endpoint: String, body: B) async throws -> T {
        try await request(endpoint, method: "PUT", body: body)
    }

    func delete<T: Decodable>(_ endpoint: String) async throws -> T {
        try await request(endpoint, method: "DELETE", body: nil as EmptyBody?)
    }

    private func request<T: Decodable, B: Encodable>(_ endpoint: String, method: String, body: B?) async throws -> T {
        guard let url = URL(string: baseURL + endpoint) else {
            throw APIError.invalidURL
        }

        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        if let token = token {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }

        if let body = body {
            let encoder = JSONEncoder()
            encoder.keyEncodingStrategy = .convertToSnakeCase
            request.httpBody = try encoder.encode(body)
        }

        do {
            let (data, response) = try await URLSession.shared.data(for: request)

            guard let httpResponse = response as? HTTPURLResponse else {
                throw APIError.noData
            }

            if httpResponse.statusCode == 401 {
                throw APIError.unauthorized
            }

            let decoder = JSONDecoder()
            decoder.keyDecodingStrategy = .convertFromSnakeCase

            if httpResponse.statusCode >= 400 {
                let errorResponse = try? decoder.decode(APIResponse<EmptyData>.self, from: data)
                throw APIError.serverError(httpResponse.statusCode, errorResponse?.message ?? "Unknown error")
            }

            let apiResponse = try decoder.decode(APIResponse<T>.self, from: data)

            guard let result = apiResponse.data else {
                throw APIError.noData
            }

            return result
        } catch let error as APIError {
            throw error
        } catch {
            throw APIError.networkError(error)
        }
    }
}

private struct EmptyBody: Encodable {}
private struct EmptyData: Decodable {}
