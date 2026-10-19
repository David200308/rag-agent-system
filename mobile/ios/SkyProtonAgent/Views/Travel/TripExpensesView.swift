import SwiftUI

// Trip expenses: the summary + day-by-day list on the trip detail screen, the add/edit
// sheet and the tracked-currencies sheet. Data is the web app's `__v: 2` shape and every
// change is written straight back with `PUT /api/v1/travel/{id}` (no separate save step).

/// Owns a trip's expense data: loads it on demand, and commits edits to the server before
/// showing them, so a failed save leaves the screen showing what's actually stored.
@MainActor
final class TripExpensesStore: ObservableObject {
    enum LoadState { case loading, ready, legacy, failed }

    @Published private(set) var state: LoadState = .loading
    @Published private(set) var data = TripExpenseData()
    @Published private(set) var cards: [FinancialCard] = []

    let trip: TravelRecord

    init(trip: TravelRecord) {
        self.trip = trip
    }

    func load() async {
        state = .loading
        async let cardsTask = try? AgentService.shared.listCards()
        do {
            let raw = try await AgentService.shared.getTravelExpenses(id: trip.id)
            if raw.isEmpty {
                var fresh = TripExpenseData()
                fresh.normalizeDays(for: trip)
                data = fresh
                state = .ready
            } else if var parsed = TripExpenseData.from(raw) {
                parsed.normalizeDays(for: trip)
                data = parsed
                state = .ready
            } else {
                // Pre-v2 format: editing here would overwrite it, so leave it to the web app.
                state = .legacy
            }
        } catch {
            state = .failed
        }
        cards = await cardsTask ?? []
    }

    func commit(_ next: TripExpenseData) async throws {
        try await AgentService.shared.updateTravelExpenses(id: trip.id, next)
        data = next
    }

    func cardName(_ id: String) -> String? {
        cards.first { $0.id == id }?.cardName
    }

    /// Where a new expense lands by default: today while the trip is on, Pre-trip before
    /// it starts, the last day once it's over.
    var suggestedDate: String? {
        let days = trip.dayKeys
        let today = tripDayFormatter(utc: false).string(from: Date())
        if days.contains(today) { return today }
        if let first = days.first, today < first { return nil }
        return days.last
    }
}

/// Which entry the editor sheet is open for — `entry == nil` means adding a new one.
struct ExpenseEditorTarget: Identifiable {
    let id = UUID()
    var entry: RichExpenseEntry?
    var date: String?
}

// MARK: - Section on the trip detail screen

struct TripExpensesSection: View {
    @ObservedObject var store: TripExpensesStore
    var scrollTo: (String) -> Void
    @Binding var editorTarget: ExpenseEditorTarget?

    @State private var showCurrencies = false
    @State private var pendingDelete: RichExpenseEntry?
    @State private var deleteError: String?

