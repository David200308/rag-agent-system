import SwiftUI

/// Stock/crypto logo with a fallback to the symbol's first letter — mirrors the web app's
/// `SymbolIcon` (Finnhub company logos for stocks, CoinGecko coin logos for crypto).
struct SymbolIcon: View {
    let logoUrl: String?
    let symbol: String
    var size: CGFloat = 26

    var body: some View {
        Group {
            if let logoUrl, let url = URL(string: logoUrl) {
                AsyncImage(url: url) { phase in
                    switch phase {
                    case .success(let image):
                        image.resizable().scaledToFit().padding(size * 0.14)
                    default:
                        fallback
                    }
                }
            } else {
                fallback
            }
        }
        .frame(width: size, height: size)
        .background(Color.white)
        .clipShape(Circle())
        .overlay(Circle().stroke(Theme.hairline, lineWidth: 1))
    }

    private var fallback: some View {
        Text(symbol.prefix(1))
            .font(.system(size: size * 0.42, weight: .semibold))
            .foregroundStyle(Theme.inkSoft)
            .frame(width: size, height: size)
            .background(Theme.chipFill)
            .clipShape(Circle())
    }
}
