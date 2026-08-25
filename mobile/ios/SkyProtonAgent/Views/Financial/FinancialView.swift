import SwiftUI

@MainActor
final class FinancialStore: ObservableObject {
    @Published var deposits: [CashDeposit] = []
    @Published var stocks: [StockInvestment] = []
    @Published var crypto: [CryptoInvestment] = []
    @Published var futures: [FutureInvestment] = []
    @Published var cards: [FinancialCard] = []
    @Published var salaryRecords: [SalaryUsageRecord] = []
    @Published var isLoading = false
    @Published var loadError: String?

    private let service = AgentService.shared
    private var didLoadOnce = false

    func loadIfNeeded() async {
        guard !didLoadOnce else { return }
        didLoadOnce = true
        await loadAll()
    }

    func loadAll() async {
        isLoading = true
        loadError = nil
        do {
            async let d = service.listCashDeposits()
            async let s = service.listStocks()
            async let c = service.listCrypto()
            async let f = service.listFutures()
            async let k = service.listCards()
            async let r = service.listSalaryRecords()
            deposits      = try await d
            stocks        = try await s
            crypto        = try await c
            futures       = try await f
            cards         = try await k
            salaryRecords = try await r
        } catch {
            loadError = error.localizedDescription
        }
        isLoading = false
    }

    func refresh() async {
        try? await service.refreshPrices()
        await loadAll()
    }

    var depositsTotal: Double { deposits.reduce(0) { $0 + $1.convertedAmount } }
    var depositsCurrency: String { deposits.first?.convertedCurrency ?? "USD" }

    var stocksValue: Double { stocks.reduce(0) { $0 + ($1.convertedCurrentValue ?? $1.convertedInvestAmount) } }
    var stocksPnlPercent: Double {
        let invest = stocks.reduce(0) { $0 + $1.convertedInvestAmount }
        return invest > 0 ? ((stocksValue - invest) / invest) * 100 : 0
    }
    var stocksCurrency: String { stocks.first?.convertedCurrency ?? "USD" }

    var cryptoValue: Double { crypto.reduce(0) { $0 + ($1.convertedCurrentValue ?? $1.convertedInvestAmount) } }
    var cryptoPnlPercent: Double {
        let invest = crypto.reduce(0) { $0 + $1.convertedInvestAmount }
        return invest > 0 ? ((cryptoValue - invest) / invest) * 100 : 0
    }
    var cryptoCurrency: String { crypto.first?.convertedCurrency ?? "USD" }

    /// Equity currently deployed in futures (margin + unrealized PnL) — this is what
    /// counts toward net worth, matching how deposits/stocks/crypto contribute their value.
    var futuresValue: Double {
        futures.reduce(0) { $0 + ($1.convertedCurrentValue ?? $1.convertedInvestAmount ?? 0) }
    }

    /// Unrealized PnL only, for display on the Futures row/screen — not part of net worth.
    var futuresOpenPnl: Double {
        futures.compactMap { f -> Double? in
            guard let cv = f.convertedCurrentValue, let inv = f.convertedInvestAmount else { return nil }
            return cv - inv
        }.reduce(0, +)
    }
    var futuresCurrency: String { futures.first?.convertedCurrency ?? "USD" }

    var netWorth: Double {
        depositsTotal + stocksValue + cryptoValue + futuresValue
    }
}

struct FinancialView: View {
    var onProfileTap: () -> Void = {}
    @StateObject private var store = FinancialStore()
    @State private var isRefreshing = false

