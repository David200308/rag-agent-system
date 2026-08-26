import Foundation

// Mirrors frontend/src/types/financial.ts exactly, so the mobile forms offer the same choices as the web app.
let commonCurrencies = ["HKD", "CNY", "USD", "JPY", "SGD", "GBP", "AUD", "EUR", "CAD"]
let depositTypes = ["FIXED", "FLEX"]
let stockTypes = ["US_STOCK", "HK_STOCK", "CN_STOCK", "SG_STOCK", "OTHER"]
let stockTypeLabels: [String: String] = [
    "US_STOCK": "US Stock", "HK_STOCK": "HK Stock", "CN_STOCK": "CN Stock", "SG_STOCK": "SG Stock", "OTHER": "Other",
]
let cardTypes = ["Credit", "Debit", "ATM"]
let cardNetworks = ["Mastercard", "Visa", "UnionPay", "JCB", "AMEX"]
let cexExchanges = ["BINANCE", "OKX", "KRAKEN"]
let futureSides = ["LONG", "SHORT"]

struct CashDeposit: Codable, Identifiable {
    let id: String
    let platform: String
    let platformType: String
    let countryRegion: String?  // nullable on the backend — older/API-created rows may not have it set
    let depositType: String
    let currency: String
    let amount: Double
    let convertedAmount: Double
    let convertedCurrency: String
    let createdAt: String
}

struct StockInvestment: Codable, Identifiable {
    let id: String
    let broker: String
    let stockType: String
    let symbol: String
    let name: String
    let stockAmount: Double
    let investAmount: Double
    let currency: String
    let fee: Double
    let currentPrice: Double?
    let priceCurrency: String?
    let logoUrl: String?
    let currentValue: Double?
    let convertedInvestAmount: Double
    let convertedCurrentValue: Double?
    let convertedCurrency: String
    let pnlPercent: Double?
    let createdAt: String
}

struct CryptoInvestment: Codable, Identifiable {
    let id: String
    let name: String
    let symbol: String
    let amount: Double
    let investAmount: Double
    let currency: String
    let currentPrice: Double?
    let logoUrl: String?
    let currentValue: Double?
    let convertedInvestAmount: Double
    let convertedCurrentValue: Double?
    let convertedCurrency: String
    let pnlPercent: Double?
    let createdAt: String
}

/// exchangeKind: SECURITY | CRYPTO_CEX | CRYPTO_DEX. source: "MANUAL" for user-entered
/// Security/CEX rows, or the DEX name (HYPERLIQUID/JUPITER_PERPS/LIGHTER) for auto-tracked rows.
struct FutureInvestment: Codable, Identifiable {
    let id: String
    let exchangeKind: String
    let exchange: String
    let symbol: String?
    let side: String?
    let quantity: Double?
    let entryPrice: Double?
    let leverage: Double?
    let currency: String
    let currentPrice: Double?
    let currentValue: Double?
    let margin: Double?
    let liquidationPrice: Double?
    let fundingSinceOpen: Double?
    let convertedInvestAmount: Double?
    let convertedCurrentValue: Double?
    let convertedCurrency: String?
    let pnlPercent: Double?
    let source: String
    let hyperliquidDex: String?
    let createdAt: String
}

struct FinancialCard: Codable, Identifiable {
    let id: String
    let bank: String
    let cardName: String
    let network: String
    let countryRegion: String?  // nullable on the backend — older/API-created rows may not have it set
    let types: [String]
    let creditLimit: Double?
    let creditLimitCurrency: String?
    let sharedCredit: Bool?
    let expireDate: String?  // nullable on the backend — older/API-created rows may not have it set
    let createdAt: String
}

struct SalaryUsageRecord: Codable, Identifiable {
    let id: String
    let year: Int
    let month: Int
    let region: String
    let currency: String
    let salary: Double
    let bonus: Double
    let retirementSavingEmployee: Double
    let retirementSavingEmployer: Double
    let tax: Double
    let houseRent: Double
    let livingExpense: Double
    let otherExpense: Double
    let totalExpense: Double
    let createdAt: String
}
