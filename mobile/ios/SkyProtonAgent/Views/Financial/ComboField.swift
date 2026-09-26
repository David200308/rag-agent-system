import SwiftUI

/// A text field that also offers autocomplete chips drawn from the user's own past entries
/// (e.g. platforms/banks/brokers they've already typed) — mirrors the web app's `ComboInput`:
/// free text is always allowed, the suggestions are just a shortcut, not a fixed enum.
/// Styled as a `FormField` row; suggestions appear while the field is focused.
struct ComboField: View {
    let label: String
    let placeholder: String
    @Binding var text: String
    let suggestions: [String]
    @FocusState private var focused: Bool

    private var filtered: [String] {
        guard !text.isEmpty else { return suggestions }
        return suggestions.filter { $0.localizedCaseInsensitiveContains(text) && $0 != text }
    }

    private var showsSuggestions: Bool { focused && !filtered.isEmpty }

    var body: some View {
        VStack(alignment: .leading, spacing: 5) {
            FormFieldLabel(text: label, isFocused: focused)
            TextField("", text: $text, prompt: Text(placeholder).foregroundColor(Theme.inkFaint))
                .font(.system(size: 17))
                .foregroundStyle(Theme.ink)
                .autocorrectionDisabled()
                .focused($focused)
            if showsSuggestions {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        ForEach(filtered.prefix(8), id: \.self) { s in
                            Button { text = s } label: {
                                HStack(spacing: 4) {
                                    Image(systemName: "clock.arrow.circlepath").font(.system(size: 10, weight: .semibold))
                                    Text(s).font(.system(size: 13, weight: .medium))
                                }
                                .padding(.horizontal, 10).padding(.vertical, 6)
                                .background(Theme.chipFill)
                                .foregroundStyle(Theme.inkSoft)
                                .clipShape(Capsule())
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
                .scrollClipDisabled()
                .padding(.top, 6)
                .transition(.opacity)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .formRowPadding()
        .contentShape(Rectangle())
        .onTapGesture { focused = true }
        .animation(.easeOut(duration: 0.18), value: showsSuggestions)
    }
}
