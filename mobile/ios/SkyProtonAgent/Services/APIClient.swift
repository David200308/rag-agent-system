import Foundation
import CryptoKit

private let appVersion = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0.0"

enum APIError: LocalizedError {
    case invalidURL
    case httpError(Int, String)
    case decodingError(Error)
    case unauthorized
    case unknown(Error)

    var errorDescription: String? {
        switch self {
        case .invalidURL:              return "Invalid URL"
        case .httpError(let c, let m): return "HTTP \(c): \(m)"
        case .decodingError(let e):    return "Decode error: \(e.localizedDescription)"
        case .unauthorized:            return "Unauthorized — please log in again"
        case .unknown(let e):          return e.localizedDescription
        }
    }

    /// True for a Task/URLSession cancellation, which is an artifact of view
    /// lifecycle (e.g. pull-to-refresh racing a teardown), not a real failure —
    /// callers should skip showing it as an error.
    var isCancellation: Bool {
        guard case .unknown(let e) = self else { return false }
        return (e as? URLError)?.code == .cancelled || e is CancellationError
    }
}

final class APIClient {
    static let shared = APIClient()

    var baseURL: String {
        if let override = UserDefaults.standard.string(forKey: "serverBaseURL"), !override.isEmpty {
            return override
        }
        if let plistURL = Bundle.main.object(forInfoDictionaryKey: "SERVER_BASE_URL") as? String,
           !plistURL.isEmpty, plistURL != "$(SERVER_BASE_URL)" {
            return plistURL
        }
        return "http://localhost:8080"
    }

    var webFrontendURL: String {
        if let override = UserDefaults.standard.string(forKey: "webFrontendURL"), !override.isEmpty {
            return override
        }
        return baseURL
    }

    private var iosSecret: String? {
        let s = Bundle.main.object(forInfoDictionaryKey: "CLIENT_IOS_SECRET") as? String
        guard let s, !s.isEmpty, s != "$(CLIENT_IOS_SECRET)" else { return nil }
        return s
    }

    private var token: String? {
        KeychainHelper.shared.read(key: "jwt_token")
    }

    private let decoder: JSONDecoder = {
        let d = JSONDecoder()
        d.keyDecodingStrategy = .convertFromSnakeCase
        return d
    }()

    private let encoder: JSONEncoder = {
        let e = JSONEncoder()
        e.keyEncodingStrategy = .convertToSnakeCase
        return e
    }()

    func get<T: Decodable>(_ path: String, auth: Bool = true) async throws -> T {
        let req = try buildRequest(method: "GET", path: path, body: nil as String?, auth: auth)
        return try await perform(req)
    }

    func post<B: Encodable, T: Decodable>(_ path: String, body: B, auth: Bool = true) async throws -> T {
        let req = try buildRequest(method: "POST", path: path, body: body, auth: auth)
        return try await perform(req)
    }

    func put<B: Encodable, T: Decodable>(_ path: String, body: B, auth: Bool = true) async throws -> T {
        let req = try buildRequest(method: "PUT", path: path, body: body, auth: auth)
        return try await perform(req)
    }

    func patch<B: Encodable, T: Decodable>(_ path: String, body: B, auth: Bool = true) async throws -> T {
        let req = try buildRequest(method: "PATCH", path: path, body: body, auth: auth)
        return try await perform(req)
    }

    func delete(_ path: String, auth: Bool = true) async throws {
        let req = try buildRequest(method: "DELETE", path: path, body: nil as String?, auth: auth)
        try await performNoContent(req)
    }

    /// POST/PUT with a raw `[String: Any]` body instead of an `Encodable` type.
    /// The financial create/update endpoints deserialize the request body into a
    /// server-side `Map<String, Object>` and look up fields by exact camelCase key
    /// (e.g. "stockAmount") — the shared `encoder` above converts keys to snake_case,
    /// which those endpoints would silently ignore. `JSONSerialization` sends keys
    /// verbatim, so it's used for these instead.
    func postRaw(_ path: String, body: [String: Any], auth: Bool = true) async throws {
        let req = try buildRawRequest(method: "POST", path: path, body: body, auth: auth)
        try await performNoContent(req)
    }

