package com.agentsystem.alert.tool;

import com.agentsystem.agent.ToolCallBudget;
import com.agentsystem.alert.service.AlertRuleClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/**
 * Spring AI tool: register and manage investment (crypto/stock price, DeFi, and
 * prediction-market) alert rules from chat, e.g. "alert me when BTC hits $100k" or
 * "notify me if QQQ drops below $400".
 *
 * The caller's user_uuid is injected per-request via setCurrentUserUuid / clearCurrentUserUuid,
 * the same ThreadLocal pattern used by TelegramAgentTool and the Google Workspace tools.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertAgentTool {

    private final AlertRuleClient alertRuleClient;
    private final ToolCallBudget  toolCallBudget;

    private static final ThreadLocal<String> CURRENT_USER_UUID = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_ORG_ID    = new ThreadLocal<>();

    public void setCurrentUserUuid(String uuid) { CURRENT_USER_UUID.set(uuid != null ? uuid : ""); }
    public void clearCurrentUserUuid()          { CURRENT_USER_UUID.remove(); }

    public void setCurrentOrgId(String orgId)   { CURRENT_ORG_ID.set(orgId); }
    public void clearCurrentOrgId()             { CURRENT_ORG_ID.remove(); }

    @Tool(description = """
            Create a price alert for a crypto or stock symbol, notified by email and/or Telegram
            (if connected) when the threshold condition is met. Both crypto (e.g. "BTC/USD") and
            stock (e.g. "QQQ/USD") symbols are supported via Pyth price feeds.
            Use this when the user asks to "alert me when X hits $Y", "notify me if X goes above/below $Y",
            or similar for a cryptocurrency or stock/ETF price.
            You must resolve the correct Pyth price feed ID for the symbol before calling this tool —
            if you don't know it, tell the user you need the Pyth price feed ID instead of guessing.
            direction must be one of: >=, >, =, <=, <
            assetType must be CRYPTO or STOCK.
            Returns a confirmation string with the created rule's ID.
            """)
    public String createPriceAlert(
            @ToolParam(description = "Symbol, e.g. \"BTC/USD\" or \"QQQ/USD\"") String symbol,
            @ToolParam(description = "Pyth Hermes price feed ID for this symbol") String priceFeedId,
            @ToolParam(description = "CRYPTO or STOCK") String assetType,
            @ToolParam(description = "Threshold price") double threshold,
            @ToolParam(description = "Comparison operator: >=, >, =, <=, <") String direction) {

        if (!toolCallBudget.tryConsume()) return ToolCallBudget.EXHAUSTED_MESSAGE;
        String uuid = CURRENT_USER_UUID.get();
        String orgId = CURRENT_ORG_ID.get();
        log.info("[AlertAgentTool] Creating price alert symbol={} for user='{}'", symbol, uuid);
        try {
            Map<String, Object> rule = alertRuleClient.createPriceRule(
                    uuid, orgId, symbol, priceFeedId, assetType, threshold, direction, null);
            return "Alert created (id=%s). You'll be notified when %s %s %s.".formatted(
                    rule.get("id"), symbol, direction, threshold);
        } catch (RestClientException e) {
            return "Could not create alert: " + e.getMessage();
        }
    }

    @Tool(description = """
            Create a DeFi protocol alert (Aave, Morpho, Kamino, Pendle, or Hyperliquid), notified by
            email and/or Telegram when a metric (TVL, APY, UTILIZATION, or LIQUIDITY) crosses a threshold.
            Use this only when the user gives enough technical detail to identify the exact market/vault
            (protocol, chain, and the on-chain token/market/vault contract address) — otherwise ask for it
            rather than guessing.
            params should contain protocol-specific fields such as market_token_contract, vault_token_address,
            borrow_token_contract, collateral_token_contract, etc. as needed for the given protocol.
            Returns a confirmation string with the created rule's ID.
            """)
    public String createDeFiAlert(
            @ToolParam(description = "aave, morpho, kamino, pendle, or hyperliquid") String protocol,
            @ToolParam(description = "Protocol version, e.g. \"v3\", \"v1\", \"v2\"") String version,
            @ToolParam(description = "Chain ID, e.g. \"1\" (Ethereum), \"8453\" (Base), \"42161\" (Arbitrum)") String chainId,
            @ToolParam(description = "TVL, APY, UTILIZATION, or LIQUIDITY") String field,
            @ToolParam(description = "Threshold value") double threshold,
            @ToolParam(description = "Comparison operator: >=, >, =, <=, <") String direction,
            @ToolParam(description = "Protocol-specific parameters (market_token_contract, vault_token_address, etc.)")
            Map<String, Object> params) {

        if (!toolCallBudget.tryConsume()) return ToolCallBudget.EXHAUSTED_MESSAGE;
        String uuid = CURRENT_USER_UUID.get();
        String orgId = CURRENT_ORG_ID.get();
        log.info("[AlertAgentTool] Creating DeFi alert protocol={} for user='{}'", protocol, uuid);
        try {
            Map<String, Object> rule = alertRuleClient.createDeFiRule(
                    uuid, orgId, protocol, version, chainId, field, threshold, direction, params, null);
            return "Alert created (id=%s). You'll be notified when %s %s %s on %s.".formatted(
                    rule.get("id"), field, direction, threshold, protocol);
        } catch (RestClientException e) {
            return "Could not create alert: " + e.getMessage();
        }
    }

    @Tool(description = """
            Create a Polymarket prediction-market alert, notified by email and/or Telegram when the
            market's midpoint price crosses a threshold.
            Use this when the user asks to be alerted about a Polymarket prediction market's odds.
            You need the CLOB token ID for the specific outcome (YES/NO) being monitored.
            Returns a confirmation string with the created rule's ID.
            """)
    public String createPredictMarketAlert(
            @ToolParam(description = "CLOB token ID for the specific market outcome") String tokenId,
            @ToolParam(description = "Threshold midpoint price (0.0 to 1.0)") double threshold,
            @ToolParam(description = "Comparison operator: >=, >, =, <=, <") String direction) {

        if (!toolCallBudget.tryConsume()) return ToolCallBudget.EXHAUSTED_MESSAGE;
        String uuid = CURRENT_USER_UUID.get();
        String orgId = CURRENT_ORG_ID.get();
        log.info("[AlertAgentTool] Creating predict-market alert tokenId={} for user='{}'", tokenId, uuid);
        try {
            Map<String, Object> rule = alertRuleClient.createPredictMarketRule(
                    uuid, orgId, "polymarket", "MIDPOINT", threshold, direction,
                    Map.of("token_id", tokenId), null);
            return "Alert created (id=%s). You'll be notified when the market midpoint %s %s.".formatted(
                    rule.get("id"), direction, threshold);
        } catch (RestClientException e) {
            return "Could not create alert: " + e.getMessage();
        }
    }

    @Tool(description = """
            List all of the user's active investment alert rules (price, DeFi, and prediction-market).
            Use this when the user asks "what alerts do I have", "show my alerts", or similar.
            """)
    public String listMyAlerts() {
        if (!toolCallBudget.tryConsume()) return ToolCallBudget.EXHAUSTED_MESSAGE;
        String uuid = CURRENT_USER_UUID.get();
        try {
            Map<String, Object> result = alertRuleClient.listRules(uuid);
            return result.toString();
        } catch (RestClientException e) {
            return "Could not list alerts: " + e.getMessage();
        }
    }

    @Tool(description = """
            Delete an investment alert rule by its ID and type.
            Use this when the user asks to remove, delete, or cancel an alert.
            type must be one of: price, defi, predict-market.
            """)
    public String deleteAlert(
            @ToolParam(description = "price, defi, or predict-market") String type,
            @ToolParam(description = "The alert rule's ID") String id) {

        if (!toolCallBudget.tryConsume()) return ToolCallBudget.EXHAUSTED_MESSAGE;
        String uuid = CURRENT_USER_UUID.get();
        try {
            alertRuleClient.deleteRule(type, id, uuid);
            return "Alert %s deleted.".formatted(id);
        } catch (RestClientException e) {
            return "Could not delete alert: " + e.getMessage();
        }
    }
}
