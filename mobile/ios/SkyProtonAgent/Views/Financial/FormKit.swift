import SwiftUI
import UIKit

// Themed building blocks for every add/edit sheet — replaces the stock grouped `Form`
// so sheets share the app's warm ground, rounded surface cards, serif headings and
// ink-filled primary action instead of looking like the Settings app.

/// Selected-state fill for chips, segments and the primary button. Graphite in light mode,
/// but graphite is nearly invisible on the dark ground, so dark mode flips to light ink.
enum FormColors {
    static let selectedFill = Color(uiColor: UIColor { trait in
        trait.userInterfaceStyle == .dark
            ? UIColor(red: 0.961, green: 0.945, blue: 0.925, alpha: 1)
            : UIColor(red: 0.122, green: 0.118, blue: 0.110, alpha: 1)
    })
    static let selectedText = Color(uiColor: UIColor { trait in
        trait.userInterfaceStyle == .dark
            ? UIColor(red: 0.086, green: 0.082, blue: 0.075, alpha: 1)
            : UIColor.white
    })
}

func dismissKeyboard() {
    UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
}

// MARK: - Sheet scaffold

/// Sheet chrome: serif title header, scrolling sections, close button and a pinned
/// full-width primary button. Pass `onPrimary: nil` for sheets with no primary action.
struct FormSheet<Content: View>: View {
    let title: String
    var subtitle: String?
    var primaryLabel: String
    var isPrimaryEnabled: Bool
    var isSaving: Bool
    var errorMessage: String?
    var onPrimary: (() -> Void)?
    let content: Content
    @Environment(\.dismiss) private var dismiss

    init(
        _ title: String,
        subtitle: String? = nil,
        primaryLabel: String = "Save",
        isPrimaryEnabled: Bool = true,
        isSaving: Bool = false,
        errorMessage: String? = nil,
        onPrimary: (() -> Void)? = nil,
        @ViewBuilder content: () -> Content
    ) {
        self.title = title
        self.subtitle = subtitle
        self.primaryLabel = primaryLabel
        self.isPrimaryEnabled = isPrimaryEnabled
        self.isSaving = isSaving
        self.errorMessage = errorMessage
        self.onPrimary = onPrimary
        self.content = content()
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 22) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(title).font(Theme.serif(30)).foregroundStyle(Theme.ink)
                        if let subtitle {
                            Text(subtitle).font(.system(size: 14)).foregroundStyle(Theme.inkSoft)
                        }
                    }
                    .padding(.horizontal, 4)

                    content

                    if let errorMessage { FormErrorBanner(message: errorMessage) }
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 24)
                .animation(.easeOut(duration: 0.2), value: errorMessage)
            }
            .scrollDismissesKeyboard(.interactively)
            .background(Theme.background.ignoresSafeArea())
            .safeAreaInset(edge: .bottom) {
                if let onPrimary { primaryBar(onPrimary) }
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Theme.background, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { dismiss() } label: {
                        Image(systemName: "xmark")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundStyle(Theme.inkSoft)
                            .frame(width: 30, height: 30)
                            .background(Theme.chipFill)
                            .clipShape(Circle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Close")
                }
                ToolbarItemGroup(placement: .keyboard) {
                    Spacer()
                    Button("Done", action: dismissKeyboard).fontWeight(.semibold).tint(Theme.ink)
                }
            }
        }
        .presentationCornerRadius(28)
        .presentationDragIndicator(.visible)
        .presentationBackground(Theme.background)
    }

    private func primaryBar(_ action: @escaping () -> Void) -> some View {
        Button {
            dismissKeyboard()
            action()
        } label: {
            ZStack {
                if isSaving {
                    ProgressView().tint(FormColors.selectedText)
                } else {
                    Text(primaryLabel).font(.system(size: 16, weight: .semibold))
                }
            }
            .frame(maxWidth: .infinity)
            .frame(height: 54)
            .foregroundStyle(FormColors.selectedText)
            .background(FormColors.selectedFill.opacity(isPrimaryEnabled ? 1 : 0.35))
            .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
            .shadow(color: .black.opacity(isPrimaryEnabled ? 0.18 : 0), radius: 12, y: 6)
        }
        .buttonStyle(.plain)
        .disabled(!isPrimaryEnabled || isSaving)
        .padding(.horizontal, 20)
        .padding(.top, 12)
        .padding(.bottom, 8)
        .background(
            LinearGradient(colors: [Theme.background.opacity(0), Theme.background],
                           startPoint: .top, endPoint: .init(x: 0.5, y: 0.3))
        )
        .animation(.easeOut(duration: 0.15), value: isPrimaryEnabled)
    }
}

