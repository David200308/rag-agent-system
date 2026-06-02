import SwiftUI
import Charts

// MARK: – Filter state

private enum PnLFilter: String, CaseIterable {
    case all = "All", gains = "Gains", losses = "Losses"
}


struct FinancialView: View {
    @State private var selectedTab = 0
    @State private var deposits: [CashDeposit] = []
    @State private var stocks: [StockInvestment] = []
    @State private var crypto: [CryptoInvestment] = []
    @State private var cards: [FinancialCard] = []
    @State private var salaryRecords: [SalaryUsageRecord] = []
    @State private var isLoading = false
    @State private var isRefreshing = false
    @State private var loadError: String?

    // Search + filter
    @State private var searchText = ""
    @State private var showFilter = false
    @State private var pnlFilter = PnLFilter.all
    @State private var marketFilter = "All"    // stocks: All / US / HK / CN / SG
    @State private var depositCurrencyFilter = "All"
    @State private var cardNetworkFilter = "All"

    // Salary date-range filter
    @State private var salaryFromEnabled = false
    @State private var salaryFromYear  = Calendar.current.component(.year,  from: Date())
    @State private var salaryFromMonth = 1
    @State private var salaryToEnabled = false
    @State private var salaryToYear    = Calendar.current.component(.year,  from: Date())
    @State private var salaryToMonth   = Calendar.current.component(.month, from: Date())
    @State private var showSalaryFromPicker = false
    @State private var showSalaryToPicker   = false

    private let service = AgentService.shared
    private let tabs = ["Deposits", "Stocks", "Crypto", "Cards", "Salary & Expense"]

