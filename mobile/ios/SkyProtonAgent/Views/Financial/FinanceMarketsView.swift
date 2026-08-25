import SwiftUI

private enum PnLFilter: String, CaseIterable { case all = "All", gains = "Gains", losses = "Losses" }

struct FinanceMarketsView: View {
    @ObservedObject var store: FinancialStore
    @State var initialTab: Int = 0

    @State private var searchText = ""
    @State private var marketFilter = "All"
    @State private var pnlFilter = PnLFilter.all
    @State private var showFilter = false

    private var hasActiveFilter: Bool {
        initialTab == 0 ? (marketFilter != "All" || pnlFilter != .all) : (pnlFilter != .all)
    }

    private var filteredStocks: [StockInvestment] {
        store.stocks.filter { s in
            let q = searchText.lowercased()
            let matchesSearch = searchText.isEmpty ||
                s.symbol.lowercased().contains(q) || s.name.lowercased().contains(q) || s.broker.lowercased().contains(q)
            let matchesMarket = marketFilter == "All" || s.stockTypeBadge == marketFilter
            let matchesPnl: Bool = {
                switch pnlFilter {
                case .gains:  return (s.pnlPercent ?? 0) >= 0
                case .losses: return (s.pnlPercent ?? 0) < 0
                case .all:    return true
                }
            }()
            return matchesSearch && matchesMarket && matchesPnl
        }
    }

    private var filteredCrypto: [CryptoInvestment] {
        store.crypto.filter { c in
            let q = searchText.lowercased()
            let matchesSearch = searchText.isEmpty || c.symbol.lowercased().contains(q) || c.name.lowercased().contains(q)
            let matchesPnl: Bool = {
                switch pnlFilter {
                case .gains:  return (c.pnlPercent ?? 0) >= 0
                case .losses: return (c.pnlPercent ?? 0) < 0
                case .all:    return true
                }
            }()
            return matchesSearch && matchesPnl
        }
    }

    private var availableMarkets: [String] {
        ["All"] + Array(Set(store.stocks.map(\.stockTypeBadge))).sorted()
    }

