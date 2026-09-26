import SwiftUI

private enum PnLFilter: String, CaseIterable { case all = "All", gains = "Gains", losses = "Losses" }

/// The same stock bought through multiple brokers should read as one position, not one row
/// per purchase — mirrors the web app's `groupStocksBySymbol` (components/financial/utils.ts):
/// same aggregation math (sum amounts/invest, weighted avg price, summed converted values),
/// same "first row wins" for display-only fields like name/logo.
private struct StockGroup: Identifiable {
    let symbol: String
    let name: String
    let stockType: String
    let logoUrl: String?
    let rows: [StockInvestment]
    let stockAmount: Double
    let investAmount: Double
    let fee: Double
    let currency: String
    let avgPrice: Double?
    let currentPrice: Double?
    let priceCurrency: String?
    let convertedInvestAmount: Double
    let convertedCurrentValue: Double?
    let convertedCurrency: String
    let pnlPercent: Double?

    var id: String { symbol }
    var currentValue: Double? { currentPrice.map { $0 * stockAmount } }
}

private func groupStocksBySymbol(_ rows: [StockInvestment]) -> [StockGroup] {
    var order: [String] = []
    var buckets: [String: [StockInvestment]] = [:]
    for r in rows {
        if buckets[r.symbol] == nil { order.append(r.symbol) }
        buckets[r.symbol, default: []].append(r)
    }
    return order.map { symbol in
        let group = buckets[symbol]!
        let first = group[0]
        let stockAmount = group.reduce(0) { $0 + $1.stockAmount }
        let investAmount = group.reduce(0) { $0 + $1.investAmount }
        let fee = group.reduce(0) { $0 + $1.fee }
        let convertedInvestAmount = group.reduce(0) { $0 + $1.convertedInvestAmount }
        let convertedCurrentValue = group.reduce(0.0) { $0 + ($1.convertedCurrentValue ?? $1.convertedInvestAmount) }
        let pnlPercent: Double? = convertedInvestAmount > 0
            ? ((convertedCurrentValue - convertedInvestAmount) / convertedInvestAmount * 10000).rounded() / 100
            : nil
        return StockGroup(
            symbol: symbol, name: first.name, stockType: first.stockType, logoUrl: first.logoUrl, rows: group,
            stockAmount: stockAmount, investAmount: investAmount, fee: fee, currency: first.currency,
            avgPrice: stockAmount > 0 ? (investAmount + fee) / stockAmount : nil,
            currentPrice: first.currentPrice, priceCurrency: first.priceCurrency,
            convertedInvestAmount: convertedInvestAmount, convertedCurrentValue: convertedCurrentValue,
            convertedCurrency: first.convertedCurrency, pnlPercent: pnlPercent
        )
    }
}

struct FinanceMarketsView: View {
    @ObservedObject var store: FinancialStore
    @State var initialTab: Int = 0