    private var data: TripExpenseData { store.data }
    private var currency: String { data.defaultCurrency }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                SectionLabel(text: "Expenses")
                Spacer()
                if store.state == .ready && !data.currencies.isEmpty {
                    currencyButton
                }
            }

            switch store.state {
            case .loading:
                HStack { Spacer(); ProgressView(); Spacer() }.padding(.vertical, 20)
            case .failed:
                messageCard("Couldn't load this trip's expenses.", action: ("Try again", { Task { await store.load() } }))
            case .legacy:
                messageCard("These expenses are in an older format. Edit them in the web app to keep them intact.", action: nil)
            case .ready:
                if data.isEmpty {
                    emptyState
                } else {
                    if let deleteError { FormErrorBanner(message: deleteError) }
                    summaryCard
                    groups
                }
            }
        }
        .sheet(isPresented: $showCurrencies) { TripCurrenciesSheet(store: store) }
        .alert(
            "Delete \(pendingDelete?.name.isEmpty == false ? "“\(pendingDelete!.name)”" : "this expense")?",
            isPresented: Binding(get: { pendingDelete != nil }, set: { if !$0 { pendingDelete = nil } })
        ) {
            Button("Delete", role: .destructive) {
                if let entry = pendingDelete { delete(entry) }
                pendingDelete = nil
            }
            Button("Cancel", role: .cancel) { pendingDelete = nil }
        }
    }

    private func delete(_ entry: RichExpenseEntry) {
        var next = data
        next.remove(entryId: entry.id)
        Task {
            do {
                try await store.commit(next)
                deleteError = nil
            } catch {
                deleteError = "Couldn't delete “\(entry.name)”. Check your connection and try again."
            }
        }
    }

    private var currencyButton: some View {
        Button { showCurrencies = true } label: {
            HStack(spacing: 4) {
                Text(currency).fontWeight(.semibold).foregroundStyle(Theme.ink)
                let others = data.currencies.filter { $0 != currency }
                if !others.isEmpty {
                    Text("· " + others.joined(separator: " · "))
                }
                Image(systemName: "chevron.right").font(.system(size: 9, weight: .bold))
            }
            .font(.system(size: 12, weight: .medium))
            .foregroundStyle(Theme.inkSoft)
            .padding(.horizontal, 10).padding(.vertical, 5)
            .background(Theme.chipFill)
            .clipShape(Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Currencies")
    }

    private func messageCard(_ text: String, action: (String, () -> Void)?) -> some View {
        ThemeCard {
            VStack(alignment: .leading, spacing: 10) {
                Text(text).font(.subheadline).foregroundStyle(Theme.inkSoft)
                if let action {
                    Button(action.0, action: action.1)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Theme.travel)
                }
            }
        }
    }

    // MARK: Empty state

    private var emptyState: some View {
        ThemeCard(padding: 26) {
            VStack(spacing: 10) {
                Image(systemName: "receipt")
                    .font(.system(size: 34, weight: .light))
                    .foregroundStyle(Theme.inkFaint)
                    .padding(.bottom, 2)
                Text("No expenses yet").font(Theme.serif(20, weight: .medium)).foregroundStyle(Theme.ink)
                Text("Track flights, hotels and daily spending for this trip. Totals show in your default currency.")
                    .font(.footnote)
                    .foregroundStyle(Theme.inkSoft)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: 260)
                Button {
                    editorTarget = ExpenseEditorTarget(entry: nil, date: store.suggestedDate)
                } label: {
                    Text("+ Add first expense")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 18).padding(.vertical, 11)
                        .background(Theme.travel)
                        .clipShape(Capsule())
                }
                .buttonStyle(.plain)
                .padding(.top, 4)
            }
            .frame(maxWidth: .infinity)
        }
    }

    // MARK: Summary

    private var dayTotals: [(date: String, total: Double)] {
        data.dateExpenses.map { g in (g.date, g.entries.reduce(0) { $0 + ($1.amounts[currency] ?? 0) }) }
    }

    private var summaryCard: some View {
        let spent = data.total(in: currency)
        let cashback = data.totalCashback
        let dailySum = dayTotals.reduce(0) { $0 + $1.total }
        let perDay = dailySum / Double(max(1, store.trip.dayCount))
        return ThemeCard {
            VStack(alignment: .leading, spacing: 10) {
                SectionLabel(text: "Net spend")
                Text(formatTripAmount(spent - cashback, currency: currency))
                    .font(Theme.serif(34, weight: .medium))
                    .foregroundStyle(Theme.ink)
                    .monospacedDigit()
                    .contentTransition(.numericText())
                    .animation(.snappy, value: spent - cashback)
                HStack(spacing: 10) {
                    Text("Spent \(formatTripAmount(spent, currency: currency))")
                    if cashback > 0 {
                        Text("Cashback −\(formatTripAmount(cashback, currency: currency))")
                            .foregroundStyle(Theme.positive)
                            .fontWeight(.medium)
                    }
                    if dailySum > 0 {
                        Text("≈ \(formatTripAmount(perDay, currency: currency)) / day")
                    }
                }
                .font(.caption)
                .foregroundStyle(Theme.inkSoft)
                .monospacedDigit()
                .lineLimit(1)
                .minimumScaleFactor(0.8)

                dayBars
            }
        }
    }

    /// One bar per trip day plus a muted Pre-trip bar, all on one scale. Tapping a bar
    /// scrolls to that day's group.
    private var dayBars: some View {
        let pre = data.itemExpenses.reduce(0) { $0 + ($1.amounts[currency] ?? 0) }
        let days = dayTotals
        let maxValue = max(pre, days.map(\.total).max() ?? 0, 1)
        let showEvery = days.count > 14 ? 3 : (days.count > 8 ? 2 : 1)
        let barHeight: CGFloat = 46
        return HStack(alignment: .bottom, spacing: days.count > 14 ? 3 : 6) {
            bar(value: pre, max: maxValue, height: barHeight, fill: Theme.inkFaint.opacity(0.45),
                label: "Pre", labelColor: Theme.inkFaint, weight: 1.2) { scrollTo(expenseAnchor(nil)) }
            ForEach(Array(days.enumerated()), id: \.offset) { idx, d in
                bar(value: d.total, max: maxValue, height: barHeight,
                    fill: d.total > 0 ? Theme.travel : Theme.chipFill,
                    label: idx % showEvery == 0 ? dayOfMonth(d.date) : "",
                    labelColor: Theme.inkFaint, weight: 1) { scrollTo(expenseAnchor(d.date)) }
            }
        }
        .padding(.top, 4)
    }

    private func bar(value: Double, max: Double, height: CGFloat, fill: Color, label: String,
                     labelColor: Color, weight: CGFloat, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 4) {
                Spacer(minLength: 0)
                RoundedRectangle(cornerRadius: 5, style: .continuous)
                    .fill(fill)
                    .frame(height: Swift.max(3, height * CGFloat(value / max)))
                Text(label.isEmpty ? " " : label)
                    .font(.system(size: 10, weight: .medium))
                    .foregroundStyle(labelColor)
                    .lineLimit(1)
                    .fixedSize()
            }
            .frame(maxWidth: .infinity)
            .frame(height: height + 18)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .layoutPriority(weight)
    }

    // MARK: Groups

    private var groups: some View {
        VStack(alignment: .leading, spacing: 12) {
            if !data.itemExpenses.isEmpty {
                groupCard(title: Text("Pre-trip / Fixed").foregroundStyle(Theme.ink),
                          entries: data.itemExpenses, date: nil)
                    .id(expenseAnchor(nil))
            }
            ForEach(Array(data.dateExpenses.enumerated()), id: \.element.date) { idx, group in
                Group {
                    if group.entries.isEmpty {
                        emptyDayRow(group.date, dayNumber: dayNumber(of: group.date))
                    } else {
                        groupCard(title: dayTitle(group.date), entries: group.entries, date: group.date)
                    }
                }
                .id(expenseAnchor(group.date))
            }
        }
    }

    private func dayNumber(of date: String) -> Int? {
        store.trip.dayKeys.firstIndex(of: date).map { $0 + 1 }
    }

    private func dayTitle(_ date: String) -> Text {
        let day = dayNumber(of: date).map { Text(" · Day \($0)").foregroundStyle(Theme.inkSoft) } ?? Text("")
        return Text(formatTripDay(date)).foregroundStyle(Theme.ink) + day
    }

    private func groupCard(title: Text, entries: [RichExpenseEntry], date: String?) -> some View {
        let subtotal = entries.reduce(0.0) { $0 + ($1.amounts[currency] ?? 0) }
        return ThemeCard(padding: 0) {
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 8) {
                    title.font(.caption.weight(.semibold))
                    Spacer()
                    Text(formatTripAmount(subtotal, currency: currency))
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(Theme.inkSoft)
                        .monospacedDigit()
                    Button {
                        editorTarget = ExpenseEditorTarget(entry: nil, date: date)
                    } label: {
                        Image(systemName: "plus")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundStyle(Theme.travel)
                            .frame(width: 26, height: 22)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Add expense")
                }
                .padding(.leading, 16).padding(.trailing, 10)
                .padding(.vertical, 10)
                .background(Theme.chipFill)

                ForEach(Array(entries.enumerated()), id: \.element.id) { idx, entry in
                    SwipeToDeleteRow(onDelete: { pendingDelete = entry }) {
                        entryRow(entry)
                            .contentShape(Rectangle())
                            .onTapGesture { editorTarget = ExpenseEditorTarget(entry: entry, date: date) }
                    }
                    if idx < entries.count - 1 {
                        Rectangle().fill(Theme.hairline).frame(height: 1).padding(.leading, 16)
                    }
                }
            }
        }
    }

    private func entryRow(_ entry: RichExpenseEntry) -> some View {
        let (primaryCur, primaryAmt) = primaryAmount(entry)
        let others = data.currencies.filter { $0 != primaryCur }.compactMap { c in
            entry.amounts[c].map { formatTripAmount($0, currency: c) }
        }
        let cardNames = entry.creditCards.compactMap(store.cardName)
        return HStack(alignment: .top, spacing: 12) {
            VStack(alignment: .leading, spacing: 5) {
                Text(entry.name.isEmpty ? "Untitled" : entry.name)
                    .font(.subheadline)
                    .foregroundStyle(entry.name.isEmpty ? Theme.inkFaint : Theme.ink)
                if !cardNames.isEmpty || entry.cashback > 0 {
                    HStack(spacing: 4) {
                        ForEach(cardNames, id: \.self) { name in
                            pill(Text("\(Image(systemName: "creditcard")) \(name)"), tint: Theme.inkSoft, fill: Theme.chipFill)
                        }
                        if entry.cashback > 0 {
                            pill(Text("−\(formatTripAmount(entry.cashback, currency: currency))"),
                                 tint: Theme.positive, fill: Theme.positiveSoft)
                        }
                    }
                }
            }
            Spacer(minLength: 8)
            VStack(alignment: .trailing, spacing: 3) {
                Text(primaryCur.map { formatTripAmount(primaryAmt, currency: $0) } ?? "—")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Theme.ink)
                if !others.isEmpty {
                    Text(others.joined(separator: " · "))
                        .font(.caption)
                        .foregroundStyle(Theme.inkFaint)
                }
            }
            .monospacedDigit()
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }

    /// Default currency when the entry has it, otherwise the first tracked currency it has.
    private func primaryAmount(_ entry: RichExpenseEntry) -> (String?, Double) {
        if let v = entry.amounts[currency] { return (currency, v) }
        for c in data.currencies { if let v = entry.amounts[c] { return (c, v) } }
        if let first = entry.amounts.sorted(by: { $0.key < $1.key }).first { return (first.key, first.value) }
        return (nil, 0)
    }

    private func pill(_ text: Text, tint: Color, fill: Color) -> some View {
        text
            .font(.system(size: 10.5, weight: .medium))
            .lineLimit(1)
            .padding(.horizontal, 7).padding(.vertical, 2)
            .foregroundStyle(tint)
            .background(fill)
            .clipShape(Capsule())
    }

    private func emptyDayRow(_ date: String, dayNumber: Int?) -> some View {
        Button {
            editorTarget = ExpenseEditorTarget(entry: nil, date: date)
        } label: {
            HStack {
                (Text(formatTripDay(date)).fontWeight(.semibold).foregroundStyle(Theme.inkSoft)
                 + Text(dayNumber.map { " · Day \($0)" } ?? "") + Text(" · nothing yet"))
                    .font(.caption)
                    .foregroundStyle(Theme.inkFaint)
                Spacer()
                Text("+ Add").font(.caption.weight(.semibold)).foregroundStyle(Theme.travel)
            }
            .padding(.horizontal, 16).padding(.vertical, 11)
            .overlay(
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .strokeBorder(Theme.hairline, style: StrokeStyle(lineWidth: 1.5, dash: [5, 4]))
            )
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

/// Floating "+ Expense" button placed above the tab bar on the trip detail screen.
struct AddExpenseButton: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Label("Expense", systemImage: "plus")
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(.white)
                .padding(.horizontal, 18).padding(.vertical, 12)
                .background(Theme.travel)
                .clipShape(Capsule())
                .shadow(color: Theme.travel.opacity(0.35), radius: 10, y: 6)
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Add expense")
    }
}

