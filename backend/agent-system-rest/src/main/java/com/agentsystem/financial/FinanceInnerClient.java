package com.agentsystem.financial;

import com.agentsystem.config.FinanceInnerProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Calls finance-inner's internal REST API on behalf of authenticated users.
 * Uses X-Finance-Key authentication — no JWT needed (mirrors StorageClient/AuthInnerClient).
 *
 * Response bodies are deserialized generically (Object/List/Map via Jackson) rather than
 * into the strongly-typed entity/DTO classes finance-inner uses internally — this module
 * only relays JSON through to its own callers, it never inspects these fields, so keeping
 * a second copy of ~450 lines of entity/DTO classes here just for type-fidelity isn't
 * worth the duplication risk. The wire format (and therefore the public API contract with
 * the frontend/mobile clients) is unchanged.
 *
 * Business-rule failures (id not owned by this user, etc.) come back as a 403 from
 * finance-inner and are re-thrown here as {@link SecurityException}, matching what the old
 * in-process FinancialServiceImpl used to throw — so FinancialController's existing
 * per-endpoint catch blocks don't need to change.
 */
@Component
public class FinanceInnerClient {

    private final RestClient restClient;

    public FinanceInnerClient(FinanceInnerProperties props) {
        this.restClient = RestClient.builder()
                .baseUrl(props.url())
                .defaultHeader("X-Finance-Key", props.serviceKey())
                .defaultStatusHandler(HttpStatusCode::is4xxClientError, (req, res) -> {
                    if (res.getStatusCode().value() == 403) {
                        throw new SecurityException("Not authorised");
                    }
                })
                .build();
    }

    private static final ParameterizedTypeReference<List<Object>> LIST_TYPE =
            new ParameterizedTypeReference<>() {};

    // ── Cash Deposits ─────────────────────────────────────────────────────────

    public List<Object> listDeposits(String ownerUuid, String currency) {
        return restClient.get()
                .uri("/internal/financial/deposits?ownerUuid={o}&currency={c}", ownerUuid, currency)
                .retrieve().body(LIST_TYPE);
    }

    public Object createDeposit(String ownerUuid, Map<String, Object> body) {
        return restClient.post()
                .uri("/internal/financial/deposits?ownerUuid={o}", ownerUuid)
                .body(body).retrieve().body(Object.class);
    }

    public Object updateDeposit(String id, String ownerUuid, Map<String, Object> body) {
        return restClient.put()
                .uri("/internal/financial/deposits/{id}?ownerUuid={o}", id, ownerUuid)
                .body(body).retrieve().body(Object.class);
    }

    public void deleteDeposit(String id, String ownerUuid) {
        restClient.delete()
                .uri("/internal/financial/deposits/{id}?ownerUuid={o}", id, ownerUuid)
                .retrieve().toBodilessEntity();
    }

    // ── Stocks ────────────────────────────────────────────────────────────────

    public List<Object> listStocks(String ownerUuid, String currency) {
        return restClient.get()
                .uri("/internal/financial/stocks?ownerUuid={o}&currency={c}", ownerUuid, currency)
                .retrieve().body(LIST_TYPE);
    }

    public Object createStock(String ownerUuid, Map<String, Object> body) {
        return restClient.post()
                .uri("/internal/financial/stocks?ownerUuid={o}", ownerUuid)
                .body(body).retrieve().body(Object.class);
    }

    public Object updateStock(String id, String ownerUuid, Map<String, Object> body) {
        return restClient.put()
                .uri("/internal/financial/stocks/{id}?ownerUuid={o}", id, ownerUuid)
                .body(body).retrieve().body(Object.class);
    }

    public void deleteStock(String id, String ownerUuid) {
        restClient.delete()
                .uri("/internal/financial/stocks/{id}?ownerUuid={o}", id, ownerUuid)
                .retrieve().toBodilessEntity();
    }

    private static final ParameterizedTypeReference<Map<String, String>> STRING_MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    public Map<String, String> lookupStock(String symbol) {
        Map<String, String> result = restClient.get()
                .uri("/internal/financial/stocks/lookup?symbol={s}", symbol)
                .retrieve().body(STRING_MAP_TYPE);
        return result != null ? result : Map.of();
    }

    // ── Crypto ────────────────────────────────────────────────────────────────

