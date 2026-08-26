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

    // MARK: – Financial mutations

    func createDeposit(_ fields: [String: Any]) async throws { try await client.postRaw("/api/v1/financial/deposits", body: fields) }
    func updateDeposit(id: String, _ fields: [String: Any]) async throws { try await client.putRaw("/api/v1/financial/deposits/\(id)", body: fields) }
    func deleteDeposit(id: String) async throws { try await client.delete("/api/v1/financial/deposits/\(id)") }

    func createStock(_ fields: [String: Any]) async throws { try await client.postRaw("/api/v1/financial/stocks", body: fields) }
    func updateStock(id: String, _ fields: [String: Any]) async throws { try await client.putRaw("/api/v1/financial/stocks/\(id)", body: fields) }
    func deleteStock(id: String) async throws { try await client.delete("/api/v1/financial/stocks/\(id)") }

    func createCrypto(_ fields: [String: Any]) async throws { try await client.postRaw("/api/v1/financial/crypto", body: fields) }
    func updateCrypto(id: String, _ fields: [String: Any]) async throws { try await client.putRaw("/api/v1/financial/crypto/\(id)", body: fields) }
    func deleteCrypto(id: String) async throws { try await client.delete("/api/v1/financial/crypto/\(id)") }

    func createFuture(_ fields: [String: Any]) async throws { try await client.postRaw("/api/v1/financial/futures", body: fields) }
    func updateFuture(id: String, _ fields: [String: Any]) async throws { try await client.putRaw("/api/v1/financial/futures/\(id)", body: fields) }
    func deleteFuture(id: String) async throws { try await client.delete("/api/v1/financial/futures/\(id)") }

    func createCard(_ fields: [String: Any]) async throws { try await client.postRaw("/api/v1/financial/cards", body: fields) }
    func updateCard(id: String, _ fields: [String: Any]) async throws { try await client.putRaw("/api/v1/financial/cards/\(id)", body: fields) }
    func deleteCard(id: String) async throws { try await client.delete("/api/v1/financial/cards/\(id)") }

    func createSalary(_ fields: [String: Any]) async throws { try await client.postRaw("/api/v1/financial/salary", body: fields) }
    func updateSalary(id: String, _ fields: [String: Any]) async throws { try await client.putRaw("/api/v1/financial/salary/\(id)", body: fields) }
    func deleteSalary(id: String) async throws { try await client.delete("/api/v1/financial/salary/\(id)") }

    // MARK: – Travel

    func listTravelRecords() async throws -> [TravelRecord] {
        try await client.get("/api/v1/travel")
    }
}

private struct _EmptyBody: Encodable {}