    var body: some View {
        VStack(spacing: 0) {
            Picker("", selection: $initialTab) {
                Text("Stocks").tag(0)
                Text("Crypto").tag(1)
            }
            .pickerStyle(.segmented)
            .padding(.horizontal, 20)
            .padding(.top, 12)
            .padding(.bottom, 14)

            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    summaryCard
                    if initialTab == 0 {
                        HStack(spacing: 8) {
                            ForEach(availableMarkets, id: \.self) { m in
                                ThemeChip(label: m, isActive: marketFilter == m) { marketFilter = m }
                            }
                        }
                        holdingsCard(stocks: filteredStocks)
                    } else {
                        holdingsCard(crypto: filteredCrypto)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 12)
            }
            .tabBarSafeArea()
        }
        .background(Theme.background)
        .navigationTitle("Markets")
        .navigationBarTitleDisplayMode(.inline)
        .searchable(text: $searchText, prompt: "Search…")
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button {
                    showFilter = true
                } label: {
                    Image(systemName: hasActiveFilter ? "line.3.horizontal.decrease.circle.fill" : "line.3.horizontal.decrease.circle")
                }
            }
        }
        .sheet(isPresented: $showFilter) {
            NavigationStack {
                Form {
                    if initialTab == 0 {
                        Section("Market") {
                            Picker("Market", selection: $marketFilter) {
                                ForEach(availableMarkets, id: \.self) { Text($0).tag($0) }
                            }
                            .pickerStyle(.segmented)
                        }
                    }
                    Section("Performance") {
                        Picker("P&L", selection: $pnlFilter) {
                            ForEach(PnLFilter.allCases, id: \.self) { Text($0.rawValue).tag($0) }
                        }
                        .pickerStyle(.segmented)
                    }
                }
                .navigationTitle("Filter")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Done") { showFilter = false }
                    }
                }
            }
            .presentationDetents([.medium])
        }
    }

    private var summaryCard: some View {
        let value = initialTab == 0 ? store.stocksValue : store.cryptoValue
        let pnl = initialTab == 0 ? store.stocksPnlPercent : store.cryptoPnlPercent
        let currency = initialTab == 0 ? store.stocksCurrency : store.cryptoCurrency
        let hasData = initialTab == 0 ? !store.stocks.isEmpty : !store.crypto.isEmpty
        return ThemeCard(padding: 20) {
            VStack(alignment: .leading, spacing: 12) {
                Text(initialTab == 0 ? "Stocks portfolio" : "Crypto portfolio")
                    .font(.system(size: 13, weight: .medium)).foregroundStyle(Theme.inkSoft)
                Text(formatMoney(value, currency: currency))
                    .font(Theme.serif(34)).foregroundStyle(Theme.ink)
                if hasData { PnLBadge(pnl: pnl, large: true) }
            }
        }
    }

    private func holdingsCard(stocks: [StockInvestment] = [], crypto: [CryptoInvestment] = []) -> some View {
        ThemeCard(padding: 6) {
            VStack(spacing: 0) {
                if !stocks.isEmpty {
                    ForEach(Array(stocks.enumerated()), id: \.element.id) { idx, s in
                        HStack(alignment: .top) {
                            VStack(alignment: .leading, spacing: 3) {
                                HStack(spacing: 6) {
                                    Text(s.symbol).font(.system(size: 15, weight: .bold)).foregroundStyle(Theme.ink)
                                    Text(s.stockTypeBadge).font(.caption2).foregroundStyle(Theme.inkFaint)
                                }
                                Text(s.name).font(.caption).foregroundStyle(Theme.inkSoft).lineLimit(1)
                                Text("\(formatNum(s.stockAmount)) sh · \(s.broker)")
                                    .font(.caption2).foregroundStyle(Theme.inkFaint)
                            }
                            Spacer()
                            VStack(alignment: .trailing, spacing: 3) {
                                Text(s.convertedCurrentValue.map { formatMoney($0, currency: s.convertedCurrency) } ?? "—")
                                    .font(.system(size: 15, weight: .semibold)).foregroundStyle(Theme.ink)
                                if let pnl = s.pnlPercent { PnLBadge(pnl: pnl) }
                            }
                        }
                        .padding(.horizontal, 12).padding(.vertical, 12)
                        if idx < stocks.count - 1 { Divider().overlay(Theme.hairline) }
                    }
                } else if !crypto.isEmpty {
                    ForEach(Array(crypto.enumerated()), id: \.element.id) { idx, c in
                        HStack(alignment: .top) {
                            VStack(alignment: .leading, spacing: 3) {
                                HStack(spacing: 6) {
                                    Text(c.symbol).font(.system(size: 15, weight: .bold)).foregroundStyle(Theme.ink)
                                    Text(c.name).font(.caption).foregroundStyle(Theme.inkSoft)
                                }
                                Text("\(formatNum(c.amount)) coins").font(.caption2).foregroundStyle(Theme.inkFaint)
                            }
                            Spacer()
                            VStack(alignment: .trailing, spacing: 3) {
                                Text(c.convertedCurrentValue.map { formatMoney($0, currency: c.convertedCurrency) } ?? "—")
                                    .font(.system(size: 15, weight: .semibold)).foregroundStyle(Theme.ink)
                                if let pnl = c.pnlPercent { PnLBadge(pnl: pnl) }
                            }
                        }
                        .padding(.horizontal, 12).padding(.vertical, 12)
                        if idx < crypto.count - 1 { Divider().overlay(Theme.hairline) }
                    }
                } else {
                    Text("No results").foregroundStyle(Theme.inkFaint).font(.subheadline)
                        .frame(maxWidth: .infinity).padding(.vertical, 24)
                }
            }
        }
    }
}
