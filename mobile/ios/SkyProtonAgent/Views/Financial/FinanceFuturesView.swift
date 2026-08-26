import SwiftUI

struct FinanceFuturesView: View {
    @ObservedObject var store: FinancialStore
    @State private var sourceFilter = "All"
    @State private var showAddSheet = false
    @State private var editingFuture: FutureInvestment?
    @State private var pendingDelete: FutureInvestment?
    @AppStorage(balanceHiddenKey) private var isBalanceHidden = false

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
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button { showAddSheet = true } label: { Image(systemName: "plus") }
            }
        }
        .sheet(isPresented: $showAddSheet) { FutureFormView(store: store, editing: nil) }
        .sheet(item: $editingFuture) { f in FutureFormView(store: store, editing: f) }
        .confirmationDialog(
            "Delete this position?",
            isPresented: Binding(get: { pendingDelete != nil }, set: { if !$0 { pendingDelete = nil } }),
            titleVisibility: .visible
        ) {
            Button("Delete", role: .destructive) {
                if let f = pendingDelete { Task { try? await store.removeFuture(id: f.id) } }
                pendingDelete = nil
            }
            Button("Cancel", role: .cancel) { pendingDelete = nil }
        }
    }

    private var summaryCard: some View {
        ThemeCard(padding: 20) {
            VStack(alignment: .leading, spacing: 6) {
                Text("Open PnL").font(.system(size: 13, weight: .medium)).foregroundStyle(Theme.inkSoft)
                Text(maskedMoney(store.futuresOpenPnl, currency: store.futuresCurrency, hidden: isBalanceHidden))
                    .font(Theme.serif(34))
                    .foregroundStyle(store.futures.isEmpty ? Theme.ink : (store.futuresOpenPnl >= 0 ? Theme.positive : Theme.negative))
                Text("\(store.futures.count) position\(store.futures.count == 1 ? "" : "s")")
                    .font(.caption).foregroundStyle(Theme.inkFaint)
            }
        }
    }

    private func positionCard(_ f: FutureInvestment) -> some View {
        let isManual = f.source == "MANUAL"
        return ThemeCard {
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
                    if isManual {
                        Spacer()
                        Button { pendingDelete = f } label: {
                            Image(systemName: "trash").font(.system(size: 13)).foregroundStyle(Theme.inkFaint)
                        }
                        .buttonStyle(.plain)
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
                        Text(maskedMoney(cv, currency: f.convertedCurrency ?? f.currency, hidden: isBalanceHidden))
                            .font(.system(size: 15, weight: .bold))
                            .foregroundStyle(pnl >= 0 ? Theme.positive : Theme.negative)
                        PnLBadge(pnl: pnl)
                    } else {
                        Text("—").foregroundStyle(Theme.inkFaint)
                    }
                }
            }
        }
        .contentShape(Rectangle())
        .onTapGesture { if isManual { editingFuture = f } }
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

/// Only SECURITY and CRYPTO_CEX (manual) positions are editable here — CRYPTO_DEX rows
/// are live-tracked from a wallet/connection address, not user-entered, so they're read-only.
private let manualFutureKinds = ["SECURITY", "CRYPTO_CEX"]

private struct FutureFormView: View {
    @ObservedObject var store: FinancialStore
    var editing: FutureInvestment?
    @Environment(\.dismiss) private var dismiss

    @State private var exchangeKind = "SECURITY"
    @State private var exchange = "BINANCE"
    @State private var symbol = ""
    @State private var side = "LONG"
    @State private var quantity = ""
    @State private var entryPrice = ""
    @State private var leverage = ""
    @State private var currency = "USD"
    @State private var isSaving = false
    @State private var errorMessage: String?

    private var isValid: Bool {
        !symbol.trimmingCharacters(in: .whitespaces).isEmpty &&
        Double(quantity) != nil && Double(entryPrice) != nil
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Market") {
                    Picker("Type", selection: $exchangeKind) {
                        Text("Security").tag("SECURITY")
                        Text("Crypto (CEX)").tag("CRYPTO_CEX")
                    }
                    .pickerStyle(.segmented)
                    if exchangeKind == "CRYPTO_CEX" {
                        Picker("Exchange", selection: $exchange) {
                            ForEach(cexExchanges, id: \.self) { Text($0.capitalized).tag($0) }
                        }
                    }
                    TextField("Symbol (e.g. AAPL, BTCUSDT)", text: $symbol).autocapitalization(.allCharacters)
                    Picker("Side", selection: $side) {
                        ForEach(futureSides, id: \.self) { Text($0.capitalized).tag($0) }
                    }
                    .pickerStyle(.segmented)
                }
                Section("Position") {
                    TextField("Quantity", text: $quantity).keyboardType(.decimalPad)
                    TextField("Entry price", text: $entryPrice).keyboardType(.decimalPad)
                    TextField("Leverage (optional)", text: $leverage).keyboardType(.decimalPad)
                    Picker("Currency", selection: $currency) {
                        ForEach(commonCurrencies, id: \.self) { Text($0).tag($0) }
                    }
                }
                if let errorMessage {
                    Section { Text(errorMessage).font(.caption).foregroundStyle(.red) }
                }
            }
            .navigationTitle(editing == nil ? "Add Future" : "Edit Future")
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
        exchangeKind = manualFutureKinds.contains(editing.exchangeKind) ? editing.exchangeKind : "SECURITY"
        if cexExchanges.contains(editing.exchange) { exchange = editing.exchange }
        symbol = editing.symbol ?? ""
        side = editing.side ?? "LONG"
        quantity = editing.quantity.map(formatNum) ?? ""
        entryPrice = editing.entryPrice.map(formatNum) ?? ""
        leverage = editing.leverage.map(formatNum) ?? ""
        currency = editing.currency
    }

    private func save() async {
        guard let qty = Double(quantity), let entry = Double(entryPrice) else { return }
        isSaving = true; errorMessage = nil
        var fields: [String: Any] = [
            "exchangeKind": exchangeKind, "symbol": symbol.uppercased(), "side": side,
            "quantity": qty, "entryPrice": entry, "currency": currency,
        ]
        if exchangeKind == "CRYPTO_CEX" { fields["exchange"] = exchange }
        if let lev = Double(leverage) { fields["leverage"] = lev }
        do {
            if let editing {
                try await store.editFuture(id: editing.id, fields)
            } else {
                try await store.addFuture(fields)
            }
            dismiss()
        } catch {
            errorMessage = error.localizedDescription
        }
        isSaving = false
    }
}