func expenseAnchor(_ date: String?) -> String { "expense-day-\(date ?? "pre")" }

// MARK: - Add / edit sheet

struct ExpenseEditSheet: View {
    @ObservedObject var store: TripExpensesStore
    let target: ExpenseEditorTarget
    @Environment(\.dismiss) private var dismiss

    @State private var name: String
    @State private var payCurrency: String
    @State private var amountText: [String: String]
    @State private var date: String?
    @State private var cardIds: Set<String>
    @State private var cashbackText: String
    @State private var isSaving = false
    @State private var errorMessage: String?
    @State private var confirmDelete = false

    /// Last currency typed into per trip, so a run of daily entries keeps the local currency.
    private static var lastPayCurrency: [String: String] = [:]

    init(store: TripExpensesStore, target: ExpenseEditorTarget) {
        self.store = store
        self.target = target
        let data = store.data
        let entry = target.entry
        _name = State(initialValue: entry?.name ?? "")
        _date = State(initialValue: target.date)
        _cardIds = State(initialValue: Set(entry?.creditCards ?? []))
        _cashbackText = State(initialValue: entry.map { $0.cashback > 0 ? editableNumber($0.cashback) : "" } ?? "")
        _amountText = State(initialValue: (entry?.amounts ?? [:]).mapValues(editableNumber))

        let fallback = data.defaultCurrency.isEmpty
            ? (Locale.current.currency?.identifier).flatMap { tripCurrencies.contains($0) ? $0 : nil } ?? "HKD"
            : data.defaultCurrency
        let pay: String
        if let entry {
            pay = entry.amounts[data.defaultCurrency] != nil
                ? data.defaultCurrency
                : data.currencies.first { entry.amounts[$0] != nil } ?? fallback
        } else {
            pay = Self.lastPayCurrency[store.trip.id].flatMap { data.currencies.contains($0) ? $0 : nil } ?? fallback
        }
        _payCurrency = State(initialValue: pay)
    }