    var body: some View {
        NavigationStack {
            Group {
                if store.isLoading && store.deposits.isEmpty && store.stocks.isEmpty {
                    ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
                        .background(Theme.background)
                } else {
                    ScrollView {
                        VStack(alignment: .leading, spacing: 22) {
                            if let err = store.loadError {
                                errorBanner(err)
                            }
                            netWorthHero
                            sectionChips
                            modulesList
                        }
                        .padding(.horizontal, 20)
                        .padding(.top, 16)
                    }
                    .background(Theme.background)
                    .tabBarSafeArea()
                    .refreshable {
                        isRefreshing = true
                        await store.refresh()
                        isRefreshing = false
                    }
                }
            }
            .navigationTitle("Finance")
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(action: onProfileTap) {
                        Circle()
                            .fill(Theme.graphite)
                            .frame(width: 32, height: 32)
                            .overlay(
                                Image(systemName: "person.fill")
                                    .font(.system(size: 13))
                                    .foregroundStyle(.white)
                            )
                    }
                }
            }
            .task { await store.loadIfNeeded() }
        }
        .tint(Theme.graphite)
    }

    private var netWorthHero: some View {
        ThemeCard(padding: 20) {
            VStack(alignment: .leading, spacing: 14) {
                Text("Total net worth")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(Theme.inkSoft)
                Text(formatMoney(store.netWorth, currency: store.stocksCurrency))
                    .font(Theme.serif(42, weight: .regular))
                    .foregroundStyle(Theme.ink)
            }
        }
    }

    private var sectionChips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                NavigationLink { FinanceDepositsView(store: store) } label: { NavChipLabel(label: "Deposits") }
                NavigationLink { FinanceMarketsView(store: store, initialTab: 0) } label: { NavChipLabel(label: "Stocks") }
                NavigationLink { FinanceMarketsView(store: store, initialTab: 1) } label: { NavChipLabel(label: "Crypto") }
                NavigationLink { FinanceFuturesView(store: store) } label: { NavChipLabel(label: "Futures") }
                NavigationLink { FinanceCardsView(store: store) } label: { NavChipLabel(label: "Cards") }
                NavigationLink { FinanceSalaryView(store: store) } label: { NavChipLabel(label: "Salary & Expense") }
            }
        }
    }

    private var modulesList: some View {
        ThemeCard(padding: 6) {
            VStack(spacing: 0) {
                NavigationLink { FinanceDepositsView(store: store) } label: {
                    moduleRow(icon: "building.columns.fill",
                              title: "Deposits",
                              subtitle: "\(store.deposits.count) accounts",
                              trailing: formatMoney(store.depositsTotal, currency: store.depositsCurrency))
                }
                Divider().overlay(Theme.hairline).padding(.leading, 68)

                NavigationLink { FinanceMarketsView(store: store, initialTab: 0) } label: {
                    moduleRow(icon: "chart.bar.fill",
                              title: "Stocks",
                              subtitle: "\(store.stocks.count) positions",
                              trailing: formatMoney(store.stocksValue, currency: store.stocksCurrency),
                              pnl: store.stocks.isEmpty ? nil : store.stocksPnlPercent)
                }
                Divider().overlay(Theme.hairline).padding(.leading, 68)

                NavigationLink { FinanceMarketsView(store: store, initialTab: 1) } label: {
                    moduleRow(icon: "hexagon.fill",
                              title: "Crypto",
                              subtitle: "\(store.crypto.count) assets",
                              trailing: formatMoney(store.cryptoValue, currency: store.cryptoCurrency),
                              pnl: store.crypto.isEmpty ? nil : store.cryptoPnlPercent)
                }
                Divider().overlay(Theme.hairline).padding(.leading, 68)

                NavigationLink { FinanceFuturesView(store: store) } label: {
                    moduleRow(icon: "bolt.fill",
                              title: "Futures",
                              subtitle: "\(store.futures.count) open",
                              trailing: formatMoney(store.futuresOpenPnl, currency: store.futuresCurrency),
                              trailingTint: store.futures.isEmpty ? nil : (store.futuresOpenPnl >= 0 ? Theme.positive : Theme.negative))
                }
            }
        }
    }

    private func moduleRow(icon: String, title: String, subtitle: String, trailing: String,
                            pnl: Double? = nil, trailingTint: Color? = nil) -> some View {
        HStack(spacing: 14) {
            RoundedRectangle(cornerRadius: 13, style: .continuous)
                .fill(Theme.chipFill)
                .frame(width: 40, height: 40)
                .overlay(Image(systemName: icon).font(.system(size: 16)).foregroundStyle(Theme.graphite))
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.system(size: 15, weight: .semibold)).foregroundStyle(Theme.ink)
                Text(subtitle).font(.system(size: 12.5)).foregroundStyle(Theme.inkFaint)
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 3) {
                Text(trailing)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(trailingTint ?? Theme.ink)
                if let pnl { PnLBadge(pnl: pnl) }
            }
            Image(systemName: "chevron.right")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(Theme.inkFaint)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 14)
        .contentShape(Rectangle())
    }

    private func errorBanner(_ err: String) -> some View {
        HStack(spacing: 8) {
            Image(systemName: "exclamationmark.triangle.fill").foregroundStyle(.orange)
            Text(err).font(.caption).foregroundStyle(Theme.inkSoft)
        }
        .padding(12)
        .background(Theme.surface)
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}

func formatMoney(_ v: Double, currency: String) -> String {
    let f = NumberFormatter()
    f.numberStyle = .currency
    f.currencyCode = currency
    f.maximumFractionDigits = 2
    return f.string(from: NSNumber(value: v)) ?? "\(currency) \(String(format: "%.2f", v))"
}

func formatNum(_ v: Double) -> String {
    v.truncatingRemainder(dividingBy: 1) == 0
        ? String(format: "%.0f", v)
        : String(format: "%.4f", v)
}

extension StockInvestment {
    var stockTypeBadge: String {
        switch stockType {
        case "US_STOCK": return "US"
        case "HK_STOCK": return "HK"
        case "CN_STOCK": return "CN"
        case "SG_STOCK": return "SG"
        default:         return "—"
        }
    }
}
