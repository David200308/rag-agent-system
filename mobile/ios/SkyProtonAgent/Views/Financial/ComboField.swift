import SwiftUI

/// A text field that also offers autocomplete chips drawn from the user's own past entries
/// (e.g. platforms/banks/brokers they've already typed) — mirrors the web app's `ComboInput`:
/// free text is always allowed, the suggestions are just a shortcut, not a fixed enum.
struct ComboField: View {
    let placeholder: String
    @Binding var text: String
    let suggestions: [String]

    private var filtered: [String] {
        guard !text.isEmpty else { return suggestions }
        return suggestions.filter { $0.localizedCaseInsensitiveContains(text) && $0 != text }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: filtered.isEmpty ? 0 : 8) {
            TextField(placeholder, text: $text)
            if !filtered.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        ForEach(filtered.prefix(8), id: \.self) { s in
                            Button(s) { text = s }
                                .font(.caption)
                                .padding(.horizontal, 10).padding(.vertical, 5)
                                .background(Theme.chipFill)
                                .foregroundStyle(Theme.inkSoft)
                                .clipShape(Capsule())
                        }
                    }
                }
            }
        }
    }
}
