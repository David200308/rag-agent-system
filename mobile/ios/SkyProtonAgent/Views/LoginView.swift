import SwiftUI

struct LoginView: View {
    @ObservedObject var vm: AuthViewModel
    @FocusState private var isEmailFieldFocused: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var markProgress: CGFloat = 0

    // "Light content on dark chrome" — Theme.graphite is dark in both light
    // and dark mode, so the panel's own content doesn't need to swap by
    // theme, only the panel's background (Theme.graphite itself) does.
    fileprivate static let onPanelInk = Color(red: 0.961, green: 0.945, blue: 0.925)
    fileprivate static let onPanelSoft = onPanelInk.opacity(0.62)
    fileprivate static let onPanelFaint = onPanelInk.opacity(0.38)
    fileprivate static let onPanelField = Color.white.opacity(0.07)
    fileprivate static let onPanelFieldBorder = Color.white.opacity(0.13)
    fileprivate static let onPanelLine = Color.white.opacity(0.10)
    fileprivate static let ctaFill = onPanelInk
    fileprivate static let ctaInk = Color(red: 0.122, green: 0.118, blue: 0.110) // Theme.graphite, light-mode value
    fileprivate static let errorTint = Color(red: 0.851, green: 0.557, blue: 0.478)
    fileprivate static let errorText = Color(red: 0.914, green: 0.706, blue: 0.643)

    var body: some View {
        ZStack(alignment: .bottom) {
            Theme.background.ignoresSafeArea()

            // Brand zone — centered in the upper portion
            VStack(spacing: 14) {
                Spacer()
                mark
                Text("SKYPROTON")
                    .font(Theme.serif(26, weight: .medium))
                    .foregroundStyle(Theme.ink)
                Text("One agent for your finances and your trips.")
                    .font(.caption)
                    .foregroundStyle(Theme.inkSoft)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: 220)
                Spacer()
                Spacer()
            }

            // Graphite bottom panel
            VStack(spacing: 12) {
                stepIndicator
                if vm.step == .email {
                    emailPanel
                } else {
                    codePanel
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 22)
            .padding(.bottom, 40)
            .frame(maxWidth: .infinity)
            .background(Theme.graphite)
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
        .onAppear {
            if reduceMotion {
                markProgress = 1
            } else {
                withAnimation(.easeInOut(duration: 1.1).delay(0.15)) { markProgress = 1 }
            }
        }
    }

    private var mark: some View {
        Rectangle()
            .trim(from: 0, to: markProgress)
            .stroke(Theme.ink, style: StrokeStyle(lineWidth: 5, lineCap: .square, lineJoin: .miter))
            .frame(width: 38, height: 51)
    }

    private var stepIndicator: some View {
        HStack(spacing: 6) {
            Capsule().fill(Self.onPanelInk).frame(height: 3)
            Capsule().fill(vm.step == .code ? Self.onPanelInk : Self.onPanelLine).frame(height: 3)
        }
        .animation(.easeOut(duration: 0.3), value: vm.step)
    }

    // MARK: – Email step

    private var emailPanel: some View {
        VStack(spacing: 12) {
            VStack(spacing: 4) {
                Text("Welcome back")
                    .font(Theme.serif(20, weight: .medium))
                    .foregroundStyle(Self.onPanelInk)
                Text("Sign in to pick up where you left off.")
                    .font(.caption)
                    .foregroundStyle(Self.onPanelSoft)
            }
            .padding(.bottom, 4)

            // Email field styled for the graphite panel
            HStack {
                Image(systemName: "envelope")
                    .foregroundStyle(Self.onPanelSoft)
                    .frame(width: 20)
                TextField("", text: $vm.email, prompt: Text("Email address").foregroundStyle(Self.onPanelFaint))
                    .keyboardType(.emailAddress)
                    .autocapitalization(.none)
                    .textContentType(.emailAddress)
                    .foregroundStyle(Self.onPanelInk)
                    .focused($isEmailFieldFocused)
            }
            .padding(.horizontal, 18)
            .frame(height: 56)
            .background(Self.onPanelField)
            .clipShape(Capsule())
            .overlay(
                Capsule().stroke(isEmailFieldFocused ? Self.onPanelInk.opacity(0.55) : Self.onPanelFieldBorder, lineWidth: 1)
            )
            .animation(.easeOut(duration: 0.15), value: isEmailFieldFocused)

            // Primary action
            actionButton(title: "Continue", style: .primary, loading: vm.isLoading) {
                Task { await vm.requestOTP() }
            }

            // Divider
            HStack {
                Rectangle().frame(height: 1).foregroundStyle(Self.onPanelLine)
                Text("or").font(.caption2).foregroundStyle(Self.onPanelFaint)
                Rectangle().frame(height: 1).foregroundStyle(Self.onPanelLine)
            }

            // Passkey button
            actionButton(title: "Sign in with Passkey", style: .secondary, loading: false) {
                Task { await vm.signInWithPasskey() }
            }
            .overlay(
                HStack {
                    Image(systemName: "faceid")
                        .font(.system(size: 16, weight: .medium))
                        .foregroundStyle(Self.onPanelSoft)
                    Spacer()
                }
                .padding(.horizontal, 20)
                .allowsHitTesting(false)
            )

            if let err = vm.errorMessage {
                errorBanner(err)
            }
        }
    }

    // MARK: – Code step

    private var codePanel: some View {
        VStack(spacing: 12) {
            VStack(spacing: 4) {
                Text("Check your email")
                    .font(Theme.serif(20, weight: .medium))
                    .foregroundStyle(Self.onPanelInk)
                Text("Code sent to \(vm.email)")
                    .font(.caption)
                    .foregroundStyle(Self.onPanelSoft)
            }
            .padding(.bottom, 4)

            if let err = vm.errorMessage {
                errorBanner(err)
            }

            OTPCodeField(code: $vm.code, length: 6, hasError: vm.errorMessage != nil) {
                Task { await vm.verifyOTP() }
            }

            actionButton(title: "Verify", style: .primary, loading: vm.isLoading) {
                Task { await vm.verifyOTP() }
            }

            HStack(spacing: 18) {
                Button {
                    Task { await vm.requestOTP() }
                } label: {
                    Text("Resend code").font(.caption).foregroundStyle(Self.onPanelSoft)
                }
                Button {
                    vm.step = .email
                    vm.code = ""
                    vm.errorMessage = nil
                } label: {
                    Text("Use a different email").font(.caption).foregroundStyle(Self.onPanelSoft)
                }
            }
        }
    }

    // MARK: – Shared pieces

    private func errorBanner(_ message: String) -> some View {
        HStack(alignment: .top, spacing: 8) {
            Image(systemName: "exclamationmark.circle.fill")
                .font(.system(size: 14))
                .foregroundStyle(Self.errorText)
            Text(message)
                .font(.caption)
                .foregroundStyle(Self.errorText)
                .multilineTextAlignment(.leading)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(Self.errorTint.opacity(0.14))
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(Self.errorTint.opacity(0.35), lineWidth: 1)
        )
    }

    private enum ButtonStyle { case primary, secondary }

    private func actionButton(title: String, style: ButtonStyle, loading: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            ZStack {
                if loading {
                    ProgressView().tint(style == .primary ? Self.ctaInk : Self.onPanelInk)
                } else {
                    Text(title)
                        .font(.headline)
                        .foregroundStyle(style == .primary ? Self.ctaInk : Self.onPanelInk)
                }
            }
            .frame(maxWidth: .infinity)
            .frame(height: 56)
        }
        .background(style == .primary ? Self.ctaFill : Color.clear)
        .clipShape(Capsule())
        .overlay(
            Capsule().stroke(style == .secondary ? Self.onPanelFieldBorder : Color.clear, lineWidth: 1)
        )
        .disabled(loading)
    }
}

