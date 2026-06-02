import SwiftUI

struct LoginView: View {
    @ObservedObject var vm: AuthViewModel

    var body: some View {
        ZStack(alignment: .bottom) {
            // White canvas
            Color.white.ignoresSafeArea()

            // Logo — centered in the upper portion
            VStack {
                Spacer()
                Image("Logo")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 200)
                    .colorMultiply(.black)
                Spacer()
                Spacer()
            }

            // Black bottom panel
            VStack(spacing: 12) {
                if vm.step == .email {
                    emailPanel
                } else {
                    codePanel
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 28)
            .padding(.bottom, 40)
            .frame(maxWidth: .infinity)
            .background(Color.black)
            .clipShape(
                UnevenRoundedRectangle(
                    topLeadingRadius: 36,
                    bottomLeadingRadius: 0,
                    bottomTrailingRadius: 0,
                    topTrailingRadius: 36
                )
            )
        }
        .ignoresSafeArea(edges: .bottom)
    }

    // MARK: – Email step

    private var emailPanel: some View {
        VStack(spacing: 12) {
            // Email field styled for dark background
            HStack {
                Image(systemName: "envelope")
                    .foregroundStyle(Color(.systemGray))
                    .frame(width: 20)
                TextField("", text: $vm.email, prompt: Text("Email address").foregroundStyle(Color(.systemGray)))
                    .keyboardType(.emailAddress)
                    .autocapitalization(.none)
                    .textContentType(.emailAddress)
                    .foregroundStyle(.white)
            }
            .padding(.horizontal, 18)
            .frame(height: 56)
            .background(Color(white: 0.13))
            .clipShape(Capsule())

            // Primary action
            actionButton(title: "Continue", style: .primary, loading: vm.isLoading) {
                Task { await vm.requestOTP() }
            }

            // Divider
            HStack {
                Rectangle().frame(height: 1).foregroundStyle(Color(white: 0.25))
                Text("or").font(.caption).foregroundStyle(Color(.systemGray))
                Rectangle().frame(height: 1).foregroundStyle(Color(white: 0.25))
            }

            // Passkey button
            actionButton(title: "Sign in with Passkey", style: .secondary, loading: false) {
                Task { await vm.signInWithPasskey() }
            }
            .overlay(
                HStack {
                    Image(systemName: "faceid")
                        .font(.system(size: 16, weight: .medium))
                        .foregroundStyle(Color(.systemGray))
                    Spacer()
                }
                .padding(.horizontal, 20)
                .allowsHitTesting(false)
            )

            if let err = vm.errorMessage {
                Text(err)
                    .font(.caption)
                    .foregroundStyle(Color(red: 1, green: 0.4, blue: 0.4))
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 8)
            }
        }
    }

    // MARK: – Code step

    private var codePanel: some View {
        VStack(spacing: 12) {
            VStack(spacing: 4) {
                Text("Check your email")
                    .font(.headline)
                    .foregroundStyle(.white)
                Text("Sent to \(vm.email)")
                    .font(.caption)
                    .foregroundStyle(Color(.systemGray))
            }
            .padding(.bottom, 4)

            // Code field
            HStack {
                Image(systemName: "key")
                    .foregroundStyle(Color(.systemGray))
                    .frame(width: 20)
                TextField("", text: $vm.code, prompt: Text("6-digit code").foregroundStyle(Color(.systemGray)))
                    .keyboardType(.numberPad)
                    .textContentType(.oneTimeCode)
                    .foregroundStyle(.white)
                    .font(.title3.monospacedDigit().weight(.medium))
            }
            .padding(.horizontal, 18)
            .frame(height: 56)
            .background(Color(white: 0.13))
            .clipShape(Capsule())

            actionButton(title: "Verify", style: .primary, loading: vm.isLoading) {
                Task { await vm.verifyOTP() }
            }

            Button {
                vm.step = .email
                vm.code = ""
                vm.errorMessage = nil
            } label: {
                Text("Use a different email")
                    .font(.subheadline)
                    .foregroundStyle(Color(.systemGray))
            }

            if let err = vm.errorMessage {
                Text(err)
                    .font(.caption)
                    .foregroundStyle(Color(red: 1, green: 0.4, blue: 0.4))
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 8)
            }
        }
    }

    // MARK: – Shared button

    private enum ButtonStyle { case primary, secondary }

    private func actionButton(title: String, style: ButtonStyle, loading: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            ZStack {
                if loading {
                    ProgressView().tint(style == .primary ? .black : .white)
                } else {
                    Text(title)
                        .font(.headline)
                        .foregroundStyle(style == .primary ? .black : .white)
                }
            }
            .frame(maxWidth: .infinity)
            .frame(height: 56)
        }
        .background(style == .primary ? .white : Color(white: 0.18))
        .clipShape(Capsule())
        .disabled(loading)
    }
}