    @State private var searchText = ""
    @State private var marketFilter = "All"
    @State private var pnlFilter = PnLFilter.all
    @State private var showFilter = false
    @State private var showAddSheet = false
    @State private var editingStock: StockInvestment?
    @State private var editingCrypto: CryptoInvestment?
    @State private var pendingDeleteStock: StockInvestment?
    @State private var pendingDeleteCrypto: CryptoInvestment?
    @State private var alertTarget: (symbol: String, assetType: String)?
    @State private var expandedSymbols: Set<String> = []
    @AppStorage(balanceHiddenKey) private var isBalanceHidden = false

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
                        ThemeChipRow {
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
                Button { showAddSheet = true } label: { Image(systemName: "plus") }
            }
            ToolbarItem(placement: .navigationBarTrailing) {
                Button {
                    showFilter = true
                } label: {
                    Image(systemName: hasActiveFilter ? "line.3.horizontal.decrease.circle.fill" : "line.3.horizontal.decrease.circle")
                }
            }
        }
        .sheet(isPresented: $showAddSheet) {
            if initialTab == 0 {
                StockFormView(store: store, editing: nil)
            } else {
                CryptoFormView(store: store, editing: nil)
            }
        }
        .sheet(item: $editingStock) { s in StockFormView(store: store, editing: s) }
        .sheet(item: $editingCrypto) { c in CryptoFormView(store: store, editing: c) }
        .sheet(isPresented: Binding(get: { alertTarget != nil }, set: { if !$0 { alertTarget = nil } })) {
            if let target = alertTarget {
                PriceAlertSheet(symbol: target.symbol, assetType: target.assetType)
            }
        }
        .confirmationDialog(
            "Delete this position?",
            isPresented: Binding(get: { pendingDeleteStock != nil }, set: { if !$0 { pendingDeleteStock = nil } }),
            titleVisibility: .visible
        ) {
            Button("Delete", role: .destructive) {
                if let s = pendingDeleteStock { Task { try? await store.removeStock(id: s.id) } }
                pendingDeleteStock = nil
            }
            Button("Cancel", role: .cancel) { pendingDeleteStock = nil }
        }
        .confirmationDialog(
            "Delete this position?",
            isPresented: Binding(get: { pendingDeleteCrypto != nil }, set: { if !$0 { pendingDeleteCrypto = nil } }),
            titleVisibility: .visible
        ) {
            Button("Delete", role: .destructive) {
                if let c = pendingDeleteCrypto { Task { try? await store.removeCrypto(id: c.id) } }
                pendingDeleteCrypto = nil
            }
            Button("Cancel", role: .cancel) { pendingDeleteCrypto = nil }
        }
        .sheet(isPresented: $showFilter) {
            FormSheet("Filter", primaryLabel: "Show results", onPrimary: { showFilter = false }) {
                FormSection {
                    if initialTab == 0 {
                        FormChoiceRow("Market") {
                            FormChips(options: availableMarkets, selection: $marketFilter) { $0 }
                        }
                        FormDivider()
                    }
                    FormChoiceRow("Performance") {
                        FormSegmented(options: PnLFilter.allCases, selection: $pnlFilter) { $0.rawValue }
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
                Text(maskedMoney(value, currency: currency, hidden: isBalanceHidden))
                    .font(Theme.serif(34)).foregroundStyle(Theme.ink)
                if hasData { PnLBadge(pnl: pnl, large: true) }
            }
        }
    }

    private func holdingsCard(stocks: [StockInvestment] = [], crypto: [CryptoInvestment] = []) -> some View {
        ThemeCard(padding: 6) {
            VStack(spacing: 0) {
                if !stocks.isEmpty {
                    let groups = groupStocksBySymbol(stocks)
                    ForEach(Array(groups.enumerated()), id: \.element.id) { idx, group in
                        if group.rows.count == 1 {
                            stockRow(group.rows[0])
                        } else {
                            stockGroupHeaderRow(group)
                            if expandedSymbols.contains(group.symbol) {
                                ForEach(group.rows) { s in
                                    stockRow(s, indented: true)
                                }
                            }
                        }
                        if idx < groups.count - 1 { Divider().overlay(Theme.hairline) }
                    }
                } else if !crypto.isEmpty {
                    ForEach(Array(crypto.enumerated()), id: \.element.id) { idx, c in
                        SwipeToDeleteRow(
                            actions: [SwipeAction(icon: "bell", tint: .orange, action: { alertTarget = (c.symbol, "CRYPTO") })],
                            onDelete: { pendingDeleteCrypto = c }
                        ) {
                            HStack(alignment: .top) {
                                SymbolIcon(logoUrl: c.logoUrl, symbol: c.symbol)
                                    .padding(.top, 1)
                                VStack(alignment: .leading, spacing: 3) {
                                    HStack(spacing: 6) {
                                        Text(c.symbol).font(.system(size: 15, weight: .bold)).foregroundStyle(Theme.ink)
                                        Text(c.name).font(.caption).foregroundStyle(Theme.inkSoft)
                                    }
                                    Text("\(formatNum(c.amount)) coins").font(.caption2).foregroundStyle(Theme.inkFaint)
                                    avgCostLabel(c.amount > 0 ? c.investAmount / c.amount : nil, currency: c.currency)
                                }
                                Spacer()
                                VStack(alignment: .trailing, spacing: 3) {
                                    Text(c.convertedCurrentValue.map { maskedMoney($0, currency: c.convertedCurrency, hidden: isBalanceHidden) } ?? "—")
                                        .font(.system(size: 15, weight: .semibold)).foregroundStyle(Theme.ink)
                                    currentPriceLabel(c.currentPrice, currency: "USD")
                                    if let pnl = c.pnlPercent { PnLBadge(pnl: pnl) }
                                }
                                .fixedSize()
                            }
                            .padding(.horizontal, 12).padding(.vertical, 12)
                            .contentShape(Rectangle())
                            .onTapGesture { editingCrypto = c }
                        }
                        if idx < crypto.count - 1 { Divider().overlay(Theme.hairline) }
                    }
                } else {
                    Text("No results").foregroundStyle(Theme.inkFaint).font(.subheadline)
                        .frame(maxWidth: .infinity).padding(.vertical, 24)
                }
            }
        }
    }

    /// Live per-unit price under the position value — "—" until the price refresh fills it in.
    private func currentPriceLabel(_ price: Double?, currency: String?) -> some View {
        Text(price.map { maskedPrice($0, currency: currency, hidden: isBalanceHidden) } ?? "Price —")
            .font(.caption2.monospacedDigit())
            .foregroundStyle(Theme.inkSoft)
            .lineLimit(1)
    }

    /// Average cost per share including fees, same as the web table.
    private func avgCost(_ s: StockInvestment) -> Double? {
        s.stockAmount > 0 ? (s.investAmount + s.fee) / s.stockAmount : nil
    }

    /// Own line under shares/broker so a long price never truncates the broker name.
    @ViewBuilder
    private func avgCostLabel(_ avg: Double?, currency: String) -> some View {
        if let avg {
            Text("Avg \(maskedPrice(avg, currency: currency, hidden: isBalanceHidden))")
                .font(.caption2.monospacedDigit())
                .foregroundStyle(Theme.inkFaint)
                .lineLimit(1)
        }
    }

    /// One stock row. `indented` is used for a broker sub-row inside an expanded group —
    /// no bell action there (alerts are per-symbol, already on the group header) and it
    /// shows the broker instead of the symbol/name, which the group header already shows.
    private func stockRow(_ s: StockInvestment, indented: Bool = false) -> some View {
        SwipeToDeleteRow(
            actions: indented ? [] : [SwipeAction(icon: "bell", tint: .orange, action: { alertTarget = (s.symbol, "STOCK") })],
            onDelete: { pendingDeleteStock = s }
        ) {
            HStack(alignment: .top) {
                if indented {
                    Text("↳").foregroundStyle(Theme.inkFaint)
                    VStack(alignment: .leading, spacing: 3) {
                        Text(s.broker).font(.system(size: 13, weight: .medium)).foregroundStyle(Theme.inkSoft).lineLimit(1)
                        Text("\(formatNum(s.stockAmount)) sh").font(.caption2).foregroundStyle(Theme.inkFaint)
                        avgCostLabel(avgCost(s), currency: s.currency)
                    }
                } else {
                    SymbolIcon(logoUrl: s.logoUrl, symbol: s.symbol)
                        .padding(.top, 1)
                    VStack(alignment: .leading, spacing: 3) {
                        HStack(spacing: 6) {
                            Text(s.symbol).font(.system(size: 15, weight: .bold)).foregroundStyle(Theme.ink)
                            Text(s.stockTypeBadge).font(.caption2).foregroundStyle(Theme.inkFaint)
                        }
                        Text(s.name).font(.caption).foregroundStyle(Theme.inkSoft).lineLimit(1)
                        Text("\(formatNum(s.stockAmount)) sh · \(s.broker)")
                            .font(.caption2).foregroundStyle(Theme.inkFaint).lineLimit(1)
                        avgCostLabel(avgCost(s), currency: s.currency)
                    }
                }
                Spacer()
                VStack(alignment: .trailing, spacing: 3) {
                    Text(s.convertedCurrentValue.map { maskedMoney($0, currency: s.convertedCurrency, hidden: isBalanceHidden) } ?? "—")
                        .font(.system(size: indented ? 13 : 15, weight: .semibold))
                        .foregroundStyle(indented ? Theme.inkSoft : Theme.ink)
                    if !indented { currentPriceLabel(s.currentPrice, currency: s.priceCurrency ?? s.currency) }
                    if let pnl = s.pnlPercent { PnLBadge(pnl: pnl) }
                }
                .fixedSize()
            }
            .padding(.horizontal, 12).padding(.vertical, indented ? 10 : 12)
            .padding(.leading, indented ? 22 : 0)
            .background(indented ? Theme.chipFill.opacity(0.5) : Color.clear)
            .contentShape(Rectangle())
            .onTapGesture { editingStock = s }
        }
    }

    /// Collapsed summary for a symbol held across multiple brokers — tap to expand/collapse
    /// the individual broker rows. No swipe-to-delete here (a group isn't one deletable
    /// record); alert is a persistent button instead of a swipe action for the same reason
    /// swipe would otherwise have nothing else to reveal.
    private func stockGroupHeaderRow(_ group: StockGroup) -> some View {
        let isExpanded = expandedSymbols.contains(group.symbol)
        return HStack(alignment: .top) {
            Image(systemName: isExpanded ? "chevron.down" : "chevron.right")
                .font(.caption2).foregroundStyle(Theme.inkFaint)
                .padding(.top, 5)
            SymbolIcon(logoUrl: group.logoUrl, symbol: group.symbol)
                .padding(.top, 1)
            VStack(alignment: .leading, spacing: 3) {
                HStack(spacing: 6) {
                    Text(group.symbol).font(.system(size: 15, weight: .bold)).foregroundStyle(Theme.ink)
                    Text(stockTypeBadgeLabel(group.stockType)).font(.caption2).foregroundStyle(Theme.inkFaint)
                }
                Text(group.name).font(.caption).foregroundStyle(Theme.inkSoft).lineLimit(1)
                Text("\(formatNum(group.stockAmount)) sh · \(group.rows.count) brokers")
                    .font(.caption2).foregroundStyle(Theme.inkFaint)
                avgCostLabel(group.avgPrice, currency: group.currency)
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 3) {
                Text(group.convertedCurrentValue.map { maskedMoney($0, currency: group.convertedCurrency, hidden: isBalanceHidden) } ?? "—")
                    .font(.system(size: 15, weight: .semibold)).foregroundStyle(Theme.ink)
                currentPriceLabel(group.currentPrice, currency: group.priceCurrency ?? group.currency)
                if let pnl = group.pnlPercent { PnLBadge(pnl: pnl) }
            }
            .fixedSize()
            Button {
                alertTarget = (group.symbol, "STOCK")
            } label: {
                Image(systemName: "bell").font(.system(size: 13)).foregroundStyle(.orange).padding(.leading, 8)
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 12).padding(.vertical, 12)
        .contentShape(Rectangle())
        .onTapGesture {
            withAnimation(.easeOut(duration: 0.15)) {
                if isExpanded { expandedSymbols.remove(group.symbol) } else { expandedSymbols.insert(group.symbol) }
            }
        }
    }
}

private struct StockFormView: View {
    @ObservedObject var store: FinancialStore
    var editing: StockInvestment?
    @Environment(\.dismiss) private var dismiss

    @State private var broker = ""
    @State private var stockType = "US_STOCK"
    @State private var symbol = ""
    @State private var name = ""
    @State private var stockAmount = ""
    @State private var investAmount = ""
    @State private var currency = "USD"
    @State private var fee = ""
    @State private var isSaving = false
    @State private var errorMessage: String?

    private var isValid: Bool {
        !broker.trimmingCharacters(in: .whitespaces).isEmpty &&
        !symbol.trimmingCharacters(in: .whitespaces).isEmpty &&
        !name.trimmingCharacters(in: .whitespaces).isEmpty &&
        Double(stockAmount) != nil && Double(investAmount) != nil
    }

    var body: some View {
        FormSheet(
            editing == nil ? "New stock" : "Edit stock",
            subtitle: "Shares held with a broker",
            primaryLabel: editing == nil ? "Add position" : "Save changes",
            isPrimaryEnabled: isValid, isSaving: isSaving, errorMessage: errorMessage,
            onPrimary: { Task { await save() } }
        ) {
            FormSection("Position") {
                FormChoiceRow("Market") {
                    FormChips(options: stockTypes, selection: $stockType) { stockTypeLabels[$0] ?? $0 }
                }
                FormDivider()
                FormField(label: "Symbol", text: $symbol, placeholder: "e.g. AAPL, 0700.HK",
                          capitalization: .characters)
                FormDivider()
                FormField(label: "Company name", text: $name, placeholder: "e.g. Apple Inc.")
                FormDivider()
                ComboField(label: "Broker", placeholder: "e.g. Interactive Brokers, Futu", text: $broker,
                           suggestions: Array(Set(store.stocks.map(\.broker))).sorted())
            }
            FormSection("Cost basis") {
                FormAmountHero(label: "Amount invested", amount: $investAmount, currency: $currency)
                FormDivider()
                FormNumberRow(label: "Shares", text: $stockAmount)
                FormDivider()
                FormNumberRow(label: "Fee", text: $fee, placeholder: "Optional")
            }
        }
        .onAppear(perform: populate)
    }

    private func populate() {
        guard let editing else { return }
        broker = editing.broker
        stockType = editing.stockType
        symbol = editing.symbol
        name = editing.name
        stockAmount = formatNum(editing.stockAmount)
        investAmount = formatNum(editing.investAmount)
        currency = editing.currency
        fee = formatNum(editing.fee)
    }

    private func save() async {
        guard let amt = Double(stockAmount), let inv = Double(investAmount) else { return }
        isSaving = true; errorMessage = nil
        var fields: [String: Any] = [
            "broker": broker, "stockType": stockType, "symbol": symbol.uppercased(), "name": name,
            "stockAmount": amt, "investAmount": inv, "currency": currency,
        ]
        if let f = Double(fee) { fields["fee"] = f }
        do {
            if let editing {
                try await store.editStock(id: editing.id, fields)
            } else {
                try await store.addStock(fields)
            }
            dismiss()
        } catch {
            errorMessage = error.localizedDescription
        }
        isSaving = false
    }
}

private struct CryptoFormView: View {
    @ObservedObject var store: FinancialStore
    var editing: CryptoInvestment?
    @Environment(\.dismiss) private var dismiss

    @State private var name = ""
    @State private var symbol = ""
    @State private var amount = ""
    @State private var investAmount = ""
    @State private var currency = "USD"
    @State private var isSaving = false
    @State private var errorMessage: String?

    private var isValid: Bool {
        !symbol.trimmingCharacters(in: .whitespaces).isEmpty &&
        !name.trimmingCharacters(in: .whitespaces).isEmpty &&
        Double(amount) != nil && Double(investAmount) != nil
    }

    var body: some View {
        FormSheet(
            editing == nil ? "New crypto" : "Edit crypto",
            subtitle: "Coins or tokens you hold",
            primaryLabel: editing == nil ? "Add position" : "Save changes",
            isPrimaryEnabled: isValid, isSaving: isSaving, errorMessage: errorMessage,
            onPrimary: { Task { await save() } }
        ) {
            FormSection("Asset") {
                FormField(label: "Symbol", text: $symbol, placeholder: "e.g. BTC", capitalization: .characters)
                FormDivider()
                FormField(label: "Name", text: $name, placeholder: "e.g. Bitcoin")
            }
            FormSection("Cost basis") {
                FormAmountHero(label: "Amount invested", amount: $investAmount, currency: $currency)
                FormDivider()
                FormNumberRow(label: "Coins / tokens held", text: $amount)
            }
        }
        .onAppear(perform: populate)
    }

    private func populate() {
        guard let editing else { return }
        name = editing.name
        symbol = editing.symbol
        amount = formatNum(editing.amount)
        investAmount = formatNum(editing.investAmount)
        currency = editing.currency
    }

    private func save() async {
        guard let amt = Double(amount), let inv = Double(investAmount) else { return }
        isSaving = true; errorMessage = nil
        let fields: [String: Any] = [
            "name": name, "symbol": symbol.uppercased(), "amount": amt,
            "investAmount": inv, "currency": currency,
        ]
        do {
            if let editing {
                try await store.editCrypto(id: editing.id, fields)
            } else {
                try await store.addCrypto(fields)
            }
            dismiss()
        } catch {
            errorMessage = error.localizedDescription
        }
        isSaving = false
    }
}
