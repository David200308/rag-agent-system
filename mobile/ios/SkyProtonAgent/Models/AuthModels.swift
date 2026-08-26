import Foundation

struct OTPRequest: Codable {
    let email: String
}

struct OTPVerifyRequest: Codable {
    let email: String
    let code: String
}

struct AuthToken: Codable {
    let token: String
}

struct ValidateResponse: Codable {
    let valid: Bool
    let email: String?
}

struct PasskeyBeginResponse: Decodable {
    let challenge: String
    let rpId: String
    let timeout: Int?
    let allowCredentials: [PasskeyAllowedCredential]?
    let userVerification: String?
}

struct PasskeyAllowedCredential: Decodable {
    let id: String
    let type: String
    let transports: [String]?
}
