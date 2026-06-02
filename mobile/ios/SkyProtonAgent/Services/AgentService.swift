import Foundation

final class AgentService {
    static let shared = AgentService()
    private let client = APIClient.shared

    // MARK: – Agent

    func query(_ request: AgentRequest) async throws -> AgentResponse {
        try await client.post("/api/v1/agent/query", body: request)
    }

    // MARK: – Conversations

    func listConversations() async throws -> [Conversation] {
        try await client.get("/api/v1/agent/conversations")
    }

    func listArchivedConversations() async throws -> [Conversation] {
        try await client.get("/api/v1/agent/conversations/archived")
    }

    func getMessages(conversationId: String) async throws -> [ConversationMessage] {
        try await client.get("/api/v1/agent/conversations/\(conversationId)")
    }

    func deleteConversation(id: String) async throws {
        try await client.delete("/api/v1/agent/conversations/\(id)")
    }

    func archiveConversation(id: String) async throws {
        let _: EmptyResponse = try await client.patch("/api/v1/agent/conversations/\(id)/archive", body: _EmptyBody())
    }

    func unarchiveConversation(id: String) async throws {
        let _: EmptyResponse = try await client.patch("/api/v1/agent/conversations/\(id)/unarchive", body: _EmptyBody())
    }

    func setConversationModel(conversationId: String, displayName: String?) async throws {
        struct Body: Encodable { let selectedModel: String? }
        let _: EmptyResponse = try await client.patch(
            "/api/v1/agent/conversations/\(conversationId)/model",
            body: Body(selectedModel: displayName)
        )
    }

    // MARK: – Share

    func getShare(conversationId: String) async throws -> ConversationShare? {
        do {
            return try await client.get("/api/v1/agent/conversations/\(conversationId)/share")
        } catch APIError.httpError(404, _) {
            return nil
        }
    }

    func createShare(
        conversationId: String,
        expireDays: Int?,
        shareMode: ShareMode,
        accessType: ShareAccessType,
        whitelist: [String]
    ) async throws -> ConversationShare {
        struct Body: Encodable {
            let expireDays: Int?
            let shareMode: String
            let accessType: String
            let whitelist: [String]
        }
        return try await client.post(
            "/api/v1/agent/conversations/\(conversationId)/share",
            body: Body(
                expireDays: expireDays,
                shareMode: shareMode.rawValue,
                accessType: accessType.rawValue,
                whitelist: whitelist
            )
        )
    }

    func revokeShare(conversationId: String) async throws {
        try await client.delete("/api/v1/agent/conversations/\(conversationId)/share")
    }

    // MARK: – Knowledge

    func listKnowledge() async throws -> [KnowledgeSource] {
        try await client.get("/api/v1/agent/knowledge")
    }

    func deleteKnowledgeSource(source: String) async throws {
        guard let encoded = source.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) else { return }
        try await client.delete("/api/v1/agent/knowledge?source=\(encoded)")
    }

    // MARK: – Models

    func listModels() async throws -> [ModelConfig] {
        try await client.get("/api/v1/models")
    }

    // MARK: – User Preferences

    func getUserPreferences() async throws -> UserPreferences {
        try await client.get("/api/v1/agent/user/preferences")
    }

    func setUserDefaultModel(_ displayName: String?) async throws {
        struct Body: Encodable { let selectedModel: String? }
        let _: EmptyResponse = try await client.put(
            "/api/v1/agent/user/preferences",
            body: Body(selectedModel: displayName)
        )
    }

    func setUserTimezone(_ timezone: String) async throws {
        struct Body: Encodable { let timezone: String }
        let _: EmptyResponse = try await client.put(
            "/api/v1/agent/user/preferences",
            body: Body(timezone: timezone)
        )
    }

    // MARK: – Web-fetch whitelist

    func listWebFetchWhitelist() async throws -> [WebFetchWhitelistEntry] {
        try await client.get("/api/v1/agent/web-fetch/whitelist")
    }

    func addWebFetchDomain(_ domain: String) async throws -> WebFetchWhitelistEntry {
        struct Body: Encodable { let domain: String }
        return try await client.post("/api/v1/agent/web-fetch/whitelist", body: Body(domain: domain))
    }

    func removeWebFetchDomain(_ domain: String) async throws {
        guard let encoded = domain.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) else { return }
        try await client.delete("/api/v1/agent/web-fetch/whitelist/\(encoded)")
    }

    // MARK: – Skills

    func listSkills() async throws -> [Skill] {
        try await client.get("/api/v1/agent/skills")
    }

    // MARK: – Ingest

    func ingestText(text: String, source: String, replace: Bool = false) async throws -> IngestionResult {
        struct Body: Encodable { let text: String; let source: String; let replace: String }
        return try await client.post(
            "/api/v1/agent/ingest-text",
            body: Body(text: text, source: source, replace: replace ? "true" : "false")
        )
    }

    func ingestUrl(url: String, category: String? = nil) async throws -> UrlIngestionResult {
        struct Body: Encodable { let url: String; let category: String? }
        return try await client.post(
            "/api/v1/agent/ingest-url",
            body: Body(url: url, category: category)
        )
    }

    // MARK: – Workflows

    func listWorkflows() async throws -> [Workflow] {
        try await client.get("/api/v1/workflow")
    }

    func createWorkflow(name: String, agentPattern: String, teamExecMode: String?) async throws -> Workflow {
        struct Body: Encodable { let name: String; let agentPattern: String; let teamExecMode: String? }
        return try await client.post("/api/v1/workflow",
                                     body: Body(name: name, agentPattern: agentPattern, teamExecMode: teamExecMode))
    }

    func deleteWorkflow(id: String) async throws {
        try await client.delete("/api/v1/workflow/\(id)")
    }

    func runWorkflow(id: String, input: String) async throws -> String {
        struct Body: Encodable { let userInput: String }
        struct Resp: Decodable { let runId: String }
        let r: Resp = try await client.post("/api/v1/workflow/\(id)/runs", body: Body(userInput: input))
        return r.runId
    }

    func listWorkflowRuns(id: String) async throws -> [WorkflowRun] {
        try await client.get("/api/v1/workflow/\(id)/runs")
    }

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

    func listCards() async throws -> [FinancialCard] {
        try await client.get("/api/v1/financial/cards")
    }

    func listSalaryRecords() async throws -> [SalaryUsageRecord] {
        try await client.get("/api/v1/financial/salary")
    }

    func refreshPrices() async throws {
        let _: EmptyResponse = try await client.post("/api/v1/financial/prices/refresh", body: _EmptyBody())
    }
}

private struct _EmptyBody: Encodable {}
