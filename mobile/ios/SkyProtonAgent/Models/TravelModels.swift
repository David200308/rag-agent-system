import Foundation

enum TransportType: String, Codable, CaseIterable {
    case plane = "PLANE", train = "TRAIN", bus = "BUS", ferry = "FERRY", car = "CAR", walk = "WALK"

    var label: String {
        switch self {
        case .plane: return "Plane"
        case .train: return "Train"
        case .bus:   return "Bus"
        case .ferry: return "Ferry"
        case .car:   return "Car"
        case .walk:  return "Walk"
        }
    }
}

struct TravelStop: Codable, Identifiable {
    var id: String { "\(city)-\(country)" }
    let city: String
    let country: String
    let lat: Double
    let lon: Double
    let transport: TransportType?
    let flightNumber: String?
    let seatNumber: String?
    let notes: String?
}

/// A minimal, permissive JSON tree used to read a trip's expenses (`GET /api/v1/travel/{id}/expenses`) —
/// that data can hold either the current `{__v: 2, ...}` shape or an older, differently-shaped format.
/// Decoding it through this generic tree (rather than a strict `Codable` struct) means legacy expense
/// data can't fail decoding for the whole response.
enum JSONValue: Codable {
    case string(String)
    case number(Double)
    case bool(Bool)
    case object([String: JSONValue])
    case array([JSONValue])
    case null

    init(from decoder: Decoder) throws {
        let c = try decoder.singleValueContainer()
        if c.decodeNil() { self = .null; return }
        if let v = try? c.decode(Bool.self) { self = .bool(v); return }
        if let v = try? c.decode(Double.self) { self = .number(v); return }
        if let v = try? c.decode(String.self) { self = .string(v); return }
        if let v = try? c.decode([String: JSONValue].self) { self = .object(v); return }
        if let v = try? c.decode([JSONValue].self) { self = .array(v); return }
        throw DecodingError.dataCorruptedError(in: c, debugDescription: "Unsupported JSON value")
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.singleValueContainer()
        switch self {
        case .string(let v): try c.encode(v)
        case .number(let v): try c.encode(v)
        case .bool(let v):   try c.encode(v)
        case .object(let v): try c.encode(v)
        case .array(let v):  try c.encode(v)
        case .null:          try c.encodeNil()
        }
    }

    subscript(key: String) -> JSONValue? {
        if case .object(let dict) = self { return dict[key] }
        return nil
    }
    var stringValue: String? { if case .string(let v) = self { return v }; return nil }
    var doubleValue: Double? { if case .number(let v) = self { return v }; return nil }
    var arrayValue: [JSONValue]? { if case .array(let v) = self { return v }; return nil }
}

struct RichExpenseEntry: Identifiable, Equatable {
    let id: String
    var name: String
    /// Amount per tracked currency, e.g. `["HKD": 198, "JPY": 3800]` — a currency may be absent.
    var amounts: [String: Double]
    /// Always in the trip's default currency (matches the web app).
    var cashback: Double
    /// `FinancialCard.id`s, not names — the web app stores ids too.
    var creditCards: [String]

    init(id: String = UUID().uuidString.lowercased(), name: String = "", amounts: [String: Double] = [:],
         cashback: Double = 0, creditCards: [String] = []) {
        self.id = id
        self.name = name
        self.amounts = amounts
        self.cashback = cashback
        self.creditCards = creditCards
    }

    init?(json: JSONValue) {
        guard let id = json["id"]?.stringValue, let name = json["name"]?.stringValue else { return nil }
        self.id = id
        self.name = name
        var amounts: [String: Double] = [:]
        if case .object(let amtsDict)? = json["amounts"] {
            for (k, v) in amtsDict { if let d = v.doubleValue { amounts[k] = d } }
        }
        self.amounts = amounts
        self.cashback = json["cashback"]?.doubleValue ?? 0
        self.creditCards = json["creditCards"]?.arrayValue?.compactMap { $0.stringValue } ?? []
    }