    private var data: TripExpenseData { store.data }
    private var isNew: Bool { target.entry == nil }
    private var defaultCurrency: String { data.defaultCurrency.isEmpty ? payCurrency : data.defaultCurrency }
    private var currencyOptions: [String] { data.currencies + tripCurrencies.filter { !data.currencies.contains($0) } }
    private var otherCurrencies: [String] { data.currencies.filter { $0 != payCurrency } }

    private var dayOptions: [String?] {
        var days = store.trip.dayKeys
        if let date, !days.contains(date) { days.append(date); days.sort() }
        return [nil] + days.map(Optional.some)
    }

    private var isValid: Bool {
        !name.trimmingCharacters(in: .whitespaces).isEmpty && parseAmount(amountText[payCurrency]) != nil
    }

    var body: some View {
        FormSheet(
            isNew ? "New expense" : "Edit expense",
            subtitle: "\(store.trip.title) · \(store.trip.dateRangeLabel)",
            primaryLabel: isNew ? "Add expense" : "Save",
            isPrimaryEnabled: isValid,
            isSaving: isSaving,
            errorMessage: errorMessage,
            onPrimary: save
        ) {
            FormSection {
                VStack(alignment: .leading, spacing: 0) {
                    FormAmountHero(label: "Amount", amount: binding(for: payCurrency))
                        .overlay(alignment: .topTrailing) {
                            FormMenuPicker(options: currencyOptions, selection: $payCurrency) { $0 }
                                .padding(14)
                        }
                    ForEach(otherCurrencies, id: \.self) { c in
                        FormDivider()
                        FormNumberRow(label: "Also in \(c)", text: binding(for: c), placeholder: "Optional", unit: c)
                    }
                }
            }

            FormSection {
                FormField(label: "What", text: $name, placeholder: "e.g. teamLab Planets", capitalization: .sentences)
            }

            VStack(alignment: .leading, spacing: 8) {
                SectionLabel(text: "When").padding(.horizontal, 4)
                dayChips
            }

            VStack(alignment: .leading, spacing: 8) {
                SectionLabel(text: "Paid with").padding(.horizontal, 4)
                if store.cards.isEmpty {
                    Text("Add cards in Finance › Cards to tag what you paid with.")
                        .font(.caption)
                        .foregroundStyle(Theme.inkFaint)
                        .padding(.horizontal, 4)
                } else {
                    FormChips(options: store.cards.map(\.id), selection: $cardIds) { store.cardName($0) ?? $0 }
                }
            }

            FormSection(footer: "Cashback is subtracted from the trip's net spend.") {
                FormNumberRow(label: "Cashback", text: $cashbackText, unit: defaultCurrency)
            }

            if !isNew {
                FormDestructiveButton(label: "Delete expense") { confirmDelete = true }
                    .disabled(isSaving)
            }
        }
        .alert("Delete this expense?", isPresented: $confirmDelete) {
            Button("Delete", role: .destructive, action: delete)
            Button("Cancel", role: .cancel) {}
        }
    }

