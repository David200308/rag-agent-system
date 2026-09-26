import SwiftUI

struct FinanceDepositsView: View {
    @ObservedObject var store: FinancialStore
    @State private var currencyFilter = "All"
    @State private var showAddSheet = false
    @State private var editingDeposit: CashDeposit?
    @State private var pendingDelete: CashDeposit?
    @AppStorage(balanceHiddenKey) private var isBalanceHidden = false

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
                            Text(maskedMoney(filtered.reduce(0) { $0 + $1.convertedAmount },
                                              currency: filtered.first?.convertedCurrency ?? "USD", hidden: isBalanceHidden))
                                .font(Theme.serif(30)).foregroundStyle(Theme.ink)
                        }
                    }
                    if availableCurrencies.count > 2 {
                        ThemeChipRow {
                            ForEach(availableCurrencies, id: \.self) { c in
                                ThemeChip(label: c, isActive: currencyFilter == c) { currencyFilter = c }
                            }
                        }
                    }
                    ThemeCard(padding: 6) {
                        VStack(spacing: 0) {
                            ForEach(Array(filtered.enumerated()), id: \.element.id) { idx, d in
                                SwipeToDeleteRow(onDelete: { pendingDelete = d }) {
                                    HStack {
                                        VStack(alignment: .leading, spacing: 3) {
                                            Text(d.platform).font(.system(size: 15, weight: .medium)).foregroundStyle(Theme.ink)
                                            Text("\(d.depositType) · \(d.platformType)").font(.caption).foregroundStyle(Theme.inkFaint)
                                        }
                                        Spacer()
                                        VStack(alignment: .trailing, spacing: 3) {
                                            Text(maskedMoney(d.amount, currency: d.currency, hidden: isBalanceHidden)).font(.system(size: 15, weight: .semibold)).foregroundStyle(Theme.ink)
                                            Text(maskedMoney(d.convertedAmount, currency: d.convertedCurrency, hidden: isBalanceHidden)).font(.caption).foregroundStyle(Theme.inkFaint)
                                        }
                                    }
                                    .padding(.horizontal, 12).padding(.vertical, 12)
                                    .contentShape(Rectangle())
                                    .onTapGesture { editingDeposit = d }
                                }
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
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button { showAddSheet = true } label: { Image(systemName: "plus") }
            }
        }
        .sheet(isPresented: $showAddSheet) { DepositFormView(store: store, editing: nil) }
        .sheet(item: $editingDeposit) { d in DepositFormView(store: store, editing: d) }
        .confirmationDialog(
            "Delete this deposit?",
            isPresented: Binding(get: { pendingDelete != nil }, set: { if !$0 { pendingDelete = nil } }),
            titleVisibility: .visible
        ) {
            Button("Delete", role: .destructive) {
                if let d = pendingDelete { Task { try? await store.removeDeposit(id: d.id) } }
                pendingDelete = nil
            }
            Button("Cancel", role: .cancel) { pendingDelete = nil }
        }
    }
}

private struct DepositFormView: View {
    @ObservedObject var store: FinancialStore
    var editing: CashDeposit?
    @Environment(\.dismiss) private var dismiss

    @State private var platform = ""
    @State private var platformType = ""
    @State private var countryRegion = ""
    @State private var depositType = "FIXED"
    @State private var currency = "USD"
    @State private var amount = ""
    @State private var isSaving = false
    @State private var errorMessage: String?

    private var isValid: Bool {
        !platform.trimmingCharacters(in: .whitespaces).isEmpty &&
        !platformType.trimmingCharacters(in: .whitespaces).isEmpty &&
        Double(amount) != nil
    }

    var body: some View {
        FormSheet(
            editing == nil ? "New deposit" : "Edit deposit",
            subtitle: "Cash held at a bank or platform",
            primaryLabel: editing == nil ? "Add deposit" : "Save changes",
            isPrimaryEnabled: isValid, isSaving: isSaving, errorMessage: errorMessage,
            onPrimary: { Task { await save() } }
        ) {
            FormSection {
                FormAmountHero(label: "Amount", amount: $amount, currency: $currency)
                FormDivider()
                FormChoiceRow("Term") {
                    FormSegmented(options: depositTypes, selection: $depositType) { $0.capitalized }
                }
            }
            FormSection("Account") {
                ComboField(label: "Platform", placeholder: "e.g. HSBC, DBS", text: $platform,
                           suggestions: Array(Set(store.deposits.map(\.platform))).sorted())
                FormDivider()
                ComboField(label: "Platform type", placeholder: "e.g. Bank, Brokerage", text: $platformType,
                           suggestions: Array(Set(store.deposits.map(\.platformType))).sorted())
                FormDivider()
                ComboField(label: "Country / region", placeholder: "Optional", text: $countryRegion,
                           suggestions: Array(Set(store.deposits.compactMap {
                               let v = $0.countryRegion?.trimmingCharacters(in: .whitespaces)
                               return (v?.isEmpty ?? true) ? nil : v
                           })).sorted())
            }
        }
        .onAppear(perform: populate)
    }

    private func populate() {
        guard let editing else { return }
        platform = editing.platform
        platformType = editing.platformType
        countryRegion = editing.countryRegion ?? ""
        depositType = editing.depositType
        currency = editing.currency
        amount = formatNum(editing.amount)
    }

    private func save() async {
        guard let amt = Double(amount) else { return }
        isSaving = true; errorMessage = nil
        let fields: [String: Any] = [
            "platform": platform, "platformType": platformType, "countryRegion": countryRegion,
            "depositType": depositType, "currency": currency, "amount": amt,
        ]
        do {
            if let editing {
                try await store.editDeposit(id: editing.id, fields)
            } else {
                try await store.addDeposit(fields)
            }
            dismiss()
        } catch {
            errorMessage = error.localizedDescription
        }
        isSaving = false
    }
}