// MARK: - Sections & rows

/// A labelled rounded surface card grouping rows. Separate rows with `FormDivider()`.
struct FormSection<Content: View>: View {
    var title: String?
    var footer: String?
    var trailing: AnyView?
    let content: Content

    init(_ title: String? = nil, footer: String? = nil, trailing: AnyView? = nil,
         @ViewBuilder content: () -> Content) {
        self.title = title
        self.footer = footer
        self.trailing = trailing
        self.content = content()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if title != nil || trailing != nil {
                HStack {
                    if let title { SectionLabel(text: title) }
                    Spacer()
                    trailing
                }
                .padding(.horizontal, 4)
            }
            VStack(spacing: 0) { content }
                .background(Theme.surface)
                .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).stroke(Theme.hairline, lineWidth: 1))
            if let footer {
                Text(footer).font(.caption).foregroundStyle(Theme.inkFaint).padding(.horizontal, 4)
            }
        }
    }
}

struct FormDivider: View {
    var body: some View {
        Rectangle().fill(Theme.hairline).frame(height: 1).padding(.leading, 16)
    }
}

struct FormFieldLabel: View {
    let text: String
    var isFocused = false

    var body: some View {
        Text(text)
            .font(.system(size: 12, weight: .semibold))
            .foregroundStyle(isFocused ? Theme.ink : Theme.inkFaint)
            .animation(.easeOut(duration: 0.15), value: isFocused)
    }
}

extension View {
    func formRowPadding() -> some View {
        padding(.horizontal, 16).padding(.vertical, 13)
    }
}

private func placeholderText(_ s: String) -> Text {
    Text(s).foregroundColor(Theme.inkFaint)
}

/// Stacked label-over-value text input. Tapping anywhere in the row focuses the field.
struct FormField: View {
    let label: String
    @Binding var text: String
    var placeholder: String = ""
    var keyboard: UIKeyboardType = .default
    var capitalization: TextInputAutocapitalization = .words
    @FocusState private var focused: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 5) {
            FormFieldLabel(text: label, isFocused: focused)
            TextField("", text: $text, prompt: placeholderText(placeholder))
                .font(.system(size: 17))
                .foregroundStyle(Theme.ink)
                .keyboardType(keyboard)
                .textInputAutocapitalization(capitalization)
                .autocorrectionDisabled()
                .focused($focused)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .formRowPadding()
        .contentShape(Rectangle())
        .onTapGesture { focused = true }
    }
}

/// Label on the left, right-aligned numeric input — for dense money/quantity rows.
struct FormNumberRow: View {
    let label: String
    @Binding var text: String
    var placeholder: String = "0"
    var unit: String?
    @FocusState private var focused: Bool

    var body: some View {
        HStack(spacing: 10) {
            Text(label)
                .font(.system(size: 15))
                .foregroundStyle(focused ? Theme.ink : Theme.inkSoft)
            Spacer(minLength: 12)
            TextField("", text: $text, prompt: placeholderText(placeholder))
                .font(.system(size: 17, weight: .medium).monospacedDigit())
                .foregroundStyle(Theme.ink)
                .multilineTextAlignment(.trailing)
                .keyboardType(.decimalPad)
                .focused($focused)
                .frame(maxWidth: 170)
            if let unit {
                Text(unit).font(.system(size: 13, weight: .medium)).foregroundStyle(Theme.inkFaint)
            }
        }
        .formRowPadding()
        .contentShape(Rectangle())
        .onTapGesture { focused = true }
    }
}

