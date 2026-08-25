import SwiftUI

struct FinanceFuturesView: View {
    @ObservedObject var store: FinancialStore
    @State private var sourceFilter = "All"

    private var filtered: [FutureInvestment] {
        store.futures.filter { f in
            switch sourceFilter {
            case "Auto-tracked": return f.source != "MANUAL"
            case "Manual":       return f.source == "MANUAL"
            default:             return true
            }
        }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                summaryCard

                HStack(spacing: 8) {
                    ForEach(["All", "Auto-tracked", "Manual"], id: \.self) { opt in
                        ThemeChip(label: opt, isActive: sourceFilter == opt) { sourceFilter = opt }
                    }
                }

                if filtered.isEmpty {
                    ThemeCard {
                        Text(store.futures.isEmpty ? "No open futures" : "No results")
                            .foregroundStyle(Theme.inkFaint).font(.subheadline)
                            .frame(maxWidth: .infinity)
                    }
                } else {
                    ForEach(filtered) { f in
                        positionCard(f)
                    }
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 16)
        }
        .background(Theme.background)
        .tabBarSafeArea()
        .navigationTitle("Futures")
        .navigationBarTitleDisplayMode(.inline)
    }

    private var summaryCard: some View {
        ThemeCard(padding: 20) {
            VStack(alignment: .leading, spacing: 6) {
                Text("Open PnL").font(.system(size: 13, weight: .medium)).foregroundStyle(Theme.inkSoft)
                Text(formatMoney(store.futuresOpenPnl, currency: store.futuresCurrency))
                    .font(Theme.serif(34))
                    .foregroundStyle(store.futures.isEmpty ? Theme.ink : (store.futuresOpenPnl >= 0 ? Theme.positive : Theme.negative))
                Text("\(store.futures.count) position\(store.futures.count == 1 ? "" : "s")")
                    .font(.caption).foregroundStyle(Theme.inkFaint)
            }
        }
    }

    private func positionCard(_ f: FutureInvestment) -> some View {
        ThemeCard {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 8) {
                    Text(f.symbol ?? f.exchange).font(.system(size: 16, weight: .bold)).foregroundStyle(Theme.ink)
                    if let side = f.side {
                        Text(side)
                            .font(.caption2.weight(.semibold))
                            .padding(.horizontal, 8).padding(.vertical, 3)
                            .background(side == "LONG" ? Theme.positiveSoft : Theme.negativeSoft)
                            .foregroundStyle(side == "LONG" ? Theme.positive : Theme.negative)
                            .clipShape(Capsule())
                    }
                    if let lev = f.leverage {
                        Text("\(formatNum(lev))x")
                            .font(.caption2.weight(.semibold))
                            .padding(.horizontal, 8).padding(.vertical, 3)
                            .background(Theme.chipFill)
                            .foregroundStyle(Theme.inkSoft)
                            .clipShape(Capsule())
                    }
                }
                Text(sourceLabel(f)).font(.caption2).foregroundStyle(Theme.inkFaint)

                LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 8) {
                    stat("Entry", f.entryPrice)
                    stat("Mark", f.currentPrice)
                    stat("Margin", f.margin)
                    stat("Liq. Price", f.liquidationPrice)
                }

                Divider().overlay(Theme.hairline)
                HStack {
                    Text("Unrealized PnL").font(.caption).foregroundStyle(Theme.inkSoft)
                    Spacer()
                    if let pnl = f.pnlPercent, let cv = f.convertedCurrentValue {
                        Text(formatMoney(cv, currency: f.convertedCurrency ?? f.currency))
                            .font(.system(size: 15, weight: .bold))
                            .foregroundStyle(pnl >= 0 ? Theme.positive : Theme.negative)
                        PnLBadge(pnl: pnl)
                    } else {
                        Text("—").foregroundStyle(Theme.inkFaint)
                    }
                }
            }
        }
    }

    private func sourceLabel(_ f: FutureInvestment) -> String {
        f.source == "MANUAL" ? "\(f.exchange.capitalized) · Manual entry" : "\(f.exchange.capitalized) · Auto-synced"
    }

    private func stat(_ label: String, _ value: Double?) -> some View {
        VStack(alignment: .leading, spacing: 1) {
            Text(label).font(.caption2).foregroundStyle(Theme.inkFaint)
            Text(value.map { formatNum($0) } ?? "—").font(.caption.weight(.semibold)).foregroundStyle(Theme.ink)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
