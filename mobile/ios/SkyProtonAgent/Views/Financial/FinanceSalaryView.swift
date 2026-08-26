import SwiftUI
import Charts

struct FinanceSalaryView: View {
    @ObservedObject var store: FinancialStore
    @State private var showAddSheet = false
    @State private var editingRecord: SalaryUsageRecord?
    @AppStorage(balanceHiddenKey) private var isBalanceHidden = false

    private var sorted: [SalaryUsageRecord] {
        store.salaryRecords.sorted { ($0.year, $0.month) > ($1.year, $1.month) }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                if sorted.isEmpty {
                    ThemeCard {
                        Text("No salary records").foregroundStyle(Theme.inkFaint).frame(maxWidth: .infinity)
                    }
                } else {
                    let totalSalaryBonus = sorted.reduce(0.0) { $0 + $1.salary + $1.bonus }
                    let totalExpense = sorted.reduce(0.0) { $0 + $1.totalExpense }
                    let currency = sorted.first?.currency ?? "USD"

                    HStack(spacing: 12) {
                        ThemeCard(padding: 16) {
                            VStack(alignment: .leading, spacing: 4) {
                                Text("Salary + Bonus").font(.caption).foregroundStyle(Theme.inkSoft)
                                Text(maskedMoney(totalSalaryBonus, currency: currency, hidden: isBalanceHidden)).font(.system(size: 18, weight: .bold)).foregroundStyle(Theme.ink)
                            }
                        }
                        ThemeCard(padding: 16) {
                            VStack(alignment: .leading, spacing: 4) {
                                Text("Total Expense").font(.caption).foregroundStyle(Theme.inkSoft)
                                Text(maskedMoney(totalExpense, currency: currency, hidden: isBalanceHidden)).font(.system(size: 18, weight: .bold)).foregroundStyle(Theme.ink)
                            }
                        }
                    }

                    if isBalanceHidden {
                        ThemeCard {
                            Text("Amounts hidden").font(.subheadline).foregroundStyle(Theme.inkFaint)
                                .frame(maxWidth: .infinity).padding(.vertical, 40)
                        }
                    } else {
                        ThemeCard {
                            SalaryChartView(records: sorted)
                        }
                    }
                    ThemeCard(padding: 12) {
                        SalaryTableView(records: sorted, onSelect: { editingRecord = $0 }, hidden: isBalanceHidden)
                    }
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 16)
        }
        .background(Theme.background)
        .tabBarSafeArea()
        .navigationTitle("Salary & Expense")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button { showAddSheet = true } label: { Image(systemName: "plus") }
            }
        }
        .sheet(isPresented: $showAddSheet) { SalaryFormView(store: store, editing: nil) }
        .sheet(item: $editingRecord) { r in SalaryFormView(store: store, editing: r) }
    }
}

private struct SalaryChartView: View {
    let records: [SalaryUsageRecord]

    private struct Point: Identifiable {
        let id = UUID()
        let label: String
        let value: Double
        let series: String
    }

    private var chartData: [Point] {
        records
            .sorted { ($0.year, $0.month) < ($1.year, $1.month) }
            .flatMap { r -> [Point] in
                let ym = "\(r.year)/\(String(format: "%02d", r.month))"
                return [
                    Point(label: ym, value: r.salary + r.bonus, series: "Salary + Bonus"),
                    Point(label: ym, value: r.totalExpense,      series: "Total Expense"),
                ]
            }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Salary & Expense Trend").font(.caption).foregroundStyle(Theme.inkSoft)
            Chart(chartData) { point in
                LineMark(x: .value("Month", point.label), y: .value("Amount", point.value))
                    .foregroundStyle(by: .value("Series", point.series))
                    .symbol(by: .value("Series", point.series))
                    .interpolationMethod(.catmullRom)
            }
            .chartForegroundStyleScale(["Salary + Bonus": Theme.graphite, "Total Expense": Theme.travel])
            .chartLegend(position: .bottom, alignment: .center)
            .frame(height: 200)
        }
    }
}

private struct SalaryTableView: View {
    let records: [SalaryUsageRecord]
    var onSelect: (SalaryUsageRecord) -> Void = { _ in }
    var hidden: Bool = false