/// Big serif amount input, optionally with a currency menu — the focal field of a form.
struct FormAmountHero: View {
    let label: String
    @Binding var amount: String
    var currency: Binding<String>?
    var placeholder: String = "0"
    @FocusState private var focused: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                FormFieldLabel(text: label.uppercased(), isFocused: focused)
                Spacer()
                if let currency {
                    FormMenuPicker(options: commonCurrencies, selection: currency) { $0 }
                }
            }
            TextField("", text: $amount, prompt: placeholderText(placeholder))
                .font(Theme.serif(38))
                .foregroundStyle(Theme.ink)
                .keyboardType(.decimalPad)
                .focused($focused)
        }
        .padding(18)
        .contentShape(Rectangle())
        .onTapGesture { focused = true }
    }
}

/// A plain label + trailing control row (menus, steppers, read-only values).
struct FormRow<Trailing: View>: View {
    let label: String
    let trailing: Trailing

    init(_ label: String, @ViewBuilder trailing: () -> Trailing) {
        self.label = label
        self.trailing = trailing()
    }

    var body: some View {
        HStack(spacing: 10) {
            Text(label).font(.system(size: 15)).foregroundStyle(Theme.inkSoft)
            Spacer(minLength: 12)
            trailing
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 11)
        .frame(minHeight: 50)
    }
}

/// Stacked label over an arbitrary control (chips, segmented control).
struct FormChoiceRow<Control: View>: View {
    let label: String
    let control: Control

    init(_ label: String, @ViewBuilder control: () -> Control) {
        self.label = label
        self.control = control()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            FormFieldLabel(text: label)
            control
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .formRowPadding()
    }
}

// MARK: - Controls

/// Compact capsule that opens a menu of options (with a checkmark on the current one).
struct FormMenuPicker<T: Hashable>: View {
    let options: [T]
    @Binding var selection: T
    let title: (T) -> String

    var body: some View {
        Menu {
            Picker("", selection: $selection) {
                ForEach(options, id: \.self) { Text(title($0)).tag($0) }
            }
        } label: {
            HStack(spacing: 5) {
                Text(title(selection)).font(.system(size: 14, weight: .semibold))
                Image(systemName: "chevron.up.chevron.down").font(.system(size: 10, weight: .semibold))
            }
            .foregroundStyle(Theme.ink)
            .lineLimit(1)
            .padding(.horizontal, 12).padding(.vertical, 7)
            .background(Theme.chipFill)
            .clipShape(Capsule())
            .fixedSize()
        }
        .fixedSize()
    }
}

/// Capsule segmented control with a sliding thumb.
struct FormSegmented<T: Hashable>: View {
    let options: [T]
    @Binding var selection: T
    /// Custom per-option fill (e.g. green Long / red Short); white text is used on it.
    var accent: ((T) -> Color)?
    let title: (T) -> String
    @Namespace private var thumb

    var body: some View {
        HStack(spacing: 4) {
            ForEach(options, id: \.self) { option in
                let isActive = option == selection
                Button {
                    withAnimation(.snappy(duration: 0.25)) { selection = option }
                } label: {
                    Text(title(option))
                        .font(.system(size: 14, weight: .semibold))
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 9)
                        .foregroundStyle(isActive ? (accent == nil ? FormColors.selectedText : .white) : Theme.inkSoft)
                        .background {
                            if isActive {
                                Capsule().fill(accent?(option) ?? FormColors.selectedFill)
                                    .matchedGeometryEffect(id: "thumb", in: thumb)
                            }
                        }
                        .contentShape(Capsule())
                }
                .buttonStyle(.plain)
            }
        }
        .padding(4)
        .background(Theme.chipFill)
        .clipShape(Capsule())
    }
}

