package com.agentsystem.financial.controller;

import com.agentsystem.financial.FinanceInnerClient;
import com.agentsystem.user.service.UserPreferenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Thin proxy over finance-inner's internal API. Resolves ownerUuid (from the JWT, via
 * AuthFilter's request attribute) and defaultCurrency (from user preferences) locally —
 * finance-inner never needs to know about the user/org packages — then forwards to
 * {@link FinanceInnerClient}.
 */
@RestController
@RequestMapping("/api/v1/financial")
@RequiredArgsConstructor
@Tag(name = "Financial", description = "Financial portfolio management")
public class FinancialController {

    private final FinanceInnerClient    client;
    private final UserPreferenceService prefService;

    // ── Cash Deposits ─────────────────────────────────────────────────────────

    @GetMapping("/deposits")
    @Operation(summary = "List cash deposits with amounts converted to the user's default currency")
    public ResponseEntity<List<Object>> listDeposits(HttpServletRequest req) {
        return ResponseEntity.ok(client.listDeposits(ownerUuid(req), defaultCurrency(req)));
    }

    @PostMapping("/deposits")
    @Operation(summary = "Create a cash deposit")
    public ResponseEntity<Object> createDeposit(
            @RequestBody Map<String, Object> body, HttpServletRequest req) {
        return ResponseEntity.status(201).body(client.createDeposit(ownerUuid(req), body));
    }

