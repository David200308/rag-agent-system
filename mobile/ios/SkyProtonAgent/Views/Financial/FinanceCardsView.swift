import SwiftUI

struct FinanceCardsView: View {
    @ObservedObject var store: FinancialStore
    @State private var networkFilter = "All"

    private var availableNetworks: [String] { ["All"] + Array(Set(store.cards.map(\.network))).sorted() }
    private var filtered: [FinancialCard] {
        store.cards.filter { networkFilter == "All" || $0.network == networkFilter }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                if availableNetworks.count > 2 {
                    HStack(spacing: 8) {
                        ForEach(availableNetworks, id: \.self) { n in
                            ThemeChip(label: n, isActive: networkFilter == n) { networkFilter = n }
                        }
                    }
                }
                if filtered.isEmpty {
                    ThemeCard {
                        Text(store.cards.isEmpty ? "No cards" : "No results")
                            .foregroundStyle(Theme.inkFaint).frame(maxWidth: .infinity)
                    }
                } else {
                    ForEach(filtered) { c in
                        ThemeCard {
                            VStack(alignment: .leading, spacing: 6) {
                                HStack {
                                    Text(c.cardName).font(.system(size: 15, weight: .medium)).foregroundStyle(Theme.ink)
                                    Spacer()
                                    Text(c.network).font(.caption.weight(.semibold)).foregroundStyle(Theme.inkSoft)
                                }
                                HStack(spacing: 6) {
                                    Text(c.bank).font(.caption).foregroundStyle(Theme.inkSoft)
                                    Text("·").foregroundStyle(Theme.inkFaint)
                                    Text(c.types.joined(separator: "/")).font(.caption).foregroundStyle(Theme.inkSoft)
                                }
                                HStack {
                                    if let limit = c.creditLimit, let cur = c.creditLimitCurrency {
                                        Text("Limit: \(formatMoney(limit, currency: cur))").font(.caption2).foregroundStyle(Theme.inkFaint)
                                    }
                                    Spacer()
                                    Text("Exp: \(c.expireDate)").font(.caption2).foregroundStyle(Theme.inkFaint)
                                }
                            }
                        }
                    }
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 16)
        }
        .background(Theme.background)
        .tabBarSafeArea()
        .navigationTitle("Cards")
        .navigationBarTitleDisplayMode(.inline)
    }
}
