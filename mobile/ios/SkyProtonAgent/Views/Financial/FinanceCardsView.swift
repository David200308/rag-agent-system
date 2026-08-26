import SwiftUI

struct FinanceCardsView: View {
    @ObservedObject var store: FinancialStore
    @State private var networkFilter = "All"
    @State private var showAddSheet = false
    @State private var editingCard: FinancialCard?
    @State private var pendingDelete: FinancialCard?
    @AppStorage(balanceHiddenKey) private var isBalanceHidden = false

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
                                    Button { pendingDelete = c } label: {
                                        Image(systemName: "trash").font(.system(size: 13)).foregroundStyle(Theme.inkFaint).padding(.leading, 6)
                                    }
                                    .buttonStyle(.plain)
                                }
                                HStack(spacing: 6) {
                                    Text(c.bank).font(.caption).foregroundStyle(Theme.inkSoft)
                                    Text("·").foregroundStyle(Theme.inkFaint)
                                    Text(c.types.joined(separator: "/")).font(.caption).foregroundStyle(Theme.inkSoft)
                                }
                                HStack {
                                    if let limit = c.creditLimit, let cur = c.creditLimitCurrency {
                                        Text("Limit: \(maskedMoney(limit, currency: cur, hidden: isBalanceHidden))").font(.caption2).foregroundStyle(Theme.inkFaint)
                                    }
                                    Spacer()
                                    if let expireDate = c.expireDate {
                                        Text("Exp: \(expireDate)").font(.caption2).foregroundStyle(Theme.inkFaint)
                                    }
                                }
                            }
                        }
                        .contentShape(Rectangle())
                        .onTapGesture { editingCard = c }
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
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button { showAddSheet = true } label: { Image(systemName: "plus") }
            }
        }
        .sheet(isPresented: $showAddSheet) { CardFormView(store: store, editing: nil) }
        .sheet(item: $editingCard) { c in CardFormView(store: store, editing: c) }
        .confirmationDialog(
            "Delete this card?",
            isPresented: Binding(get: { pendingDelete != nil }, set: { if !$0 { pendingDelete = nil } }),
            titleVisibility: .visible
        ) {
            Button("Delete", role: .destructive) {
                if let c = pendingDelete { Task { try? await store.removeCard(id: c.id) } }
                pendingDelete = nil
            }
            Button("Cancel", role: .cancel) { pendingDelete = nil }
        }
    }
}

private struct CardFormView: View {
    @ObservedObject var store: FinancialStore
    var editing: FinancialCard?
    @Environment(\.dismiss) private var dismiss

    @State private var bank = ""
    @State private var countryRegion = ""
    @State private var cardName = ""
    @State private var network = "Visa"
    @State private var types: Set<String> = []
    @State private var expireYear = Calendar.current.component(.year, from: Date())
    @State private var expireMonth = Calendar.current.component(.month, from: Date())
    @State private var creditLimit = ""
    @State private var creditLimitCurrency = "HKD"
    @State private var sharedCredit: Bool?
    @State private var isSaving = false
    @State private var errorMessage: String?

    private var isValid: Bool {
        !bank.trimmingCharacters(in: .whitespaces).isEmpty &&
        !cardName.trimmingCharacters(in: .whitespaces).isEmpty &&
        !types.isEmpty
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Card") {
                    TextField("Card name (e.g. Sapphire Reserve)", text: $cardName)
                    ComboField(placeholder: "Bank (e.g. HSBC, DBS, Citi)", text: $bank,
                               suggestions: Array(Set(store.cards.map(\.bank))).sorted())
                    TextField("Country / region", text: $countryRegion)
                    Picker("Network", selection: $network) {
                        ForEach(cardNetworks, id: \.self) { Text($0).tag($0) }
                    }
                }
                Section("Type") {
                    ForEach(cardTypes, id: \.self) { t in
                        Button {
                            if types.contains(t) { types.remove(t) } else { types.insert(t) }
                        } label: {
                            HStack {
                                Text(t).foregroundStyle(Theme.ink)
                                Spacer()
                                if types.contains(t) { Image(systemName: "checkmark").foregroundStyle(Theme.graphite) }
                            }
                        }
                    }
                }
                Section("Expiry") {
                    Stepper("Year: \(String(expireYear))", value: $expireYear, in: 2000...2100)
                    Picker("Month", selection: $expireMonth) {
                        ForEach(1...12, id: \.self) { Text(String(format: "%02d", $0)).tag($0) }
                    }
                }
                if types.contains("Credit") {
                    Section("Credit limit") {
                        TextField("Amount (leave blank if unknown)", text: $creditLimit).keyboardType(.decimalPad)
                        Picker("Currency", selection: $creditLimitCurrency) {
                            ForEach(commonCurrencies, id: \.self) { Text($0).tag($0) }
                        }
                        Picker("Shared credit", selection: $sharedCredit) {
                            Text("Shared").tag(true as Bool?)
                            Text("Dedicated").tag(false as Bool?)
                            Text("Unknown").tag(nil as Bool?)
                        }
                    }
                }
                if let errorMessage {
                    Section { Text(errorMessage).font(.caption).foregroundStyle(.red) }
                }
            }
            .navigationTitle(editing == nil ? "Add Card" : "Edit Card")
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
        bank = editing.bank
        countryRegion = editing.countryRegion ?? ""
        cardName = editing.cardName
        network = editing.network
        types = Set(editing.types)
        let parts = (editing.expireDate ?? "").split(separator: "-")
        if parts.count == 2, let y = Int(parts[0]), let m = Int(parts[1]) { expireYear = y; expireMonth = m }
        if let limit = editing.creditLimit { creditLimit = formatNum(limit) }
        if let cur = editing.creditLimitCurrency { creditLimitCurrency = cur }
        sharedCredit = editing.sharedCredit
    }

    private func save() async {
        isSaving = true; errorMessage = nil
        let expireDate = "\(String(format: "%04d", expireYear))-\(String(format: "%02d", expireMonth))"
        var fields: [String: Any] = [
            "bank": bank, "countryRegion": countryRegion, "cardName": cardName,
            "network": network, "types": Array(types), "expireDate": expireDate,
        ]
        if types.contains("Credit") {
            if let limit = Double(creditLimit) {
                fields["creditLimit"] = limit
                fields["creditLimitCurrency"] = creditLimitCurrency
            }
            if let shared = sharedCredit { fields["sharedCredit"] = shared }
        }
        do {
            if let editing {
                try await store.editCard(id: editing.id, fields)
            } else {
                try await store.addCard(fields)
            }
            dismiss()
        } catch {
            errorMessage = error.localizedDescription
        }
        isSaving = false
    }
}