    var jsonObject: [String: Any] {
        ["id": id, "name": name, "amounts": amounts, "cashback": cashback, "creditCards": creditCards]
    }
}

struct DateExpenseGroup: Identifiable, Equatable {
    var id: String { date }
    let date: String
    var entries: [RichExpenseEntry]

    init(date: String, entries: [RichExpenseEntry] = []) {
        self.date = date
        self.entries = entries
    }

    init?(json: JSONValue) {
        guard let date = json["date"]?.stringValue else { return nil }
        self.date = date
        self.entries = json["entries"]?.arrayValue?.compactMap { RichExpenseEntry(json: $0) } ?? []
    }

    var jsonObject: [String: Any] {
        ["date": date, "entries": entries.map(\.jsonObject)]
    }
}

/// Only recognizes the current `__v: 2` expense shape; anything else (missing, legacy format) is nil.
struct TripExpenseData: Equatable {
    var currencies: [String]
    var defaultCurrency: String
    /// One-off items (e.g. flight tickets) — the web app's "PRE-TRIP / FIXED" section.
    var itemExpenses: [RichExpenseEntry]
    var dateExpenses: [DateExpenseGroup]

    init(currencies: [String] = [], defaultCurrency: String = "",
         itemExpenses: [RichExpenseEntry] = [], dateExpenses: [DateExpenseGroup] = []) {
        self.currencies = currencies
        self.defaultCurrency = defaultCurrency
        self.itemExpenses = itemExpenses
        self.dateExpenses = dateExpenses
    }

    init?(json: JSONValue) {
        guard json["__v"]?.doubleValue == 2, let defaultCurrency = json["defaultCurrency"]?.stringValue else {
            return nil
        }
        self.defaultCurrency = defaultCurrency
        self.currencies = json["currencies"]?.arrayValue?.compactMap { $0.stringValue } ?? []
        self.itemExpenses = json["itemExpenses"]?.arrayValue?.compactMap { RichExpenseEntry(json: $0) } ?? []
        self.dateExpenses = json["dateExpenses"]?.arrayValue?.compactMap { DateExpenseGroup(json: $0) } ?? []
    }

    /// `GET /api/v1/travel/{id}/expenses` returns the raw `[Map]` list stored on the trip — the
    /// current format wraps its one `TripExpenseData` object as that list's first element.
    static func from(_ raw: [JSONValue]) -> TripExpenseData? {
        raw.first.flatMap { TripExpenseData(json: $0) }
    }

    /// The element stored as the trip's `expenses: [ ... ]` list — same shape the web app writes.
    var jsonObject: [String: Any] {
        [
            "__v": 2,
            "currencies": currencies,
            "defaultCurrency": defaultCurrency,
            "itemExpenses": itemExpenses.map(\.jsonObject),
            "dateExpenses": dateExpenses.map(\.jsonObject),
        ]
    }

    var allEntries: [RichExpenseEntry] { itemExpenses + dateExpenses.flatMap(\.entries) }
    var isEmpty: Bool { allEntries.isEmpty }

    func total(in currency: String) -> Double {
        allEntries.reduce(0) { $0 + ($1.amounts[currency] ?? 0) }
    }
    var totalCashback: Double { allEntries.reduce(0) { $0 + $1.cashback } }

    /// One group per trip day (mirrors the web's `buildDateGroups`), keeping existing entries.
    /// Groups outside the trip's dates are kept only if they still hold entries, so editing
    /// trip dates elsewhere never silently drops spending.
    mutating func normalizeDays(for trip: TravelRecord) {
        let existing = Dictionary(dateExpenses.map { ($0.date, $0) }, uniquingKeysWith: { a, _ in a })
        let days = trip.dayKeys
        var groups = days.map { existing[$0] ?? DateExpenseGroup(date: $0) }
        groups += dateExpenses.filter { !days.contains($0.date) && !$0.entries.isEmpty }
        dateExpenses = groups.sorted { $0.date < $1.date }
    }