    private var hasActiveFilter: Bool {
        switch selectedTab {
        case 0: return depositCurrencyFilter != "All"
        case 1: return marketFilter != "All" || pnlFilter != .all
        case 2: return pnlFilter != .all
        case 3: return cardNetworkFilter != "All"
        default: return false  // salary tab has no filter
        }
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                Picker("Tab", selection: $selectedTab) {
                    ForEach(tabs.indices, id: \.self) { i in Text(tabs[i]).tag(i) }
                }
                .pickerStyle(.segmented)
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
                .background(Color(.systemBackground))

                Divider()

                if isLoading {
                    ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    ScrollView {
                        if let err = loadError {
                            HStack(spacing: 8) {
                                Image(systemName: "exclamationmark.triangle.fill").foregroundStyle(.orange)
                                Text(err).font(.caption).foregroundStyle(.secondary)
                            }
                            .padding(12)
                            .background(Color(.secondarySystemBackground))
                            .clipShape(RoundedRectangle(cornerRadius: 10))
                            .padding(16)
                        }
                        LazyVStack(spacing: 12) {
                            switch selectedTab {
                            case 0: depositsSection
                            case 1: stocksSection
                            case 2: cryptoSection
                            case 3: cardsSection
                            default: salarySection
                            }
                        }
                        .padding(16)
                    }
                    .background(Color(.secondarySystemBackground))
                    .refreshable { await refresh() }
                }
            }
            .navigationTitle("Financial")
            .searchable(text: $searchText, placement: .navigationBarDrawer(displayMode: .always), prompt: "Search…")
            .toolbar {
                ToolbarItemGroup(placement: .navigationBarTrailing) {
                    Button {
                        showFilter = true
                    } label: {
                        Image(systemName: hasActiveFilter ? "line.3.horizontal.decrease.circle.fill" : "line.3.horizontal.decrease.circle")
                            .foregroundStyle(hasActiveFilter ? Color.accentColor : Color(.label))
                    }
                    Button {
                        Task { await refresh() }
                    } label: {
                        Image(systemName: isRefreshing ? "arrow.triangle.2.circlepath" : "arrow.clockwise")
                    }
                    .disabled(isRefreshing)
                }
            }
            .task { await loadAll() }
            .sheet(isPresented: $showFilter) {
                FilterSheet(
                    selectedTab: selectedTab,
                    pnlFilter: $pnlFilter,
                    marketFilter: $marketFilter,
                    depositCurrencyFilter: $depositCurrencyFilter,
                    cardNetworkFilter: $cardNetworkFilter,
                    availableMarkets: availableMarkets,
                    availableCurrencies: availableDepositCurrencies,
                    availableNetworks: availableCardNetworks
                )
                .presentationDetents([.medium])
            }
        }
    }

    // MARK: – Filtered data

    private var filteredDeposits: [CashDeposit] {
        deposits.filter { d in
            let q = searchText.lowercased()
            let matchesSearch = searchText.isEmpty ||
                d.platform.lowercased().contains(q) ||
                d.depositType.lowercased().contains(q) ||
                d.currency.lowercased().contains(q)
            let matchesCurrency = depositCurrencyFilter == "All" || d.currency == depositCurrencyFilter
            return matchesSearch && matchesCurrency
        }
    }

    private var filteredStocks: [StockInvestment] {
        stocks.filter { s in
            let q = searchText.lowercased()
            let matchesSearch = searchText.isEmpty ||
                s.symbol.lowercased().contains(q) ||
                s.name.lowercased().contains(q) ||
                s.broker.lowercased().contains(q)
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
        crypto.filter { c in
            let q = searchText.lowercased()
            let matchesSearch = searchText.isEmpty ||
                c.symbol.lowercased().contains(q) ||
                c.name.lowercased().contains(q)
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

    private var filteredCards: [FinancialCard] {
        cards.filter { c in
            let q = searchText.lowercased()
            let matchesSearch = searchText.isEmpty ||
                c.cardName.lowercased().contains(q) ||
                c.bank.lowercased().contains(q) ||
                c.network.lowercased().contains(q)
            let matchesNetwork = cardNetworkFilter == "All" || c.network == cardNetworkFilter
            return matchesSearch && matchesNetwork
        }
    }

    private var filteredSalaryRecords: [SalaryUsageRecord] {
        salaryRecords
            .filter { r in
                if salaryFromEnabled {
                    guard r.year > salaryFromYear ||
                          (r.year == salaryFromYear && r.month >= salaryFromMonth)
                    else { return false }
                }
                if salaryToEnabled {
                    guard r.year < salaryToYear ||
                          (r.year == salaryToYear && r.month <= salaryToMonth)
                    else { return false }
                }
                let q = searchText.lowercased()
                return searchText.isEmpty ||
                    r.region.lowercased().contains(q) ||
                    r.currency.lowercased().contains(q) ||
                    "\(r.year)".contains(q) ||
                    String(format: "%02d", r.month).contains(q)
            }
            .sorted { lhs, rhs in
                lhs.year != rhs.year ? lhs.year > rhs.year : lhs.month > rhs.month
            }
    }

    // MARK: – Filter option helpers

    private var availableMarkets: [String] {
        ["All"] + Array(Set(stocks.map { $0.stockTypeBadge })).sorted()
    }

    private var availableDepositCurrencies: [String] {
        ["All"] + Array(Set(deposits.map { $0.currency })).sorted()
    }

    private var availableCardNetworks: [String] {
        ["All"] + Array(Set(cards.map { $0.network })).sorted()
    }

    // MARK: – Sections

    @ViewBuilder
    private var depositsSection: some View {
        let items = filteredDeposits
        if items.isEmpty {
            emptyCard(label: deposits.isEmpty ? "No deposits" : "No results")
        } else {
            let total = items.reduce(0) { $0 + $1.convertedAmount }
            summaryCard(label: "Total", value: formatMoney(total, currency: items.first?.convertedCurrency ?? "USD"))
            ForEach(items) { d in
                FinancialCard_View {
                    HStack {
                        VStack(alignment: .leading, spacing: 3) {
                            Text(d.platform).font(.system(size: 15, weight: .medium))
                            Text("\(d.depositType) · \(d.countryRegion(d))")
                                .font(.caption).foregroundStyle(.secondary)
                        }
                        Spacer()
                        VStack(alignment: .trailing, spacing: 3) {
                            Text(formatMoney(d.amount, currency: d.currency))
                                .font(.system(size: 15, weight: .semibold))
                            Text(formatMoney(d.convertedAmount, currency: d.convertedCurrency))
                                .font(.caption).foregroundStyle(.secondary)
                        }
                    }
                }
            }
        }
    }

    @ViewBuilder
    private var stocksSection: some View {
        let items = filteredStocks
        if items.isEmpty {
            emptyCard(label: stocks.isEmpty ? "No stocks" : "No results")
        } else {
            let totalInvest = items.reduce(0) { $0 + $1.convertedInvestAmount }
            let totalValue  = items.compactMap { $0.convertedCurrentValue }.reduce(0, +)
            let pnlPct = totalInvest > 0 ? ((totalValue - totalInvest) / totalInvest) * 100 : 0
            summaryCard(label: "Portfolio", value: formatMoney(totalValue, currency: items.first?.convertedCurrency ?? "USD"), pnl: pnlPct)
            ForEach(items) { s in
                FinancialCard_View {
                    HStack(alignment: .top) {
                        VStack(alignment: .leading, spacing: 3) {
                            HStack(spacing: 6) {
                                Text(s.symbol).font(.system(size: 15, weight: .bold))
                                Text(s.stockTypeBadge).font(.caption2).foregroundStyle(.secondary)
                            }
                            Text(s.name).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                            Text("\(formatNum(s.stockAmount)) shares · \(s.broker)")
                                .font(.caption2).foregroundStyle(Color(.tertiaryLabel))
                        }
                        Spacer()
                        VStack(alignment: .trailing, spacing: 3) {
                            if let cv = s.convertedCurrentValue {
                                Text(formatMoney(cv, currency: s.convertedCurrency))
                                    .font(.system(size: 15, weight: .semibold))
                            } else {
                                Text("—").font(.subheadline).foregroundStyle(.secondary)
                            }
                            if let pnl = s.pnlPercent {
                                PnLBadge(pnl: pnl)
                            }
                        }
                    }
                }
            }
        }
    }

    @ViewBuilder
    private var cryptoSection: some View {
        let items = filteredCrypto
        if items.isEmpty {
            emptyCard(label: crypto.isEmpty ? "No crypto" : "No results")
        } else {
            let totalInvest = items.reduce(0) { $0 + $1.convertedInvestAmount }
            let totalValue  = items.compactMap { $0.convertedCurrentValue }.reduce(0, +)
            let pnlPct = totalInvest > 0 ? ((totalValue - totalInvest) / totalInvest) * 100 : 0
            summaryCard(label: "Portfolio", value: formatMoney(totalValue, currency: items.first?.convertedCurrency ?? "USD"), pnl: pnlPct)
            ForEach(items) { c in
                FinancialCard_View {
                    HStack(alignment: .top) {
                        VStack(alignment: .leading, spacing: 3) {
                            HStack(spacing: 6) {
                                Text(c.symbol).font(.system(size: 15, weight: .bold))
                                Text(c.name).font(.caption).foregroundStyle(.secondary)
                            }
                            Text(formatNum(c.amount) + " coins").font(.caption2).foregroundStyle(Color(.tertiaryLabel))
                        }
                        Spacer()
                        VStack(alignment: .trailing, spacing: 3) {
                            if let cv = c.convertedCurrentValue {
                                Text(formatMoney(cv, currency: c.convertedCurrency))
                                    .font(.system(size: 15, weight: .semibold))
                            } else {
                                Text("—").font(.subheadline).foregroundStyle(.secondary)
                            }
                            if let pnl = c.pnlPercent { PnLBadge(pnl: pnl) }
                        }
                    }
                }
            }
        }
    }

    @ViewBuilder
    private var cardsSection: some View {
        let items = filteredCards
        if items.isEmpty {
            emptyCard(label: cards.isEmpty ? "No cards" : "No results")
        } else {
            ForEach(items) { c in
                FinancialCard_View {
                    VStack(alignment: .leading, spacing: 6) {
                        HStack {
                            Text(c.cardName).font(.system(size: 15, weight: .medium))
                            Spacer()
                            Text(c.network).font(.caption.weight(.semibold))
                                .foregroundStyle(.secondary)
                        }
                        HStack(spacing: 6) {
                            Text(c.bank).font(.caption).foregroundStyle(.secondary)
                            Text("·")
                            Text(c.types.joined(separator: "/")).font(.caption).foregroundStyle(.secondary)
                        }
                        HStack {
                            if let limit = c.creditLimit, let cur = c.creditLimitCurrency {
                                Text("Limit: \(formatMoney(limit, currency: cur))")
                                    .font(.caption2).foregroundStyle(Color(.tertiaryLabel))
                            }
                            Spacer()
                            Text("Exp: \(c.expireDate)")
                                .font(.caption2).foregroundStyle(Color(.tertiaryLabel))
                        }
                    }
                }
            }
        }
    }

    @ViewBuilder
    private var salarySection: some View {
        HStack(spacing: 8) {
            Text("From").font(.caption).foregroundStyle(.secondary)
            Button {
                showSalaryFromPicker = true
            } label: {
                Text(salaryFromEnabled
                     ? "\(salaryFromYear)/\(String(format: "%02d", salaryFromMonth))"
                     : "Start")
                    .font(.caption.weight(.medium))
                    .padding(.horizontal, 10).padding(.vertical, 5)
                    .background(Color(.secondarySystemBackground))
                    .clipShape(Capsule())
            }
            Text("—").font(.caption).foregroundStyle(.secondary)
            Text("To").font(.caption).foregroundStyle(.secondary)
            Button {
                showSalaryToPicker = true
            } label: {
                Text(salaryToEnabled
                     ? "\(salaryToYear)/\(String(format: "%02d", salaryToMonth))"
                     : "Now")
                    .font(.caption.weight(.medium))
                    .padding(.horizontal, 10).padding(.vertical, 5)
                    .background(Color(.secondarySystemBackground))
                    .clipShape(Capsule())
            }
            if salaryFromEnabled || salaryToEnabled {
                Button("Clear") {
                    salaryFromEnabled = false
                    salaryToEnabled   = false
                }
                .font(.caption).foregroundStyle(.red)
            }
            Spacer()
        }
        .sheet(isPresented: $showSalaryFromPicker) {
            SalaryYearMonthPicker(
                year: $salaryFromYear, month: $salaryFromMonth,
                onDone: { salaryFromEnabled = true }
            )
        }
        .sheet(isPresented: $showSalaryToPicker) {
            SalaryYearMonthPicker(
                year: $salaryToYear, month: $salaryToMonth,
                onDone: { salaryToEnabled = true }
            )
        }

        let items = filteredSalaryRecords
        if items.isEmpty {
            emptyCard(label: salaryRecords.isEmpty ? "No salary records" : "No results")
        } else {
            let totalSalaryBonus = items.reduce(0.0) { $0 + $1.salary + $1.bonus }
            let totalExpense     = items.reduce(0.0) { $0 + $1.totalExpense }
            let currency         = items.first?.currency ?? "USD"

            // Sliding banner
            TabView {
                salarySummaryBanner(
                    label: "Total Salary & Bonus",
                    value: formatMoney(totalSalaryBonus, currency: currency)
                )
                .tag(0)
                salarySummaryBanner(
                    label: "Total Expense",
                    value: formatMoney(totalExpense, currency: currency)
                )
                .tag(1)
            }
            .tabViewStyle(.page(indexDisplayMode: .always))
            .frame(height: 88)

            FinancialCard_View {
                SalaryChartView(records: items)
            }
            FinancialCard_View {
                SalaryTableView(records: items)
            }
        }
    }

    private func salarySummaryBanner(label: String, value: String) -> some View {
        FinancialCard_View {
            VStack(alignment: .leading, spacing: 4) {
                Text(label).font(.caption).foregroundStyle(.secondary)
                Text(value).font(.system(size: 22, weight: .bold))
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    // MARK: – Helpers

    private func summaryCard(label: String, value: String, pnl: Double? = nil) -> some View {
        FinancialCard_View {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(label).font(.caption).foregroundStyle(.secondary)
                    Text(value).font(.system(size: 22, weight: .bold))
                }
                Spacer()
                if let pnl { PnLBadge(pnl: pnl, large: true) }
            }
        }
    }

    private func emptyCard(label: String) -> some View {
        FinancialCard_View {
            Text(label).foregroundStyle(.secondary).font(.subheadline)
                .frame(maxWidth: .infinity)
        }
    }

    private func loadAll() async {
        isLoading = true
        loadError = nil
        do {
            async let d = service.listCashDeposits()
            async let s = service.listStocks()
            async let c = service.listCrypto()
            async let k = service.listCards()
            async let r = service.listSalaryRecords()
            deposits      = try await d
            stocks        = try await s
            crypto        = try await c
            cards         = try await k
            salaryRecords = try await r
        } catch {
            loadError = error.localizedDescription
        }
        isLoading = false
    }

    private func refresh() async {
        isRefreshing = true
        try? await service.refreshPrices()
        await loadAll()
        isRefreshing = false
    }

    private func formatMoney(_ v: Double, currency: String) -> String {
        let f = NumberFormatter()
        f.numberStyle = .currency; f.currencyCode = currency
        f.maximumFractionDigits = 2
        return f.string(from: NSNumber(value: v)) ?? "\(currency) \(String(format: "%.2f", v))"
    }

    private func formatNum(_ v: Double) -> String {
        v.truncatingRemainder(dividingBy: 1) == 0
            ? String(format: "%.0f", v)
            : String(format: "%.4f", v)
    }
}

private extension CashDeposit {
    func countryRegion(_ d: CashDeposit) -> String { d.platformType }
}

private extension StockInvestment {
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

// MARK: – Salary year/month picker

private struct SalaryYearMonthPicker: View {
    @Binding var year: Int
    @Binding var month: Int
    let onDone: () -> Void
    @Environment(\.dismiss) private var dismiss

    private let years  = Array(2015...2035)
    private let months = ["Jan","Feb","Mar","Apr","May","Jun",
                          "Jul","Aug","Sep","Oct","Nov","Dec"]

    var body: some View {
        NavigationStack {
            HStack(spacing: 0) {
                Picker("Year", selection: $year) {
                    ForEach(years, id: \.self) { Text(String($0)).tag($0) }
                }
                .pickerStyle(.wheel)
                .frame(maxWidth: .infinity)

                Picker("Month", selection: $month) {
                    ForEach(1...12, id: \.self) { m in
                        Text(months[m - 1]).tag(m)
                    }
                }
                .pickerStyle(.wheel)
                .frame(maxWidth: .infinity)
            }
            .navigationTitle("Select Year / Month")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { onDone(); dismiss() }
                }
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
        .presentationDetents([.height(300)])
    }
}

// MARK: – Salary chart

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
            .sorted { lhs, rhs in lhs.year != rhs.year ? lhs.year < rhs.year : lhs.month < rhs.month }
            .flatMap { r in
                let ym = "\(r.year)/\(String(format: "%02d", r.month))"
                return [
                    Point(label: ym, value: r.salary + r.bonus, series: "Salary + Bonus"),
                    Point(label: ym, value: r.totalExpense,      series: "Total Expense"),
                ]
            }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Salary & Expense Trend")
                .font(.caption).foregroundStyle(.secondary)
            Chart(chartData) { point in
                LineMark(
                    x: .value("Month", point.label),
                    y: .value("Amount", point.value)
                )
                .foregroundStyle(by: .value("Series", point.series))
                .symbol(by: .value("Series", point.series))
                .interpolationMethod(.catmullRom)
            }
            .chartForegroundStyleScale([
                "Salary + Bonus": Color.blue,
                "Total Expense":  Color.orange,
            ])
            .chartLegend(position: .bottom, alignment: .center)
            .frame(height: 200)
        }
    }
}

// MARK: – Salary table

private struct SalaryTableView: View {
    let records: [SalaryUsageRecord]

    private struct ColDef {
        let title: String
        let width: CGFloat
    }

    private let cols: [ColDef] = [
        ColDef(title: "Year / Month",       width: 88),
        ColDef(title: "Region / Currency",  width: 100),
        ColDef(title: "Salary\n(Excl. Retirement)", width: 95),
        ColDef(title: "Bonus",              width: 80),
        ColDef(title: "Retirement\nSaving (Emp.)", width: 96),
        ColDef(title: "Retirement\nSaving (Emplr.)", width: 96),
        ColDef(title: "Tax",                width: 78),
        ColDef(title: "House Rent*",        width: 88),
        ColDef(title: "Living Expense",     width: 95),
        ColDef(title: "Other Expense",      width: 90),
        ColDef(title: "Total Expense",      width: 95),
    ]

    var body: some View {
        ScrollView(.horizontal, showsIndicators: true) {
            VStack(alignment: .leading, spacing: 0) {
                // Header row
                HStack(spacing: 6) {
                    ForEach(cols, id: \.title) { col in
                        Text(col.title)
                            .font(.caption2.weight(.semibold))
                            .foregroundStyle(.secondary)
                            .multilineTextAlignment(.center)
                            .lineLimit(2)
                            .frame(width: col.width, alignment: .center)
                    }
                }
                .padding(.vertical, 8)
                .padding(.horizontal, 2)

                Divider()

                ForEach(Array(records.enumerated()), id: \.element.id) { idx, record in
                    HStack(spacing: 6) {
                        dataCell("\(record.year)/\(String(format: "%02d", record.month))",
                                 width: cols[0].width)
                        dataCell("\(record.region) / \(record.currency)",
                                 width: cols[1].width)
                        dataCell(fmt(record.salary, record.currency),
                                 width: cols[2].width)
                        dataCell(fmt(record.bonus, record.currency),
                                 width: cols[3].width)
                        dataCell(fmt(record.retirementSavingEmployee, record.currency),
                                 width: cols[4].width)
                        dataCell(fmt(record.retirementSavingEmployer, record.currency),
                                 width: cols[5].width)
                        dataCell(fmt(record.tax, record.currency),
                                 width: cols[6].width)
                        dataCell(fmt(record.houseRent, record.currency),
                                 width: cols[7].width)
                        dataCell(fmt(record.livingExpense, record.currency),
                                 width: cols[8].width)
                        dataCell(fmt(record.otherExpense, record.currency),
                                 width: cols[9].width)
                        dataCell(fmt(record.totalExpense, record.currency),
                                 width: cols[10].width, bold: true)
                    }
                    .padding(.vertical, 9)
                    .padding(.horizontal, 2)
                    .background(idx % 2 == 0 ? Color.clear : Color(.tertiarySystemBackground))

                    Divider()
                }

                Text("* House Rent: some months are paid in the following month")
                    .font(.caption2)
                    .foregroundStyle(Color(.tertiaryLabel))
                    .padding(.top, 8)
                    .padding(.horizontal, 2)
            }
        }
    }

    private func dataCell(_ text: String, width: CGFloat, bold: Bool = false) -> some View {
        Text(text)
            .font(bold ? .caption.weight(.semibold) : .caption)
            .foregroundStyle(bold ? Color(.label) : Color(.secondaryLabel))
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
        return f.string(from: NSNumber(value: value))
            ?? "\(currency) \(String(format: "%.2f", value))"
    }
}

// MARK: – Filter sheet

private struct FilterSheet: View {
    let selectedTab: Int
    @Binding var pnlFilter: PnLFilter
    @Binding var marketFilter: String
    @Binding var depositCurrencyFilter: String
    @Binding var cardNetworkFilter: String
    let availableMarkets: [String]
    let availableCurrencies: [String]
    let availableNetworks: [String]
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                switch selectedTab {
                case 0:
                    Section("Currency") {
                        Picker("Currency", selection: $depositCurrencyFilter) {
                            ForEach(availableCurrencies, id: \.self) { Text($0).tag($0) }
                        }
                        .pickerStyle(.menu)
                    }
                case 1:
                    Section("Market") {
                        Picker("Market", selection: $marketFilter) {
                            ForEach(availableMarkets, id: \.self) { Text($0).tag($0) }
                        }
                        .pickerStyle(.segmented)
                    }
                    Section("Performance") {
                        Picker("P&L", selection: $pnlFilter) {
                            ForEach(PnLFilter.allCases, id: \.self) { Text($0.rawValue).tag($0) }
                        }
                        .pickerStyle(.segmented)
                    }
                case 2:
                    Section("Performance") {
                        Picker("P&L", selection: $pnlFilter) {
                            ForEach(PnLFilter.allCases, id: \.self) { Text($0.rawValue).tag($0) }
                        }
                        .pickerStyle(.segmented)
                    }
                case 3:
                    Section("Network") {
                        Picker("Network", selection: $cardNetworkFilter) {
                            ForEach(availableNetworks, id: \.self) { Text($0).tag($0) }
                        }
                        .pickerStyle(.menu)
                    }
                default:
                    Section {
                        Text("No filters available for this tab.")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                }

                Section {
                    Button("Reset Filters", role: .destructive) {
                        pnlFilter = .all
                        marketFilter = "All"
                        depositCurrencyFilter = "All"
                        cardNetworkFilter = "All"
                    }
                }
            }
            .navigationTitle("Filter")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }
}

// MARK: – Shared components

struct FinancialCard_View<Content: View>: View {
    @ViewBuilder let content: Content
    var body: some View {
        content
            .padding(14)
            .background(Color(.systemBackground))
            .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}

struct PnLBadge: View {
    let pnl: Double
    var large: Bool = false

    private var isPositive: Bool { pnl >= 0 }

    var body: some View {
        Text((isPositive ? "+" : "") + String(format: "%.2f%%", pnl))
            .font(large ? .system(size: 15, weight: .semibold) : .caption.weight(.semibold))
            .foregroundStyle(isPositive ? .green : .red)
            .padding(.horizontal, 8).padding(.vertical, 3)
            .background((isPositive ? Color.green : Color.red).opacity(0.1))
            .clipShape(Capsule())
    }
}
