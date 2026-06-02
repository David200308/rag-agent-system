import Foundation
import SwiftUI
import AuthenticationServices
import UIKit

// MARK: – Passkey helpers (same file = no cross-file dependency)

private enum PasskeyError: LocalizedError {
    case unexpectedCredentialType
    case invalidChallenge

    var errorDescription: String? {
        switch self {
        case .unexpectedCredentialType: return "Unexpected passkey credential type"
        case .invalidChallenge:         return "Invalid challenge received from server"
        }
    }
}

private extension Data {
    func base64URLEncoded() -> String {
        base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }

    static func fromBase64URL(_ string: String) -> Data? {
        var s = string
            .replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        while s.count % 4 != 0 { s += "=" }
        return Data(base64Encoded: s)
    }
}

private final class PasskeyAuthCoordinator: NSObject,
    ASAuthorizationControllerDelegate,
    ASAuthorizationControllerPresentationContextProviding
{
    private var continuation: CheckedContinuation<ASAuthorizationPlatformPublicKeyCredentialAssertion, Error>?
    private var authController: ASAuthorizationController?

    func performAuth(
        rpId: String,
        challenge: Data,
        allowedCredentials: [ASAuthorizationPlatformPublicKeyCredentialDescriptor]
    ) async throws -> ASAuthorizationPlatformPublicKeyCredentialAssertion {
        try await withCheckedThrowingContinuation { [weak self] continuation in
            guard let self else { continuation.resume(throwing: CancellationError()); return }
            self.continuation = continuation

            let provider = ASAuthorizationPlatformPublicKeyCredentialProvider(relyingPartyIdentifier: rpId)
            let request = provider.createCredentialAssertionRequest(challenge: challenge)
            if !allowedCredentials.isEmpty { request.allowedCredentials = allowedCredentials }

            let controller = ASAuthorizationController(authorizationRequests: [request])
            controller.delegate = self
            controller.presentationContextProvider = self
            self.authController = controller
            controller.performRequests()
        }
    }

    func authorizationController(controller: ASAuthorizationController,
                                 didCompleteWithAuthorization authorization: ASAuthorization) {
        defer { authController = nil }
        guard let cred = authorization.credential as? ASAuthorizationPlatformPublicKeyCredentialAssertion else {
            continuation?.resume(throwing: PasskeyError.unexpectedCredentialType); continuation = nil; return
        }
        continuation?.resume(returning: cred); continuation = nil
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        defer { authController = nil }
        continuation?.resume(throwing: error); continuation = nil
    }

    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first { $0.isKeyWindow } ?? UIWindow()
    }
}

// MARK: – AuthViewModel

@MainActor
final class AuthViewModel: ObservableObject {
    @Published var email: String = ""
    @Published var code: String = ""
    @Published var step: AuthStep = .email
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published var isAuthenticated = false
    @Published var currentUserEmail: String?

    enum AuthStep { case email, code }

    private let auth = AuthService.shared
    private let passkeyCoordinator = PasskeyAuthCoordinator()

    func checkAuth() async {
        guard auth.isLoggedIn else { return }
        do {
            if let email = try await auth.validate() {
                currentUserEmail = email
                isAuthenticated = true
            } else {
                auth.logout()
            }
        } catch {
            auth.logout()
        }
    }

    func requestOTP() async {
        guard !email.isBlank else { errorMessage = "Enter a valid email"; return }
        isLoading = true; errorMessage = nil
        do {
            try await auth.requestOTP(email: email)
            step = .code
        } catch {
            errorMessage = error.localizedDescription
        }
        isLoading = false
    }

    func verifyOTP() async {
        guard !code.isBlank else { errorMessage = "Enter the 6-digit code"; return }
        isLoading = true; errorMessage = nil
        do {
            _ = try await auth.verifyOTP(email: email, code: code)
            currentUserEmail = email
            isAuthenticated = true
        } catch {
            errorMessage = error.localizedDescription
        }
        isLoading = false
    }

    func signInWithPasskey() async {
        isLoading = true; errorMessage = nil
        do {
            // 1. Get challenge from server
            let options = try await auth.beginPasskeyAuth(email: email.isBlank ? nil : email)

            // 2. Decode base64url challenge
            guard let challengeData = Data.fromBase64URL(options.challenge) else {
                throw PasskeyError.invalidChallenge
            }

            // 3. Build allowed credentials list
            let allowed: [ASAuthorizationPlatformPublicKeyCredentialDescriptor] = (options.allowCredentials ?? [])
                .compactMap { cred in
                    guard let id = Data.fromBase64URL(cred.id) else { return nil }
                    return ASAuthorizationPlatformPublicKeyCredentialDescriptor(credentialID: id)
                }

            // 4. Present Face ID / Touch ID
            let assertion = try await passkeyCoordinator.performAuth(
                rpId: options.rpId,
                challenge: challengeData,
                allowedCredentials: allowed
            )

            // 5. Send assertion to server
            try await auth.finishPasskeyAuth(assertion: assertion)

            // 6. Fetch authenticated email
            if let verifiedEmail = try await auth.validate() {
                currentUserEmail = verifiedEmail
                isAuthenticated = true
            }
        } catch let e as ASAuthorizationError where e.code == .canceled {
            // user dismissed — no error
        } catch {
            errorMessage = error.localizedDescription
        }
        isLoading = false
    }

    func logout() {
        auth.logout()
        isAuthenticated = false; currentUserEmail = nil
        email = ""; code = ""; step = .email
    }
}

private extension String {
    var isBlank: Bool { trimmingCharacters(in: .whitespaces).isEmpty }
}