    @PutMapping("/deposits/{id}")
    public ResponseEntity<Object> updateDeposit(
            @PathVariable String id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest req) {
        try {
            return ResponseEntity.ok(client.updateDeposit(id, ownerUuid(req), body));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    @DeleteMapping("/deposits/{id}")
    public ResponseEntity<Void> deleteDeposit(@PathVariable String id, HttpServletRequest req) {
        try {
            client.deleteDeposit(id, ownerUuid(req));
            return ResponseEntity.noContent().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    // ── Stocks ────────────────────────────────────────────────────────────────

    @GetMapping("/stocks")
    @Operation(summary = "List stocks with live prices and converted amounts")
    public ResponseEntity<List<Object>> listStocks(HttpServletRequest req) {
        return ResponseEntity.ok(client.listStocks(ownerUuid(req), defaultCurrency(req)));
    }

    @PostMapping("/stocks")
    public ResponseEntity<Object> createStock(
            @RequestBody Map<String, Object> body, HttpServletRequest req) {
        return ResponseEntity.status(201).body(client.createStock(ownerUuid(req), body));
    }

    @PutMapping("/stocks/{id}")
    public ResponseEntity<Object> updateStock(
            @PathVariable String id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest req) {
        try {
            return ResponseEntity.ok(client.updateStock(id, ownerUuid(req), body));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    @DeleteMapping("/stocks/{id}")
    public ResponseEntity<Void> deleteStock(@PathVariable String id, HttpServletRequest req) {
        try {
            client.deleteStock(id, ownerUuid(req));
            return ResponseEntity.noContent().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    @GetMapping("/stocks/lookup")
    @Operation(summary = "Look up a stock ticker's company display name, for Add Stock form auto-fill")
    public ResponseEntity<Map<String, String>> lookupStock(@RequestParam String symbol) {
        return ResponseEntity.ok(client.lookupStock(symbol));
    }

    // ── Crypto ────────────────────────────────────────────────────────────────

    @GetMapping("/crypto")
    @Operation(summary = "List crypto investments with live prices and converted amounts")
    public ResponseEntity<List<Object>> listCrypto(HttpServletRequest req) {
        return ResponseEntity.ok(client.listCrypto(ownerUuid(req), defaultCurrency(req)));
    }

    @PostMapping("/crypto")
    public ResponseEntity<Object> createCrypto(
            @RequestBody Map<String, Object> body, HttpServletRequest req) {
        return ResponseEntity.status(201).body(client.createCrypto(ownerUuid(req), body));
    }

    @PutMapping("/crypto/{id}")
    public ResponseEntity<Object> updateCrypto(
            @PathVariable String id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest req) {
        try {
            return ResponseEntity.ok(client.updateCrypto(id, ownerUuid(req), body));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    @DeleteMapping("/crypto/{id}")
    public ResponseEntity<Void> deleteCrypto(@PathVariable String id, HttpServletRequest req) {
        try {
            client.deleteCrypto(id, ownerUuid(req));
            return ResponseEntity.noContent().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    // ── Futures ───────────────────────────────────────────────────────────────

    @GetMapping("/futures")
    @Operation(summary = "List futures positions (Security/CEX manual entries + live-tracked Hyperliquid DEX positions)")
    public ResponseEntity<List<Object>> listFutures(HttpServletRequest req) {
        return ResponseEntity.ok(client.listFutures(ownerUuid(req), defaultCurrency(req)));
    }

    @PostMapping("/futures")
    public ResponseEntity<Object> createFuture(
            @RequestBody Map<String, Object> body, HttpServletRequest req) {
        return ResponseEntity.status(201).body(client.createFuture(ownerUuid(req), body));
    }

    @PutMapping("/futures/{id}")
    public ResponseEntity<Object> updateFuture(
            @PathVariable String id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest req) {
        try {
            return ResponseEntity.ok(client.updateFuture(id, ownerUuid(req), body));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    @DeleteMapping("/futures/{id}")
    public ResponseEntity<Void> deleteFuture(@PathVariable String id, HttpServletRequest req) {
        try {
            client.deleteFuture(id, ownerUuid(req));
            return ResponseEntity.noContent().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    // ── Cards ─────────────────────────────────────────────────────────────────

    @GetMapping("/cards")
    @Operation(summary = "List cards")
    public ResponseEntity<List<Object>> listCards(HttpServletRequest req) {
        return ResponseEntity.ok(client.listCards(ownerUuid(req)));
    }

    @PostMapping("/cards")
    @Operation(summary = "Create a card")
    public ResponseEntity<Object> createCard(
            @RequestBody Map<String, Object> body, HttpServletRequest req) {
        return ResponseEntity.status(201).body(client.createCard(ownerUuid(req), body));
    }

    @PutMapping("/cards/{id}")
    public ResponseEntity<Object> updateCard(
            @PathVariable String id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest req) {
        try {
            return ResponseEntity.ok(client.updateCard(id, ownerUuid(req), body));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    @DeleteMapping("/cards/{id}")
    public ResponseEntity<Void> deleteCard(@PathVariable String id, HttpServletRequest req) {
        try {
            client.deleteCard(id, ownerUuid(req));
            return ResponseEntity.noContent().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    // ── Salary Usage Records ──────────────────────────────────────────────────

    @GetMapping("/salary")
    @Operation(summary = "List salary usage records")
    public ResponseEntity<List<Object>> listSalary(HttpServletRequest req) {
        return ResponseEntity.ok(client.listSalary(ownerUuid(req)));
    }

    @PostMapping("/salary")
    @Operation(summary = "Create a salary usage record")
    public ResponseEntity<Object> createSalary(
            @RequestBody Map<String, Object> body, HttpServletRequest req) {
        return ResponseEntity.status(201).body(client.createSalary(ownerUuid(req), body));
    }

    @PutMapping("/salary/{id}")
    public ResponseEntity<Object> updateSalary(
            @PathVariable String id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest req) {
        try {
            return ResponseEntity.ok(client.updateSalary(id, ownerUuid(req), body));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    @DeleteMapping("/salary/{id}")
    public ResponseEntity<Void> deleteSalary(@PathVariable String id, HttpServletRequest req) {
        try {
            client.deleteSalary(id, ownerUuid(req));
            return ResponseEntity.noContent().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    // ── Price refresh ─────────────────────────────────────────────────────────

    @PostMapping("/prices/refresh")
    @Operation(summary = "Force-refresh live market prices for all of the user's symbols")
    public ResponseEntity<Map<String, String>> refreshPrices(HttpServletRequest req) {
        client.refreshPrices(ownerUuid(req));
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String ownerUuid(HttpServletRequest req) {
        String uuid = (String) req.getAttribute("authenticatedUserUuid");
        return uuid != null ? uuid : "anonymous";
    }

    private String defaultCurrency(HttpServletRequest req) {
        String cur = prefService.getOrDefault(ownerUuid(req)).getDefaultCurrency();
        return (cur != null && !cur.isBlank()) ? cur : "USD";
    }
}
