import SwiftUI

struct FinanceDepositsView: View {
    @ObservedObject var store: FinancialStore
    @State private var currencyFilter = "All"

    private var availableCurrencies: [String] { ["All"] + Array(Set(store.deposits.map(\.currency))).sorted() }
    private var filtered: [CashDeposit] {
        store.deposits.filter { currencyFilter == "All" || $0.currency == currencyFilter }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                if filtered.isEmpty {
                    ThemeCard {
                        Text(store.deposits.isEmpty ? "No deposits" : "No results")
                            .foregroundStyle(Theme.inkFaint).frame(maxWidth: .infinity)
                    }
                } else {
                    ThemeCard(padding: 20) {
                        VStack(alignment: .leading, spacing: 6) {
                            Text("Total").font(.system(size: 13, weight: .medium)).foregroundStyle(Theme.inkSoft)
                            Text(formatMoney(filtered.reduce(0) { $0 + $1.convertedAmount },
                                              currency: filtered.first?.convertedCurrency ?? "USD"))
                                .font(Theme.serif(30)).foregroundStyle(Theme.ink)
                        }
                    }
                    if availableCurrencies.count > 2 {
                        HStack(spacing: 8) {
                            ForEach(availableCurrencies, id: \.self) { c in
                                ThemeChip(label: c, isActive: currencyFilter == c) { currencyFilter = c }
                            }
                        }
                    }
                    ThemeCard(padding: 6) {
                        VStack(spacing: 0) {
                            ForEach(Array(filtered.enumerated()), id: \.element.id) { idx, d in
                                HStack {
                                    VStack(alignment: .leading, spacing: 3) {
                                        Text(d.platform).font(.system(size: 15, weight: .medium)).foregroundStyle(Theme.ink)
                                        Text("\(d.depositType) · \(d.platformType)").font(.caption).foregroundStyle(Theme.inkFaint)
                                    }
                                    Spacer()
                                    VStack(alignment: .trailing, spacing: 3) {
                                        Text(formatMoney(d.amount, currency: d.currency)).font(.system(size: 15, weight: .semibold)).foregroundStyle(Theme.ink)
                                        Text(formatMoney(d.convertedAmount, currency: d.convertedCurrency)).font(.caption).foregroundStyle(Theme.inkFaint)
                                    }
                                }
                                .padding(.horizontal, 12).padding(.vertical, 12)
                                if idx < filtered.count - 1 { Divider().overlay(Theme.hairline) }
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
        .navigationTitle("Deposits")
        .navigationBarTitleDisplayMode(.inline)
    }
}