    private var dayChips: some View {
        ScrollViewReader { proxy in
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(dayOptions, id: \.self) { d in
                        dayChip(d).id(d ?? "pre")
                    }
                }
            }
            .scrollClipDisabled()
            .onAppear { proxy.scrollTo(date ?? "pre", anchor: .center) }
        }
    }

    private func dayChip(_ d: String?) -> some View {
        let isActive = d == date
        return Button {
            withAnimation(.easeOut(duration: 0.15)) { date = d }
        } label: {
            VStack(spacing: 1) {
                Text(d.map { weekday($0) } ?? "Pre").font(.system(size: 14, weight: .medium))
                Text(d.map { monthDay($0) } ?? "trip").font(.system(size: 10, weight: .medium)).opacity(0.7)
            }
            .fixedSize()
            .padding(.horizontal, 13).padding(.vertical, 6)
            .foregroundStyle(isActive ? FormColors.selectedText : Theme.inkSoft)
            .background(isActive ? FormColors.selectedFill : Theme.chipFill)
            .clipShape(Capsule())
        }
        .buttonStyle(.plain)
    }

    private func binding(for currency: String) -> Binding<String> {
        Binding(get: { amountText[currency] ?? "" }, set: { amountText[currency] = $0 })
    }

    private func save() {
        var amounts: [String: Double] = [:]
        for c in Set(data.currencies + [payCurrency]) {
            if let v = parseAmount(amountText[c]) { amounts[c] = v }
        }
        // Keep amounts in currencies no longer tracked — they'd otherwise vanish on edit.
        for (c, v) in target.entry?.amounts ?? [:] where amounts[c] == nil && !data.currencies.contains(c) && c != payCurrency {
            amounts[c] = v
        }
        let entry = RichExpenseEntry(
            id: target.entry?.id ?? UUID().uuidString.lowercased(),
            name: name.trimmingCharacters(in: .whitespaces),
            amounts: amounts,
            cashback: parseAmount(cashbackText) ?? 0,
            creditCards: Array(cardIds).sorted()
        )
        var next = data
        if !next.currencies.contains(payCurrency) { next.currencies.append(payCurrency) }
        if next.defaultCurrency.isEmpty { next.defaultCurrency = payCurrency }
        next.upsert(entry, date: date)
        Self.lastPayCurrency[store.trip.id] = payCurrency
        persist(next)
    }

    private func delete() {
        guard let entry = target.entry else { return }
        var next = data
        next.remove(entryId: entry.id)
        persist(next)
    }

    private func persist(_ next: TripExpenseData) {
        isSaving = true
        errorMessage = nil
        Task {
            do {
                try await store.commit(next)
                dismiss()
            } catch {
                errorMessage = "Couldn't save. Check your connection and try again."
            }
            isSaving = false
        }
    }

    private func weekday(_ key: String) -> String {
        tripDayFormatter(utc: true).date(from: key).map { Self.weekdayFmt.string(from: $0) } ?? key
    }
    private func monthDay(_ key: String) -> String {
        tripDayFormatter(utc: true).date(from: key).map { Self.monthDayFmt.string(from: $0) } ?? ""
    }
    private static let weekdayFmt: DateFormatter = {
        let f = DateFormatter(); f.dateFormat = "EEE"; f.timeZone = TimeZone(identifier: "UTC"); return f
    }()
    private static let monthDayFmt: DateFormatter = {
        let f = DateFormatter(); f.dateFormat = "MMM d"; f.timeZone = TimeZone(identifier: "UTC"); return f
    }()
}

