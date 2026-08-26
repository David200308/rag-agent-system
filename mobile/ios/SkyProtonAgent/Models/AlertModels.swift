import Foundation

// Mirrors frontend/src/types/alerts.ts, which is the verified-working shape returned by the
// Go investment-alert-task service (proxied unchanged through agent-system-rest's AlertController).

struct AlertFrequency: Codable, Equatable {
    var number: Int?
    var unit: String  // "DAY" | "HOUR" | "ONCE" | "NEVER"
}

struct PriceAlert: Codable, Identifiable {
    let id: String
    let symbol: String
    let priceFeedId: String?
    let assetType: String  // "CRYPTO" | "STOCK"
    let threshold: Double
    let direction: String  // ">=" | ">" | "=" | "<=" | "<"
    let enabled: Bool
    let frequency: AlertFrequency?
}

struct AlertsResponse: Codable {
    let price: [PriceAlert]
}

let alertDirections = [">=", ">", "=", "<=", "<"]
let alertDirectionLabels: [String: String] = [
    ">=": "At or above", ">": "Above", "=": "Equals", "<=": "At or below", "<": "Below",
]
let alertFrequencyUnits = ["HOUR", "DAY", "ONCE", "NEVER"]
let alertFrequencyUnitLabels: [String: String] = [
    "HOUR": "Every N hours", "DAY": "Every N days", "ONCE": "Once, then disable", "NEVER": "Never re-fire",
]

func formatAlertFrequency(_ freq: AlertFrequency?) -> String {
    guard let freq else { return "Default (at most once/hour)" }
    switch freq.unit {
    case "ONCE":  return "Once, then disabled"
    case "NEVER": return "Never re-fires"
    case "HOUR":  return "Every \(freq.number ?? 1)h"
    case "DAY":   return "Every \(freq.number ?? 1)d"
    default:      return ""
    }
}