struct FormChip: View {
    let label: String
    let isActive: Bool
    var showsCheck = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 5) {
                if showsCheck && isActive {
                    Image(systemName: "checkmark").font(.system(size: 11, weight: .bold))
                }
                Text(label).font(.system(size: 14, weight: .medium)).lineLimit(1)
            }
            .fixedSize()
            .padding(.horizontal, 14).padding(.vertical, 8)
            .foregroundStyle(isActive ? FormColors.selectedText : Theme.inkSoft)
            .background(isActive ? FormColors.selectedFill : Theme.chipFill)
            .clipShape(Capsule())
            .animation(.easeOut(duration: 0.15), value: isActive)
        }
        .buttonStyle(.plain)
    }
}

/// Horizontally scrolling chip group — single-select or (with `Set` binding) multi-select.
struct FormChips<T: Hashable>: View {
    let options: [T]
    let isSelected: (T) -> Bool
    let onTap: (T) -> Void
    let title: (T) -> String
    let showsCheck: Bool

    init(options: [T], selection: Binding<T>, title: @escaping (T) -> String) {
        self.options = options
        self.isSelected = { $0 == selection.wrappedValue }
        self.onTap = { selection.wrappedValue = $0 }
        self.title = title
        self.showsCheck = false
    }

    init(options: [T], selection: Binding<Set<T>>, title: @escaping (T) -> String) {
        self.options = options
        self.isSelected = { selection.wrappedValue.contains($0) }
        self.onTap = { t in
            if selection.wrappedValue.contains(t) { selection.wrappedValue.remove(t) } else { selection.wrappedValue.insert(t) }
        }
        self.title = title
        self.showsCheck = true
    }

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(options, id: \.self) { o in
                    FormChip(label: title(o), isActive: isSelected(o), showsCheck: showsCheck) { onTap(o) }
                }
            }
        }
        .scrollClipDisabled()
    }
}

/// − value + capsule stepper.
struct FormStepper: View {
    @Binding var value: Int
    let range: ClosedRange<Int>
    var format: (Int) -> String = { String($0) }

    var body: some View {
        HStack(spacing: 0) {
            stepButton("minus", enabled: value > range.lowerBound) { value -= 1 }
            Text(format(value))
                .font(.system(size: 15, weight: .semibold).monospacedDigit())
                .foregroundStyle(Theme.ink)
                .frame(minWidth: 44)
                .contentTransition(.numericText())
            stepButton("plus", enabled: value < range.upperBound) { value += 1 }
        }
        .background(Theme.chipFill)
        .clipShape(Capsule())
    }

    private func stepButton(_ icon: String, enabled: Bool, action: @escaping () -> Void) -> some View {
        Button {
            withAnimation(.snappy(duration: 0.2)) { action() }
        } label: {
            Image(systemName: icon)
                .font(.system(size: 12, weight: .bold))
                .foregroundStyle(enabled ? Theme.ink : Theme.inkFaint)
                .frame(width: 34, height: 32)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }
}

/// Month menu + year stepper on one row (salary period, card expiry).
struct FormPeriodRow: View {
    let label: String
    @Binding var year: Int
    @Binding var month: Int

    var body: some View {
        FormRow(label) {
            HStack(spacing: 8) {
                FormMenuPicker(options: Array(1...12), selection: $month) {
                    Calendar.current.shortMonthSymbols[$0 - 1]
                }
                FormStepper(value: $year, range: 2000...2100)
            }
        }
    }
}

// MARK: - Feedback

struct FormErrorBanner: View {
    let message: String

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "exclamationmark.circle.fill")
            Text(message).font(.system(size: 14)).frame(maxWidth: .infinity, alignment: .leading)
        }
        .foregroundStyle(Theme.negative)
        .padding(14)
        .background(Theme.negativeSoft)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .transition(.opacity.combined(with: .move(edge: .top)))
    }
}

struct FormDestructiveButton: View {
    let label: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(Theme.negative)
                .frame(maxWidth: .infinity)
                .frame(height: 50)
                .background(Theme.negativeSoft)
                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
        .buttonStyle(.plain)
    }
}