    private struct ColDef { let title: String; let width: CGFloat }
    private let cols: [ColDef] = [
        ColDef(title: "Year / Month", width: 88),
        ColDef(title: "Region / Currency", width: 100),
        ColDef(title: "Salary\n(Excl. Retirement)", width: 95),
        ColDef(title: "Bonus", width: 80),
        ColDef(title: "Retirement\nSaving (Emp.)", width: 96),
        ColDef(title: "Retirement\nSaving (Emplr.)", width: 96),
        ColDef(title: "Tax", width: 78),
        ColDef(title: "House Rent*", width: 88),
        ColDef(title: "Living Expense", width: 95),
        ColDef(title: "Other Expense", width: 90),
        ColDef(title: "Total Expense", width: 95),
    ]

    var body: some View {
        ScrollView(.horizontal, showsIndicators: true) {
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 6) {
                    ForEach(cols, id: \.title) { col in
                        Text(col.title)
                            .font(.caption2.weight(.semibold))
                            .foregroundStyle(Theme.inkFaint)
                            .multilineTextAlignment(.center)
                            .lineLimit(2)
                            .frame(width: col.width, alignment: .center)
                    }
                }
                .padding(.vertical, 8).padding(.horizontal, 2)
                Divider().overlay(Theme.hairline)

                ForEach(Array(records.enumerated()), id: \.element.id) { idx, record in
                    HStack(spacing: 6) {
                        dataCell("\(record.year)/\(String(format: "%02d", record.month))", width: cols[0].width)
                        dataCell("\(record.region) / \(record.currency)", width: cols[1].width)
                        dataCell(fmt(record.salary, record.currency), width: cols[2].width)
                        dataCell(fmt(record.bonus, record.currency), width: cols[3].width)
                        dataCell(fmt(record.retirementSavingEmployee, record.currency), width: cols[4].width)
                        dataCell(fmt(record.retirementSavingEmployer, record.currency), width: cols[5].width)
                        dataCell(fmt(record.tax, record.currency), width: cols[6].width)
                        dataCell(fmt(record.houseRent, record.currency), width: cols[7].width)
                        dataCell(fmt(record.livingExpense, record.currency), width: cols[8].width)
                        dataCell(fmt(record.otherExpense, record.currency), width: cols[9].width)
                        dataCell(fmt(record.totalExpense, record.currency), width: cols[10].width, bold: true)
                    }
                    .padding(.vertical, 9).padding(.horizontal, 2)
                    .background(idx % 2 == 0 ? Color.clear : Theme.chipFill)
                    .contentShape(Rectangle())
                    .onTapGesture { onSelect(record) }
                    Divider().overlay(Theme.hairline)
                }

                Text("* House Rent: some months are paid in the following month")
                    .font(.caption2).foregroundStyle(Theme.inkFaint)
                    .padding(.top, 8).padding(.horizontal, 2)
            }
        }
    }

    private func dataCell(_ text: String, width: CGFloat, bold: Bool = false) -> some View {
        Text(text)
            .font(bold ? .caption.weight(.semibold) : .caption)
            .foregroundStyle(bold ? Theme.ink : Theme.inkSoft)
            .lineLimit(1)
            .minimumScaleFactor(0.8)
            .frame(width: width, alignment: .center)
    }

    private func fmt(_ value: Double, _ currency: String) -> String {
        guard value != 0 else { return "—" }
        if hidden { return "••••" }
        let f = NumberFormatter()
        f.numberStyle = .currency
        f.currencyCode = currency
        f.minimumFractionDigits = 0
        f.maximumFractionDigits = 2
        return f.string(from: NSNumber(value: value)) ?? "\(currency) \(String(format: "%.2f", value))"
    }
}

private struct SalaryFormView: View {
    @ObservedObject var store: FinancialStore
    var editing: SalaryUsageRecord?
    @Environment(\.dismiss) private var dismiss

    @State private var year = Calendar.current.component(.year, from: Date())
    @State private var month = Calendar.current.component(.month, from: Date())
    @State private var region = ""
    @State private var currency = "USD"
    @State private var salary = ""
    @State private var bonus = ""
    @State private var retirementSavingEmployee = ""
    @State private var retirementSavingEmployer = ""
    @State private var tax = ""
    @State private var houseRent = ""
    @State private var livingExpense = ""
    @State private var otherExpense = ""
    @State private var isSaving = false
    @State private var errorMessage: String?
    @State private var showDeleteConfirm = false

    // Matches the web app's computed total exactly: living + house rent + other — tax is
    // tracked separately and deliberately excluded (it isn't a discretionary "expense").
    private var totalExpense: Double {
        (Double(houseRent) ?? 0) + (Double(livingExpense) ?? 0) + (Double(otherExpense) ?? 0)
    }

