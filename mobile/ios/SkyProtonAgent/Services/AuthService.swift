import Foundation
import AuthenticationServices

final class AuthService {
    static let shared = AuthService()
    private let client = APIClient.shared

    func requestOTP(email: String) async throws {
        struct Msg: Decodable { let message: String }
        let _: Msg = try await client.post("/api/v1/auth/request-otp",
                                           body: ["email": email], auth: false)
    }

    func verifyOTP(email: String, code: String) async throws -> String {
        let res: AuthToken = try await client.post("/api/v1/auth/verify-otp",
                                                   body: ["email": email, "code": code], auth: false)
        KeychainHelper.shared.save(key: "jwt_token", value: res.token)
        return res.token
    }

    func validate() async throws -> String? {
        let res: ValidateResponse = try await client.get("/api/v1/auth/validate")
        return res.valid ? res.email : nil
    }

    func logout() {
        KeychainHelper.shared.delete(key: "jwt_token")
    }

    var isLoggedIn: Bool {
        KeychainHelper.shared.read(key: "jwt_token") != nil
    }

    // MARK: – Passkey API calls

    func beginPasskeyAuth(email: String?) async throws -> PasskeyBeginResponse {
        struct Body: Encodable { let email: String? }
        return try await client.post("/api/v1/auth/passkey/authenticate/begin",
                                     body: Body(email: email), auth: false)
    }

    func finishPasskeyAuth(assertion: ASAuthorizationPlatformPublicKeyCredentialAssertion) async throws {
        struct Response: Encodable {
            struct Inner: Encodable {
                let clientDataJSON: String
                let authenticatorData: String
                let signature: String
                let userHandle: String?
            }
            let id: String
            let rawId: String
            let type: String
            let response: Inner
        }

        func b64url(_ d: Data) -> String {
            d.base64EncodedString()
                .replacingOccurrences(of: "+", with: "-")
                .replacingOccurrences(of: "/", with: "_")
                .replacingOccurrences(of: "=", with: "")
        }

        let credId = b64url(assertion.credentialID)
        let body = Response(
            id: credId, rawId: credId, type: "public-key",
            response: .init(
                clientDataJSON:    b64url(assertion.rawClientDataJSON),
                authenticatorData: b64url(assertion.rawAuthenticatorData),
                signature:         b64url(assertion.signature),
                userHandle:        assertion.userID.isEmpty ? nil : b64url(assertion.userID)
            )
        )
        let token: AuthToken = try await client.post(
            "/api/v1/auth/passkey/authenticate/finish", body: body, auth: false)
        KeychainHelper.shared.save(key: "jwt_token", value: token.token)
    }
}
