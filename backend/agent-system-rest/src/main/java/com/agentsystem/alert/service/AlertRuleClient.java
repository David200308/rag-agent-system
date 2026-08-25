package com.agentsystem.alert.service;

import com.agentsystem.config.AlertProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Calls the Go investment-alert-task service's internal REST API on behalf of
 * REST controllers and agent tools. Uses X-Alert-Key authentication — no JWT
 * needed, since callers have already been authenticated by agent-system-rest.
 */
@Slf4j
@Component
public class AlertRuleClient {

    private final RestClient restClient;

    public AlertRuleClient(AlertProperties props) {
        this.restClient = RestClient.builder()
                .baseUrl(props.url())
                .defaultHeader("X-Alert-Key", props.serviceKey())
                .build();
    }

    /** Create a price (crypto or stock) alert rule. Returns the created rule as a Map. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> createPriceRule(String ownerUuid, String orgId, String symbol, String priceFeedId,
                                                String assetType, double threshold, String direction,
                                                Map<String, Object> frequency) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("ownerUuid",   ownerUuid);
        body.put("orgId",       orgId);
        body.put("symbol",      symbol);
        body.put("priceFeedId", priceFeedId);
        body.put("assetType",   assetType);
        body.put("threshold",   threshold);
        body.put("direction",   direction);
        if (frequency != null) body.put("frequency", frequency);

        return restClient.post()
                .uri("/internal/alerts/price")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);
    }

    /** Create a DeFi protocol alert rule. Returns the created rule as a Map. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> createDeFiRule(String ownerUuid, String orgId, String protocol, String version,
                                               String chainId, String field, double threshold, String direction,
                                               Map<String, Object> params, Map<String, Object> frequency) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("ownerUuid", ownerUuid);
        body.put("orgId",     orgId);
        body.put("protocol",  protocol);
        body.put("version",   version);
        body.put("chainId",   chainId);
        body.put("field",     field);
        body.put("threshold", threshold);
        body.put("direction", direction);
        body.put("params",    params != null ? params : Map.of());
        if (frequency != null) body.put("frequency", frequency);

        return restClient.post()
                .uri("/internal/alerts/defi")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);
    }

    /** Create a prediction-market alert rule. Returns the created rule as a Map. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> createPredictMarketRule(String ownerUuid, String orgId, String predictMarket,
                                                        String field, double threshold, String direction,
                                                        Map<String, Object> params, Map<String, Object> frequency) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("ownerUuid",      ownerUuid);
        body.put("orgId",          orgId);
        body.put("predictMarket",  predictMarket);
        body.put("field",          field);
        body.put("threshold",      threshold);
        body.put("direction",      direction);
        body.put("params",         params != null ? params : Map.of());
        if (frequency != null) body.put("frequency", frequency);

        return restClient.post()
                .uri("/internal/alerts/predict-market")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);
    }

    /** Lists all alert rules (price, defi, predictMarket) owned by ownerUuid. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> listRules(String ownerUuid) {
        return restClient.get()
                .uri("/internal/alerts?ownerUuid={uuid}", ownerUuid)
                .retrieve()
                .body(Map.class);
    }

    /** Updates threshold/direction/enabled/frequency on an existing rule. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> updateRule(String type, String id, String ownerUuid, Map<String, Object> updates) {
        Map<String, Object> body = new java.util.HashMap<>(updates);
        body.put("ownerUuid", ownerUuid);

        return restClient.patch()
                .uri("/internal/alerts/{type}/{id}", type, id)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);
    }

    /** Deletes an alert rule. Throws if the rule doesn't exist or isn't owned by ownerUuid. */
    public void deleteRule(String type, String id, String ownerUuid) {
        restClient.delete()
                .uri("/internal/alerts/{type}/{id}?ownerUuid={uuid}", type, id, ownerUuid)
                .retrieve()
                .toBodilessEntity();
    }
}
