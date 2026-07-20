package com.agentsystem.alert.controller;

import com.agentsystem.alert.service.AlertRuleClient;
import com.agentsystem.alert.service.PythFeedResolver;
import com.agentsystem.org.OrgContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/**
 * User-facing CRUD REST endpoints for investment (crypto/stock price, DeFi, and
 * prediction-market) alert rules. Proxies to the Go investment-alert-task service
 * via {@link AlertRuleClient}, scoping every call by the authenticated user
 * (see {@link OrgContext}).
 *
 * POST   /api/v1/alerts/price            → create a price (crypto or stock) alert rule
 * POST   /api/v1/alerts/defi             → create a DeFi protocol alert rule
 * POST   /api/v1/alerts/predict-market   → create a prediction-market alert rule
 * GET    /api/v1/alerts                  → { price: [...], defi: [...], predictMarket: [...] }
 * PATCH  /api/v1/alerts/{type}/{id}      → update threshold/direction/enabled/frequency
 * DELETE /api/v1/alerts/{type}/{id}      → delete a rule
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertRuleClient alertRuleClient;
    private final PythFeedResolver pythFeedResolver;

    @PostMapping("/price")
    public ResponseEntity<?> createPriceRule(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        OrgContext ctx = OrgContext.from(request);
        String symbol    = (String) body.get("symbol");
        String assetType = (String) body.get("assetType");
        String priceFeedId = (String) body.get("priceFeedId");

        if (priceFeedId == null || priceFeedId.isBlank()) {
            priceFeedId = pythFeedResolver.resolveFeedId(symbol, assetType).orElse(null);
            if (priceFeedId == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Could not find a Pyth price feed for " + symbol));
            }
        }

        try {
            var rule = alertRuleClient.createPriceRule(
                    ctx.userUuid(), ctx.orgId(),
                    symbol, priceFeedId, assetType,
                    toDouble(body.get("threshold")),
                    (String) body.get("direction"),
                    castFrequency(body.get("frequency")));
            return ResponseEntity.status(201).body(rule);
        } catch (RestClientException e) {
            log.warn("[AlertController] createPriceRule failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @SuppressWarnings("unchecked")
    @PostMapping("/defi")
    public ResponseEntity<?> createDeFiRule(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        OrgContext ctx = OrgContext.from(request);
        try {
            var rule = alertRuleClient.createDeFiRule(
                    ctx.userUuid(), ctx.orgId(),
                    (String) body.get("protocol"),
                    (String) body.get("version"),
                    (String) body.get("chainId"),
                    (String) body.get("field"),
                    toDouble(body.get("threshold")),
                    (String) body.get("direction"),
                    (Map<String, Object>) body.get("params"),
                    castFrequency(body.get("frequency")));
            return ResponseEntity.status(201).body(rule);
        } catch (RestClientException e) {
            log.warn("[AlertController] createDeFiRule failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @SuppressWarnings("unchecked")
    @PostMapping("/predict-market")
    public ResponseEntity<?> createPredictMarketRule(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        OrgContext ctx = OrgContext.from(request);
        try {
            var rule = alertRuleClient.createPredictMarketRule(
                    ctx.userUuid(), ctx.orgId(),
                    (String) body.get("predictMarket"),
                    (String) body.get("field"),
                    toDouble(body.get("threshold")),
                    (String) body.get("direction"),
                    (Map<String, Object>) body.get("params"),
                    castFrequency(body.get("frequency")));
            return ResponseEntity.status(201).body(rule);
        } catch (RestClientException e) {
            log.warn("[AlertController] createPredictMarketRule failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<?> listRules(HttpServletRequest request) {
        OrgContext ctx = OrgContext.from(request);
        try {
            return ResponseEntity.ok(alertRuleClient.listRules(ctx.userUuid()));
        } catch (RestClientException e) {
            log.warn("[AlertController] listRules failed: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to list alert rules"));
        }
    }

    @PatchMapping("/{type}/{id}")
    public ResponseEntity<?> updateRule(
            @PathVariable String type, @PathVariable String id,
            @RequestBody Map<String, Object> updates, HttpServletRequest request) {

        OrgContext ctx = OrgContext.from(request);
        try {
            return ResponseEntity.ok(alertRuleClient.updateRule(type, id, ctx.userUuid(), updates));
        } catch (RestClientException e) {
            log.warn("[AlertController] updateRule failed type={} id={}: {}", type, id, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{type}/{id}")
    public ResponseEntity<Void> deleteRule(
            @PathVariable String type, @PathVariable String id, HttpServletRequest request) {

        OrgContext ctx = OrgContext.from(request);
        try {
            alertRuleClient.deleteRule(type, id, ctx.userUuid());
            return ResponseEntity.noContent().build();
        } catch (RestClientException e) {
            log.warn("[AlertController] deleteRule failed type={} id={}: {}", type, id, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    private static double toDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        return Double.parseDouble(String.valueOf(v));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castFrequency(Object v) {
        return (Map<String, Object>) v;
    }
}