/// Six-cell segmented code entry. A transparent `TextField` captures real
/// keyboard input (so autofill / paste / numberPad all keep working) while
/// the visible cells mirror its contents; focus auto-advances per digit and
/// auto-submits on the sixth, which sidesteps the number pad's missing
/// Done key rather than just adding one.
private struct OTPCodeField: View {
    @Binding var code: String
    let length: Int
    let hasError: Bool
    var onComplete: () -> Void = {}

    @FocusState private var isFocused: Bool

    var body: some View {
        ZStack {
            TextField("", text: $code)
                .keyboardType(.numberPad)
                .textContentType(.oneTimeCode)
                .focused($isFocused)
                .foregroundStyle(.clear)
                .tint(.clear)
                .onChange(of: code) { _, newValue in
                    let digits = newValue.filter(\.isNumber)
                    let clamped = String(digits.prefix(length))
                    if clamped != code { code = clamped }
                    if clamped.count == length {
                        isFocused = false
                        onComplete()
                    }
                }
                .toolbar {
                    ToolbarItemGroup(placement: .keyboard) {
                        Spacer()
                        Button("Done") { isFocused = false }
                    }
                }

            HStack(spacing: 7) {
                ForEach(0..<length, id: \.self) { index in
                    cell(at: index)
                }
            }
            .allowsHitTesting(false)
        }
        .contentShape(Rectangle())
        .onTapGesture { isFocused = true }
    }

    private func cell(at index: Int) -> some View {
        let chars = Array(code)
        let isFilled = index < chars.count
        let isActive = isFocused && index == chars.count

        return Text(isFilled ? String(chars[index]) : "")
            .font(.system(size: 19, weight: .medium, design: .monospaced))
            .foregroundStyle(LoginView.onPanelInk)
            .frame(maxWidth: .infinity)
            .frame(height: 52)
            .background(LoginView.onPanelField)
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .stroke(borderColor(isFilled: isFilled, isActive: isActive), lineWidth: 1)
            )
            .animation(.easeOut(duration: 0.15), value: isActive)
    }

    private func borderColor(isFilled: Bool, isActive: Bool) -> Color {
        if hasError { return LoginView.errorTint }
        if isActive { return LoginView.onPanelInk.opacity(0.55) }
        if isFilled { return Color.white.opacity(0.22) }
        return LoginView.onPanelFieldBorder
    }
}