// MARK: - Currencies sheet

struct TripCurrenciesSheet: View {
    @ObservedObject var store: TripExpensesStore
    @State private var isSaving = false
    @State private var errorMessage: String?

    private var data: TripExpenseData { store.data }

    var body: some View {
        FormSheet(
            "Currencies",
            subtitle: "Totals, net spend and cashback use the default currency.",
            errorMessage: errorMessage
        ) {
            FormSection(footer: "Swipe a currency to stop tracking it. Amounts already entered in it stay saved.") {
                ForEach(Array(data.currencies.enumerated()), id: \.element) { idx, c in
                    SwipeToDeleteRow(onDelete: { update { $0.removeCurrency(c) } }) {
                        HStack(spacing: 8) {
                            Text(c).font(.system(size: 15, weight: .semibold)).foregroundStyle(Theme.ink)
                            Text(currencyName(c)).font(.caption).foregroundStyle(Theme.inkSoft)
                            Spacer()
                            if c == data.defaultCurrency {
                                Text("Default")
                                    .font(.system(size: 11, weight: .semibold))
                                    .foregroundStyle(Theme.travel)
                                    .padding(.horizontal, 8).padding(.vertical, 3)
                                    .background(Theme.travelSoft)
                                    .clipShape(Capsule())
                            } else {
                                Button("Make default") { update { $0.defaultCurrency = c } }
                                    .font(.caption.weight(.medium))
                                    .foregroundStyle(Theme.inkSoft)
                                    .buttonStyle(.plain)
                            }
                        }
                        .formRowPadding()
                    }
                    if idx < data.currencies.count - 1 { FormDivider() }
                }
                let untracked = tripCurrencies.filter { !data.currencies.contains($0) }
                if !untracked.isEmpty {
                    if !data.currencies.isEmpty { FormDivider() }
                    Menu {
                        ForEach(untracked, id: \.self) { c in
                            Button("\(c) · \(currencyName(c))") { update { $0.addCurrency(c) } }
                        }
                    } label: {
                        HStack {
                            Text("+ Track another currency").font(.system(size: 15, weight: .semibold))
                            Spacer()
                        }
                        .foregroundStyle(Theme.travel)
                        .formRowPadding()
                        .contentShape(Rectangle())
                    }
                }
            }
            .disabled(isSaving)
        }
    }