    public List<Object> listCrypto(String ownerUuid, String currency) {
        return restClient.get()
                .uri("/internal/financial/crypto?ownerUuid={o}&currency={c}", ownerUuid, currency)
                .retrieve().body(LIST_TYPE);
    }

    public Object createCrypto(String ownerUuid, Map<String, Object> body) {
        return restClient.post()
                .uri("/internal/financial/crypto?ownerUuid={o}", ownerUuid)
                .body(body).retrieve().body(Object.class);
    }

    public Object updateCrypto(String id, String ownerUuid, Map<String, Object> body) {
        return restClient.put()
                .uri("/internal/financial/crypto/{id}?ownerUuid={o}", id, ownerUuid)
                .body(body).retrieve().body(Object.class);
    }

    public void deleteCrypto(String id, String ownerUuid) {
        restClient.delete()
                .uri("/internal/financial/crypto/{id}?ownerUuid={o}", id, ownerUuid)
                .retrieve().toBodilessEntity();
    }

    // ── Futures ───────────────────────────────────────────────────────────────

    public List<Object> listFutures(String ownerUuid, String currency) {
        return restClient.get()
                .uri("/internal/financial/futures?ownerUuid={o}&currency={c}", ownerUuid, currency)
                .retrieve().body(LIST_TYPE);
    }

    public Object createFuture(String ownerUuid, Map<String, Object> body) {
        return restClient.post()
                .uri("/internal/financial/futures?ownerUuid={o}", ownerUuid)
                .body(body).retrieve().body(Object.class);
    }

    public Object updateFuture(String id, String ownerUuid, Map<String, Object> body) {
        return restClient.put()
                .uri("/internal/financial/futures/{id}?ownerUuid={o}", id, ownerUuid)
                .body(body).retrieve().body(Object.class);
    }

    public void deleteFuture(String id, String ownerUuid) {
        restClient.delete()
                .uri("/internal/financial/futures/{id}?ownerUuid={o}", id, ownerUuid)
                .retrieve().toBodilessEntity();
    }

    // ── Cards ─────────────────────────────────────────────────────────────────

    public List<Object> listCards(String ownerUuid) {
        return restClient.get()
                .uri("/internal/financial/cards?ownerUuid={o}", ownerUuid)
                .retrieve().body(LIST_TYPE);
    }

    public Object createCard(String ownerUuid, Map<String, Object> body) {
        return restClient.post()
                .uri("/internal/financial/cards?ownerUuid={o}", ownerUuid)
                .body(body).retrieve().body(Object.class);
    }

    public Object updateCard(String id, String ownerUuid, Map<String, Object> body) {
        return restClient.put()
                .uri("/internal/financial/cards/{id}?ownerUuid={o}", id, ownerUuid)
                .body(body).retrieve().body(Object.class);
    }

    public void deleteCard(String id, String ownerUuid) {
        restClient.delete()
                .uri("/internal/financial/cards/{id}?ownerUuid={o}", id, ownerUuid)
                .retrieve().toBodilessEntity();
    }

    // ── Salary Usage Records ──────────────────────────────────────────────────

    public List<Object> listSalary(String ownerUuid) {
        return restClient.get()
                .uri("/internal/financial/salary?ownerUuid={o}", ownerUuid)
                .retrieve().body(LIST_TYPE);
    }

    public Object createSalary(String ownerUuid, Map<String, Object> body) {
        return restClient.post()
                .uri("/internal/financial/salary?ownerUuid={o}", ownerUuid)
                .body(body).retrieve().body(Object.class);
    }

    public Object updateSalary(String id, String ownerUuid, Map<String, Object> body) {
        return restClient.put()
                .uri("/internal/financial/salary/{id}?ownerUuid={o}", id, ownerUuid)
                .body(body).retrieve().body(Object.class);
    }

    public void deleteSalary(String id, String ownerUuid) {
        restClient.delete()
                .uri("/internal/financial/salary/{id}?ownerUuid={o}", id, ownerUuid)
                .retrieve().toBodilessEntity();
    }

    // ── Price refresh ─────────────────────────────────────────────────────────

    public void refreshPrices(String ownerUuid) {
        restClient.post()
                .uri("/internal/financial/prices/refresh?ownerUuid={o}", ownerUuid)
                .retrieve().toBodilessEntity();
    }
}
