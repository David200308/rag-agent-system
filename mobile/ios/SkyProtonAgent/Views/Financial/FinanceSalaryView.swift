import SwiftUI
import Charts

struct FinanceSalaryView: View {
    @ObservedObject var store: FinancialStore

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
                                Text(formatMoney(totalSalaryBonus, currency: currency)).font(.system(size: 18, weight: .bold)).foregroundStyle(Theme.ink)
                            }
                        }
                        ThemeCard(padding: 16) {
                            VStack(alignment: .leading, spacing: 4) {
                                Text("Total Expense").font(.caption).foregroundStyle(Theme.inkSoft)
                                Text(formatMoney(totalExpense, currency: currency)).font(.system(size: 18, weight: .bold)).foregroundStyle(Theme.ink)
                            }
                        }
                    }

                    ThemeCard {
                        SalaryChartView(records: sorted)
                    }
                    ThemeCard(padding: 12) {
                        SalaryTableView(records: sorted)
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
        let f = NumberFormatter()
        f.numberStyle = .currency
        f.currencyCode = currency
        f.minimumFractionDigits = 0
        f.maximumFractionDigits = 2
        return f.string(from: NSNumber(value: value)) ?? "\(currency) \(String(format: "%.2f", value))"
    }
}
