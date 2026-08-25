import Foundation

final class AgentService {
    static let shared = AgentService()
    private let client = APIClient.shared

    // MARK: – Financial

    func listCashDeposits() async throws -> [CashDeposit] {
        try await client.get("/api/v1/financial/deposits")
    }

    func listStocks() async throws -> [StockInvestment] {
        try await client.get("/api/v1/financial/stocks")
    }

    func listCrypto() async throws -> [CryptoInvestment] {
        try await client.get("/api/v1/financial/crypto")
    }

    func listFutures() async throws -> [FutureInvestment] {
        try await client.get("/api/v1/financial/futures")
    }

    func listCards() async throws -> [FinancialCard] {
        try await client.get("/api/v1/financial/cards")
    }

    func listSalaryRecords() async throws -> [SalaryUsageRecord] {
        try await client.get("/api/v1/financial/salary")
    }

    func refreshPrices() async throws {
        let _: EmptyResponse = try await client.post("/api/v1/financial/prices/refresh", body: _EmptyBody())
    }

    // MARK: – Travel

    func listTravelRecords() async throws -> [TravelRecord] {
        try await client.get("/api/v1/travel")
    }
}

private struct _EmptyBody: Encodable {}