    /// Location of an entry: `nil` date means the pre-trip / fixed list.
    func location(of entryId: String) -> String?? {
        if itemExpenses.contains(where: { $0.id == entryId }) { return .some(nil) }
        if let g = dateExpenses.first(where: { $0.entries.contains { $0.id == entryId } }) { return .some(g.date) }
        return nil
    }

    mutating func remove(entryId: String) {
        itemExpenses.removeAll { $0.id == entryId }
        for i in dateExpenses.indices { dateExpenses[i].entries.removeAll { $0.id == entryId } }
    }

    /// Insert or replace `entry`, placing it under `date` (nil = pre-trip). Moving between
    /// days keeps the entry's id.
    mutating func upsert(_ entry: RichExpenseEntry, date: String?) {
        if location(of: entry.id) == .some(date) {
            if date == nil {
                if let i = itemExpenses.firstIndex(where: { $0.id == entry.id }) { itemExpenses[i] = entry }
            } else if let g = dateExpenses.firstIndex(where: { $0.date == date }),
                      let i = dateExpenses[g].entries.firstIndex(where: { $0.id == entry.id }) {
                dateExpenses[g].entries[i] = entry
            }
            return
        }
        remove(entryId: entry.id)
        guard let date else { itemExpenses.append(entry); return }
        if let g = dateExpenses.firstIndex(where: { $0.date == date }) {
            dateExpenses[g].entries.append(entry)
        } else {
            dateExpenses.append(DateExpenseGroup(date: date, entries: [entry]))
            dateExpenses.sort { $0.date < $1.date }
        }
    }
}

struct TravelRecord: Codable, Identifiable {
    let id: String
    let title: String
    let startDate: String
    let endDate: String
    let stops: [TravelStop]
    let notes: String?
    let allowChat: Bool
    let createdAt: String
    let updatedAt: String

    // `expenses` is deliberately absent here: `GET /api/v1/travel` (the list endpoint this struct
    // decodes) omits it entirely to avoid shipping every trip's expense history on load. Fetch a
    // trip's expenses on demand via `AgentService.getTravelExpenses(id:)`, which decodes them
    // straight into `TripExpenseData` without going through `TravelRecord`.

    private static let dayFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        f.timeZone = TimeZone(identifier: "UTC")
        return f
    }()

    var dayCount: Int {
        guard let s = Self.dayFormatter.date(from: startDate),
              let e = Self.dayFormatter.date(from: endDate) else { return 1 }
        let days = Calendar(identifier: .gregorian).dateComponents([.day], from: s, to: e).day ?? 0
        return max(1, days + 1)
    }

    /// Every trip day as `yyyy-MM-dd`, start through end inclusive.
    var dayKeys: [String] {
        guard let s = Self.dayFormatter.date(from: startDate),
              let e = Self.dayFormatter.date(from: endDate), s <= e else { return [] }
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "UTC")!
        var keys: [String] = []
        var d = s
        while d <= e && keys.count < 366 {
            keys.append(Self.dayFormatter.string(from: d))
            d = cal.date(byAdding: .day, value: 1, to: d)!
        }
        return keys
    }

    var isUpcoming: Bool {
        guard let s = Self.dayFormatter.date(from: startDate) else { return false }
        return s > Date()
    }

    var dateRangeLabel: String {
        guard let s = Self.dayFormatter.date(from: startDate),
              let e = Self.dayFormatter.date(from: endDate) else { return "\(startDate) – \(endDate)" }
        let f = DateFormatter()
        f.dateFormat = "MMM d, yyyy"
        return "\(f.string(from: s)) – \(f.string(from: e))"
    }

    var routeLabel: String {
        stops.map(\.city).joined(separator: " → ")
    }
}
