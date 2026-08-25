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

/// A minimal, permissive JSON tree used to read `TravelRecord.expenses` — that column can hold
/// either the current `{__v: 2, ...}` shape or an older, differently-shaped format. Decoding it
/// through this generic tree (rather than a strict `Codable` struct) means one trip with legacy
/// expense data can't fail decoding for the whole `/api/v1/travel` list response.
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

struct RichExpenseEntry: Identifiable {
    let id: String
    let name: String
    let amounts: [String: Double]
    let cashback: Double
    let creditCards: [String]

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
}

struct DateExpenseGroup: Identifiable {
    var id: String { date }
    let date: String
    let entries: [RichExpenseEntry]

    init?(json: JSONValue) {
        guard let date = json["date"]?.stringValue else { return nil }
        self.date = date
        self.entries = json["entries"]?.arrayValue?.compactMap { RichExpenseEntry(json: $0) } ?? []
    }
}

/// Only recognizes the current `__v: 2` expense shape; anything else (missing, legacy format) is nil.
struct TripExpenseData {
    let currencies: [String]
    let defaultCurrency: String
    let dateExpenses: [DateExpenseGroup]

    init?(json: JSONValue) {
        guard json["__v"]?.doubleValue == 2, let defaultCurrency = json["defaultCurrency"]?.stringValue else {
            return nil
        }
        self.defaultCurrency = defaultCurrency
        self.currencies = json["currencies"]?.arrayValue?.compactMap { $0.stringValue } ?? []
        self.dateExpenses = json["dateExpenses"]?.arrayValue?.compactMap { DateExpenseGroup(json: $0) } ?? []
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
    private let expensesRaw: [JSONValue]

    enum CodingKeys: String, CodingKey {
        case id, title, startDate, endDate, stops, notes, allowChat, createdAt, updatedAt
        case expensesRaw = "expenses"
    }

    var expenseData: TripExpenseData? {
        expensesRaw.first.flatMap { TripExpenseData(json: $0) }
    }

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
