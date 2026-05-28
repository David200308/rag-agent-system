package com.ragagent.financial;

import com.ragagent.financial.dto.CardDto;
import com.ragagent.financial.dto.CashDepositDto;
import com.ragagent.financial.dto.CryptoInvestmentDto;
import com.ragagent.financial.dto.StockInvestmentDto;
import com.ragagent.financial.entity.Card;
import com.ragagent.financial.entity.CashDeposit;
import com.ragagent.financial.entity.CryptoInvestment;
import com.ragagent.financial.entity.StockInvestment;
import com.ragagent.user.UserPreferenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/financial")
@RequiredArgsConstructor
@Tag(name = "Financial", description = "Financial portfolio management")
public class FinancialController {

    private final FinancialService      service;
    private final UserPreferenceService prefService;

    // ── Cash Deposits ─────────────────────────────────────────────────────────

    @GetMapping("/deposits")
    @Operation(summary = "List cash deposits with amounts converted to the user's default currency")
    public ResponseEntity<List<CashDepositDto>> listDeposits(HttpServletRequest req) {
        return ResponseEntity.ok(service.listDeposits(email(req), defaultCurrency(req)));
    }

    @PostMapping("/deposits")
    @Operation(summary = "Create a cash deposit")
    public ResponseEntity<CashDeposit> createDeposit(
            @RequestBody Map<String, Object> body, HttpServletRequest req) {
        return ResponseEntity.status(201).body(service.createDeposit(email(req), body));
    }

    @PutMapping("/deposits/{id}")
    public ResponseEntity<CashDeposit> updateDeposit(
            @PathVariable String id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest req) {
        try {
            return ResponseEntity.ok(service.updateDeposit(id, email(req), body));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    @DeleteMapping("/deposits/{id}")
    public ResponseEntity<Void> deleteDeposit(@PathVariable String id, HttpServletRequest req) {
        try {
            service.deleteDeposit(id, email(req));
            return ResponseEntity.noContent().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    // ── Stocks ────────────────────────────────────────────────────────────────

    @GetMapping("/stocks")
    @Operation(summary = "List stocks with live prices and converted amounts")
    public ResponseEntity<List<StockInvestmentDto>> listStocks(HttpServletRequest req) {
        return ResponseEntity.ok(service.listStocks(email(req), defaultCurrency(req)));
    }

    @PostMapping("/stocks")
    public ResponseEntity<StockInvestment> createStock(
            @RequestBody Map<String, Object> body, HttpServletRequest req) {
        return ResponseEntity.status(201).body(service.createStock(email(req), body));
    }

    @PutMapping("/stocks/{id}")
    public ResponseEntity<StockInvestment> updateStock(
            @PathVariable String id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest req) {
        try {
            return ResponseEntity.ok(service.updateStock(id, email(req), body));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    @DeleteMapping("/stocks/{id}")
    public ResponseEntity<Void> deleteStock(@PathVariable String id, HttpServletRequest req) {
        try {
            service.deleteStock(id, email(req));
            return ResponseEntity.noContent().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    // ── Crypto ────────────────────────────────────────────────────────────────

    @GetMapping("/crypto")
    @Operation(summary = "List crypto investments with live prices and converted amounts")
    public ResponseEntity<List<CryptoInvestmentDto>> listCrypto(HttpServletRequest req) {
        return ResponseEntity.ok(service.listCrypto(email(req), defaultCurrency(req)));
    }

    @PostMapping("/crypto")
    public ResponseEntity<CryptoInvestment> createCrypto(
            @RequestBody Map<String, Object> body, HttpServletRequest req) {
        return ResponseEntity.status(201).body(service.createCrypto(email(req), body));
    }

    @PutMapping("/crypto/{id}")
    public ResponseEntity<CryptoInvestment> updateCrypto(
            @PathVariable String id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest req) {
        try {
            return ResponseEntity.ok(service.updateCrypto(id, email(req), body));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    @DeleteMapping("/crypto/{id}")
    public ResponseEntity<Void> deleteCrypto(@PathVariable String id, HttpServletRequest req) {
        try {
            service.deleteCrypto(id, email(req));
            return ResponseEntity.noContent().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    // ── Cards ─────────────────────────────────────────────────────────────────

    @GetMapping("/cards")
    @Operation(summary = "List cards")
    public ResponseEntity<List<CardDto>> listCards(HttpServletRequest req) {
        return ResponseEntity.ok(service.listCards(email(req)));
    }

    @PostMapping("/cards")
    @Operation(summary = "Create a card")
    public ResponseEntity<Card> createCard(
            @RequestBody Map<String, Object> body, HttpServletRequest req) {
        return ResponseEntity.status(201).body(service.createCard(email(req), body));
    }

    @PutMapping("/cards/{id}")
    public ResponseEntity<Card> updateCard(
            @PathVariable String id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest req) {
        try {
            return ResponseEntity.ok(service.updateCard(id, email(req), body));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    @DeleteMapping("/cards/{id}")
    public ResponseEntity<Void> deleteCard(@PathVariable String id, HttpServletRequest req) {
        try {
            service.deleteCard(id, email(req));
            return ResponseEntity.noContent().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    // ── Price refresh ─────────────────────────────────────────────────────────

    @PostMapping("/prices/refresh")
    @Operation(summary = "Force-refresh live market prices for all of the user's symbols")
    public ResponseEntity<Map<String, String>> refreshPrices(HttpServletRequest req) {
        service.refreshPrices(email(req));
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String email(HttpServletRequest req) {
        String email = (String) req.getAttribute("authenticatedEmail");
        return email != null ? email : "anonymous";
    }

    private String defaultCurrency(HttpServletRequest req) {
        String email = email(req);
        String cur   = prefService.getOrDefault(email).getDefaultCurrency();
        return (cur != null && !cur.isBlank()) ? cur : "USD";
    }
}