    private func update(_ change: (inout TripExpenseData) -> Void) {
        var next = data
        change(&next)
        guard next != data else { return }
        isSaving = true
        errorMessage = nil
        Task {
            do {
                try await store.commit(next)
            } catch {
                errorMessage = "Couldn't update currencies. Check your connection and try again."
            }
            isSaving = false
        }
    }

    private func currencyName(_ code: String) -> String {
        Locale.current.localizedString(forCurrencyCode: code) ?? ""
    }
}

private extension TripExpenseData {
    mutating func addCurrency(_ c: String) {
        guard !currencies.contains(c) else { return }
        currencies.append(c)
        if defaultCurrency.isEmpty { defaultCurrency = c }
    }

    /// Mirrors the web: amounts in the removed currency stay on each entry.
    mutating func removeCurrency(_ c: String) {
        currencies.removeAll { $0 == c }
        if defaultCurrency == c { defaultCurrency = currencies.first ?? "" }
    }
}

// MARK: - Helpers

/// Same list the web app offers for trip currencies.
let tripCurrencies = ["HKD", "USD", "EUR", "GBP", "JPY", "CNY", "TWD", "SGD", "KRW", "THB", "MYR", "AUD", "CAD", "CHF"]

private var tripAmountFormatters: [String: NumberFormatter] = [:]

/// Currency amount with exactly 2 decimals ("HK$1,280.50", "¥3,800.00") — matches the web
/// app, which shows every trip amount with 2 decimals regardless of currency.
func formatTripAmount(_ value: Double, currency: String) -> String {
    let f = tripAmountFormatters[currency] ?? {
        let f = NumberFormatter()
        f.numberStyle = .currency
        f.currencyCode = currency
        f.minimumFractionDigits = 2
        f.maximumFractionDigits = 2
        tripAmountFormatters[currency] = f
        return f
    }()
    return f.string(from: NSNumber(value: value)) ?? "\(currency) \(String(format: "%.2f", value))"
}

/// Trip day keys are calendar dates (`yyyy-MM-dd`); parse and print them in UTC so the
/// device time zone can't shift them by a day.
func tripDayFormatter(utc: Bool) -> DateFormatter {
    let f = DateFormatter()
    f.dateFormat = "yyyy-MM-dd"
    f.locale = Locale(identifier: "en_US_POSIX")
    if utc { f.timeZone = TimeZone(identifier: "UTC") }
    return f
}

func formatTripDay(_ key: String) -> String {
    guard let d = tripDayFormatter(utc: true).date(from: key) else { return key }
    let out = DateFormatter()
    out.dateFormat = "EEE, MMM d"
    out.timeZone = TimeZone(identifier: "UTC")
    return out.string(from: d)
}

private func dayOfMonth(_ key: String) -> String {
    key.split(separator: "-").last.map { String(Int($0) ?? 0) } ?? ""
}

/// Accepts "1,280.5" or a comma-decimal "12,5" from a European keypad.
private func parseAmount(_ text: String?) -> Double? {
    guard var t = text?.trimmingCharacters(in: .whitespaces), !t.isEmpty else { return nil }
    if t.contains(",") && !t.contains(".") && t.split(separator: ",").last?.count ?? 0 <= 2 {
        t = t.replacingOccurrences(of: ",", with: ".")
    } else {
        t = t.replacingOccurrences(of: ",", with: "")
    }
    guard let v = Double(t), v >= 0 else { return nil }
    return (v * 100).rounded() / 100  // store at most 2 decimals, like the web inputs (step 0.01)
}

private func editableNumber(_ v: Double) -> String {
    v.truncatingRemainder(dividingBy: 1) == 0 ? String(format: "%.0f", v) : String(format: "%.2f", v)
}