    func putRaw(_ path: String, body: [String: Any], auth: Bool = true) async throws {
        let req = try buildRawRequest(method: "PUT", path: path, body: body, auth: auth)
        try await performNoContent(req)
    }

    private func buildRawRequest(method: String, path: String, body: [String: Any], auth: Bool) throws -> URLRequest {
        guard let url = URL(string: baseURL + path) else { throw APIError.invalidURL }
        var req = URLRequest(url: url)
        req.httpMethod = method
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if auth, let t = token {
            req.setValue("Bearer \(t)", forHTTPHeaderField: "Authorization")
        }
        req.httpBody = try JSONSerialization.data(withJSONObject: body)
        addClientIdentityHeaders(to: &req, method: method, path: path)
        return req
    }

    private func performNoContent(_ req: URLRequest) async throws {
        do {
            let (data, response) = try await URLSession.shared.data(for: req)
            guard let http = response as? HTTPURLResponse else { throw APIError.invalidURL }
            if http.statusCode == 401 { throw APIError.unauthorized }
            if http.statusCode == 403 { throw APIError.httpError(403, "You don't have permission to do that.") }
            if !(200..<300).contains(http.statusCode) {
                let msg = String(data: data, encoding: .utf8) ?? "Unknown error"
                throw APIError.httpError(http.statusCode, msg)
            }
        } catch let e as APIError {
            throw e
        } catch {
            throw APIError.unknown(error)
        }
    }

    private func buildRequest<B: Encodable>(method: String, path: String, body: B?, auth: Bool) throws -> URLRequest {
        guard let url = URL(string: baseURL + path) else { throw APIError.invalidURL }
        var req = URLRequest(url: url)
        req.httpMethod = method
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if auth, let t = token {
            req.setValue("Bearer \(t)", forHTTPHeaderField: "Authorization")
        }
        if let b = body {
            req.httpBody = try encoder.encode(b)
        }
        addClientIdentityHeaders(to: &req, method: method, path: path)
        return req
    }

    // MARK: – Client identity (X-Mobile-Ios-*)

    private func addClientIdentityHeaders(to req: inout URLRequest, method: String, path: String) {
        guard let secret = iosSecret else { return }
        let timestamp = Int(Date().timeIntervalSince1970)
        // Server verifies against getRequestURI() which excludes the query string
        let uriPath   = path.components(separatedBy: "?").first ?? path
        let message   = "ios:\(appVersion):\(method.uppercased()):\(uriPath):\(timestamp)"
        guard let keyData = secret.data(using: .utf8) else { return }
        let key  = SymmetricKey(data: keyData)
        let hmac = HMAC<SHA256>.authenticationCode(for: Data(message.utf8), using: key)
        let sig  = Data(hmac).base64EncodedString()
        req.setValue(sig,            forHTTPHeaderField: "X-Mobile-Ios-Signature")
        req.setValue("\(timestamp)", forHTTPHeaderField: "X-Mobile-Ios-Timestamp")
        req.setValue(appVersion,     forHTTPHeaderField: "X-Mobile-Ios-Version")
    }

    private func perform<T: Decodable>(_ req: URLRequest) async throws -> T {
        do {
            let (data, response) = try await URLSession.shared.data(for: req)
            guard let http = response as? HTTPURLResponse else { throw APIError.invalidURL }
            if http.statusCode == 401 { throw APIError.unauthorized }
            if http.statusCode == 404 { throw APIError.httpError(404, "Not found") }
            if !(200..<300).contains(http.statusCode) {
                let msg = String(data: data, encoding: .utf8) ?? "Unknown error"
                throw APIError.httpError(http.statusCode, msg)
            }
            if T.self == EmptyResponse.self {
                return EmptyResponse() as! T
            }
            return try decoder.decode(T.self, from: data)
        } catch let e as APIError {
            throw e
        } catch let e as DecodingError {
            throw APIError.decodingError(e)
        } catch {
            throw APIError.unknown(error)
        }
    }
}

struct EmptyResponse: Decodable {}
