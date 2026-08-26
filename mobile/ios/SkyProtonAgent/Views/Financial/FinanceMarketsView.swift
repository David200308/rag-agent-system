import SwiftUI

private enum PnLFilter: String, CaseIterable { case all = "All", gains = "Gains", losses = "Losses" }

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
                    ForEach(Array(stocks.enumerated()), id: \.element.id) { idx, s in
                        SwipeToDeleteRow(onDelete: { pendingDeleteStock = s }) {
                            HStack(alignment: .top) {
                                SymbolIcon(logoUrl: s.logoUrl, symbol: s.symbol)
                                    .padding(.top, 1)
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
                                    Text(s.convertedCurrentValue.map { maskedMoney($0, currency: s.convertedCurrency, hidden: isBalanceHidden) } ?? "—")
                                        .font(.system(size: 15, weight: .semibold)).foregroundStyle(Theme.ink)
                                    if let pnl = s.pnlPercent { PnLBadge(pnl: pnl) }
                                }
                            }
                            .padding(.horizontal, 12).padding(.vertical, 12)
                            .contentShape(Rectangle())
                            .onTapGesture { editingStock = s }
                        }
                        if idx < stocks.count - 1 { Divider().overlay(Theme.hairline) }
                    }
                } else if !crypto.isEmpty {
                    ForEach(Array(crypto.enumerated()), id: \.element.id) { idx, c in
                        SwipeToDeleteRow(onDelete: { pendingDeleteCrypto = c }) {
                            HStack(alignment: .top) {
                                SymbolIcon(logoUrl: c.logoUrl, symbol: c.symbol)
                                    .padding(.top, 1)
                                VStack(alignment: .leading, spacing: 3) {
                                    HStack(spacing: 6) {
                                        Text(c.symbol).font(.system(size: 15, weight: .bold)).foregroundStyle(Theme.ink)
                                        Text(c.name).font(.caption).foregroundStyle(Theme.inkSoft)
                                    }
                                    Text("\(formatNum(c.amount)) coins").font(.caption2).foregroundStyle(Theme.inkFaint)
                                }
                                Spacer()
                                VStack(alignment: .trailing, spacing: 3) {
                                    Text(c.convertedCurrentValue.map { maskedMoney($0, currency: c.convertedCurrency, hidden: isBalanceHidden) } ?? "—")
                                        .font(.system(size: 15, weight: .semibold)).foregroundStyle(Theme.ink)
                                    if let pnl = c.pnlPercent { PnLBadge(pnl: pnl) }
                                }
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
        NavigationStack {
            Form {
                Section("Position") {
                    Picker("Market", selection: $stockType) {
                        ForEach(stockTypes, id: \.self) { Text(stockTypeLabels[$0] ?? $0).tag($0) }
                    }
                    TextField("Symbol (e.g. AAPL, 0700.HK)", text: $symbol).autocapitalization(.allCharacters)
                    TextField("Company name (e.g. Apple Inc.)", text: $name)
                    ComboField(placeholder: "Broker (e.g. Interactive Brokers, Futu)", text: $broker,
                               suggestions: Array(Set(store.stocks.map(\.broker))).sorted())
                }
                Section("Cost basis") {
                    TextField("Shares", text: $stockAmount).keyboardType(.decimalPad)
                    Picker("Currency", selection: $currency) {
                        ForEach(commonCurrencies, id: \.self) { Text($0).tag($0) }
                    }
                    TextField("Amount invested", text: $investAmount).keyboardType(.decimalPad)
                    TextField("Fee (optional)", text: $fee).keyboardType(.decimalPad)
                }
                if let errorMessage {
                    Section { Text(errorMessage).font(.caption).foregroundStyle(.red) }
                }
            }
            .navigationTitle(editing == nil ? "Add Stock" : "Edit Stock")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    if isSaving { ProgressView() } else {
                        Button("Save") { Task { await save() } }.disabled(!isValid)
                    }
                }
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
        NavigationStack {
            Form {
                Section("Position") {
                    TextField("Symbol (e.g. BTC)", text: $symbol).autocapitalization(.allCharacters)
                    TextField("Name (e.g. Bitcoin)", text: $name)
                }
                Section("Cost basis") {
                    TextField("Coins/tokens held", text: $amount).keyboardType(.decimalPad)
                    Picker("Currency", selection: $currency) {
                        ForEach(commonCurrencies, id: \.self) { Text($0).tag($0) }
                    }
                    TextField("Amount invested", text: $investAmount).keyboardType(.decimalPad)
                }
                if let errorMessage {
                    Section { Text(errorMessage).font(.caption).foregroundStyle(.red) }
                }
            }
            .navigationTitle(editing == nil ? "Add Crypto" : "Edit Crypto")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    if isSaving { ProgressView() } else {
                        Button("Save") { Task { await save() } }.disabled(!isValid)
                    }
                }
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
