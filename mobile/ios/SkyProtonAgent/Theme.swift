import SwiftUI
import UIKit

/// Shared design tokens for the Finance / Travel redesign — mirrors the approved mockup's
/// warm off-white ground, graphite ledger tone and terracotta Travel accent, adapted for dark mode.
enum Theme {
    static let background = Color(uiColor: UIColor { trait in
        trait.userInterfaceStyle == .dark
            ? UIColor(red: 0.086, green: 0.078, blue: 0.071, alpha: 1)
            : UIColor(red: 0.969, green: 0.961, blue: 0.949, alpha: 1)
    })

    static let surface = Color(uiColor: UIColor { trait in
        trait.userInterfaceStyle == .dark
            ? UIColor(red: 0.145, green: 0.133, blue: 0.122, alpha: 1)
            : UIColor.white
    })

    static let ink = Color(uiColor: UIColor { trait in
        trait.userInterfaceStyle == .dark
            ? UIColor(red: 0.961, green: 0.945, blue: 0.925, alpha: 1)
            : UIColor(red: 0.086, green: 0.082, blue: 0.075, alpha: 1)
    })

    static let inkSoft = Color(uiColor: UIColor { trait in
        trait.userInterfaceStyle == .dark
            ? UIColor(white: 0.72, alpha: 1)
            : UIColor(red: 0.420, green: 0.408, blue: 0.388, alpha: 1)
    })

    static let inkFaint = Color(uiColor: UIColor { trait in
        trait.userInterfaceStyle == .dark
            ? UIColor(white: 0.50, alpha: 1)
            : UIColor(red: 0.651, green: 0.635, blue: 0.608, alpha: 1)
    })

    static let hairline = Color(uiColor: UIColor { trait in
        trait.userInterfaceStyle == .dark
            ? UIColor(white: 1, alpha: 0.09)
            : UIColor(red: 0.906, green: 0.890, blue: 0.867, alpha: 1)
    })

    /// Finance's neutral accent — the floating tab bar, active chips, primary CTAs.
    static let graphite = Color(uiColor: UIColor { trait in
        trait.userInterfaceStyle == .dark
            ? UIColor(red: 0.216, green: 0.200, blue: 0.184, alpha: 1)
            : UIColor(red: 0.122, green: 0.118, blue: 0.110, alpha: 1)
    })

    static let chipFill = Color(uiColor: UIColor { trait in
        trait.userInterfaceStyle == .dark
            ? UIColor(white: 1, alpha: 0.07)
            : UIColor(red: 0.937, green: 0.925, blue: 0.906, alpha: 1)
    })

    static let positive = Color(red: 0.243, green: 0.490, blue: 0.310)
    static let positiveSoft = positive.opacity(0.14)
    static let negative = Color(red: 0.702, green: 0.271, blue: 0.184)
    static let negativeSoft = negative.opacity(0.14)

    /// Travel's own identity accent — terracotta, distinct from Finance's monochrome.
    static let travel = Color(red: 0.757, green: 0.392, blue: 0.184)
    static let travelSoft = travel.opacity(0.16)

    static func serif(_ size: CGFloat, weight: Font.Weight = .regular) -> Font {
        .system(size: size, weight: weight, design: .serif)
    }
}

/// The rounded, softly-shadowed surface every list row / summary block sits on.
struct ThemeCard<Content: View>: View {
    var padding: CGFloat = 18
    @ViewBuilder var content: Content

    var body: some View {
        content
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(padding)
            .background(Theme.surface)
            .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
            .shadow(color: .black.opacity(0.05), radius: 14, y: 6)
    }
}

struct ThemeChip: View {
    let label: String
    let isActive: Bool
    var accent: Color = Theme.graphite
    var action: () -> Void = {}

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: 13, weight: .medium))
                .padding(.horizontal, 13).padding(.vertical, 7)
                .background(isActive ? accent : Theme.surface)
                .foregroundStyle(isActive ? Color.white : Theme.inkSoft)
                .overlay(
                    Capsule().stroke(isActive ? Color.clear : Theme.hairline, lineWidth: 1)
                )
                .clipShape(Capsule())
        }
        .buttonStyle(.plain)
    }
}

/// A chip-styled label with no interaction of its own — use this (not `ThemeChip`) as the
/// `label:` of a `NavigationLink`. `ThemeChip` is itself a `Button`, and nesting a button inside
/// a NavigationLink's label makes the inner button swallow the tap so navigation never fires.
struct NavChipLabel: View {
    let label: String

    var body: some View {
        Text(label)
            .font(.system(size: 13, weight: .medium))
            .padding(.horizontal, 13).padding(.vertical, 7)
            .background(Theme.surface)
            .foregroundStyle(Theme.inkSoft)
            .overlay(Capsule().stroke(Theme.hairline, lineWidth: 1))
            .clipShape(Capsule())
    }
}

struct PnLBadge: View {
    let pnl: Double
    var large: Bool = false

    private var isPositive: Bool { pnl >= 0 }

    var body: some View {
        Text((isPositive ? "+" : "") + String(format: "%.2f%%", pnl))
            .font(large ? .system(size: 15, weight: .semibold) : .caption.weight(.semibold))
            .foregroundStyle(isPositive ? Theme.positive : Theme.negative)
            .padding(.horizontal, 8).padding(.vertical, 3)
            .background(isPositive ? Theme.positiveSoft : Theme.negativeSoft)
            .clipShape(Capsule())
    }
}

struct SectionLabel: View {
    let text: String
    var body: some View {
        Text(text.uppercased())
            .font(.system(size: 12, weight: .semibold))
            .foregroundStyle(Theme.inkFaint)
            .kerning(0.4)
    }
}