    private var isValid: Bool {
        !region.trimmingCharacters(in: .whitespaces).isEmpty && Double(salary) != nil
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Period") {
                    Stepper("Year: \(String(year))", value: $year, in: 2000...2100)
                    Picker("Month", selection: $month) {
                        ForEach(1...12, id: \.self) { Text(String(format: "%02d", $0)).tag($0) }
                    }
                    ComboField(placeholder: "Region (e.g. Hong Kong SAR, Singapore)", text: $region,
                               suggestions: Array(Set(store.salaryRecords.map(\.region))).sorted())
                    Picker("Currency", selection: $currency) {
                        ForEach(commonCurrencies, id: \.self) { Text($0).tag($0) }
                    }
                }
                Section("Income") {
                    labeledField("Salary (excl. retirement)", $salary)
                    labeledField("Bonus", $bonus)
                    labeledField("Retirement saving — employee", $retirementSavingEmployee)
                    labeledField("Retirement saving — employer", $retirementSavingEmployer)
                }
                Section("Expenses") {
                    labeledField("Tax", $tax)
                    labeledField("House rent", $houseRent)
                    labeledField("Living expense", $livingExpense)
                    labeledField("Other expense", $otherExpense)
                    HStack {
                        Text("Total expense").foregroundStyle(Theme.inkSoft)
                        Spacer()
                        Text(formatMoney(totalExpense, currency: currency)).foregroundStyle(Theme.ink)
                    }
                }
                if editing != nil {
                    Section {
                        Button("Delete record", role: .destructive) { showDeleteConfirm = true }
                    }
                }
                if let errorMessage {
                    Section { Text(errorMessage).font(.caption).foregroundStyle(.red) }
                }
            }
            .navigationTitle(editing == nil ? "Add Salary Record" : "Edit Salary Record")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    if isSaving { ProgressView() } else {
                        Button("Save") { Task { await save() } }.disabled(!isValid)
                    }
                }
            }
            .confirmationDialog("Delete this salary record?", isPresented: $showDeleteConfirm, titleVisibility: .visible) {
                Button("Delete", role: .destructive) { Task { await delete() } }
                Button("Cancel", role: .cancel) {}
            }
        }
        .onAppear(perform: populate)
    }

    private func labeledField(_ label: String, _ text: Binding<String>) -> some View {
        HStack {
            Text(label).foregroundStyle(Theme.inkSoft)
            Spacer()
            TextField("0", text: text)
                .keyboardType(.decimalPad)
                .multilineTextAlignment(.trailing)
                .frame(maxWidth: 120)
        }
    }

    private func populate() {
        guard let editing else { return }
        year = editing.year
        month = editing.month
        region = editing.region
        currency = editing.currency
        salary = formatNum(editing.salary)
        bonus = formatNum(editing.bonus)
        retirementSavingEmployee = formatNum(editing.retirementSavingEmployee)
        retirementSavingEmployer = formatNum(editing.retirementSavingEmployer)
        tax = formatNum(editing.tax)
        houseRent = formatNum(editing.houseRent)
        livingExpense = formatNum(editing.livingExpense)
        otherExpense = formatNum(editing.otherExpense)
    }

    private func save() async {
        guard let sal = Double(salary) else { return }
        isSaving = true; errorMessage = nil
        let fields: [String: Any] = [
            "year": year, "month": month, "region": region, "currency": currency,
            "salary": sal, "bonus": Double(bonus) ?? 0,
            "retirementSavingEmployee": Double(retirementSavingEmployee) ?? 0,
            "retirementSavingEmployer": Double(retirementSavingEmployer) ?? 0,
            "tax": Double(tax) ?? 0, "houseRent": Double(houseRent) ?? 0,
            "livingExpense": Double(livingExpense) ?? 0, "otherExpense": Double(otherExpense) ?? 0,
            "totalExpense": totalExpense,
        ]
        do {
            if let editing {
                try await store.editSalary(id: editing.id, fields)
            } else {
                try await store.addSalary(fields)
            }
            dismiss()
        } catch {
            errorMessage = error.localizedDescription
        }
        isSaving = false
    }

    private func delete() async {
        guard let editing else { return }
        isSaving = true; errorMessage = nil
        do {
            try await store.removeSalary(id: editing.id)
            dismiss()
        } catch {
            errorMessage = error.localizedDescription
        }
        isSaving = false
    }
}
